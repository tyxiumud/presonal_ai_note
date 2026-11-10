package com.example.test0.backup

import android.content.Context
import android.net.Uri
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.chat.data.ChatBackupData
import com.example.test0.chat.data.ChatRepository
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.ChatRole
import com.example.test0.chat.model.Conversation
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.data.MediaStore
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 按日期导出/导入。zip 里每一天是一个文件夹，内容是给人读的 Markdown：
 *
 * ```text
 * README.md
 * 2026-11-07/
 *   notes.md          当天的全部记录，长文本原样保留
 *   analysis.md       AI 整理结果（如果有）
 *   media/           图片、语音原文件
 * ```
 *
 * 元数据写在 HTML 注释里：Markdown 渲染时看不见，导入时用来无损还原
 * 时间、类型、媒体路径和整理结果的指纹。不写绝对路径，换设备才不会失效。
 */
class BackupRepository(
    context: Context,
    private val captureRepository: CaptureRepository,
    private val analysisRepository: DailyAnalysisRepository,
    private val mediaStore: MediaStore,
    private val chatRepository: ChatRepository,
) {
    private val applicationContext = context.applicationContext

    suspend fun export(uri: Uri, days: List<LocalDate>): BackupResult = withContext(Dispatchers.IO) {
        val analyses = analysisRepository.observeAll().first().associateBy(DailyAnalysis::day)
        val chat = chatRepository.exportAll()
        var captureCount = 0

        val output = applicationContext.contentResolver.openOutputStream(uri)
            ?: throw BackupException("无法创建备份文件")
        output.use { fileStream ->
            ZipOutputStream(BufferedOutputStream(fileStream)).use { zip ->
                writeText(zip, "README.md", buildReadme(days))
                writeText(zip, CHAT_ENTRY, renderConversations(chat))

                days.forEach { day ->
                    val captures = captureRepository.getByDay(day)
                    captureCount += captures.size
                    val dayDir = day.toString()
                    val mediaByCapture = mutableMapOf<Long, String>()

                    captures.forEach { capture ->
                        val path = capture.mediaPath ?: return@forEach
                        val file = mediaStore.file(path)
                        if (!file.exists()) return@forEach
                        val entryName = "$dayDir/media/${capture.id}_${file.name}${extensionFor(capture.type)}"
                        mediaByCapture[capture.id] = entryName
                        zip.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    writeText(
                        zip,
                        "$dayDir/notes.md",
                        renderNotes(day, captures, mediaByCapture.mapValues { (_, entry) ->
                            entry.removePrefix("$dayDir/")
                        }),
                    )

                    analyses[day]?.let { analysis ->
                        writeText(zip, "$dayDir/analysis.md", renderAnalysis(analysis))
                    }
                }
            }
        }
        BackupResult(
            dayCount = days.size,
            captureCount = captureCount,
            conversationCount = chat.conversations.size,
            messageCount = chat.messages.size,
        )
    }

    suspend fun import(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val entries = readEntries(uri)
        val result = when {
            entries.containsKey(LEGACY_MANIFEST) -> importLegacy(entries)
            entries.containsKey("README.md") -> importMarkdown(entries)
            else -> throw BackupException("这不是个人AI笔记的备份包")
        }
        // 聊天数据是格式 v2 之后追加的可选条目：旧备份没有就跳过；
        // 同一个包重复导入时由仓库按「标题 + 创建时间」去重，不会翻倍。
        val chatMarkdown = entries[CHAT_ENTRY]?.toString(Charsets.UTF_8)
        if (chatMarkdown == null) {
            result
        } else {
            val restored = chatRepository.restoreAll(parseConversations(chatMarkdown))
            result.copy(
                conversationCount = restored.conversationCount,
                messageCount = restored.messageCount,
            )
        }
    }

    // ---------------------------------------------------------------------
    // 新格式：Markdown
    // ---------------------------------------------------------------------

    private suspend fun importMarkdown(entries: Map<String, ByteArray>): BackupResult {
        val readme = entries.getValue("README.md").toString(Charsets.UTF_8)
        if (!readme.contains("格式版本：${FORMAT_VERSION}")) {
            throw BackupException("备份包版本不兼容")
        }

        var captureCount = 0
        var importedDays = 0
        val skippedDays = mutableListOf<LocalDate>()
        val noteEntries = entries.keys
            .filter { it.matches(Regex("""\d{4}-\d{2}-\d{2}/notes\.md""")) }
            .sorted()

        noteEntries.forEach { entryName ->
            val day = LocalDate.parse(entryName.substringBefore('/'))
            if (captureRepository.getByDay(day).isNotEmpty()) {
                skippedDays += day
                return@forEach
            }
            val markdown = entries.getValue(entryName).toString(Charsets.UTF_8)
            val parsed = parseNotes(markdown, day)
            val restored = parsed.map { item ->
                val mediaPath = item.mediaEntry?.let { name ->
                    val fullEntry = "${day}/$name"
                    val bytes = entries[fullEntry] ?: throw BackupException("$day 的媒体文件缺失")
                    mediaStore.writeMedia(name.removePrefix("media/"), bytes)
                        ?: throw BackupException("无法保存媒体文件")
                }
                Capture(
                    id = 0,
                    text = item.text,
                    contentAt = Instant.ofEpochMilli(item.contentAt),
                    importedAt = Instant.ofEpochMilli(item.importedAt),
                    updatedAt = Instant.ofEpochMilli(item.updatedAt),
                    dayKey = day,
                    type = item.type,
                    mediaPath = mediaPath,
                    tags = item.tags,
                )
            }
            captureRepository.restore(restored)
            captureCount += restored.size

            entries["${day}/analysis.md"]?.toString(Charsets.UTF_8)?.let { content ->
                parseAnalysis(content)?.let { analysis ->
                    if (analysisRepository.observeByDay(day).first() == null) {
                        analysisRepository.save(
                            analysis.copy(
                                day = day,
                                markdownPath = "analysis/daily/$day.md",
                            ),
                        )
                    }
                }
            }
            importedDays += 1
        }

        return BackupResult(importedDays, captureCount, skippedDays)
    }

    // ---------------------------------------------------------------------
    // 旧格式：JSON（保留兼容，老备份仍可导入）
    // ---------------------------------------------------------------------

    private suspend fun importLegacy(entries: Map<String, ByteArray>): BackupResult {
        val manifest = runCatching { JSONObject(entries.getValue(LEGACY_MANIFEST).toString(Charsets.UTF_8)) }
            .getOrElse { throw BackupException("备份清单已损坏") }
        if (manifest.optInt("version") != LEGACY_VERSION) {
            throw BackupException("备份包版本不兼容")
        }

        var captureCount = 0
        val skippedDays = mutableListOf<LocalDate>()
        var importedDays = 0
        val dayEntries = entries.keys
            .filter { it.startsWith("days/") && it.endsWith(".json") }
            .sorted()

        dayEntries.forEach { entryName ->
            val day = runCatching {
                LocalDate.parse(entryName.removePrefix("days/").removeSuffix(".json"))
            }.getOrNull() ?: return@forEach
            if (captureRepository.getByDay(day).isNotEmpty()) {
                skippedDays += day
                return@forEach
            }
            val json = runCatching { JSONObject(entries.getValue(entryName).toString(Charsets.UTF_8)) }
                .getOrElse { throw BackupException("$day 的记录已损坏") }
            val captureArray = json.optJSONArray("captures") ?: JSONArray()
            val restored = mutableListOf<Capture>()
            for (index in 0 until captureArray.length()) {
                val item = captureArray.getJSONObject(index)
                val mediaEntry = item.optString("mediaEntry")
                    .takeUnless { it.isBlank() || it == "null" }
                val mediaPath = mediaEntry?.let { name ->
                    val bytes = entries[name] ?: throw BackupException("$day 的媒体文件缺失")
                    mediaStore.writeMedia(name.removePrefix("media/"), bytes)
                        ?: throw BackupException("无法保存媒体文件")
                }
                restored += Capture(
                    id = 0,
                    text = item.optString("text"),
                    contentAt = Instant.ofEpochMilli(item.getLong("contentAtEpochMillis")),
                    importedAt = Instant.ofEpochMilli(item.getLong("importedAtEpochMillis")),
                    updatedAt = Instant.ofEpochMilli(item.getLong("updatedAtEpochMillis")),
                    dayKey = day,
                    type = runCatching { CaptureType.valueOf(item.optString("type")) }
                        .getOrDefault(CaptureType.TEXT),
                    mediaPath = mediaPath,
                )
            }
            captureRepository.restore(restored)
            captureCount += restored.size

            json.optJSONObject("analysis")?.let { analysisJson ->
                if (analysisRepository.observeByDay(day).first() == null) {
                    analysisRepository.save(
                        DailyAnalysis(
                            day = day,
                            markdownPath = "analysis/daily/$day.md",
                            markdown = analysisJson.optString("markdown"),
                            generatedAt = Instant.ofEpochMilli(
                                analysisJson.getLong("generatedAtEpochMillis"),
                            ),
                            sourceFingerprint = analysisJson.optString("sourceFingerprint"),
                            modelName = analysisJson.optString("modelName"),
                        ),
                    )
                }
            }
            importedDays += 1
        }
        return BackupResult(importedDays, captureCount, skippedDays)
    }

    // ---------------------------------------------------------------------
    // Markdown 渲染
    // ---------------------------------------------------------------------

    private fun buildReadme(days: List<LocalDate>): String = buildString {
        appendLine("# 个人AI笔记 · 备份")
        appendLine()
        appendLine("导出时间：${Instant.now()}")
        appendLine("格式版本：$FORMAT_VERSION")
        appendLine("包含日期：${days.sorted().joinToString("、")}")
        appendLine()
        appendLine("## 目录结构")
        appendLine()
        appendLine("每一天是一个文件夹，聊天会话单独放在 `chat/`：")
        appendLine()
        appendLine("```text")
        appendLine("chat/")
        appendLine("  conversations.md  全部聊天会话与消息（含参考日期与引用记录）")
        appendLine("2026-11-07/")
        appendLine("  notes.md          当天全部随记，按时间排列，长文本原样保留")
        appendLine("  analysis.md       AI 生成的整理结果（如果有）")
        appendLine("  media/           图片、语音原文件")
        appendLine("```")
        appendLine()
        appendLine("`notes.md` 与 `conversations.md` 都是给人读的 Markdown，可以直接用任何笔记软件打开。")
        appendLine("每条记录 / 每条消息上方的 `<!-- ... -->` HTML 注释渲染时看不见，")
        appendLine("只在重新导入时用来还原时间、类型、标签、媒体文件和引用。")
        appendLine()
        appendLine("## 导入")
        appendLine()
        appendLine("在 App 右上角「备份与迁移」里选「导入备份包」，选中这个 zip 即可。")
        appendLine("本地已有记录的日期会整日跳过，不会产生重复随记；重复的聊天会话也不会被再次添加。")
        appendLine("旧版备份没有 `chat/` 目录，仍然可以正常导入。")
    }

    private fun renderNotes(
        day: LocalDate,
        captures: List<Capture>,
        media: Map<Long, String>,
    ): String = buildString {
        appendLine("# ${day} 随记")
        appendLine()
        appendLine("共 ${captures.size} 条。")
        appendLine()

        // 按时间正序，读起来像一天的时间线
        captures.sortedBy(Capture::contentAt).forEach { capture ->
            val time = timeFormatter.format(capture.contentAt)
            val typeLabel = when (capture.type) {
                CaptureType.TEXT -> "文字"
                CaptureType.IMAGE -> "图片"
                CaptureType.AUDIO -> "语音"
            }
            val mediaName = media[capture.id]
            appendLine("<!-- ${metaHeader(capture, mediaName)} -->")
            appendLine()
            appendLine("## $time · $typeLabel")
            appendLine()
            if (capture.tags.isNotEmpty()) {
                appendLine("标签：${capture.tags.joinToString(" ") { "#$it" }}")
                appendLine()
            }
            mediaName?.let { name ->
                when (capture.type) {
                    CaptureType.IMAGE -> appendLine("![$typeLabel]($name)")
                    CaptureType.AUDIO -> appendLine("[$typeLabel]($name)")
                    CaptureType.TEXT -> Unit
                }
                appendLine()
            }
            // 长文本原样写出，不做转义、不截断；
            // 只把恰好以元数据注释开头的行加零宽空格，避免导入时被误判为下一条记录。
            appendLine(escapeMetaLikeLines(capture.text.ifBlank { "（无文字内容）" }))
            appendLine()
        }
    }

    private fun renderAnalysis(analysis: DailyAnalysis): String = buildString {
        appendLine("<!-- vault-analysis generatedAt=${analysis.generatedAt.toEpochMilli()} " +
            "fingerprint=${enc(analysis.sourceFingerprint)} model=${enc(analysis.modelName)} -->")
        appendLine()
        append(analysis.markdown)
        if (!analysis.markdown.endsWith("\n")) appendLine()
    }

    private fun renderConversations(data: ChatBackupData): String = buildString {
        appendLine("# 聊天会话")
        appendLine()
        appendLine("共 ${data.conversations.size} 个会话、${data.messages.size} 条消息。")
        appendLine()
        val messagesByConversation = data.messages.groupBy(ChatMessage::conversationId)
        data.conversations.sortedBy(Conversation::id).forEach { conversation ->
            appendLine(
                "<!-- vault-conversation id=${conversation.id} " +
                    "title=${enc(conversation.title)} " +
                    "created=${conversation.createdAt.toEpochMilli()} " +
                    "updated=${conversation.updatedAt.toEpochMilli()} -->",
            )
            appendLine()
            appendLine("## ${conversation.title}")
            appendLine()
            val messages = messagesByConversation[conversation.id].orEmpty()
            if (messages.isEmpty()) {
                appendLine("（这个会话还没有消息）")
                appendLine()
            }
            messages.forEach { message ->
                appendLine(
                    "<!-- vault-message conversation=${message.conversationId} " +
                        "role=${message.role.name} " +
                        "at=${message.createdAt.toEpochMilli()} " +
                        "days=${enc(message.referencedDays.joinToString(",") { it.toString() })} " +
                        "captures=${enc(
                            message.referencedCaptureIds.joinToString(",") { it.toString() },
                        )} -->",
                )
                appendLine()
                val roleLabel = if (message.role == ChatRole.USER) "我" else "小札"
                val reference = if (message.referencedDays.isEmpty()) {
                    ""
                } else {
                    " · 参考：" + message.referencedDays.joinToString("、") {
                        it.format(chatDayFormatter)
                    }
                }
                appendLine("### $roleLabel · ${chatTimeFormatter.format(message.createdAt)}$reference")
                appendLine()
                appendLine(escapeMetaLikeLines(message.content.ifBlank { "（空消息）" }))
                appendLine()
            }
        }
    }

    private fun parseConversations(markdown: String): ChatBackupData {
        val lines = markdown.lines()
        val conversations = mutableListOf<Conversation>()
        val messages = mutableListOf<ChatMessage>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.startsWith("<!-- vault-conversation ") -> {
                    val meta = parseMeta(line.removePrefix("<!-- ").removeSuffix(" -->"))
                    conversations += Conversation(
                        id = meta["id"]?.toLongOrNull() ?: 0L,
                        title = dec(meta["title"].orEmpty()),
                        createdAt = Instant.ofEpochMilli(meta["created"]?.toLongOrNull() ?: 0L),
                        updatedAt = Instant.ofEpochMilli(meta["updated"]?.toLongOrNull() ?: 0L),
                    )
                    index += 1
                }

                line.startsWith("<!-- vault-message ") -> {
                    val meta = parseMeta(line.removePrefix("<!-- ").removeSuffix(" -->"))
                    index += 1
                    val body = mutableListOf<String>()
                    while (index < lines.size &&
                        !lines[index].startsWith("<!-- vault-conversation ") &&
                        !lines[index].startsWith("<!-- vault-message ")
                    ) {
                        body += lines[index]
                        index += 1
                    }
                    var cursor = 0
                    while (cursor < body.size && body[cursor].isBlank()) cursor += 1
                    // 只跳过导出时生成的「### 我/小札 · ...」标题行，正文原样保留。
                    if (cursor < body.size && CHAT_HEADING.matches(body[cursor])) {
                        cursor += 1
                    }
                    val content = body.subList(cursor, body.size)
                        .joinToString("\n") { unescapeMetaLikeLine(it) }
                        .trim()
                        .removeSuffix("（空消息）")
                        .trim()
                    val days = dec(meta["days"].orEmpty())
                        .split(',')
                        .mapNotNull { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
                    val captures = dec(meta["captures"].orEmpty())
                        .split(',')
                        .mapNotNull { it.trim().toLongOrNull() }
                    messages += ChatMessage(
                        role = when (meta["role"]) {
                            "ASSISTANT" -> ChatRole.ASSISTANT
                            else -> ChatRole.USER
                        },
                        content = content,
                        createdAt = Instant.ofEpochMilli(meta["at"]?.toLongOrNull() ?: 0L),
                        referencedDays = days,
                        referencedCaptureIds = captures,
                        conversationId = meta["conversation"]?.toLongOrNull() ?: 0L,
                    )
                }

                else -> index += 1
            }
        }
        return ChatBackupData(conversations, messages)
    }

    private fun metaHeader(capture: Capture, mediaName: String?): String = buildString {
        append("vault-capture type=${capture.type.name}")
        append(" at=${capture.contentAt.toEpochMilli()}")
        append(" imported=${capture.importedAt.toEpochMilli()}")
        append(" updated=${capture.updatedAt.toEpochMilli()}")
        append(" media=${enc(mediaName.orEmpty())}")
        append(" tags=${enc(capture.tags.joinToString(","))}")
    }

    // ---------------------------------------------------------------------
    // Markdown 解析
    // ---------------------------------------------------------------------

    private fun parseNotes(markdown: String, day: LocalDate): List<ParsedCapture> {
        val lines = markdown.lines()
        val result = mutableListOf<ParsedCapture>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("<!-- vault-capture ")) {
                index += 1
                continue
            }
            val meta = parseMeta(line.removePrefix("<!-- ").removeSuffix(" -->"))
            val type = runCatching { CaptureType.valueOf(meta["type"].orEmpty()) }
                .getOrDefault(CaptureType.TEXT)
            val contentAt = meta["at"]?.toLongOrNull()
                ?: day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val importedAt = meta["imported"]?.toLongOrNull() ?: contentAt
            val updatedAt = meta["updated"]?.toLongOrNull() ?: contentAt
            val mediaEntry = meta["media"]?.let(::dec)?.takeIf(String::isNotBlank)
            val tags = meta["tags"]?.let(::dec)
                ?.split(',')
                .orEmpty()
                .map(String::trim)
                .filter(String::isNotEmpty)

            // 收集到下一个 capture 注释之前的所有内容。
            // 只精确跳过导出时生成的「## HH:mm · 类型」标题和紧跟的媒体引用，
            // 正文里的 Markdown 标题、链接、列表一律原样保留（长笔记会用到）。
            index += 1
            val bodyLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].startsWith("<!-- vault-capture ")) {
                bodyLines += lines[index]
                index += 1
            }
            var cursor = 0
            while (cursor < bodyLines.size && bodyLines[cursor].isBlank()) cursor += 1
            if (cursor < bodyLines.size && TIME_HEADING.matches(bodyLines[cursor])) {
                cursor += 1
                while (cursor < bodyLines.size && bodyLines[cursor].isBlank()) cursor += 1
                if (cursor < bodyLines.size && bodyLines[cursor].startsWith("标签：")) {
                    cursor += 1
                    while (cursor < bodyLines.size && bodyLines[cursor].isBlank()) cursor += 1
                }
                if (cursor < bodyLines.size && MEDIA_LINK.matches(bodyLines[cursor])) {
                    cursor += 1
                }
            }
            val text = bodyLines.subList(cursor, bodyLines.size)
                .joinToString("\n") { unescapeMetaLikeLine(it) }
                .trim()
                .removeSuffix("（无文字内容）")
                .trim()

            result += ParsedCapture(type, text, contentAt, importedAt, updatedAt, mediaEntry, tags)
        }
        return result
    }

    private fun parseAnalysis(markdown: String): DailyAnalysis? {
        val marker = markdown.lineSequence()
            .firstOrNull { it.startsWith("<!-- vault-analysis ") }
            ?: return null
        val meta = parseMeta(marker.removePrefix("<!-- ").removeSuffix(" -->"))
        val body = markdown.lineSequence()
            .filterNot { it.startsWith("<!-- vault-analysis ") }
            .joinToString("\n")
            .trim()
        if (body.isBlank()) return null
        return DailyAnalysis(
            day = LocalDate.of(1970, 1, 1), // importMarkdown 会用真实日期覆盖
            markdownPath = "",
            markdown = body,
            generatedAt = Instant.ofEpochMilli(meta["generatedAt"]?.toLongOrNull() ?: 0L),
            sourceFingerprint = dec(meta["fingerprint"].orEmpty()),
            modelName = dec(meta["model"].orEmpty()),
        )
    }

    private fun parseMeta(raw: String): Map<String, String> = raw
        .substringAfter(' ')
        .split(' ')
        .mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }
        .toMap()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun dec(value: String): String = URLDecoder.decode(value, "UTF-8")

    // 四种元数据注释统一转义，避免随记或聊天正文里恰好以 <!-- vault- 开头的行
    // 被导入时误判成「下一条记录 / 下一条消息」。
    private fun escapeMetaLikeLines(text: String): String = text
        .lineSequence()
        .joinToString("\n") { line ->
            if (line.startsWith("<!-- vault-")) "\u200B$line" else line
        }

    private fun unescapeMetaLikeLine(line: String): String =
        line.removePrefix("\u200B")

    private fun extensionFor(type: CaptureType): String = when (type) {
        CaptureType.IMAGE -> ".jpg"
        CaptureType.AUDIO -> ".m4a"
        CaptureType.TEXT -> ""
    }

    // ---------------------------------------------------------------------

    private fun readEntries(uri: Uri): Map<String, ByteArray> {
        val input = applicationContext.contentResolver.openInputStream(uri)
            ?: throw BackupException("无法读取备份文件")
        val result = mutableMapOf<String, ByteArray>()
        var total = 0L
        input.use { stream ->
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8_192)
                    while (true) {
                        val count = zip.read(chunk)
                        if (count < 0) break
                        total += count
                        if (total > MAX_TOTAL_BYTES) {
                            throw BackupException("备份包过大，无法导入")
                        }
                        buffer.write(chunk, 0, count)
                    }
                    result[entry.name] = buffer.toByteArray()
                    zip.closeEntry()
                }
            }
        }
        return result
    }

    private fun writeText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private data class ParsedCapture(
        val type: CaptureType,
        val text: String,
        val contentAt: Long,
        val importedAt: Long,
        val updatedAt: Long,
        val mediaEntry: String?,
        val tags: List<String>,
    )

    private companion object {
        const val FORMAT_VERSION = 2
        const val LEGACY_VERSION = 1
        const val LEGACY_MANIFEST = "manifest.json"
        const val CHAT_ENTRY = "chat/conversations.md"
        const val MAX_TOTAL_BYTES = 100L * 1024 * 1024

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

        val chatTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

        val chatDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

        val TIME_HEADING = Regex("""^## \d{2}:\d{2} · (文字|图片|语音)$""")
        val MEDIA_LINK = Regex("""^!?\[(图片|语音)\]\(media/[^)]+\)$""")
        val CHAT_HEADING = Regex("""^### (我|小札) · .*$""")
    }
}
