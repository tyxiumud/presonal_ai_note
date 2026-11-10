package com.example.test0.chat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    /** 批量导入：返回按传入顺序生成的新 id，用于重映射消息的 conversationId。 */
    @Insert
    suspend fun insertAll(entities: List<ConversationEntity>): List<Long>

    @Query("SELECT * FROM conversations ORDER BY updatedAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAtEpochMillis DESC, id DESC")
    suspend fun list(): List<ConversationEntity>

    @Query("SELECT title FROM conversations WHERE id = :id")
    suspend fun title(id: Long): String?

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE conversations SET updatedAtEpochMillis = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
