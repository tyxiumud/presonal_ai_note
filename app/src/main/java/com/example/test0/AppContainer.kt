package com.example.test0

import android.content.Context
import com.example.test0.ai.api.ChatApi
import com.example.test0.ai.api.DailyAnalysisApi
import com.example.test0.ai.api.MediaUnderstandingApi
import com.example.test0.ai.api.OpenAiCompatibleApiClient
import com.example.test0.ai.settings.AiConnectionSettings
import com.example.test0.ai.settings.AiSettingsStore
import com.example.test0.ai.settings.KeystoreAiSettingsStore
import com.example.test0.analysis.data.AtomicDailyMarkdownStore
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.data.RoomDailyAnalysisRepository
import com.example.test0.analysis.domain.DailyAnalysisService
import com.example.test0.backup.BackupRepository
import com.example.test0.chat.data.ChatRepository
import com.example.test0.chat.data.RoomChatRepository
import com.example.test0.chat.domain.ChatService
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.data.LocalMediaStore
import com.example.test0.journal.data.MediaStore
import com.example.test0.journal.data.PersonalVaultDatabase
import com.example.test0.journal.data.RoomCaptureRepository
import com.example.test0.media.MediaUnderstandingService

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
