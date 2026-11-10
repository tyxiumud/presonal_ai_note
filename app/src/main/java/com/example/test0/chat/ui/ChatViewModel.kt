package com.example.test0.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test0.chat.data.ChatRepository
import com.example.test0.chat.data.ConversationPreview
import com.example.test0.chat.domain.ChatNotConfiguredException
import com.example.test0.chat.domain.ChatService
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.Conversation
import com.example.test0.chat.model.DEFAULT_CONVERSATION_ID
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val currentConversationId: Long = DEFAULT_CONVERSATION_ID,
    val previews: Map<Long, ConversationPreview> = emptyMap(),
    /** 非 null 时展示「本次回答参考的原始记录」卡片；空列表表示引用已全部被删除。 */
    val referenceSheet: List<Capture>? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isSending
}

class ChatViewModel(
    private val chatService: ChatService,
    private val chatRepository: ChatRepository,
    private val captureRepository: CaptureRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val draft = savedStateHandle.getStateFlow(KEY_DRAFT, "")
    private val currentConversationId =
        savedStateHandle.getStateFlow(KEY_CONVERSATION_ID, DEFAULT_CONVERSATION_ID)
    private val isSending = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val canRetry = MutableStateFlow(false)
    private val referenceSheet = MutableStateFlow<List<Capture>?>(null)

    // 切换会话时取消上一个会话的消息流，只订阅当前这一个。
    private val messages = currentConversationId.flatMapLatest { id ->
        chatRepository.observeMessages(id)
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(emptyList())
            }
    }

    private data class Core(
        val messages: List<ChatMessage>,
        val conversations: List<Conversation>,
        val currentConversationId: Long,
        val previews: Map<Long, ConversationPreview>,
        val referenceSheet: List<Capture>?,
    )

    private val core = combine(
        messages,
        chatRepository.observeConversations()
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(emptyList())
            },
        currentConversationId,
        chatRepository.observeConversationPreviews()
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(emptyMap())
            },
        referenceSheet,
    ) { messages, conversations, currentId, previews, references ->
        Core(messages, conversations, currentId, previews, references)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        core,
        draft,
        isSending,
        errorMessage,
        canRetry,
    ) { core, draft, sending, error, retry ->
        ChatUiState(
            messages = core.messages,
            conversations = core.conversations,
            currentConversationId = core.currentConversationId,
            previews = core.previews,
            referenceSheet = core.referenceSheet,
            draft = draft,
            isSending = sending,
            errorMessage = error,
            canRetry = retry,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChatUiState(draft = draft.value),
    )

    init {
        // 进程重建后 SavedStateHandle 里记的会话可能已经被删掉，回退到最新一个。
        viewModelScope.launch {
            try {
                val conversations = chatRepository.conversations()
                val savedId = currentConversationId.value
                if (conversations.none { it.id == savedId }) {
                    savedStateHandle[KEY_CONVERSATION_ID] = conversations.firstOrNull()?.id
                        ?: chatRepository.createConversation().id
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = "读取会话失败，请重试"
            }
        }
    }

    fun onDraftChanged(value: String) {
        savedStateHandle[KEY_DRAFT] = value
        errorMessage.value = null
    }

    fun onSuggestionSelected(question: String) {
        if (isSending.value) return
        savedStateHandle[KEY_DRAFT] = question
        send()
    }

    fun selectConversation(id: Long) {
        if (isSending.value) return
        savedStateHandle[KEY_CONVERSATION_ID] = id
        errorMessage.value = null
        canRetry.value = false
        referenceSheet.value = null
    }

    fun createConversation() {
        if (isSending.value) return
        // 当前会话还没聊过就直接复用它，避免攒下一堆同名空会话。
        val preview = uiState.value.previews[currentConversationId.value]
        if (preview == null || preview.messageCount == 0) {
            savedStateHandle[KEY_DRAFT] = ""
            errorMessage.value = null
            canRetry.value = false
            return
        }
        viewModelScope.launch {
            try {
                val created = chatRepository.createConversation()
                savedStateHandle[KEY_DRAFT] = ""
                savedStateHandle[KEY_CONVERSATION_ID] = created.id
                errorMessage.value = null
                canRetry.value = false
                referenceSheet.value = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = "新建对话失败，请稍后重试"
            }
        }
    }

    fun deleteConversation(id: Long) {
        if (isSending.value) return
        viewModelScope.launch {
            try {
                chatRepository.deleteConversation(id)
                if (currentConversationId.value == id) {
                    val fallback = chatRepository.conversations().firstOrNull()?.id
                        ?: chatRepository.createConversation().id
                    savedStateHandle[KEY_CONVERSATION_ID] = fallback
                    savedStateHandle[KEY_DRAFT] = ""
                    referenceSheet.value = null
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = "删除对话失败，请稍后重试"
            }
        }
    }

    /** 点气泡下方的「查看原文」：按引用 id 找回原始随记，已被删除的自然过滤掉。 */
    fun onReferenceClicked(message: ChatMessage) {
        val ids = message.referencedCaptureIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            referenceSheet.value = ids.mapNotNull { captureRepository.getById(it) }
        }
    }

    fun dismissReferences() {
        referenceSheet.value = null
    }

    fun send() {
        if (isSending.value) return
        val text = draft.value.trim()
        if (text.isEmpty()) {
            errorMessage.value = "请输入想问的内容"
            canRetry.value = false
            return
        }
        val conversationId = currentConversationId.value

        // 用户消息会先落库再请求，因此这里立刻清空输入框；配置缺失时再恢复原文。
        savedStateHandle[KEY_DRAFT] = ""
        request {
            try {
                chatService.send(text, conversationId)
            } catch (throwable: Throwable) {
                if (throwable is ChatNotConfiguredException) {
                    savedStateHandle[KEY_DRAFT] = text
                }
                throw throwable
            }
        }
    }

    fun retry() {
        if (isSending.value) return
        val conversationId = currentConversationId.value
        request { chatService.retry(conversationId) }
    }

    /** 只清空当前会话，原始随记、每日整理和其他会话都不受影响。 */
    fun clearConversation() {
        if (isSending.value) return
        val conversationId = currentConversationId.value
        viewModelScope.launch {
            errorMessage.value = null
            canRetry.value = false
            try {
                chatRepository.clear(conversationId)
                referenceSheet.value = null
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = "清空失败，请稍后重试"
            }
        }
    }

    /** 删除单条消息，其余对话保留。 */
    fun deleteMessage(message: ChatMessage) {
        if (isSending.value) return
        viewModelScope.launch {
            errorMessage.value = null
            canRetry.value = false
            try {
                chatRepository.delete(message.id)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = "删除失败，请稍后重试"
            }
        }
    }

    private fun request(block: suspend () -> Unit) {
        viewModelScope.launch {
            isSending.value = true
            errorMessage.value = null
            canRetry.value = false
            try {
                block()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                errorMessage.value = throwable.message
                    ?.takeIf(String::isNotBlank)
                    ?: "发送失败，请稍后重试"
                canRetry.value = throwable !is ChatNotConfiguredException
            } finally {
                isSending.value = false
            }
        }
    }

    private companion object {
        const val KEY_DRAFT = "chat_draft"
        const val KEY_CONVERSATION_ID = "chat_conversation_id"
    }
}
