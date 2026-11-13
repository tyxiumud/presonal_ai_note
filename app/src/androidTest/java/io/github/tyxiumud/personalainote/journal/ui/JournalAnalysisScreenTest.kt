package io.github.tyxiumud.personalainote.journal.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.tyxiumud.personalainote.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JournalAnalysisScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun analyzeButtonIsEnabledForDayWithCapturesAndDispatches() {
        val day = LocalDate.parse("2026-11-05")
        val instant = Instant.parse("2026-11-05T08:00:00Z")
        var clicked = false

        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(
                    selectedDate = day,
                    captures = listOf(
                        Capture(1, "记录", instant, instant, instant, day),
                    ),
                ),
                today = day,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
                onAnalyze = { clicked = true },
            )
        }

        composeRule.onNodeWithTag("journal_analyze").assertIsEnabled().performClick()
        assertTrue(clicked)
    }
}
