package com.example.test0.analysis.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.test0.journal.data.PersonalVaultDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigration5To6Test {
    @Test
    fun migrationKeepsOldMessagesInDefaultConversation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration56-${System.nanoTime()}.db"
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
                mediaPath TEXT,
                tags TEXT NOT NULL
            )
            """.trimIndent(),
        )
        legacy.execSQL("CREATE INDEX index_captures_dayKey ON captures (dayKey)")
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
        legacy.execSQL(
            """
            INSERT INTO chat_messages
                (id, role, content, createdAtEpochMillis, referencedDayKeys, referencedCaptureIds)
            VALUES (1, 'USER', '升级前的问题', 1000, '', '')
            """.trimIndent(),
        )
        legacy.setVersion(5)
        legacy.close()

        val migrated = Room.databaseBuilder(context, PersonalVaultDatabase::class.java, name)
            .addMigrations(PersonalVaultDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()

        try {
            val db = migrated.openHelper.writableDatabase
            db.query("SELECT title FROM conversations WHERE id = 1").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("默认对话", cursor.getString(0))
            }
            db.query("SELECT conversationId, content FROM chat_messages WHERE id = 1").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals("升级前的问题", cursor.getString(1))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_chat_messages_conversationId'",
            ).use { cursor ->
                assertEquals(1, cursor.count)
            }
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
