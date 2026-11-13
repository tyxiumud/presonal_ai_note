package io.github.tyxiumud.personalainote.reader

import io.github.tyxiumud.personalainote.MainDispatcherRule
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisFileMissingException
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderStateSafetyTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val firstDay = LocalDate.parse("2026-11-05")
    private val secondDay = LocalDate.parse("2026-11-03")

    @Test
    fun switchingDateClearsOldContentUntilNewDateEmits() {
        val captures = ControlledCaptureRepository(listOf(firstDay, secondDay))
        val analyses = ControlledAnalysisRepository()
        captures.emit(firstDay, listOf(capture(firstDay, "第一天")))
        analyses.emit(firstDay, analysis(firstDay, "第一天摘要"))
        val viewModel = ReaderViewModel(captures, analyses)

        assertEquals("第一天", viewModel.uiState.value.captures.single().text)

        viewModel.onDateSelected(secondDay)

        assertEquals(secondDay, viewModel.uiState.value.selectedDate)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.captures.isEmpty())
        assertNull(viewModel.uiState.value.analysis)

        captures.emit(secondDay, listOf(capture(secondDay, "第二天")))
        analyses.emit(secondDay, null)

        assertEquals("第二天", viewModel.uiState.value.captures.single().text)
    }

    @Test
    fun historyFailureIsVisibleAndRetryRestartsObservation() {
        val captures = ControlledCaptureRepository(listOf(firstDay)).apply {
            historyFailuresRemaining = 1
        }
        val viewModel = ReaderViewModel(captures, ControlledAnalysisRepository())

        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("日期"))
        assertTrue(viewModel.uiState.value.canRetry)

        viewModel.onRetry()

        assertEquals(listOf(firstDay), viewModel.uiState.value.days)
    }

    @Test
    fun missingMarkdownFileIsDistinctFromNeverAnalyzed() {
        val captures = ControlledCaptureRepository(listOf(firstDay))
        captures.emit(firstDay, listOf(capture(firstDay, "记录")))
        val analyses = ControlledAnalysisRepository().apply {
            failures[firstDay] = DailyAnalysisFileMissingException()
        }

        val viewModel = ReaderViewModel(captures, analyses)

        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("文件"))
        assertTrue(viewModel.uiState.value.canRetry)
    }

    private fun capture(day: LocalDate, text: String): Capture {
        val instant = Instant.parse("${day}T08:00:00Z")
        return Capture(1, text, instant, instant, instant, day)
    }

    private fun analysis(day: LocalDate, markdown: String) = DailyAnalysis(
        day,
        "analysis/daily/$day.md",
        markdown,
        Instant.EPOCH,
        "fingerprint",
        "demo",
    )
}

private class ControlledCaptureRepository(
    private val availableDays: List<LocalDate>,
) : CaptureRepository {
    var historyFailuresRemaining = 0
    private val captures = mutableMapOf<LocalDate, MutableSharedFlow<List<Capture>>>()

    override fun observeDays(): Flow<List<LocalDate>> {
        if (historyFailuresRemaining > 0) {
            historyFailuresRemaining -= 1
            return flow { throw IllegalStateException("history failed") }
        }
        return flowOf(availableDays)
    }

    override fun observeByDay(day: LocalDate): Flow<List<Capture>> =
        captures.getOrPut(day) { MutableSharedFlow(replay = 1) }

    override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime) = 1L

    override suspend fun delete(id: Long) = Unit

    override suspend fun updateText(id: Long, text: String, now: java.time.Instant) = Unit

    fun emit(day: LocalDate, values: List<Capture>) {
        captures.getOrPut(day) { MutableSharedFlow(replay = 1) }.tryEmit(values)
    }
}

private class ControlledAnalysisRepository : DailyAnalysisRepository {
    val failures = mutableMapOf<LocalDate, Throwable>()
    private val analyses = mutableMapOf<LocalDate, MutableSharedFlow<DailyAnalysis?>>()

    override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> {
        val failure = failures[day]
        return if (failure == null) {
            analyses.getOrPut(day) { MutableSharedFlow(replay = 1) }
        } else {
            flow { throw failure }
        }
    }

    override suspend fun save(analysis: DailyAnalysis): DailyAnalysis = analysis

    override suspend fun deleteByDay(day: LocalDate) = Unit

    fun emit(day: LocalDate, value: DailyAnalysis?) {
        analyses.getOrPut(day) { MutableSharedFlow(replay = 1) }.tryEmit(value)
    }
}
