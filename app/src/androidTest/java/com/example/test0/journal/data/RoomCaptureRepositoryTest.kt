package com.example.test0.journal.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.test0.journal.model.CaptureType
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCaptureRepositoryTest {
    private lateinit var database: PersonalVaultDatabase
    private lateinit var repository: RoomCaptureRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            PersonalVaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCaptureRepository(database.captureDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pastDateUsesSelectedDayKeyInPositiveTimezone() = runBlocking {
        val now = ZonedDateTime.of(
            2026,
            11,
            4,
            0,
            15,
            0,
            0,
            ZoneId.of("Asia/Shanghai"),
        )

        repository.addText(
            text = "补录以前的内容",
            contentDate = LocalDate.of(2026, 10, 31),
            now = now,
        )

        val pastRecords = database.captureDao().getByDay("2026-10-31")
        val todayRecords = database.captureDao().getByDay("2026-11-04")

        assertEquals("2026-10-31", pastRecords.single().dayKey)
        assertTrue(todayRecords.isEmpty())
    }

    @Test
    fun mediaCaptureStoresTypeAndPathAndDeleteRemovesFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "media-test-${System.nanoTime()}")
        file.writeText("fake-media")
        val store = FakeMediaStore(file.absolutePath)
        val repo = RoomCaptureRepository(database.captureDao(), store)
        val now = ZonedDateTime.of(2026, 11, 4, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai"))

        val id = repo.addMedia(
            type = CaptureType.IMAGE,
            mediaPath = file.absolutePath,
            caption = "",
            contentDate = LocalDate.of(2026, 11, 4),
            now = now,
        )
        val saved = database.captureDao().getByDay("2026-11-04").single { it.id == id }

        assertEquals("IMAGE", saved.type)
        assertEquals(file.absolutePath, saved.mediaPath)

        repo.delete(id)

        assertTrue(database.captureDao().getByDay("2026-11-04").none { it.id == id })
        assertTrue(!file.exists())
    }
}

private class FakeMediaStore(
    private val path: String,
) : MediaStore {
    override fun import(uri: android.net.Uri, type: CaptureType): String? = path

    override fun delete(absolutePath: String) {
        File(absolutePath).delete()
    }

    override fun file(absolutePath: String): File = File(absolutePath)

    override fun writeMedia(fileName: String, bytes: ByteArray): String? = path
}
