package io.github.tyxiumud.personalainote.analysis.model

import java.time.Instant
import java.time.LocalDate

data class DailyAnalysis(
    val day: LocalDate,
    val markdownPath: String,
    val markdown: String,
    val generatedAt: Instant,
    val sourceFingerprint: String,
    val modelName: String,
)
