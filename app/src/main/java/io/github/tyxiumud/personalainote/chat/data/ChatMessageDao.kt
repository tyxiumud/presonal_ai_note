package io.github.tyxiumud.personalainote.chat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(entity: ChatMessageEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ChatMessageEntity>): List<Long>

    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun listAll(): List<ChatMessageEntity>

    @Query(
        "SELECT conversationId, COUNT(*) AS messageCount " +
            "FROM chat_messages GROUP BY conversationId",
    )
    fun observeCounts(): Flow<List<ConversationCountRow>>

    @Query(
        "SELECT * FROM chat_messages WHERE id IN " +
            "(SELECT MAX(id) FROM chat_messages GROUP BY conversationId)",
    )
    fun observeLastMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeAll(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
            "ORDER BY id DESC LIMIT :limit",
    )
    suspend fun recent(conversationId: Long, limit: Int): List<ChatMessageEntity>

    @Query(
        "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
            "ORDER BY id DESC LIMIT 1",
    )
    suspend fun latest(conversationId: Long): ChatMessageEntity?

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun clear(conversationId: Long)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)
}

data class ConversationCountRow(
    val conversationId: Long,
    val messageCount: Int,
)
