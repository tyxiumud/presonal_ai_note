package io.github.tyxiumud.personalainote.chat.model

import java.time.Instant
import java.time.LocalDate

enum class ChatRole {
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val createdAt: Instant,
    val referencedDays: List<LocalDate> = emptyList(),
    val referencedCaptureIds: List<Long> = emptyList(),
    val conversationId: Long = DEFAULT_CONVERSATION_ID,
)
