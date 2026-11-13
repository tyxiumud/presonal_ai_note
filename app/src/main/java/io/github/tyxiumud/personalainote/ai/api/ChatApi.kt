package io.github.tyxiumud.personalainote.ai.api

import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings
import io.github.tyxiumud.personalainote.ai.settings.ChatPersona

data class ChatTurn(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val question: String,
    val context: String,
    val history: List<ChatTurn> = emptyList(),
    val persona: ChatPersona = ChatPersona.WARM,
)

interface ChatApi {
    suspend fun reply(
        connection: AiConnectionSettings,
        request: ChatRequest,
    ): String
}
