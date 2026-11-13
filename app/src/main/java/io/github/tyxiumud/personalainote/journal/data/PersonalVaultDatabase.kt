package io.github.tyxiumud.personalainote.journal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisDao
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisEntity
import io.github.tyxiumud.personalainote.chat.data.ChatMessageDao
import io.github.tyxiumud.personalainote.chat.data.ChatMessageEntity
import io.github.tyxiumud.personalainote.chat.data.ConversationDao
import io.github.tyxiumud.personalainote.chat.data.ConversationEntity
import io.github.tyxiumud.personalainote.chat.model.DEFAULT_CONVERSATION_TITLE

@Database(
    entities = [
        CaptureEntity::class,
        DailyAnalysisEntity::class,
        ChatMessageEntity::class,
        ConversationEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class PersonalVaultDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    abstract fun dailyAnalysisDao(): DailyAnalysisDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun conversationDao(): ConversationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_analyses (
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
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        referencedDayKeys TEXT NOT NULL,
                        referencedCaptureIds TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN mediaPath TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE captures ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                // 旧版本只有一条连续的对话，全部归到 id=1 的默认会话，不丢消息。
                db.execSQL(
                    "INSERT INTO conversations " +
                        "(id, title, createdAtEpochMillis, updatedAtEpochMillis) " +
                        "SELECT 1, '$DEFAULT_CONVERSATION_TITLE', " +
                        "COALESCE(MIN(createdAtEpochMillis), 0), " +
                        "COALESCE(MAX(createdAtEpochMillis), 0) FROM chat_messages",
                )
                db.execSQL(
                    "ALTER TABLE chat_messages " +
                        "ADD COLUMN conversationId INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId " +
                        "ON chat_messages (conversationId)",
                )
            }
        }

        /** 全新安装时也要有一个会话，否则聊天页没有可写入的会话。 */
        private val seedConversation = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO conversations " +
                        "(id, title, createdAtEpochMillis, updatedAtEpochMillis) " +
                        "VALUES (1, '$DEFAULT_CONVERSATION_TITLE', $now, $now)",
                )
            }
        }

        fun create(context: Context): PersonalVaultDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                PersonalVaultDatabase::class.java,
                "personal-vault.db",
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .addCallback(seedConversation)
                .build()
    }
}
