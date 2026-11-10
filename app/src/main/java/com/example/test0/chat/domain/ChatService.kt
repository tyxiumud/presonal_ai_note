package com.example.test0.chat.domain

import com.example.test0.ai.api.ChatApi
import com.example.test0.ai.api.ChatRequest
import com.example.test0.ai.api.ChatTurn
import com.example.test0.ai.settings.AiConnectionSettings
import com.example.test0.ai.settings.AiSettingsStore
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.chat.data.ChatRepository
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.ChatRole
import com.example.test0.chat.model.DEFAULT_CONVERSATION_ID
import com.example.test0.journal.data.CaptureRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 一轮问答：先保存用户消息，再构建有限的本地上下文，最后把回答和引用一起落库。
 *
 * 引用来自本地实际发送的资料，不依赖模型自己声明，避免出现无法核实的“参考”。
 */
class ChatService(
    captureRepository: CaptureRepository,
    analysisRepository: DailyAnalysisRepository,
    private val chatRepository: ChatRepository,
    private val settingsStore: AiSettingsStore,
    private val api: ChatApi,
    private val contextBuilder: ChatContextBuilder = ChatContextBuilder(
        captureRepository = captureRepository,
        analysisRepository = analysisRepository,
    ),
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val today: LocalDate
        get() = LocalDate.now(clock)

    suspend fun send(
        question: String,
        conversationId: Long = DEFAULT_CONVERSATION_ID,
    ): ChatMessage {
        val text = question.trim()
        if (text.isEmpty()) throw ChatException("请输入想问的内容")
        val connection = requireConnection()

        val history = chatRepository.recent(conversationId, HISTORY_LIMIT)
        val userMessage = chatRepository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = text,
                createdAt = Instant.now(clock),
                conversationId = conversationId,
            ),
        )
        return replyTo(userMessage, connection, history, conversationId)
    }

    /** 重新回答当前会话里最后一条还没有得到回复的问题，不会重复保存用户消息。 */
    suspend fun retry(conversationId: Long = DEFAULT_CONVERSATION_ID): ChatMessage {
        val latest = chatRepository.latest(conversationId)
            ?: throw ChatException("还没有可以重试的问题")
        if (latest.role != ChatRole.USER) throw ChatException("上一条消息已经有回答")
        val connection = requireConnection()
        val history = chatRepository.recent(conversationId, HISTORY_LIMIT + 1).dropLast(1)
        return replyTo(latest, connection, history, conversationId)
    }

    private suspend fun requireConnection(): AiConnectionSettings =
        settingsStore.connection() ?: throw ChatNotConfiguredException(
            "请先在设置中填写并保存 API 配置",
        )

    private suspend fun replyTo(
        question: ChatMessage,
        connection: AiConnectionSettings,
        history: List<ChatMessage>,
        conversationId: Long,
    ): ChatMessage {
        val context = contextBuilder.build(question.content, today)
        val answer = api.reply(
            connection = connection,
            request = ChatRequest(
                question = question.content,
                context = context.text,
                history = history.map { turn ->
                    ChatTurn(role = turn.role.apiValue(), content = turn.content)
                },
                persona = settingsStore.settings.value.persona,
            ),
        ).trim()
        if (answer.isEmpty()) throw ChatException("AI 没有返回有效内容")

        return chatRepository.append(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = answer,
                createdAt = Instant.now(clock),
                referencedDays = context.referencedDays,
                referencedCaptureIds = context.referencedCaptureIds,
                conversationId = conversationId,
            ),
        )
    }

    private fun ChatRole.apiValue(): String = when (this) {
        ChatRole.USER -> "user"
        ChatRole.ASSISTANT -> "assistant"
    }

    private companion object {
        const val HISTORY_LIMIT = 8
    }
}

open class ChatException(message: String) : Exception(message)

/** 请求还没有发出就失败的场景，例如还没配置 API。 */
class ChatNotConfiguredException(message: String) : ChatException(message)
