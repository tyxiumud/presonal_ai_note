package com.example.test0.journal.data

import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

interface CaptureRepository {
    fun observeByDay(day: LocalDate): Flow<List<Capture>>

    fun observeDays(): Flow<List<LocalDate>> = flowOf(emptyList())

    suspend fun getByDay(day: LocalDate): List<Capture> = observeByDay(day).first()

    /** 引用可追溯：按数据库 id 找回原始随记；记录已被删除时返回 null。 */
    suspend fun getById(id: Long): Capture? = null

    suspend fun addText(
        text: String,
        contentDate: LocalDate,
        now: ZonedDateTime,
    ): Long

    suspend fun delete(id: Long)

    /** 归档一张图片或一段语音；mediaPath 是应用私有目录里的绝对路径。 */
    suspend fun addMedia(
        type: CaptureType,
        mediaPath: String,
        caption: String,
        contentDate: LocalDate,
        now: ZonedDateTime,
    ): Long = 0L

    /** 修改正文；会刷新 updatedAt，使当天的整理来源指纹失效。 */
    suspend fun updateText(
        id: Long,
        text: String,
        now: Instant,
    )

    /** 只改标签，不动正文与时间戳，因此不会让当天整理失效。 */
    suspend fun updateTags(
        id: Long,
        tags: List<String>,
    ) = Unit

    /** 导入备份：按原始时间戳整批恢复某一天的记录。 */
    suspend fun restore(captures: List<Capture>) = Unit
}
