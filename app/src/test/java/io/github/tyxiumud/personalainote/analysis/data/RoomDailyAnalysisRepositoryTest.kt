package io.github.tyxiumud.personalainote.analysis.data

import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDailyAnalysisRepositoryTest {
    private val day = LocalDate.parse("2026-11-05")

    @Test
    fun daoFailureKeepsPreviousFileAndMetadataAndDeletesCandidate() = runTest {
        val oldEntity = entity("analysis/daily/old.md", "old")
        val dao = FakeDailyAnalysisDao(oldEntity, failUpsert = true)
        val store = FakeMarkdownStore(mutableMapOf(oldEntity.markdownPath to "旧内容"))
        val repository = RoomDailyAnalysisRepository(dao, store)

        val error = runCatching { repository.save(analysis("新内容", "new")) }.exceptionOrNull()

        assertEquals(dao.failure.message, error?.message)
        assertEquals(oldEntity, dao.current.value)
        assertEquals("旧内容", store.files[oldEntity.markdownPath])
        assertEquals(setOf(oldEntity.markdownPath), store.files.keys)
    }

    @Test
    fun cancellationAfterDatabaseCommitKeepsCommittedCandidateFile() = runTest {
        val oldEntity = entity("analysis/daily/old.md", "old")
        val dao = FakeDailyAnalysisDao(oldEntity, commitThenCancel = true)
        val store = FakeMarkdownStore(mutableMapOf(oldEntity.markdownPath to "旧内容"))
        val repository = RoomDailyAnalysisRepository(dao, store)

        runCatching { repository.save(analysis("新内容", "new")) }

        val committedPath = dao.current.value?.markdownPath
        assertEquals("新内容", store.files[committedPath])
    }

    @Test
    fun successfulCommitSwitchesMetadataThenCleansOldFile() = runTest {
        val oldEntity = entity("analysis/daily/old.md", "old")
        val dao = FakeDailyAnalysisDao(oldEntity)
        val store = FakeMarkdownStore(mutableMapOf(oldEntity.markdownPath to "旧内容"))
        val repository = RoomDailyAnalysisRepository(dao, store)

        val saved = repository.save(analysis("新内容", "new"))

        assertEquals(saved.markdownPath, dao.current.value?.markdownPath)
        assertEquals("新内容", store.files[saved.markdownPath])
        assertNull(store.files[oldEntity.markdownPath])
    }

    @Test
    fun deleteByDayRemovesMetadataAndMarkdownFile() = runTest {
        val existing = entity("analysis/daily/old.md", "old")
        val dao = FakeDailyAnalysisDao(existing)
        val store = FakeMarkdownStore(mutableMapOf(existing.markdownPath to "旧内容"))
        val repository = RoomDailyAnalysisRepository(dao, store)

        repository.deleteByDay(day)

        assertNull(dao.current.value)
        assertTrue(store.files.isEmpty())
    }

    private fun analysis(markdown: String, fingerprint: String) = DailyAnalysis(
        day = day,
        markdownPath = "",
        markdown = markdown,
        generatedAt = Instant.parse("2026-11-05T12:00:00Z"),
        sourceFingerprint = fingerprint,
        modelName = "demo",
    )

    private fun entity(path: String, fingerprint: String) = DailyAnalysisEntity(
        dayKey = day.toString(),
        markdownPath = path,
        generatedAtEpochMillis = 1,
        sourceFingerprint = fingerprint,
        modelName = "demo",
        status = "READY",
        errorMessage = null,
    )
}

private class FakeDailyAnalysisDao(
    initial: DailyAnalysisEntity?,
    private val failUpsert: Boolean = false,
    private val commitThenCancel: Boolean = false,
) : DailyAnalysisDao {
    val current = MutableStateFlow(initial)
    val failure = IllegalStateException("dao failed")

    override suspend fun upsert(entity: DailyAnalysisEntity) {
        if (failUpsert) throw failure
        current.value = entity
        if (commitThenCancel) throw CancellationException("cancel after commit")
    }

    override fun observeByDay(dayKey: String): Flow<DailyAnalysisEntity?> = current

    override fun observeAll(): Flow<List<DailyAnalysisEntity>> =
        current.map { entity -> listOfNotNull(entity) }

    override suspend fun getByDay(dayKey: String): DailyAnalysisEntity? = current.value

    override suspend fun deleteByDay(dayKey: String) {
        current.value = null
    }
}

private class FakeMarkdownStore(
    val files: MutableMap<String, String>,
) : DailyMarkdownStore {
    private var generation = 0

    override fun write(day: LocalDate, markdown: String): String {
        generation += 1
        return "analysis/daily/$day-$generation.md".also { files[it] = markdown }
    }

    override fun read(relativePath: String): String? = files[relativePath]

    override fun delete(relativePath: String) {
        files.remove(relativePath)
    }
}
