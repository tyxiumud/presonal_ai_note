package io.github.tyxiumud.personalainote.chat.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.tyxiumud.personalainote.chat.model.ChatMessage
import io.github.tyxiumud.personalainote.chat.model.ChatRole
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val content: String,
    val createdAtEpochMillis: Long,
    val referencedDayKeys: String,
    val referencedCaptureIds: String,
    // 迁移里给旧表加列时 SQLite 要求 NOT NULL 列带默认值，这里保持一致，
    // 否则 Room 会在打开数据库时判定迁移没有正确处理。
    @ColumnInfo(defaultValue = "1")
    val conversationId: Long = 1,
)

internal fun ChatMessageEntity.toModel() = ChatMessage(
    id = id,
    role = when (role) {
        ROLE_ASSISTANT -> ChatRole.ASSISTANT
        else -> ChatRole.USER
    },
    content = content,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    referencedDays = referencedDayKeys.toDayList(),
    referencedCaptureIds = referencedCaptureIds.toIdList(),
    conversationId = conversationId,
)

internal fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    role = if (role == ChatRole.ASSISTANT) ROLE_ASSISTANT else ROLE_USER,
    content = content,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    referencedDayKeys = referencedDays.joinToString(SEPARATOR) { it.toString() },
    referencedCaptureIds = referencedCaptureIds.joinToString(SEPARATOR) { it.toString() },
    conversationId = conversationId,
)

private fun String.toDayList(): List<LocalDate> =
    split(SEPARATOR)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(LocalDate::parse)

private fun String.toIdList(): List<Long> =
    split(SEPARATOR)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map(String::toLong)

private const val SEPARATOR = ","
private const val ROLE_USER = "USER"
private const val ROLE_ASSISTANT = "ASSISTANT"
