package io.github.tyxiumud.personalainote.journal.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureHistoryTest {
    private lateinit var database: PersonalVaultDatabase
    private lateinit var repository: RoomCaptureRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PersonalVaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomCaptureRepository(database.captureDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun observeDaysIsDistinctNewestFirstAndGetByDayReturnsModels() = runBlocking {
        database.captureDao().insert(entity(id = 1, dayKey = "2026-11-03", text = "旧"))
        database.captureDao().insert(entity(id = 2, dayKey = "2026-11-05", text = "新"))
        database.captureDao().insert(entity(id = 3, dayKey = "2026-11-05", text = "同日"))

        assertEquals(
            listOf(LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-03")),
            repository.observeDays().first(),
        )
        assertEquals(2, repository.getByDay(LocalDate.parse("2026-11-05")).size)
    }

    private fun entity(id: Long, dayKey: String, text: String) = CaptureEntity(
        id = id,
        type = "TEXT",
        textContent = text,
        contentAtEpochMillis = 1_000,
        importedAtEpochMillis = id,
        updatedAtEpochMillis = id,
        dayKey = dayKey,
        processingState = "RAW",
    )
}
