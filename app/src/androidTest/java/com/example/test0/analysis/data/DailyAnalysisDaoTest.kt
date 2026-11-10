package com.example.test0.analysis.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.test0.journal.data.PersonalVaultDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyAnalysisDaoTest {
    private lateinit var database: PersonalVaultDatabase
    private lateinit var dao: DailyAnalysisDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PersonalVaultDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dailyAnalysisDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun upsertAndObserveAnalysisByDay() = runBlocking {
        assertNull(dao.observeByDay("2026-11-05").first())

        dao.upsert(
            DailyAnalysisEntity(
                dayKey = "2026-11-05",
                markdownPath = "analysis/daily/2026-11-05.md",
                generatedAtEpochMillis = 1234,
                sourceFingerprint = "fingerprint",
                modelName = "demo-model",
                status = "READY",
                errorMessage = null,
            ),
        )

        val saved = dao.observeByDay("2026-11-05").first()
        assertEquals("fingerprint", saved?.sourceFingerprint)
        assertEquals("demo-model", saved?.modelName)
    }
}
