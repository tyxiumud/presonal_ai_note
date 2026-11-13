package io.github.tyxiumud.personalainote.backup

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tyxiumud.personalainote.analysis.data.RoomDailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.data.AtomicDailyMarkdownStore
import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import io.github.tyxiumud.personalainote.chat.data.RoomChatRepository
import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import io.github.tyxiumud.personalainote.journal.data.LocalMediaStore
import io.github.tyxiumud.personalainote.journal.data.PersonalVaultDatabase
import io.github.tyxiumud.personalainote.journal.data.RoomCaptureRepository
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private val day = LocalDate.parse("2026-11-07")
    private val now = ZonedDateTime.of(2026, 11, 7, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai"))

    private fun chat(db: PersonalVaultDatabase) =
        RoomChatRepository(db.chatMessageDao(), db.conversationDao())

    @Test
    fun tagsSurviveMarkdownRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val media = LocalMediaStore(context)
        val captures = RoomCaptureRepository(db.captureDao(), media)
        val analyses = RoomDailyAnalysisRepository(
            db.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )
        val id = captures.addText("带标签的记录", day, now)
        captures.updateTags(id, listOf("工作", "灵感"))

        val archive = File(context.cacheDir, "tags-${System.nanoTime()}.zip")
        BackupRepository(context, captures, analyses, media, chat(db))
            .export(Uri.fromFile(archive), listOf(day))

        val zip = java.util.zip.ZipFile(archive)
        val md = zip.getInputStream(zip.getEntry("$day/notes.md"))
            .bufferedReader().readText()
        assertTrue(md.contains("标签：#工作 #灵感"))
        assertTrue(md.contains("tags="))
        zip.close()

        val targetDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val targetMedia = LocalMediaStore(context)
        val targetCaptures = RoomCaptureRepository(targetDb.captureDao(), targetMedia)
        val targetAnalyses = RoomDailyAnalysisRepository(
            targetDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )
        BackupRepository(context, targetCaptures, targetAnalyses, targetMedia, chat(targetDb))
            .import(Uri.fromFile(archive))

        assertEquals(listOf("工作", "灵感"), targetCaptures.getByDay(day).single().tags)

        archive.delete()
        db.close()
        targetDb.close()
    }

    @Test
    fun legacyJsonBackupCanStillBeImported() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val targetDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val targetMedia = LocalMediaStore(context)
        val targetCaptures = RoomCaptureRepository(targetDb.captureDao(), targetMedia)
        val targetAnalyses = RoomDailyAnalysisRepository(
            targetDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )

        val archive = File(context.cacheDir, "legacy-${System.nanoTime()}.zip")
        java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("manifest.json", """{"version":1,"app":"个人AI笔记","days":["2026-11-07"]}""")
            put(
                "days/2026-11-07.json",
                """{"day":"2026-11-07","captures":[{"type":"TEXT","text":"旧格式的一行","contentAtEpochMillis":1000,"importedAtEpochMillis":1000,"updatedAtEpochMillis":1000,"mediaEntry":null}]}""",
            )
        }

        val result = BackupRepository(context, targetCaptures, targetAnalyses, targetMedia, chat(targetDb))
            .import(Uri.fromFile(archive))

        assertEquals(1, result.dayCount)
        assertEquals("旧格式的一行", targetCaptures.getByDay(day).single().text)

        archive.delete()
        targetDb.close()
    }

    @Test
    fun exportThenImportIntoAnotherDatabaseRestoresCapturesMediaAndAnalysis() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val sourceMedia = LocalMediaStore(context)
        val targetMedia = LocalMediaStore(context)
        val sourceCaptures = RoomCaptureRepository(sourceDb.captureDao(), sourceMedia)
        val targetCaptures = RoomCaptureRepository(targetDb.captureDao(), targetMedia)
        val sourceAnalyses = RoomDailyAnalysisRepository(
            sourceDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )
        val targetAnalyses = RoomDailyAnalysisRepository(
            targetDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )

        val image = File(context.filesDir, "backup-test-${System.nanoTime()}.jpg")
        image.writeBytes(byteArrayOf(1, 2, 3, 4))
        sourceCaptures.addText("早上写了点东西", day, now)
        val longNote = """
            一段比较长的随记，用来验证 Markdown 导出不会破坏正文。

            ## 这是笔记自己的小标题

            正文里可以有 - 列表、[链接](https://example.com) 和 `代码`。
            还可以有多行：
            第二行
            第三行

            <!-- vault-capture type=TEXT at=1 -->
        """.trimIndent()
        sourceCaptures.addText(longNote, day, now)
        sourceCaptures.addMedia(CaptureType.IMAGE, image.absolutePath, "", day, now)
        sourceAnalyses.save(
            DailyAnalysis(
                day = day,
                markdownPath = "analysis/daily/$day.md",
                markdown = "# 2026-11-07 每日整理\n\n摘要内容",
                generatedAt = Instant.EPOCH,
                sourceFingerprint = "fingerprint",
                modelName = "demo",
            ),
        )

        val archive = File(context.cacheDir, "backup-test-${System.nanoTime()}.zip")
        val exporter = BackupRepository(
            context, sourceCaptures, sourceAnalyses, sourceMedia, chat(sourceDb),
        )
        val exported = exporter.export(Uri.fromFile(archive), listOf(day))
        assertEquals(1, exported.dayCount)
        assertEquals(3, exported.captureCount)
        assertTrue(archive.length() > 0)

        // 检查 zip 里确实是「每天一个文件夹 + Markdown」，且长文本与自带标题完好
        val zip = java.util.zip.ZipFile(archive)
        val names = zip.entries().toList().map { it.name }
        assertTrue(names.contains("README.md"))
        assertTrue(names.contains("$day/notes.md"))
        assertTrue(names.contains("$day/analysis.md"))
        val notesMd = zip.getInputStream(zip.getEntry("$day/notes.md"))
            .bufferedReader().readText()
        assertTrue(notesMd.contains("## 这是笔记自己的小标题"))
        assertTrue(notesMd.contains("[链接](https://example.com)"))
        assertTrue(notesMd.contains("第三行"))
        assertTrue(
            zip.getInputStream(zip.getEntry("README.md"))
                .bufferedReader().readText().contains("格式版本：2"),
        )
        zip.close()

        val importer = BackupRepository(context, targetCaptures, targetAnalyses, targetMedia, chat(targetDb))
        val imported = importer.import(Uri.fromFile(archive))

        assertEquals(1, imported.dayCount)
        assertEquals(3, imported.captureCount)
        val restored = targetCaptures.getByDay(day)
        assertEquals(3, restored.size)
        assertTrue(restored.any { it.text.contains("## 这是笔记自己的小标题") })
        assertTrue(restored.any { it.text.contains("[链接](https://example.com)") })
        assertTrue(restored.any { it.text.contains("第三行") })
        val restoredImage = restored.single { it.type == CaptureType.IMAGE }
        assertTrue(restoredImage.mediaPath?.let { File(it).exists() } == true)
        assertTrue(restoredImage.mediaPath != image.absolutePath)
        assertTrue(targetAnalyses.observeByDay(day).first()?.markdown.orEmpty().contains("摘要内容"))

        // 再导一次同一天，本地已有记录应整日跳过
        val again = importer.import(Uri.fromFile(archive))
        assertEquals(0, again.dayCount)
        assertEquals(listOf(day), again.skippedDays)
        assertEquals(3, targetCaptures.getByDay(day).size)

        archive.delete()
        image.delete()
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun chatConversationsAndMessagesSurviveBackupRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, PersonalVaultDatabase::class.java)
            .allowMainThreadQueries().build()
        val media = LocalMediaStore(context)
        val sourceCaptures = RoomCaptureRepository(sourceDb.captureDao(), media)
        val targetCaptures = RoomCaptureRepository(targetDb.captureDao(), media)
        val sourceAnalyses = RoomDailyAnalysisRepository(
            sourceDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )
        val targetAnalyses = RoomDailyAnalysisRepository(
            targetDb.dailyAnalysisDao(),
            AtomicDailyMarkdownStore(context),
        )
        val sourceChat = chat(sourceDb)
        val targetChat = chat(targetDb)

        val first = sourceChat.createConversation()
        val second = sourceChat.createConversation()
        val at = Instant.parse("2026-11-10T09:00:00Z")
        sourceChat.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "这周在忙什么",
                createdAt = at,
                conversationId = first.id,
            ),
        )
        sourceChat.append(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "在准备考试。\n第二行还在写\n<!-- vault-message conversation=99 -->",
                createdAt = at.plusMillis(1_000),
                referencedDays = listOf(day),
                referencedCaptureIds = listOf(7L),
                conversationId = first.id,
            ),
        )
        sourceChat.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "另一个话题",
                createdAt = at,
                conversationId = second.id,
            ),
        )
        sourceCaptures.addText("一条随记", day, now)

        val archive = File(context.cacheDir, "chat-backup-${System.nanoTime()}.zip")
        val exported = BackupRepository(
            context, sourceCaptures, sourceAnalyses, media, sourceChat,
        ).export(Uri.fromFile(archive), listOf(day))
        assertEquals(2, exported.conversationCount)
        assertEquals(3, exported.messageCount)

        val zip = java.util.zip.ZipFile(archive)
        val conversationMd = zip.getInputStream(zip.getEntry("chat/conversations.md"))
            .bufferedReader().readText()
        assertTrue(conversationMd.contains("在准备考试。"))
        assertTrue(conversationMd.contains("<!-- vault-message"))
        zip.close()

        val importer = BackupRepository(
            context, targetCaptures, targetAnalyses, media, targetChat,
        )
        val imported = importer.import(Uri.fromFile(archive))
        assertEquals(2, imported.conversationCount)
        assertEquals(3, imported.messageCount)

        val restored = targetChat.exportAll()
        assertEquals(2, restored.conversations.size)
        assertEquals(3, restored.messages.size)
        val answer = restored.messages.single { it.role == ChatRole.ASSISTANT }
        assertEquals(
            "在准备考试。\n第二行还在写\n<!-- vault-message conversation=99 -->",
            answer.content,
        )
        assertEquals(listOf(day), answer.referencedDays)
        assertEquals(listOf(7L), answer.referencedCaptureIds)
        // 会话 id 在导入时重新分配，消息仍归属到各自的会话
        assertEquals(2, restored.messages.map(ChatMessage::conversationId).distinct().size)

        // 重复导入同一个包：会话不翻倍
        val again = importer.import(Uri.fromFile(archive))
        assertEquals(0, again.conversationCount)
        assertEquals(0, again.messageCount)
        assertEquals(2, targetChat.exportAll().conversations.size)

        archive.delete()
        sourceDb.close()
        targetDb.close()
    }
}
