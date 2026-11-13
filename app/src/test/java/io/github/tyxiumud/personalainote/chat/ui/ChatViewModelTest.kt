package io.github.tyxiumud.personalainote.chat.ui

import androidx.lifecycle.SavedStateHandle
import io.github.tyxiumud.personalainote.MainDispatcherRule
import io.github.tyxiumud.personalainote.chat.domain.ChatService
import io.github.tyxiumud.personalainote.chat.domain.FakeAnalysisRepository
import io.github.tyxiumud.personalainote.chat.domain.FakeCaptureRepository
import io.github.tyxiumud.personalainote.chat.domain.FakeChatApi
import io.github.tyxiumud.personalainote.chat.domain.FakeChatRepository
import io.github.tyxiumud.personalainote.chat.domain.FakeSettingsStore
import io.github.tyxiumud.personalainote.chat.domain.testCapture
import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC)

    @Test
    fun sendStoresQuestionAndClearsDraft() = runTest {
        val repository = FakeChatRepository()
        val viewModel = createViewModel(repository)

        viewModel.onDraftChanged("我在研究什么")
        viewModel.send()

        assertEquals("", viewModel.uiState.value.draft)
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT),
            repository.messages.map(ChatMessage::role),
        )
    }

    @Test
    fun clearConversationRemovesAllMessages() = runTest {
        val repository = FakeChatRepository()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "旧问题", createdAt = Instant.EPOCH),
        )
        val viewModel = createViewModel(repository)

        viewModel.clearConversation()

        assertTrue(repository.messages.isEmpty())
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun deletingMessageKeepsTheRest() = runTest {
        val repository = FakeChatRepository()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "第一个问题", createdAt = Instant.EPOCH),
        )
        repository.append(
            ChatMessage(role = ChatRole.ASSISTANT, content = "第一个回答", createdAt = Instant.EPOCH),
        )
        val viewModel = createViewModel(repository)

        viewModel.deleteMessage(repository.messages.first())

        assertEquals(listOf("第一个回答"), repository.messages.map(ChatMessage::content))
    }

    @Test
    fun switchingConversationShowsOnlyItsOwnMessages() = runTest {
        val repository = FakeChatRepository()
        val second = repository.createConversation()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "会话一", createdAt = Instant.EPOCH),
        )
        repository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "会话二",
                createdAt = Instant.EPOCH,
                conversationId = second.id,
            ),
        )
        val viewModel = createViewModel(repository)

        assertEquals(listOf("会话一"), viewModel.uiState.value.messages.map(ChatMessage::content))

        viewModel.selectConversation(second.id)

        assertEquals(listOf("会话二"), viewModel.uiState.value.messages.map(ChatMessage::content))
        assertEquals(second.id, viewModel.uiState.value.currentConversationId)
    }

    @Test
    fun newConversationStartsEmptyAndIsSelected() = runTest {
        val repository = FakeChatRepository()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "旧会话", createdAt = Instant.EPOCH),
        )
        val viewModel = createViewModel(repository)

        viewModel.createConversation()

        assertEquals(2L, viewModel.uiState.value.currentConversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(1, repository.messages.size)
    }

    @Test
    fun clearingOnlyAffectsCurrentConversation() = runTest {
        val repository = FakeChatRepository()
        val second = repository.createConversation()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "会话一", createdAt = Instant.EPOCH),
        )
        repository.append(
            ChatMessage(
                role = ChatRole.USER,
                content = "会话二",
                createdAt = Instant.EPOCH,
                conversationId = second.id,
            ),
        )
        val viewModel = createViewModel(repository)

        viewModel.clearConversation()

        assertEquals(listOf("会话二"), repository.messages.map(ChatMessage::content))
    }

    @Test
    fun deletingCurrentConversationFallsBackToAnotherOne() = runTest {
        val repository = FakeChatRepository()
        val second = repository.createConversation()
        val viewModel = createViewModel(repository)
        viewModel.selectConversation(second.id)

        viewModel.deleteConversation(second.id)

        assertEquals(1L, viewModel.uiState.value.currentConversationId)
        assertTrue(repository.conversations().none { it.id == second.id })
    }

    @Test
    fun firstQuestionNamesTheConversation() = runTest {
        val repository = FakeChatRepository()
        val viewModel = createViewModel(repository)

        viewModel.onDraftChanged("我在研究什么")
        viewModel.send()

        assertEquals("我在研究什么", repository.conversations().first { it.id == 1L }.title)
    }

    @Test
    fun creatingConversationReusesEmptyCurrentOne() = runTest {
        val repository = FakeChatRepository()
        val viewModel = createViewModel(repository)

        viewModel.createConversation()

        assertEquals(1, repository.conversations().size)
        assertEquals(1L, viewModel.uiState.value.currentConversationId)
    }

    @Test
    fun conversationPreviewShowsCountAndLastContent() = runTest {
        val repository = FakeChatRepository()
        repository.append(
            ChatMessage(role = ChatRole.USER, content = "第一个问题", createdAt = Instant.EPOCH),
        )
        repository.append(
            ChatMessage(role = ChatRole.ASSISTANT, content = "第一个回答", createdAt = Instant.EPOCH),
        )
        val viewModel = createViewModel(repository)

        val preview = viewModel.uiState.value.previews[1L]
        assertEquals(2, preview?.messageCount)
        assertEquals("第一个回答", preview?.lastContent)
    }

    @Test
    fun clickingReferenceLoadsOriginalCaptures() = runTest {
        val capture = testCapture(7L, LocalDate.parse("2026-11-03"), "那天在准备考试")
        val repository = FakeChatRepository()
        val viewModel = createViewModel(
            repository,
            captureRepository = FakeCaptureRepository(emptyMap(), mapOf(7L to capture)),
        )
        val message = ChatMessage(
            id = 1,
            role = ChatRole.ASSISTANT,
            content = "你在准备考试",
            createdAt = Instant.EPOCH,
            referencedCaptureIds = listOf(7L),
        )

        viewModel.onReferenceClicked(message)

        assertEquals(listOf(capture), viewModel.uiState.value.referenceSheet)
        viewModel.dismissReferences()
        assertEquals(null, viewModel.uiState.value.referenceSheet)
    }

    private fun createViewModel(
        chatRepository: FakeChatRepository,
        captureRepository: FakeCaptureRepository = FakeCaptureRepository(emptyMap()),
    ) = ChatViewModel(
        chatService = ChatService(
            captureRepository = captureRepository,
            analysisRepository = FakeAnalysisRepository(),
            chatRepository = chatRepository,
            settingsStore = FakeSettingsStore(),
            api = FakeChatApi(),
            clock = clock,
        ),
        chatRepository = chatRepository,
        captureRepository = captureRepository,
        savedStateHandle = SavedStateHandle(),
    )
}
