package io.github.tyxiumud.personalainote

import android.content.Context
import io.github.tyxiumud.personalainote.ai.api.ChatApi
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisApi
import io.github.tyxiumud.personalainote.ai.api.MediaUnderstandingApi
import io.github.tyxiumud.personalainote.ai.api.OpenAiCompatibleApiClient
import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsStore
import io.github.tyxiumud.personalainote.ai.settings.KeystoreAiSettingsStore
import io.github.tyxiumud.personalainote.analysis.data.AtomicDailyMarkdownStore
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.data.RoomDailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.domain.DailyAnalysisService
import io.github.tyxiumud.personalainote.backup.BackupRepository
import io.github.tyxiumud.personalainote.chat.data.ChatRepository
import io.github.tyxiumud.personalainote.chat.data.RoomChatRepository
import io.github.tyxiumud.personalainote.chat.domain.ChatService
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.data.LocalMediaStore
import io.github.tyxiumud.personalainote.journal.data.MediaStore
import io.github.tyxiumud.personalainote.journal.data.PersonalVaultDatabase
import io.github.tyxiumud.personalainote.journal.data.RoomCaptureRepository
import io.github.tyxiumud.personalainote.media.MediaUnderstandingService

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val database by lazy {
        PersonalVaultDatabase.create(applicationContext)
    }
    private val apiClient by lazy { OpenAiCompatibleApiClient() }

    val mediaStore: MediaStore by lazy { LocalMediaStore(applicationContext) }

    val captureRepository: CaptureRepository by lazy {
        RoomCaptureRepository(database.captureDao(), mediaStore)
    }

    val aiSettingsStore: AiSettingsStore by lazy {
        val defaultConnection = BuildConfig.DEFAULT_API_KEY
            .takeIf(String::isNotBlank)
            ?.let { key ->
                AiConnectionSettings(
                    baseUrl = BuildConfig.DEFAULT_API_BASE_URL,
                    model = BuildConfig.DEFAULT_API_MODEL,
                    apiKey = key,
                )
            }
        KeystoreAiSettingsStore(applicationContext, defaultConnection = defaultConnection)
    }

    val dailyAnalysisApi: DailyAnalysisApi
        get() = apiClient

    val chatApi: ChatApi
        get() = apiClient

    val mediaUnderstandingApi: MediaUnderstandingApi
        get() = apiClient

    val mediaUnderstandingService: MediaUnderstandingService by lazy {
        MediaUnderstandingService(
            captureRepository = captureRepository,
            settingsStore = aiSettingsStore,
            mediaApi = mediaUnderstandingApi,
            mediaStore = mediaStore,
        )
    }

    val dailyAnalysisRepository: DailyAnalysisRepository by lazy {
        RoomDailyAnalysisRepository(
            dao = database.dailyAnalysisDao(),
            markdownStore = AtomicDailyMarkdownStore(applicationContext),
        )
    }

    val dailyAnalysisService: DailyAnalysisService by lazy {
        DailyAnalysisService(
            captureRepository = captureRepository,
            settingsStore = aiSettingsStore,
            api = dailyAnalysisApi,
            analysisRepository = dailyAnalysisRepository,
        )
    }

    val chatRepository: ChatRepository by lazy {
        RoomChatRepository(database.chatMessageDao(), database.conversationDao())
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = applicationContext,
            captureRepository = captureRepository,
            analysisRepository = dailyAnalysisRepository,
            mediaStore = mediaStore,
            chatRepository = chatRepository,
        )
    }

    val chatService: ChatService by lazy {
        ChatService(
            captureRepository = captureRepository,
            analysisRepository = dailyAnalysisRepository,
            chatRepository = chatRepository,
            settingsStore = aiSettingsStore,
            api = chatApi,
        )
    }
}
