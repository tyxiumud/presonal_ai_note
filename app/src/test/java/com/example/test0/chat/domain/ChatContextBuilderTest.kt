package com.example.test0.chat.domain

import com.example.test0.analysis.domain.sourceFingerprint
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.model.Capture
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextBuilderTest {
    private val today = LocalDate.parse("2026-11-05")

    @Test
    fun includesSummaryAndOnlyRelevantCaptures() = runTest {
        val captures = listOf(
            testCapture(1, today, "今天在研究 Compose 布局"),
            testCapture(2, today, "晚上下楼散步"),
        )
        val builder = builder(
            captures = captures,
            analyses = listOf(
                testAnalysis(
                    day = today,
                    fingerprint = sourceFingerprint(captures),
                    markdown = """
                        # $today 每日整理

                        ## 当日摘要

                        今天主要在学 Compose。

                        ## 任务

                        - [ ] 复习布局
                    """.trimIndent(),
                ),
            ),
        )

        val context = builder.build(question = "Compose 布局学到哪了", today = today)

        assertTrue(context.text.contains("今天主要在学 Compose。"))
        assertTrue(context.text.contains("今天在研究 Compose 布局"))
        assertFalse(context.text.contains("晚上下楼散步"))
        assertEquals(listOf(today), context.referencedDays)
        assertEquals(listOf(1L), context.referencedCaptureIds)
    }

    @Test
    fun staleAnalysisIsKeptOutOfContext() = runTest {
        val captures = listOf(testCapture(1, today, "关于阅读的想法"))
        val builder = builder(
            captures = captures,
            analyses = listOf(
                testAnalysis(
                    day = today,
                    fingerprint = "已经过期的指纹",
                    markdown = "# 每日整理\n\n## 当日摘要\n\n过期的结论",
                ),
            ),
        )

        val context = builder.build(question = "阅读", today = today)

        assertFalse(context.text.contains("过期的结论"))
        assertTrue(context.text.contains("关于阅读的想法"))
    }

    @Test
    fun stopsAddingContentWhenBudgetIsReached() = runTest {
        val captures = (1..20).map { index ->
            testCapture(index.toLong(), today, "第 $index 条关于阅读的记录")
        }
        val builder = builder(captures = captures, analyses = emptyList(), budgetChars = 120)

        val context = builder.build(question = "阅读", today = today)

        assertTrue(context.text.length <= 120)
        assertTrue(context.referencedCaptureIds.size in 1 until captures.size)
    }

    @Test
    fun emptyLibraryProducesEmptyContext() = runTest {
        val builder = builder(captures = emptyList(), analyses = emptyList())

        val context = builder.build(question = "最近在忙什么", today = today)

        assertTrue(context.isEmpty)
        assertTrue(context.referencedDays.isEmpty())
    }

    @Test
    fun futureDaysAreIgnored() = runTest {
        val future = today.plusDays(1)
        val builder = ChatContextBuilder(
            captureRepository = FakeCaptureRepository(
                mapOf(today to listOf(testCapture(1, today, "今天的记录")), future to listOf(testCapture(2, future, "未来的记录"))),
            ),
            analysisRepository = FakeAnalysisRepository(),
        )

        val context = builder.build(question = "记录", today = today)

        assertTrue(context.text.contains("今天的记录"))
        assertFalse(context.text.contains("未来的记录"))
    }

    private fun builder(
        captures: List<Capture>,
        analyses: List<DailyAnalysis>,
        budgetChars: Int = 2_000,
    ): ChatContextBuilder = ChatContextBuilder(
        captureRepository = FakeCaptureRepository(captures.groupBy(Capture::dayKey)),
        analysisRepository = FakeAnalysisRepository(analyses.associateBy(DailyAnalysis::day)),
        budgetChars = budgetChars,
    )
}
