package com.example.test0.journal.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureDaoTest {
    private lateinit var database: PersonalVaultDatabase
    private lateinit var dao: CaptureDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(
            context,
            PersonalVaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.captureDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queryReturnsOnlySelectedDay() = runBlocking {
        dao.insert(entity(id = 1, text = "旧日记录", dayKey = "2026-11-03"))
        dao.insert(entity(id = 2, text = "今日记录", dayKey = "2026-11-04"))

        val result = dao.getByDay("2026-11-03")

        assertEquals(listOf("旧日记录"), result.map { it.textContent })
    }

    @Test
    fun recordsAreOrderedDeterministically() = runBlocking {
        dao.insert(entity(id = 1, text = "较早导入", importedAt = 100))
        dao.insert(entity(id = 2, text = "相同时间先导入", importedAt = 200))
        dao.insert(entity(id = 3, text = "相同时间后插入", importedAt = 200))

        val result = dao.getByDay("2026-11-04")

        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun updateTextRefreshesOnlyThatRow() = runBlocking {
        dao.insert(entity(id = 1, text = "保留的记录"))
        dao.insert(entity(id = 2, text = "要修改的记录"))

        dao.updateText(id = 2, text = "改写后的记录", updatedAt = 9_999)

        assertEquals("改写后的记录", dao.getByDay("2026-11-04").first { it.id == 2L }.textContent)
        assertEquals("保留的记录", dao.getByDay("2026-11-04").first { it.id == 1L }.textContent)
        assertEquals(9_999L, dao.getByDay("2026-11-04").first { it.id == 2L }.updatedAtEpochMillis)
    }

    @Test
    fun deleteRemovesOnlyTargetRecord() = runBlocking {
        dao.insert(entity(id = 1, text = "保留的记录"))
        dao.insert(entity(id = 2, text = "要删除的记录"))

        dao.deleteById(2)

        assertEquals(listOf("保留的记录"), dao.getByDay("2026-11-04").map { it.textContent })
    }

    private fun entity(
        id: Long,
        text: String,
        dayKey: String = "2026-11-04",
        importedAt: Long = 100,
    ) = CaptureEntity(
        id = id,
        type = "TEXT",
        textContent = text,
        contentAtEpochMillis = 1_000,
        importedAtEpochMillis = importedAt,
        updatedAtEpochMillis = importedAt,
        dayKey = dayKey,
        processingState = "RAW",
    )
}
