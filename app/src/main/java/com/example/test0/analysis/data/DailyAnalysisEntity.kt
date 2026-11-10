package com.example.test0.analysis.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_analyses")
data class DailyAnalysisEntity(
    @PrimaryKey
    val dayKey: String,
    val markdownPath: String,
    val generatedAtEpochMillis: Long,
    val sourceFingerprint: String,
    val modelName: String,
    val status: String,
    val errorMessage: String?,
)
