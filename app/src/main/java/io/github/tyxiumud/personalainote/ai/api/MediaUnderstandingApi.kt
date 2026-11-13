package io.github.tyxiumud.personalainote.ai.api

import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings

data class ImageDescriptionRequest(
    val imageBytes: ByteArray,
    val mimeType: String,
    val model: String,
    val prompt: String,
)

data class TranscriptionRequest(
    val audioBytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val model: String,
)

/**
 * 把图片和语音变成文字。
 * 图片走 /chat/completions 的多模态消息；语音走独立的 /audio/transcriptions。
 */
interface MediaUnderstandingApi {
    suspend fun describeImage(
        connection: AiConnectionSettings,
        request: ImageDescriptionRequest,
    ): String

    suspend fun transcribe(
        connection: AiConnectionSettings,
        request: TranscriptionRequest,
    ): String
}
