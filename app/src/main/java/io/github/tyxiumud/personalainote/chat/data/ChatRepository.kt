package io.github.tyxiumud.personalainote.chat.data

import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>>

    fun observeConversations(): Flow<List<Conversation>>

    /** 一次性拿会话列表，用于删除后回退到仍存在的会话。 */
    suspend fun conversations(): List<Conversation>

    /** 最近 [limit] 条消息，按时间从旧到新排列。 */
    suspend fun recent(conversationId: Long, limit: Int): List<ChatMessage>

    suspend fun latest(conversationId: Long): ChatMessage?

    suspend fun append(message: ChatMessage): ChatMessage

    /** 只清空一个会话，其他会话不受影响。 */
    suspend fun clear(conversationId: Long)

    suspend fun delete(id: Long)

    suspend fun createConversation(): Conversation

    suspend fun deleteConversation(id: Long)

    /** 每个会话的消息数与最后一条消息，供会话列表显示预览。 */
    fun observeConversationPreviews(): Flow<Map<Long, ConversationPreview>>

    /** 备份：一次性取出全部会话与消息。 */
    suspend fun exportAll(): ChatBackupData

    /** 备份：合并导入。会话 id 会重新分配，重复导入同一包不会产生副本。 */
    suspend fun restoreAll(data: ChatBackupData): ChatRestoreResult
}
