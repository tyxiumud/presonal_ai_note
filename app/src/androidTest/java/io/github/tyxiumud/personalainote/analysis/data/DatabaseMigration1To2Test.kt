package io.github.tyxiumud.personalainote.analysis.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.tyxiumud.personalainote.journal.data.PersonalVaultDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration1To2Test {
    @Test
    fun migrationPreservesCapturesAndCreatesAnalysisTable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-${System.nanoTime()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL(
                """
                CREATE TABLE captures (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    textContent TEXT NOT NULL,
                    contentAtEpochMillis INTEGER NOT NULL,
                    importedAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    dayKey TEXT NOT NULL,
                    processingState TEXT NOT NULL
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX index_captures_dayKey ON captures (dayKey)",
            )
            legacy.execSQL(
                """
                INSERT INTO captures (
                    id, type, textContent, contentAtEpochMillis,
                    importedAtEpochMillis, updatedAtEpochMillis, dayKey, processingState
                ) VALUES (1, 'TEXT', '保留我', 1000, 1000, 1000, '2026-11-04', 'RAW')
                """.trimIndent(),
            )
        legacy.setVersion(1)
        legacy.close()

        val migrated = Room.databaseBuilder(
            context,
            PersonalVaultDatabase::class.java,
            databaseName,
        )
            .addMigrations(
                PersonalVaultDatabase.MIGRATION_1_2,
                PersonalVaultDatabase.MIGRATION_2_3,
                PersonalVaultDatabase.MIGRATION_3_4,
                PersonalVaultDatabase.MIGRATION_4_5,
                PersonalVaultDatabase.MIGRATION_5_6,
            )
            .allowMainThreadQueries()
            .build()

        try {
            migrated.openHelper.writableDatabase
            assertEquals("保留我", migrated.captureDao().getByDayBlocking("2026-11-04"))
            migrated.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM daily_analyses")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }
}

private fun io.github.tyxiumud.personalainote.journal.data.CaptureDao.getByDayBlocking(day: String): String =
    kotlinx.coroutines.runBlocking { getByDay(day).single().textContent }
