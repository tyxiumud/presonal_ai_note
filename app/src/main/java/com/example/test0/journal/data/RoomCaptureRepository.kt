package com.example.test0.journal.data

import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCaptureRepository(
    private val captureDao: CaptureDao,
    private val mediaStore: MediaStore? = null,
) : CaptureRepository {
    override fun observeByDay(day: LocalDate): Flow<List<Capture>> =
        captureDao.observeByDay(day.toString()).map { entities ->
            entities.map(CaptureEntity::toModel)
        }

    override fun observeDays(): Flow<List<LocalDate>> =
        captureDao.observeDayKeys().map { keys -> keys.map(LocalDate::parse) }

    override fun observeAll(): Flow<List<Capture>> =
        captureDao.observeAll().map { entities -> entities.map(CaptureEntity::toModel) }

    override suspend fun getByDay(day: LocalDate): List<Capture> =
        captureDao.getByDay(day.toString()).map(CaptureEntity::toModel)

    override suspend fun getById(id: Long): Capture? =
        captureDao.getById(id)?.let(CaptureEntity::toModel)

    override suspend fun addText(
        text: String,
        contentDate: LocalDate,
        now: ZonedDateTime,
    ): Long {
        val (contentAt, importedAt) = timestamps(contentDate, now)
        return captureDao.insert(
            CaptureEntity(
                type = TYPE_TEXT,
                textContent = text.trim(),
                contentAtEpochMillis = contentAt,
                importedAtEpochMillis = importedAt,
                updatedAtEpochMillis = importedAt,
                dayKey = contentDate.toString(),
                processingState = STATE_RAW,
            ),
        )
    }

    override suspend fun addMedia(
        type: CaptureType,
        mediaPath: String,
        caption: String,
        contentDate: LocalDate,
        now: ZonedDateTime,
    ): Long {
        val (contentAt, importedAt) = timestamps(contentDate, now)
        return captureDao.insert(
            CaptureEntity(
                type = type.name,
                textContent = caption.trim(),
                contentAtEpochMillis = contentAt,
                importedAtEpochMillis = importedAt,
                updatedAtEpochMillis = importedAt,
                dayKey = contentDate.toString(),
                processingState = STATE_RAW,
                mediaPath = mediaPath,
            ),
        )
    }

    override suspend fun delete(id: Long) {
        // 先取路径再删行：行删掉之后就再也找不到文件了。
        captureDao.mediaPathById(id)?.let { mediaStore?.delete(it) }
        captureDao.deleteById(id)
    }

    override suspend fun updateText(
        id: Long,
        text: String,
        now: Instant,
    ) {
        captureDao.updateText(id, text.trim(), now.toEpochMilli())
    }

    override suspend fun updateTags(id: Long, tags: List<String>) {
        captureDao.updateTags(id, encodeTags(tags))
    }

    override suspend fun restore(captures: List<Capture>) {
        if (captures.isEmpty()) return
        captureDao.insertAll(
            captures.map { capture ->
                CaptureEntity(
                    id = 0,
                    type = capture.type.name,
                    textContent = capture.text,
                    contentAtEpochMillis = capture.contentAt.toEpochMilli(),
                    importedAtEpochMillis = capture.importedAt.toEpochMilli(),
                    updatedAtEpochMillis = capture.updatedAt.toEpochMilli(),
                    dayKey = capture.dayKey.toString(),
                    processingState = STATE_RAW,
                    mediaPath = capture.mediaPath,
                    tags = encodeTags(capture.tags),
                )
            },
        )
    }

    private fun timestamps(
        contentDate: LocalDate,
        now: ZonedDateTime,
    ): Pair<Long, Long> {
        val importedAt = now.toInstant().toEpochMilli()
        val contentAt = contentDate
            .atTime(now.toLocalTime())
            .atZone(now.zone)
            .toInstant()
            .toEpochMilli()
        return contentAt to importedAt
    }

    private companion object {
        const val TYPE_TEXT = "TEXT"
        const val STATE_RAW = "RAW"
    }
}
