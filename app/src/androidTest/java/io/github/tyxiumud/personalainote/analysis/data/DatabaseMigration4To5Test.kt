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
class DatabaseMigration4To5Test {
    @Test
    fun migrationKeepsCapturesAndDefaultsTagsToEmpty() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration45-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()

        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
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
                processingState TEXT NOT NULL,
                mediaPath TEXT
            )
            """.trimIndent(),
        )
        legacy.execSQL("CREATE INDEX index_captures_dayKey ON captures (dayKey)")
        legacy.execSQL(
            """
            INSERT INTO captures (
                id, type, textContent, contentAtEpochMillis,
                importedAtEpochMillis, updatedAtEpochMillis, dayKey, processingState
            ) VALUES (1, 'TEXT', '旧记录', 1000, 1000, 1000, '2026-11-04', 'RAW')
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            CREATE TABLE daily_analyses (
                dayKey TEXT NOT NULL PRIMARY KEY,
                markdownPath TEXT NOT NULL,
                generatedAtEpochMillis INTEGER NOT NULL,
                sourceFingerprint TEXT NOT NULL,
                modelName TEXT NOT NULL,
                status TEXT NOT NULL,
                errorMessage TEXT
            )
            """.trimIndent(),
        )
        legacy.execSQL(
            """
            CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                referencedDayKeys TEXT NOT NULL,
                referencedCaptureIds TEXT NOT NULL
            )
            """.trimIndent(),
        )
        legacy.setVersion(4)
        legacy.close()

        val migrated = Room.databaseBuilder(context, PersonalVaultDatabase::class.java, name)
            .addMigrations(
                PersonalVaultDatabase.MIGRATION_4_5,
                PersonalVaultDatabase.MIGRATION_5_6,
            )
            .allowMainThreadQueries()
            .build()

        try {
            migrated.openHelper.writableDatabase
            assertEquals("旧记录", migrated.captureDao().getByDayBlocking("2026-11-04"))
            migrated.openHelper.readableDatabase
                .query("SELECT tags FROM captures WHERE id = 1")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals("", cursor.getString(0))
                }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}

private fun io.github.tyxiumud.personalainote.journal.data.CaptureDao.getByDayBlocking(day: String): String =
    kotlinx.coroutines.runBlocking { getByDay(day).single().textContent }
