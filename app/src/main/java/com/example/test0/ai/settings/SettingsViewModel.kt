package com.example.test0.ai.settings

import androidx.lifecycle.ViewModel
import com.example.test0.BuildConfig
import com.example.test0.ai.api.DailyAnalysisApi
import kotlinx.coroutines.CancellationException
import java.net.URI
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val model: String = "",
    /** 本机 api.properties 里给出的可选模型，设置页显示成一键切换 chip。 */
    val modelPresets: List<String> = emptyList(),
    val apiKeyDraft: String = "",
    val hasApiKey: Boolean = false,
    val includeTasks: Boolean = true,
    val includeSuggestions: Boolean = true,
    val includeReflections: Boolean = true,
    val persona: ChatPersona = ChatPersona.WARM,
    val allowEditing: Boolean = false,
    val skin: AppSkin = AppSkin.PAPER,
    val visionUseLlm: Boolean = true,
    val visionBaseUrl: String = "",
    val visionModel: String = "",
    val visionApiKeyDraft: String = "",
    val hasVisionApiKey: Boolean = false,
    val transcriptionBaseUrl: String = "",
    val transcriptionModel: String = "whisper-1",
    val transcriptionApiKeyDraft: String = "",
    val hasTranscriptionApiKey: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val store: AiSettingsStore,
    private val api: DailyAnalysisApi? = null,
) : ViewModel() {
    private val modelPresets = listOf(
        BuildConfig.DEFAULT_API_MODEL,
        BuildConfig.DEFAULT_API_ALT_MODEL,
    ).filter(String::isNotBlank).distinct()

    private val mutableUiState = MutableStateFlow(
        store.settings.value.toUiState().copy(modelPresets = modelPresets),
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.settings.collect { settings ->
                mutableUiState.update { current ->
                    current.copy(
                        baseUrl = settings.baseUrl,
                        model = settings.model,
                        hasApiKey = settings.hasApiKey,
                        includeTasks = settings.includeTasks,
                        includeSuggestions = settings.includeSuggestions,
                        includeReflections = settings.includeReflections,
                        persona = settings.persona,
                        allowEditing = settings.allowEditing,
                        skin = settings.skin,
                        visionUseLlm = settings.visionUseLlm,
                        visionBaseUrl = settings.visionBaseUrl,
                        visionModel = settings.visionModel,
                        hasVisionApiKey = settings.hasVisionApiKey,
                        transcriptionBaseUrl = settings.transcriptionBaseUrl,
                        transcriptionModel = settings.transcriptionModel,
                        hasTranscriptionApiKey = settings.hasTranscriptionApiKey,
                        isSaving = false,
                    )
                }
            }
        }
    }

    fun onBaseUrlChanged(value: String) = update { copy(baseUrl = value, message = null) }

    fun onModelChanged(value: String) = update { copy(model = value, message = null) }

    fun onApiKeyChanged(value: String) = update { copy(apiKeyDraft = value, message = null) }

    fun onIncludeTasksChanged(value: Boolean) = update { copy(includeTasks = value) }

    fun onIncludeSuggestionsChanged(value: Boolean) = update { copy(includeSuggestions = value) }

    fun onIncludeReflectionsChanged(value: Boolean) = update { copy(includeReflections = value) }

    fun onPersonaChanged(value: ChatPersona) = update { copy(persona = value, message = null) }

    fun onVisionUseLlmChanged(value: Boolean) =
        update { copy(visionUseLlm = value, message = null) }

    fun onVisionBaseUrlChanged(value: String) =
        update { copy(visionBaseUrl = value, message = null) }

    fun onVisionModelChanged(value: String) = update { copy(visionModel = value, message = null) }

    fun onVisionApiKeyChanged(value: String) =
        update { copy(visionApiKeyDraft = value, message = null) }

    fun onTranscriptionBaseUrlChanged(value: String) =
        update { copy(transcriptionBaseUrl = value, message = null) }

    fun onTranscriptionModelChanged(value: String) =
        update { copy(transcriptionModel = value, message = null) }

    fun onTranscriptionApiKeyChanged(value: String) =
        update { copy(transcriptionApiKeyDraft = value, message = null) }

    /** 编辑开关与皮肤即时生效，不依赖「保存设置」按钮，也不要求先配好 API。 */
    fun onAllowEditingChanged(value: Boolean) {
        update { copy(allowEditing = value, message = null) }
        viewModelScope.launch {
            runCatching { store.setAllowEditing(value) }
                .onFailure { update { copy(message = "编辑设置保存失败") } }
        }
    }

    fun onSkinChanged(value: AppSkin) {
        update { copy(skin = value, message = null) }
        viewModelScope.launch {
            runCatching { store.setSkin(value) }
                .onFailure { update { copy(message = "外观设置保存失败") } }
        }
    }

    fun save() {
        val state = mutableUiState.value
        val validationMessage = validate(state)
        if (validationMessage != null) {
            update { copy(message = validationMessage) }
            return
        }

        viewModelScope.launch {
            update { copy(isSaving = true, message = null) }
            runCatching {
                store.save(
                    AiSettingsInput(
                        baseUrl = state.baseUrl.trim().trimEnd('/'),
                        model = state.model.trim(),
                        newApiKey = state.apiKeyDraft.trim().ifBlank { null },
                        includeTasks = state.includeTasks,
                        includeSuggestions = state.includeSuggestions,
                        includeReflections = state.includeReflections,
                        persona = state.persona,
                        allowEditing = state.allowEditing,
                        skin = state.skin,
                        visionUseLlm = state.visionUseLlm,
                        visionBaseUrl = state.visionBaseUrl.trim(),
                        newVisionApiKey = state.visionApiKeyDraft.trim().ifBlank { null },
                        visionModel = state.visionModel.trim(),
                        transcriptionBaseUrl = state.transcriptionBaseUrl.trim(),
                        newTranscriptionApiKey = state.transcriptionApiKeyDraft.trim()
                            .ifBlank { null },
                        transcriptionModel = state.transcriptionModel.trim(),
                    ),
                )
            }.onSuccess {
                update {
                    copy(
                        apiKeyDraft = "",
                        visionApiKeyDraft = "",
                        transcriptionApiKeyDraft = "",
                        isSaving = false,
                        message = "设置已保存",
                    )
                }
            }.onFailure {
                update { copy(isSaving = false, message = "保存失败，请重试") }
            }
        }
    }

    fun testConnection() {
        if (mutableUiState.value.isTesting) return
        val tester = api
        if (tester == null) {
            update { copy(message = "连接测试尚未启用") }
            return
        }
        viewModelScope.launch {
            val connection = store.connection()
            if (connection == null) {
                update { copy(message = "请先填写并保存完整的 API 设置") }
                return@launch
            }
            update { copy(isTesting = true, message = null) }
            try {
                tester.testConnection(connection)
                update { copy(isTesting = false, message = "连接成功") }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                update {
                    copy(
                        isTesting = false,
                        message = throwable.message
                            ?.takeIf(String::isNotBlank)
                            ?: "连接失败，请检查配置",
                    )
                }
            }
        }
    }

    fun clearApiKey() = clearMediaKey(
        action = store::clearApiKey,
        onSuccess = { it.copy(apiKeyDraft = "", hasApiKey = false) },
    )

    fun clearVisionApiKey() = clearMediaKey(
        action = store::clearVisionApiKey,
        onSuccess = { it.copy(visionApiKeyDraft = "", hasVisionApiKey = false) },
    )

    fun clearTranscriptionApiKey() = clearMediaKey(
        action = store::clearTranscriptionApiKey,
        onSuccess = { it.copy(transcriptionApiKeyDraft = "", hasTranscriptionApiKey = false) },
    )

    private fun clearMediaKey(
        action: suspend () -> Unit,
        onSuccess: (SettingsUiState) -> SettingsUiState,
    ) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess {
                    update { onSuccess(this).copy(message = "密钥已删除") }
                }
                .onFailure {
                    update { copy(message = "删除失败，请重试") }
                }
        }
    }

    private fun validate(state: SettingsUiState): String? {
        val uri = runCatching { URI(state.baseUrl.trim()) }.getOrNull()
        if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
            return "API 地址必须是有效的 HTTPS 地址"
        }
        if (state.model.isBlank()) {
            return "请填写模型名称"
        }
        if (!state.hasApiKey && state.apiKeyDraft.isBlank()) {
            return "请填写 API 密钥"
        }
        // 独立图片/语音服务：填了地址就必须是合法 HTTPS
        if (!state.visionUseLlm && state.visionBaseUrl.isNotBlank()) {
            if (!isHttpsUrl(state.visionBaseUrl)) return "图片服务地址必须是有效的 HTTPS 地址"
        }
        if (state.transcriptionBaseUrl.isNotBlank()) {
            if (!isHttpsUrl(state.transcriptionBaseUrl)) {
                return "语音服务地址必须是有效的 HTTPS 地址"
            }
        }
        return null
    }

    private fun isHttpsUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme == "https" && !uri.host.isNullOrBlank()
    }

    private fun update(transform: SettingsUiState.() -> SettingsUiState) {
        mutableUiState.update(transform)
    }
}

private fun AiSettingsSnapshot.toUiState(): SettingsUiState = SettingsUiState(
    baseUrl = baseUrl,
    model = model,
    hasApiKey = hasApiKey,
    includeTasks = includeTasks,
    includeSuggestions = includeSuggestions,
    includeReflections = includeReflections,
    persona = persona,
    allowEditing = allowEditing,
    skin = skin,
    visionUseLlm = visionUseLlm,
    visionBaseUrl = visionBaseUrl,
    visionModel = visionModel,
    hasVisionApiKey = hasVisionApiKey,
    transcriptionBaseUrl = transcriptionBaseUrl,
    transcriptionModel = transcriptionModel,
    hasTranscriptionApiKey = hasTranscriptionApiKey,
)
