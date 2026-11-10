package com.example.test0.reader

import com.example.test0.MainDispatcherRule
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.domain.sourceFingerprint
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val newest = LocalDate.parse("2026-11-05")
    private val older = LocalDate.parse("2026-11-03")
    private val capturesByDay = mapOf(
        newest to listOf(capture(1, newest, "最新记录")),
        older to listOf(capture(2, older, "旧记录")),
    )

    @Test
    fun startsOnNewestAvailableDayAndCanSelectOlderDay() {
        val viewModel = ReaderViewModel(
            captureRepository = FakeCaptureRepository(capturesByDay),
            analysisRepository = FakeAnalysisRepository(emptyMap()),
        )

        assertEquals(newest, viewModel.uiState.value.selectedDate)
        assertEquals("最新记录", viewModel.uiState.value.captures.single().text)

        viewModel.onDateSelected(older)

        assertEquals(older, viewModel.uiState.value.selectedDate)
        assertEquals("旧记录", viewModel.uiState.value.captures.single().text)
    }

    @Test
    fun emptyHistoryStopsLoadingAndShowsNoSelectedDay() {
        val viewModel = ReaderViewModel(
            captureRepository = FakeCaptureRepository(emptyMap()),
            analysisRepository = FakeAnalysisRepository(emptyMap()),
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.selectedDate)
    }

    @Test
    fun fingerprintMismatchMarksAnalysisStale() {
        val captures = capturesByDay.getValue(newest)
        val current = sourceFingerprint(captures)
        val matching = analysis(newest, current)
        val repository = FakeAnalysisRepository(mapOf(newest to matching))
        val viewModel = ReaderViewModel(
            captureRepository = FakeCaptureRepository(capturesByDay),
            analysisRepository = repository,
        )

        assertFalse(viewModel.uiState.value.isAnalysisStale)
        repository.emit(newest, matching.copy(sourceFingerprint = "old"))

        assertTrue(viewModel.uiState.value.isAnalysisStale)
    }

    @Test
    fun analysesAreListedAndDeletingOneKeepsRawCaptures() = runTest {
        val repository = FakeAnalysisRepository(
            mapOf(
                newest to analysis(newest, "fingerprint-a"),
                older to analysis(older, "fingerprint-b"),
            ),
        )
        val viewModel = ReaderViewModel(
            captureRepository = FakeCaptureRepository(capturesByDay),
            analysisRepository = repository,
        )

        assertEquals(
            listOf(newest, older),
            viewModel.uiState.value.analyses.map(DailyAnalysis::day),
        )

        viewModel.deleteAnalysis(newest)

        assertEquals(
            listOf(older),
            viewModel.uiState.value.analyses.map(DailyAnalysis::day),
        )
        assertEquals("已删除这一天的整理", viewModel.uiState.value.actionMessage)
        assertEquals("最新记录", viewModel.uiState.value.captures.single().text)
    }

    private fun analysis(day: LocalDate, fingerprint: String) = DailyAnalysis(
        day = day,
        markdownPath = "analysis/daily/$day.md",
        markdown = "# $day 每日整理\n\n摘要",
        generatedAt = Instant.EPOCH,
        sourceFingerprint = fingerprint,
        modelName = "demo",
    )

    private fun capture(id: Long, day: LocalDate, text: String): Capture {
        val instant = Instant.parse("${day}T08:00:00Z")
        return Capture(id, text, instant, instant, instant, day)
    }
}

private class FakeCaptureRepository(
    private val values: Map<LocalDate, List<Capture>>,
) : CaptureRepository {
    override fun observeDays(): Flow<List<LocalDate>> =
        flowOf(values.keys.sortedDescending())

    override fun observeByDay(day: LocalDate): Flow<List<Capture>> =
        flowOf(values[day].orEmpty())

    override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime) = 1L

    override suspend fun delete(id: Long) = Unit

    override suspend fun updateText(id: Long, text: String, now: java.time.Instant) = Unit
}

private class FakeAnalysisRepository(
    values: Map<LocalDate, DailyAnalysis>,
) : DailyAnalysisRepository {
    private val flows = values.mapValues { MutableStateFlow<DailyAnalysis?>(it.value) }.toMutableMap()
    private val all = MutableStateFlow(values.values.sortedByDescending(DailyAnalysis::day))

    override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> =
        flows.getOrPut(day) { MutableStateFlow(null) }

    override fun observeAll(): Flow<List<DailyAnalysis>> = all

    override suspend fun save(analysis: DailyAnalysis): DailyAnalysis {
        emit(analysis.day, analysis)
        all.value = (all.value.filterNot { it.day == analysis.day } + analysis)
            .sortedByDescending(DailyAnalysis::day)
        return analysis
    }

    override suspend fun deleteByDay(day: LocalDate) {
        emit(day, null)
        all.value = all.value.filterNot { it.day == day }
    }

    fun emit(day: LocalDate, analysis: DailyAnalysis?) {
        flows.getOrPut(day) { MutableStateFlow(null) }.value = analysis
    }
}
