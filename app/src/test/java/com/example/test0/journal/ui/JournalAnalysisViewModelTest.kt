package com.example.test0.journal.ui

import androidx.lifecycle.SavedStateHandle
import com.example.test0.MainDispatcherRule
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.domain.DailyAnalysisRunner
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalAnalysisViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = LocalDate.parse("2026-11-05")
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun analyzeSelectedDayReportsSuccess() = runTest {
        val runner = FakeRunner()
        val viewModel = viewModel(runner)

        viewModel.analyzeSelectedDay()

        assertEquals(day, runner.day)
        assertFalse(viewModel.uiState.value.isAnalyzing)
        assertEquals("整理完成，可到“阅览”查看", viewModel.uiState.value.analysisMessage)
    }

    @Test
    fun analyzeFailureShowsSafeMessage() = runTest {
        val runner = FakeRunner(error = IllegalStateException("请先在设置中填写并保存 API 配置"))
        val viewModel = viewModel(runner)

        viewModel.analyzeSelectedDay()

        assertTrue(viewModel.uiState.value.analysisMessage.orEmpty().contains("设置"))
        assertFalse(viewModel.uiState.value.isAnalyzing)
    }

    private fun viewModel(runner: DailyAnalysisRunner) = JournalViewModel(
        repository = FakeCaptureRepository(),
        analysisRepository = NoAnalysisRepository(),
        clock = clock,
        savedStateHandle = SavedStateHandle(),
        analysisRunner = runner,
    )

    private inner class FakeCaptureRepository : CaptureRepository {
        private val capture = Capture(
            id = 1,
            text = "记录",
            contentAt = Instant.parse("2026-11-05T08:00:00Z"),
            importedAt = Instant.parse("2026-11-05T08:00:00Z"),
            updatedAt = Instant.parse("2026-11-05T08:00:00Z"),
            dayKey = day,
        )
        override fun observeByDay(day: LocalDate): Flow<List<Capture>> = flowOf(listOf(capture))
        override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime) = 1L
        override suspend fun delete(id: Long) = Unit
        override suspend fun updateText(id: Long, text: String, now: java.time.Instant) = Unit
    }

    private class NoAnalysisRepository : DailyAnalysisRepository {
        override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> = flowOf(null)
        override suspend fun save(analysis: DailyAnalysis): DailyAnalysis = analysis
        override suspend fun deleteByDay(day: LocalDate) = Unit
    }

    private class FakeRunner(
        private val error: Exception? = null,
    ) : DailyAnalysisRunner {
        var day: LocalDate? = null

        override suspend fun analyze(day: LocalDate): DailyAnalysis {
            this.day = day
            error?.let { throw it }
            return DailyAnalysis(
                day = day,
                markdownPath = "analysis/daily/$day.md",
                markdown = "# 摘要",
                generatedAt = Instant.EPOCH,
                sourceFingerprint = "fingerprint",
                modelName = "demo",
            )
        }
    }
}
