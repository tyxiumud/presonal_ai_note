package io.github.tyxiumud.personalainote.journal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "captures",
    indices = [Index(value = ["dayKey"])],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val textContent: String,
    val contentAtEpochMillis: Long,
    val importedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val dayKey: String,
    val processingState: String,
    val mediaPath: String? = null,
    /** 逗号分隔；标签本身不允许包含逗号。 */
    val tags: String = "",
)

internal fun encodeTags(tags: List<String>): String = tags
    .map { it.trim().replace(",", "") }
    .filter(String::isNotEmpty)
    .distinct()
    .joinToString(",")

internal fun decodeTags(raw: String?): List<String> = raw.orEmpty()
    .split(',')
    .map(String::trim)
    .filter(String::isNotEmpty)

internal fun CaptureEntity.toModel() = Capture(
    id = id,
    text = textContent,
    contentAt = Instant.ofEpochMilli(contentAtEpochMillis),
    importedAt = Instant.ofEpochMilli(importedAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    dayKey = LocalDate.parse(dayKey),
    type = runCatching { CaptureType.valueOf(type) }.getOrDefault(CaptureType.TEXT),
    mediaPath = mediaPath,
    tags = decodeTags(tags),
)
