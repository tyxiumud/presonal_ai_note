package com.example.test0.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.test0.ai.api.ImageDescriptionRequest
import com.example.test0.ai.api.MediaUnderstandingApi
import com.example.test0.ai.api.TranscriptionRequest
import com.example.test0.ai.settings.AiSettingsStore
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.data.MediaStore
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaUnderstandingException(message: String) : Exception(message)

/** 把一条图片/语音记录交给 API，得到的文字写回该记录的正文。 */
class MediaUnderstandingService(
    private val captureRepository: CaptureRepository,
    private val settingsStore: AiSettingsStore,
    private val mediaApi: MediaUnderstandingApi,
    private val mediaStore: MediaStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun recognize(capture: Capture): String = withContext(Dispatchers.IO) {
        if (capture.type == CaptureType.TEXT) {
            throw MediaUnderstandingException("文字随记不需要识别")
        }
        val path = capture.mediaPath
            ?: throw MediaUnderstandingException("这条记录没有媒体文件")
        val file = mediaStore.file(path)
        if (!file.exists()) {
            throw MediaUnderstandingException("媒体文件已丢失，无法识别")
        }
        val connection = when (capture.type) {
            CaptureType.IMAGE -> settingsStore.visionConnection()
            CaptureType.AUDIO -> settingsStore.transcriptionConnection()
            CaptureType.TEXT -> error("unreachable")
        } ?: throw MediaUnderstandingException(
            when (capture.type) {
                CaptureType.IMAGE -> "请先在设置里配置图片识别服务"
                else -> "请先在设置里配置语音转写服务"
            },
        )

        val text = when (capture.type) {
            CaptureType.IMAGE -> mediaApi.describeImage(
                connection = connection,
                request = ImageDescriptionRequest(
                    imageBytes = compressForApi(file),
                    mimeType = "image/jpeg",
                    model = connection.model,
                    prompt = """
                        用简体中文，客观描述这张图片里能看到的内容：场景、物品、文字、明显的人物活动。
                        只描述看得见的东西，不要猜测拍摄者的心情，不要写成日记，不要加客套话。
                        控制在 3 句话以内；如果图片模糊或看不清，直接说明。
                    """.trimIndent(),
                ),
            )

            CaptureType.AUDIO -> mediaApi.transcribe(
                connection = connection,
                request = TranscriptionRequest(
                    audioBytes = file.readBytes(),
                    fileName = file.name,
                    mimeType = "audio/mp4",
                    model = connection.model,
                ),
            )

            CaptureType.TEXT -> error("unreachable")
        }.trim()

        if (text.isBlank()) {
            throw MediaUnderstandingException("API 没有返回可用内容")
        }
        val finalText = when (capture.type) {
            CaptureType.IMAGE -> "[图片描述] $text"
            CaptureType.AUDIO -> "[语音转写] $text"
            CaptureType.TEXT -> text
        }
        captureRepository.updateText(capture.id, finalText, Instant.now(clock))
        finalText
    }

    /** 缩到最长边 1024 再压成 JPEG，避免 base64 把请求体撑到几 MB。 */
    private fun compressForApi(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        val sample = if (maxSide > MAX_SIDE) {
            Integer.highestOneBit(maxSide / MAX_SIDE).coerceAtLeast(1)
        } else {
            1
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: throw MediaUnderstandingException("无法读取图片")
        return ByteArrayOutputStream().use { output ->
            decoded.compress(Bitmap.CompressFormat.JPEG, 82, output)
            decoded.recycle()
            output.toByteArray()
        }
    }

    private companion object {
        const val MAX_SIDE = 1024
    }
}
