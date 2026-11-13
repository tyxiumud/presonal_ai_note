package io.github.tyxiumud.personalainote.chat.data

import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import io.github.tyxiumud.personalainote.chat.model.Conversation
import io.github.tyxiumud.personalainote.chat.model.NEW_CONVERSATION_PREFIX
import io.github.tyxiumud.personalainote.chat.model.conversationTitleFrom
import io.github.tyxiumud.personalainote.chat.model.isPlaceholderConversationTitle
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomChatRepository(
    private val messageDao: ChatMessageDao,
    private val conversationDao: ConversationDao,
) : ChatRepository {
    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        messageDao.observeAll(conversationId)
            .map { entities -> entities.map(ChatMessageEntity::toModel) }

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll()
            .map { entities -> entities.map(ConversationEntity::toModel) }

    override suspend fun conversations(): List<Conversation> =
        conversationDao.list().map(ConversationEntity::toModel)

    override suspend fun recent(conversationId: Long, limit: Int): List<ChatMessage> =
        messageDao.recent(conversationId, limit).map(ChatMessageEntity::toModel).reversed()

    override suspend fun latest(conversationId: Long): ChatMessage? =
        messageDao.latest(conversationId)?.toModel()

    override suspend fun append(message: ChatMessage): ChatMessage {
        val id = messageDao.insert(message.toEntity())
        conversationDao.touch(message.conversationId, message.createdAt.toEpochMilli())
        // 第一条提问顺便给会话起名，占位标题只在这里改一次。
        if (message.role == ChatRole.USER &&
            conversationDao.title(message.conversationId)
                ?.let(::isPlaceholderConversationTitle) == true
        ) {
            conversationDao.updateTitle(
                message.conversationId,
                conversationTitleFrom(message.content),
            )
        }
        return message.copy(id = id)
    }

    override suspend fun clear(conversationId: Long) {
        messageDao.clear(conversationId)
    }

    override suspend fun delete(id: Long) {
        messageDao.deleteById(id)
    }

    override suspend fun createConversation(): Conversation {
        val now = Instant.now().toEpochMilli()
        val entity = ConversationEntity(
            title = "$NEW_CONVERSATION_PREFIX ${conversationDao.list().size + 1}",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        val id = conversationDao.insert(entity)
        return entity.copy(id = id).toModel()
    }

    override suspend fun deleteConversation(id: Long) {
        messageDao.clear(id)
        conversationDao.deleteById(id)
    }

    override fun observeConversationPreviews(): Flow<Map<Long, ConversationPreview>> =
        combine(
            messageDao.observeCounts(),
            messageDao.observeLastMessages(),
        ) { counts, lastMessages ->
            val lastById = lastMessages.associateBy(ChatMessageEntity::conversationId)
            counts.associate { row ->
                row.conversationId to ConversationPreview(
                    messageCount = row.messageCount,
                    lastContent = lastById[row.conversationId]?.content.orEmpty(),
                )
            }
        }

    override suspend fun exportAll(): ChatBackupData = ChatBackupData(
        conversations = conversationDao.list().map(ConversationEntity::toModel),
        messages = messageDao.listAll().map(ChatMessageEntity::toModel),
    )

    override suspend fun restoreAll(data: ChatBackupData): ChatRestoreResult {
        val existingKeys = conversationDao.list()
            .map { it.title to it.createdAtEpochMillis }
            .toSet()
        // 用「标题 + 创建时间」识别同一个会话：重复导入同一个包不会翻倍。
        val fresh = data.conversations.filterNot { conversation ->
            (conversation.title to conversation.createdAt.toEpochMilli()) in existingKeys
        }
        if (fresh.isEmpty()) return ChatRestoreResult(0, 0)

        val newIds = conversationDao.insertAll(
            fresh.map { it.toEntity().copy(id = 0) },
        )
        val idMap = fresh.mapIndexed { index, conversation ->
            conversation.id to newIds[index]
        }.toMap()
        val messages = data.messages
            .filter { it.conversationId in idMap }
            .map { message ->
                message.copy(
                    id = 0,
                    conversationId = idMap.getValue(message.conversationId),
                )
            }
        if (messages.isNotEmpty()) {
            messageDao.insertAll(messages.map(ChatMessage::toEntity))
        }
        return ChatRestoreResult(fresh.size, messages.size)
    }
}
