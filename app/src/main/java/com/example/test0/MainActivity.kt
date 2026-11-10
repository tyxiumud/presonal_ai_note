package com.example.test0

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.test0.ai.settings.SettingsViewModel
import com.example.test0.backup.BackupViewModel
import com.example.test0.chat.ui.ChatViewModel
import com.example.test0.journal.ui.JournalViewModel
import com.example.test0.reader.ReaderViewModel
import com.example.test0.ui.PersonalVaultApp
import com.example.test0.ui.theme.Test0Theme

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as PersonalVaultApplication).container

    private val journalViewModel: JournalViewModel by viewModels {
        viewModelFactory {
            initializer {
                JournalViewModel(
                    repository = container.captureRepository,
                    analysisRepository = container.dailyAnalysisRepository,
                    savedStateHandle = createSavedStateHandle(),
                    analysisRunner = container.dailyAnalysisService,
                    mediaStore = container.mediaStore,
                    settingsStore = container.aiSettingsStore,
                    mediaUnderstanding = container.mediaUnderstandingService,
                )
            }
        }
    }

    private val readerViewModel: ReaderViewModel by viewModels {
        viewModelFactory {
            initializer {
                ReaderViewModel(
                    captureRepository = container.captureRepository,
                    analysisRepository = container.dailyAnalysisRepository,
                    mediaUnderstanding = container.mediaUnderstandingService,
                )
            }
        }
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    store = container.aiSettingsStore,
                    api = container.dailyAnalysisApi,
                )
            }
        }
    }

    private val chatViewModel: ChatViewModel by viewModels {
        viewModelFactory {
            initializer {
                ChatViewModel(
                    chatService = container.chatService,
                    chatRepository = container.chatRepository,
                    captureRepository = container.captureRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }

    private val backupViewModel: BackupViewModel by viewModels {
        viewModelFactory {
            initializer {
                BackupViewModel(
                    repository = container.backupRepository,
                    captureRepository = container.captureRepository,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.aiSettingsStore.settings.collectAsStateWithLifecycle()
            Test0Theme(skin = settings.skin) {
                PersonalVaultApp(
                    journalViewModel = journalViewModel,
                    readerViewModel = readerViewModel,
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    backupViewModel = backupViewModel,
                )
            }
        }
    }
}
