package com.example.test0.chat.domain

import com.example.test0.ai.api.AiApiException
import com.example.test0.ai.settings.AiSettingsSnapshot
import com.example.test0.ai.settings.ChatPersona
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.ChatRole
import com.example.test0.chat.model.DEFAULT_CONVERSATION_ID
import com.example.test0.journal.model.Capture
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatServiceTest {
    private val today = LocalDate.parse("2026-11-05")
    private val clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC)

    @Test
    fun sendStoresQuestionAndAnswerWithReferences() = runTest {
        val chatRepository = FakeChatRepository()
        val service = service(
            chatRepository = chatRepository,
            captures = listOf(testCapture(1, today, "今天在研究 Compose 布局")),
        )

        val reply = service.send("我在研究什么")

        assertEquals(2, chatRepository.messages.size)
        assertEquals(ChatRole.USER, chatRepository.messages[0].role)
        assertEquals("我在研究什么", chatRepository.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, reply.role)
        assertEquals("这是回答", reply.content)
        assertEquals(listOf(today), reply.referencedDays)
        assertEquals(listOf(1L), reply.referencedCaptureIds)
    }

    @Test
    fun contextSentToApiContainsLocalNotes() = runTest {
        val api = FakeChatApi()
        val service = service(
            chatRepository = FakeChatRepository(),
            captures = listOf(testCapture(1, today, "今天在研究 Compose 布局")),
            api = api,
        )

        service.send("研究什么")

        val request = checkNotNull(api.lastRequest)
        assertTrue(request.context.contains("今天在研究 Compose 布局"))
        assertEquals("研究什么", request.question)
    }

    @Test
    fun missingConfigurationFailsBeforePersistingAnything() = runTest {
        val chatRepository = FakeChatRepository()
        val service = service(
            chatRepository = chatRepository,
            settingsStore = FakeSettingsStore(connection = null),
        )

        val error = runCatching { service.send("问题") }.exceptionOrNull()

        assertTrue(error is ChatNotConfiguredException)
        assertTrue(chatRepository.messages.isEmpty())
    }

    @Test
    fun apiFailureKeepsQuestionButDoesNotInventAnswer() = runTest {
        val chatRepository = FakeChatRepository()
        val service = service(
            chatRepository = chatRepository,
            api = FakeChatApi(error = AiApiException("API 返回 HTTP 500")),
        )

        val error = runCatching { service.send("问题") }.exceptionOrNull()

        assertTrue(error is AiApiException)
        assertEquals(1, chatRepository.messages.size)
        assertEquals(ChatRole.USER, chatRepository.messages[0].role)
    }

    @Test
    fun retryAnswersLastQuestionWithoutDuplicatingIt() = runTest {
        val chatRepository = FakeChatRepository()
        chatRepository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "我在研究什么",
                createdAt = Instant.parse("2026-11-05T08:59:00Z"),
            ),
        )
        val service = service(chatRepository = chatRepository)

        val reply = service.retry()

        assertEquals(2, chatRepository.messages.size)
        assertEquals(ChatRole.ASSISTANT, reply.role)
    }

    @Test
    fun personaFromSettingsIsSentToApi() = runTest {
        val api = FakeChatApi()
        val service = service(
            chatRepository = FakeChatRepository(),
            settingsStore = FakeSettingsStore(
                snapshot = AiSettingsSnapshot(persona = ChatPersona.BRIGHT),
            ),
            api = api,
        )

        service.send("问题")

        assertEquals(ChatPersona.BRIGHT, checkNotNull(api.lastRequest).persona)
    }

    @Test
    fun historyIsSentToApiInOrder() = runTest {        val chatRepository = FakeChatRepository()
        chatRepository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "上一个问题",
                createdAt = Instant.parse("2026-11-05T08:00:00Z"),
            ),
        )
        chatRepository.append(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "上一个回答",
                createdAt = Instant.parse("2026-11-05T08:01:00Z"),
            ),
        )
        val api = FakeChatApi()
        val service = service(chatRepository = chatRepository, api = api)

        service.send("新的问题")

        val history = checkNotNull(api.lastRequest).history
        assertEquals(listOf("user", "assistant"), history.map { it.role })
        assertEquals(listOf("上一个问题", "上一个回答"), history.map { it.content })
    }

    @Test
    fun historyAndAnswerAreScopedToOneConversation() = runTest {
        val chatRepository = FakeChatRepository()
        val other = chatRepository.createConversation()
        chatRepository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "另一个话题",
                createdAt = Instant.parse("2026-11-05T08:00:00Z"),
                conversationId = other.id,
            ),
        )
        val api = FakeChatApi()
        val service = service(chatRepository = chatRepository, api = api)

        val reply = service.send("新的问题", conversationId = DEFAULT_CONVERSATION_ID)

        assertTrue(checkNotNull(api.lastRequest).history.isEmpty())
        assertEquals(DEFAULT_CONVERSATION_ID, reply.conversationId)
        assertEquals(2, chatRepository.messages.count { it.conversationId == DEFAULT_CONVERSATION_ID })
        assertEquals(1, chatRepository.messages.count { it.conversationId == other.id })
    }

    private fun service(
        chatRepository: FakeChatRepository,
        captures: List<Capture> = emptyList(),
        settingsStore: FakeSettingsStore = FakeSettingsStore(),
        api: FakeChatApi = FakeChatApi(),
    ) = ChatService(
        captureRepository = FakeCaptureRepository(captures.groupBy(Capture::dayKey)),
        analysisRepository = FakeAnalysisRepository(),
        chatRepository = chatRepository,
        settingsStore = settingsStore,
        api = api,
        clock = clock,
    )
}
