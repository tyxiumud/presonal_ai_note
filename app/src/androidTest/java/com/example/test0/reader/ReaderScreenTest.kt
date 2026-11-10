package com.example.test0.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val day = LocalDate.parse("2026-11-05")

    private fun analysis() = DailyAnalysis(
        day,
        "analysis/daily/$day.md",
        "# 每日整理\n\nAI 摘要内容",
        Instant.parse("2026-11-05T08:00:00Z"),
        "old",
        "demo",
    )

    private fun state() = ReaderUiState(
        days = listOf(day),
        selectedDate = day,
        captures = listOf(
            Capture(1, "这是第一条内容", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, day),
        ),
        analysis = analysis(),
        analyses = listOf(analysis()),
        isAnalysisStale = true,
        isLoading = false,
    )

    @Test
    fun displaysRawCaptureAndAnalysisEntryWithStaleBadge() {
        composeRule.setContent {
            ReaderScreen(state = state(), onDateSelected = {})
        }

        composeRule.onNodeWithText("这是第一条内容").assertIsDisplayed()
        composeRule.onNodeWithText("每日整理").assertIsDisplayed()
        composeRule.onNodeWithText("2026年11月5日").assertIsDisplayed()
        composeRule.onNodeWithText("需重新整理").assertIsDisplayed()
    }

    @Test
    fun tappingEntryOpensReadOnlyDetail() {
        composeRule.setContent {
            ReaderScreen(state = state(), onDateSelected = {})
        }

        composeRule.onNodeWithText("2026年11月5日").performClick()

        composeRule.onNodeWithText("整理详情").assertIsDisplayed()
        composeRule.onNodeWithText("由 AI 生成，只能查看或删除，不能修改").assertIsDisplayed()
        // 条目预览和详情正文各出现一次
        composeRule.onAllNodesWithText("AI 摘要内容").assertCountEquals(2)
    }

    @Test
    fun tappingRawCaptureOpensDetail() {
        composeRule.setContent {
            ReaderScreen(state = state(), onDateSelected = {})
        }

        composeRule.onNodeWithText("这是第一条内容").performClick()

        composeRule.onNodeWithText("随记详情").assertIsDisplayed()
        composeRule.onNodeWithText("你亲手记下的原始内容").assertIsDisplayed()
    }

    @Test
    fun deletingAnalysisAsksForConfirmation() {
        var deleted: LocalDate? = null
        composeRule.setContent {
            ReaderScreen(
                state = state(),
                onDateSelected = {},
                onDeleteAnalysis = { deleted = it },
            )
        }

        composeRule.onNodeWithTag("reader_delete_analysis").performClick()
        composeRule.onNodeWithText("删除这篇整理？").assertIsDisplayed()
        assertNull(deleted)

        composeRule.onNodeWithTag("reader_delete_confirm").performClick()

        assertEquals(day, deleted)
    }
}
