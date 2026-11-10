package com.example.test0.analysis.data

import com.example.test0.analysis.model.DailyAnalysis
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomDailyAnalysisRepository(
    private val dao: DailyAnalysisDao,
    private val markdownStore: DailyMarkdownStore,
) : DailyAnalysisRepository {
    override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> =
        dao.observeByDay(day.toString())
            .map { entity ->
                entity?.let {
                    val markdown = markdownStore.read(it.markdownPath)
                        ?: throw DailyAnalysisFileMissingException()
                    it.toModel(markdown)
                }
            }
            .flowOn(Dispatchers.IO)

    override fun observeAll(): Flow<List<DailyAnalysis>> =
        dao.observeAll()
            .map { entities ->
                // 文件可能已被系统清理；跳过即可，不让一篇坏文件挡住整个列表。
                entities.mapNotNull { entity ->
                    markdownStore.read(entity.markdownPath)?.let { entity.toModel(it) }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun save(analysis: DailyAnalysis): DailyAnalysis =
        withContext(Dispatchers.IO) {
            val previous = dao.getByDay(analysis.day.toString())
            val candidatePath = markdownStore.write(analysis.day, analysis.markdown)
            val persisted = analysis.copy(markdownPath = candidatePath)
            try {
                dao.upsert(persisted.toEntity())
            } catch (exception: Throwable) {
                withContext(NonCancellable) {
                    val committedPath = runCatching {
                        dao.getByDay(analysis.day.toString())?.markdownPath
                    }.getOrNull()
                    if (committedPath != candidatePath) {
                        markdownStore.delete(candidatePath)
                    }
                }
                throw exception
            }

            previous
                ?.markdownPath
                ?.takeIf { it != candidatePath }
                ?.let { oldPath -> runCatching { markdownStore.delete(oldPath) } }
            persisted
        }

    override suspend fun deleteByDay(day: LocalDate) = withContext(Dispatchers.IO) {
        val existing = dao.getByDay(day.toString()) ?: return@withContext
        dao.deleteByDay(day.toString())
        markdownStore.delete(existing.markdownPath)
    }

    private companion object {
        const val STATUS_READY = "READY"
    }

    private fun DailyAnalysis.toEntity() = DailyAnalysisEntity(
        dayKey = day.toString(),
        markdownPath = markdownPath,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        sourceFingerprint = sourceFingerprint,
        modelName = modelName,
        status = STATUS_READY,
        errorMessage = null,
    )
}

class DailyAnalysisFileMissingException :
    IllegalStateException("每日整理文件缺失，请重新整理")

private fun DailyAnalysisEntity.toModel(markdown: String) = DailyAnalysis(
    day = LocalDate.parse(dayKey),
    markdownPath = markdownPath,
    markdown = markdown,
    generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis),
    sourceFingerprint = sourceFingerprint,
    modelName = modelName,
)
