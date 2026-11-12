package com.example.test0.chat.domain

import com.example.test0.ai.api.ChatApi
import com.example.test0.ai.api.ChatRequest
import com.example.test0.ai.settings.AiConnectionSettings
import com.example.test0.ai.settings.AiSettingsInput
import com.example.test0.ai.settings.AiSettingsSnapshot
import com.example.test0.ai.settings.AiSettingsStore
import com.example.test0.ai.settings.AppSkin
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.chat.data.ChatBackupData
import com.example.test0.chat.data.ChatRepository
import com.example.test0.chat.data.ChatRestoreResult
import com.example.test0.chat.data.ConversationPreview
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.ChatRole
import com.example.test0.chat.model.Conversation
import com.example.test0.chat.model.DEFAULT_CONVERSATION_ID
import com.example.test0.chat.model.DEFAULT_CONVERSATION_TITLE
import com.example.test0.chat.model.NEW_CONVERSATION_PREFIX
import com.example.test0.chat.model.conversationTitleFrom
import com.example.test0.chat.model.isPlaceholderConversationTitle
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal fun testCapture(id: Long, day: LocalDate, text: String): Capture {
    val instant = Instant.parse("${day}T08:00:00Z")
    return Capture(id, text, instant, instant, instant, day)
}

internal fun testAnalysis(
    day: LocalDate,
    fingerprint: String,
    markdown: String,
): DailyAnalysis = DailyAnalysis(
    day = day,
    markdownPath = "analysis/daily/$day.md",
    markdown = markdown,
    generatedAt = Instant.EPOCH,
    sourceFingerprint = fingerprint,
    modelName = "demo",
)

internal class FakeCaptureRepository(
    private val values: Map<LocalDate, List<Capture>>,
    private val byId: Map<Long, Capture> = emptyMap(),
) : CaptureRepository {
    override fun observeDays(): Flow<List<LocalDate>> =
        flowOf(values.keys.sortedDescending())

    override fun observeAll(): Flow<List<Capture>> = flowOf(values.values.flatten())

    override fun observeByDay(day: LocalDate): Flow<List<Capture>> =
        flowOf(values[day].orEmpty())

    override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime): Long = 1L

    override suspend fun delete(id: Long) = Unit

    override suspend fun updateText(id: Long, text: String, now: java.time.Instant) = Unit

    override suspend fun getById(id: Long): Capture? = byId[id]
}

internal class FakeAnalysisRepository(
    private val values: Map<LocalDate, DailyAnalysis> = emptyMap(),
) : DailyAnalysisRepository {
    override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> =
        flowOf(values[day])

    override suspend fun save(analysis: DailyAnalysis): DailyAnalysis = analysis

    override suspend fun deleteByDay(day: LocalDate) = Unit
}

internal class FakeChatRepository : ChatRepository {
    private val state = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val conversationState = MutableStateFlow(
        listOf(
            Conversation(
                id = DEFAULT_CONVERSATION_ID,
                title = DEFAULT_CONVERSATION_TITLE,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        ),
    )
    private var nextId = 1L
    private var nextConversationId = 2L

    val messages: List<ChatMessage> get() = state.value

    override fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        state.map { list -> list.filter { it.conversationId == conversationId } }

    override fun observeConversations(): Flow<List<Conversation>> = conversationState

    override suspend fun conversations(): List<Conversation> =
        conversationState.value.sortedByDescending(Conversation::updatedAt)

    override suspend fun recent(conversationId: Long, limit: Int): List<ChatMessage> =
        state.value.filter { it.conversationId == conversationId }.takeLast(limit)

    override suspend fun latest(conversationId: Long): ChatMessage? =
        state.value.lastOrNull { it.conversationId == conversationId }

    override suspend fun append(message: ChatMessage): ChatMessage {
        val saved = message.copy(id = nextId++)
        state.value = state.value + saved
        val updated = conversationState.value.toMutableList()
        val index = updated.indexOfFirst { it.id == message.conversationId }
        if (index >= 0) {
            var conversation = updated[index].copy(updatedAt = message.createdAt)
            if (message.role == ChatRole.USER &&
                isPlaceholderConversationTitle(conversation.title)
            ) {
                conversation = conversation.copy(title = conversationTitleFrom(message.content))
            }
            updated[index] = conversation
            conversationState.value = updated.sortedByDescending(Conversation::updatedAt)
        }
        return saved
    }

    override suspend fun clear(conversationId: Long) {
        state.value = state.value.filterNot { it.conversationId == conversationId }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun createConversation(): Conversation {
        val conversation = Conversation(
            id = nextConversationId++,
            title = "$NEW_CONVERSATION_PREFIX ${conversationState.value.size + 1}",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conversationState.value = (conversationState.value + conversation)
            .sortedByDescending(Conversation::updatedAt)
        return conversation
    }

    override suspend fun deleteConversation(id: Long) {
        state.value = state.value.filterNot { it.conversationId == id }
        conversationState.value = conversationState.value.filterNot { it.id == id }
    }

    override fun observeConversationPreviews(): Flow<Map<Long, ConversationPreview>> =
        state.map { list ->
            list.groupBy(ChatMessage::conversationId).mapValues { (_, messages) ->
                ConversationPreview(messages.size, messages.last().content)
            }
        }

    override suspend fun exportAll(): ChatBackupData =
        ChatBackupData(conversationState.value, state.value)

    override suspend fun restoreAll(data: ChatBackupData): ChatRestoreResult {
        val idMap = mutableMapOf<Long, Long>()
        data.conversations.forEach { conversation ->
            val exists = conversationState.value.any {
                it.title == conversation.title && it.createdAt == conversation.createdAt
            }
            if (!exists) {
                val created = conversation.copy(id = nextConversationId++)
                conversationState.value = conversationState.value + created
                idMap[conversation.id] = created.id
            }
        }
        var restored = 0
        data.messages.forEach { message ->
            val target = idMap[message.conversationId] ?: return@forEach
            state.value = state.value + message.copy(id = nextId++, conversationId = target)
            restored += 1
        }
        return ChatRestoreResult(idMap.size, restored)
    }
}

internal class FakeSettingsStore(
    private val connection: AiConnectionSettings? = AiConnectionSettings(
        baseUrl = "https://api.example.com/v1",
        model = "demo",
        apiKey = "secret",
    ),
    snapshot: AiSettingsSnapshot = AiSettingsSnapshot(),
) : AiSettingsStore {
    private val mutableSettings = MutableStateFlow(snapshot)

    override val settings: MutableStateFlow<AiSettingsSnapshot> = mutableSettings

    override suspend fun save(input: AiSettingsInput) = Unit

    override suspend fun connection(): AiConnectionSettings? = connection

    override suspend fun clearApiKey() = Unit

    override suspend fun setAllowEditing(value: Boolean) = Unit

    override suspend fun setSkin(skin: AppSkin) = Unit
}

internal class FakeChatApi(
    private val reply: String = "这是回答",
    private val error: Throwable? = null,
) : ChatApi {
    var lastRequest: ChatRequest? = null
        private set

    override suspend fun reply(
        connection: AiConnectionSettings,
        request: ChatRequest,
    ): String {
        lastRequest = request
        error?.let { throw it }
        return reply
    }
}
