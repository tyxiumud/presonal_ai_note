package io.github.tyxiumud.personalainote.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.tyxiumud.personalainote.chat.model.Conversation
import java.time.Instant

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

internal fun ConversationEntity.toModel() = Conversation(
    id = id,
    title = title,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)
