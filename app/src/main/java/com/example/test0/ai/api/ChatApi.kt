package com.example.test0.ai.api

import com.example.test0.ai.settings.AiConnectionSettings
import com.example.test0.ai.settings.ChatPersona

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
