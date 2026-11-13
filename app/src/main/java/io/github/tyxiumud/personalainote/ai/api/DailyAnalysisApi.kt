package io.github.tyxiumud.personalainote.ai.api

import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings
import java.time.LocalDate

data class DailyAnalysisOptions(
    val includeTasks: Boolean = true,
    val includeSuggestions: Boolean = true,
    val includeReflections: Boolean = true,
)

data class DailyAnalysisRequest(
    val day: LocalDate,
    val entries: List<String>,
    val options: DailyAnalysisOptions,
)

data class StructuredDailyAnalysis(
    val summary: String,
    val tasks: List<String>,
    val gentleSuggestions: List<String>,
    val reflectionQuestions: List<String>,
)

interface DailyAnalysisApi {
    suspend fun testConnection(connection: AiConnectionSettings)

    suspend fun analyze(
        connection: AiConnectionSettings,
        request: DailyAnalysisRequest,
    ): StructuredDailyAnalysis
}
