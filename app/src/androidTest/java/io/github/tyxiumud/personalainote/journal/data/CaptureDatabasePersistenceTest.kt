package io.github.tyxiumud.personalainote.journal.data

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
class CaptureDatabasePersistenceTest {
    private lateinit var context: Context
    private val databaseName = "capture-reopen-test.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun recordSurvivesDatabaseReopen() = runBlocking {
        var database = openDatabase()
        database.captureDao().insert(
            CaptureEntity(
                type = "TEXT",
                textContent = "重新打开后仍然存在",
                contentAtEpochMillis = 1_793_763_000_000,
                importedAtEpochMillis = 1_793_763_000_000,
                updatedAtEpochMillis = 1_793_763_000_000,
                dayKey = "2026-11-04",
                processingState = "RAW",
            ),
        )
        database.close()

        database = openDatabase()
        val restored = database.captureDao().getByDay("2026-11-04")
        database.close()

        assertEquals(1, restored.size)
        assertEquals("重新打开后仍然存在", restored.single().textContent)
        assertEquals("2026-11-04", restored.single().dayKey)
    }

    private fun openDatabase(): PersonalVaultDatabase =
        Room.databaseBuilder(context, PersonalVaultDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
}
