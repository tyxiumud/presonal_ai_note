package io.github.tyxiumud.personalainote.media

import io.github.tyxiumud.personalainote.ai.api.ImageDescriptionRequest
import io.github.tyxiumud.personalainote.ai.api.MediaUnderstandingApi
import io.github.tyxiumud.personalainote.ai.api.TranscriptionRequest
import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsInput
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsSnapshot
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsStore
import io.github.tyxiumud.personalainote.ai.settings.AppSkin
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.data.MediaStore
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaUnderstandingServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val day = LocalDate.parse("2026-11-07")
    private val instant = Instant.parse("2026-11-07T09:00:00Z")

    @Test
    fun transcriptionWritesTextBackWithPrefix() = runTest {
        val audio = folder.newFile("voice.m4a")
        audio.writeBytes(byteArrayOf(1, 2, 3))
        val api = FakeMediaApi(transcription = "今天去散了步")
        val repository = FakeCaptureRepository()
        val service = service(api, repository, transcriptionModel = "my-whisper")

        val result = service.recognize(capture(CaptureType.AUDIO, audio.absolutePath))

        assertEquals("[语音转写] 今天去散了步", result)
        assertEquals("my-whisper", api.lastTranscription?.model)
        assertEquals(
            "[语音转写] 今天去散了步",
            repository.updates.single().second,
        )
    }

    @Test
    fun audioUsesTranscriptionConnection() = runTest {
        val audio = folder.newFile("voice3.m4a")
        audio.writeBytes(byteArrayOf(4, 5, 6))
        val api = FakeMediaApi()
        val service = service(api, FakeCaptureRepository())

        service.recognize(capture(CaptureType.AUDIO, audio.absolutePath))

        assertEquals("whisper-1", api.lastTranscriptionConnection?.model)
    }

    @Test
    fun textCaptureIsRejected() = runTest {
        val api = FakeMediaApi()
        val service = service(api, FakeCaptureRepository())

        val error = runCatching { service.recognize(capture(CaptureType.TEXT, null)) }
            .exceptionOrNull()

        assertTrue(error is MediaUnderstandingException)
        assertTrue(api.calls == 0)
    }

    @Test
    fun missingMediaFileFailsClearly() = runTest {
        val api = FakeMediaApi()
        val service = service(api, FakeCaptureRepository())

        val error = runCatching {
            service.recognize(capture(CaptureType.AUDIO, "/no/such/file.m4a"))
        }.exceptionOrNull()

        assertEquals("媒体文件已丢失，无法识别", error?.message)
        assertTrue(api.calls == 0)
    }

    @Test
    fun emptyApiResultDoesNotOverwriteText() = runTest {
        val audio = folder.newFile("voice2.m4a")
        audio.writeBytes(byteArrayOf(1))
        val api = FakeMediaApi(transcription = "   ")
        val repository = FakeCaptureRepository()
        val service = service(api, repository)

        val error = runCatching {
            service.recognize(capture(CaptureType.AUDIO, audio.absolutePath))
        }.exceptionOrNull()

        assertEquals("API 没有返回可用内容", error?.message)
        assertTrue(repository.updates.isEmpty())
    }

    private fun service(
        api: FakeMediaApi,
        repository: FakeCaptureRepository,
        transcriptionModel: String = "whisper-1",
    ) = MediaUnderstandingService(
        captureRepository = repository,
        settingsStore = FakeSettingsStore(transcriptionModel),
        mediaApi = api,
        mediaStore = FakeMediaStore(),
        clock = Clock.fixed(instant, ZoneOffset.UTC),
    )

    private fun capture(type: CaptureType, mediaPath: String?) = Capture(
        id = 7,
        text = "",
        contentAt = instant,
        importedAt = instant,
        updatedAt = instant,
        dayKey = day,
        type = type,
        mediaPath = mediaPath,
    )

    private class FakeMediaApi(
        private val description: String = "一张桌子的照片",
        private val transcription: String = "一段录音",
    ) : MediaUnderstandingApi {
        var calls = 0
        var lastTranscription: TranscriptionRequest? = null
        var lastImage: ImageDescriptionRequest? = null
        var lastTranscriptionConnection: AiConnectionSettings? = null

        override suspend fun describeImage(
            connection: AiConnectionSettings,
            request: ImageDescriptionRequest,
        ): String {
            calls += 1
            lastImage = request
            return description
        }

        override suspend fun transcribe(
            connection: AiConnectionSettings,
            request: TranscriptionRequest,
        ): String {
            calls += 1
            lastTranscription = request
            lastTranscriptionConnection = connection
            return transcription
        }
    }

    private class FakeSettingsStore(
        private val transcriptionModel: String,
    ) : AiSettingsStore {
        override val settings = MutableStateFlow(
            AiSettingsSnapshot(
                baseUrl = "https://api.example/v1",
                model = "chat-model",
                hasApiKey = true,
                transcriptionModel = transcriptionModel,
            ),
        )
        override suspend fun save(input: AiSettingsInput) = Unit
        override suspend fun connection() = AiConnectionSettings(
            "https://api.example/v1",
            "chat-model",
            "secret",
        )
        override suspend fun clearApiKey() = Unit
        override suspend fun setAllowEditing(value: Boolean) = Unit
        override suspend fun setSkin(skin: AppSkin) = Unit
        override suspend fun transcriptionConnection() = AiConnectionSettings(
            "https://api.example/v1",
            transcriptionModel,
            "secret",
        )
    }

    private class FakeMediaStore : MediaStore {
        override fun import(uri: android.net.Uri, type: CaptureType): String? = null
        override fun delete(absolutePath: String) = Unit
        override fun file(absolutePath: String): File = File(absolutePath)
        override fun writeMedia(fileName: String, bytes: ByteArray): String? = null
    }

    private class FakeCaptureRepository : CaptureRepository {
        val updates = mutableListOf<Pair<Long, String>>()

        override fun observeByDay(day: LocalDate): Flow<List<Capture>> = flowOf(emptyList())
        override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime) = 1L
        override suspend fun delete(id: Long) = Unit
        override suspend fun updateText(id: Long, text: String, now: Instant) {
            updates += id to text
        }
    }
}
