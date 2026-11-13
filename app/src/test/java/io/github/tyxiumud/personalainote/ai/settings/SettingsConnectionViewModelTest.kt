package io.github.tyxiumud.personalainote.ai.settings

import io.github.tyxiumud.personalainote.MainDispatcherRule
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisApi
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisRequest
import io.github.tyxiumud.personalainote.ai.api.StructuredDailyAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun testConnectionRejectsMissingConfiguration() = runTest {
        val viewModel = SettingsViewModel(
            store = ConnectionSettingsStore(connection = null),
            api = ConnectionApi(),
        )

        viewModel.testConnection()

        assertTrue(viewModel.uiState.value.message.orEmpty().contains("保存"))
    }

    @Test
    fun testConnectionReportsSuccessAndSafeFailure() = runTest {
        val store = ConnectionSettingsStore()
        val success = SettingsViewModel(store, ConnectionApi())
        success.testConnection()
        assertEquals("连接成功", success.uiState.value.message)

        val failed = SettingsViewModel(
            store,
            ConnectionApi(error = IllegalStateException("连接失败，请检查配置")),
        )
        failed.testConnection()
        assertTrue(failed.uiState.value.message.orEmpty().contains("连接失败"))
    }
}

private class ConnectionSettingsStore(
    private val connection: AiConnectionSettings? = AiConnectionSettings(
        "https://api.example/v1",
        "demo",
        "secret",
    ),
) : AiSettingsStore {
    override val settings = MutableStateFlow(
        AiSettingsSnapshot(
            baseUrl = connection?.baseUrl.orEmpty(),
            model = connection?.model.orEmpty(),
            hasApiKey = connection != null,
        ),
    )
    override suspend fun save(input: AiSettingsInput) = Unit
    override suspend fun connection() = connection
    override suspend fun clearApiKey() = Unit
    override suspend fun setAllowEditing(value: Boolean) = Unit
    override suspend fun setSkin(skin: AppSkin) = Unit
}

private class ConnectionApi(
    private val error: Exception? = null,
) : DailyAnalysisApi {
    override suspend fun testConnection(connection: AiConnectionSettings) {
        error?.let { throw it }
    }

    override suspend fun analyze(
        connection: AiConnectionSettings,
        request: DailyAnalysisRequest,
    ): StructuredDailyAnalysis = error("not used")
}
