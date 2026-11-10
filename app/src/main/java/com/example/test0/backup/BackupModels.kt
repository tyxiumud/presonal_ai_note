package com.example.test0.backup

import java.time.LocalDate

/** zip 里的清单：只记录格式版本和导出了哪些日期。 */
data class BackupManifest(
    val version: Int,
    val app: String,
    val exportedAt: String,
    val days: List<LocalDate>,
)

data class BackupDay(
    val day: LocalDate,
    val captures: List<BackupCapture>,
    val analysis: BackupAnalysis?,
)

data class BackupCapture(
    val type: String,
    val text: String,
    val contentAtEpochMillis: Long,
    val importedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** zip 内的相对路径；纯文字记录为 null。 */
    val mediaEntry: String?,
)

data class BackupAnalysis(
    val markdown: String,
    val generatedAtEpochMillis: Long,
    val sourceFingerprint: String,
    val modelName: String,
)

data class BackupResult(
    val dayCount: Int,
    val captureCount: Int,
    val skippedDays: List<LocalDate> = emptyList(),
    val conversationCount: Int = 0,
    val messageCount: Int = 0,
)

class BackupException(message: String) : Exception(message)
