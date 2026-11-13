package io.github.tyxiumud.personalainote.ai.settings

import io.github.tyxiumud.personalainote.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveValidSettingsAndKeepExistingKeyWhenKeyFieldIsBlank() = runTest {
        val store = FakeAiSettingsStore(
            AiSettingsSnapshot(
                baseUrl = "https://old.example/v1",
                model = "old-model",
                hasApiKey = true,
            ),
        )
        val viewModel = SettingsViewModel(store)

        viewModel.onBaseUrlChanged("https://api.example/v1")
        viewModel.onModelChanged("demo-model")
        viewModel.onApiKeyChanged("")
        viewModel.save()

        assertEquals("https://api.example/v1", store.lastSaved?.baseUrl)
        assertEquals("demo-model", store.lastSaved?.model)
        assertNull(store.lastSaved?.newApiKey)
        assertEquals("设置已保存", viewModel.uiState.value.message)
    }

    @Test
    fun rejectInsecureUrlAndMissingRequiredFields() = runTest {
        val store = FakeAiSettingsStore(AiSettingsSnapshot())
        val viewModel = SettingsViewModel(store)

        viewModel.onBaseUrlChanged("http://api.example/v1")
        viewModel.onModelChanged("")
        viewModel.save()

        assertNull(store.lastSaved)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun selectedPersonaIsSaved() = runTest {
        val store = FakeAiSettingsStore(AiSettingsSnapshot())
        val viewModel = SettingsViewModel(store)

        viewModel.onBaseUrlChanged("https://api.example/v1")
        viewModel.onModelChanged("demo-model")
        viewModel.onApiKeyChanged("secret")
        viewModel.onPersonaChanged(ChatPersona.CONCISE)
        viewModel.save()

        assertEquals(ChatPersona.CONCISE, store.lastSaved?.persona)
    }

    @Test
    fun editingSwitchAndSkinApplyImmediatelyWithoutApiConfig() = runTest {
        val store = FakeAiSettingsStore(AiSettingsSnapshot())
        val viewModel = SettingsViewModel(store)

        viewModel.onAllowEditingChanged(true)
        viewModel.onSkinChanged(AppSkin.ROSE)

        assertEquals(true, store.lastAllowEditing)
        assertEquals(AppSkin.ROSE, store.lastSkin)
        assertTrue(viewModel.uiState.value.allowEditing)
        assertEquals(AppSkin.ROSE, viewModel.uiState.value.skin)
        // 没有点「保存设置」，也不应该触发完整的 API 配置写入
        assertNull(store.lastSaved)
    }

    @Test
    fun separateMediaSettingsAreSaved() = runTest {
        val store = FakeAiSettingsStore(AiSettingsSnapshot())
        val viewModel = SettingsViewModel(store)

        viewModel.onBaseUrlChanged("https://api.example/v1")
        viewModel.onModelChanged("demo-model")
        viewModel.onApiKeyChanged("secret")
        viewModel.onVisionUseLlmChanged(false)
        viewModel.onVisionBaseUrlChanged("https://vision.example/v1")
        viewModel.onVisionModelChanged("vision-model")
        viewModel.onVisionApiKeyChanged("vision-secret")
        viewModel.onTranscriptionBaseUrlChanged("https://audio.example/v1")
        viewModel.onTranscriptionModelChanged("my-whisper")
        viewModel.onTranscriptionApiKeyChanged("audio-secret")
        viewModel.save()

        val saved = store.lastSaved
        assertEquals(false, saved?.visionUseLlm)
        assertEquals("https://vision.example/v1", saved?.visionBaseUrl)
        assertEquals("vision-secret", saved?.newVisionApiKey)
        assertEquals("https://audio.example/v1", saved?.transcriptionBaseUrl)
        assertEquals("my-whisper", saved?.transcriptionModel)
        assertEquals("audio-secret", saved?.newTranscriptionApiKey)
    }

    @Test
    fun insecureMediaUrlIsRejected() = runTest {
        val store = FakeAiSettingsStore(AiSettingsSnapshot())
        val viewModel = SettingsViewModel(store)

        viewModel.onBaseUrlChanged("https://api.example/v1")
        viewModel.onModelChanged("demo-model")
        viewModel.onApiKeyChanged("secret")
        viewModel.onVisionUseLlmChanged(false)
        viewModel.onVisionBaseUrlChanged("http://vision.example/v1")
        viewModel.save()

        assertNull(store.lastSaved)
        assertTrue(viewModel.uiState.value.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun clearApiKeyRemovesConnection() = runTest {
        val store = FakeAiSettingsStore(
            AiSettingsSnapshot(
                baseUrl = "https://api.example/v1",
                model = "demo-model",
                hasApiKey = true,
            ),
        )
        val viewModel = SettingsViewModel(store)

        viewModel.clearApiKey()

        assertFalse(store.settings.value.hasApiKey)
        assertNull(store.connection())
    }
}

private class FakeAiSettingsStore(
    initial: AiSettingsSnapshot,
) : AiSettingsStore {
    override val settings = MutableStateFlow(initial)
    var lastSaved: AiSettingsInput? = null
    var lastAllowEditing: Boolean? = null
    var lastSkin: AppSkin? = null
    private var key: String? = if (initial.hasApiKey) "existing-key" else null

    override suspend fun save(input: AiSettingsInput) {
        lastSaved = input
        if (!input.newApiKey.isNullOrBlank()) {
            key = input.newApiKey
        }
        settings.value = AiSettingsSnapshot(
            baseUrl = input.baseUrl,
            model = input.model,
            hasApiKey = key != null,
            includeTasks = input.includeTasks,
            includeSuggestions = input.includeSuggestions,
            includeReflections = input.includeReflections,
        )
    }

    override suspend fun connection(): AiConnectionSettings? {
        val currentKey = key ?: return null
        val current = settings.value
        return AiConnectionSettings(current.baseUrl, current.model, currentKey)
    }

    override suspend fun clearApiKey() {
        key = null
        settings.value = settings.value.copy(hasApiKey = false)
    }

    override suspend fun setAllowEditing(value: Boolean) {
        lastAllowEditing = value
        settings.value = settings.value.copy(allowEditing = value)
    }

    override suspend fun setSkin(skin: AppSkin) {
        lastSkin = skin
        settings.value = settings.value.copy(skin = skin)
    }
}
