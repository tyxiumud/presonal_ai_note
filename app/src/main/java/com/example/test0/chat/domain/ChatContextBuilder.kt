package com.example.test0.chat.domain

import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.domain.sourceFingerprint
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * 一次提问实际用到的本地资料。
 *
 * [text] 会发送给 API；[referencedDays] 与 [referencedCaptureIds] 只保存在本地，
 * 用于在聊天气泡下方展示“这次回答参考了什么”。
 */
data class ChatContext(
    val text: String = "",
    val referencedDays: List<LocalDate> = emptyList(),
    val referencedCaptureIds: List<Long> = emptyList(),
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * 第一版检索：近期每日摘要优先，再按关键词命中挑选原始记录，总长度受 [budgetChars] 限制。
 *
 * 失效的每日整理（原始记录在整理之后又变过）不进入上下文，避免把过期结论当成事实。
 */
class ChatContextBuilder(
    private val captureRepository: CaptureRepository,
    private val analysisRepository: DailyAnalysisRepository,
    private val maxDays: Int = 7,
    private val budgetChars: Int = 2_000,
) {
    suspend fun build(question: String, today: LocalDate): ChatContext {
        val days = captureRepository.observeDays()
            .first()
            .filter { !it.isAfter(today) }
            .sortedDescending()
            .take(maxDays)
        if (days.isEmpty()) return ChatContext()

        val snapshots = days.map { day ->
            val captures = captureRepository.getByDay(day)
            DaySnapshot(
                day = day,
                captures = captures,
                analysis = analysisRepository.observeByDay(day)
                    .first()
                    ?.takeIf { it.sourceFingerprint == sourceFingerprint(captures) },
            )
        }

        val tokens = queryTokens(question)
        val ranked = snapshots
            .flatMap { snapshot ->
                snapshot.captures.map { capture ->
                    RankedCapture(
                        day = snapshot.day,
                        capture = capture,
                        score = relevance(capture.text, tokens),
                    )
                }
            }
            .filter { it.score > 0 }
            .sortedWith(
                compareByDescending<RankedCapture> { it.score }
                    .thenByDescending { it.day }
                    .thenByDescending { it.capture.id },
            )

        val usedDays = linkedSetOf<LocalDate>()
        val usedCaptureIds = mutableListOf<Long>()
        val context = StringBuilder()

        for (snapshot in snapshots) {
            val summary = snapshot.analysis?.let { extractSummary(it.markdown) } ?: continue
            val block = "【${snapshot.day} 摘要】\n$summary\n"
            if (context.length + block.length > budgetChars) break
            context.append(block)
            usedDays += snapshot.day
        }

        for (item in ranked) {
            val block = "【${item.day} 记录】${contextEntry(item.capture)}\n"
            if (context.length + block.length > budgetChars) break
            context.append(block)
            usedDays += item.day
            usedCaptureIds += item.capture.id
        }

        return ChatContext(
            text = context.toString().trim(),
            referencedDays = usedDays.sortedDescending(),
            referencedCaptureIds = usedCaptureIds,
        )
    }

    private data class DaySnapshot(
        val day: LocalDate,
        val captures: List<Capture>,
        val analysis: DailyAnalysis?,
    )

    private data class RankedCapture(
        val day: LocalDate,
        val capture: Capture,
        val score: Int,
    )
}

private fun contextEntry(capture: Capture): String = when (capture.type) {
    CaptureType.TEXT -> capture.text.trim()
    CaptureType.IMAGE -> capture.text.takeIf(String::isNotBlank) ?: "[图片记录]"
    CaptureType.AUDIO -> capture.text.takeIf(String::isNotBlank) ?: "[语音记录]"
}

internal fun relevance(text: String, tokens: Set<String>): Int =
    tokens.count { token -> text.contains(token, ignoreCase = true) }

/**
 * 中文按二元组切分，英文和数字按单词切分。足够处理“这周我在忙什么”这类短问题，
 * 也不需要引入分词库。
 */
internal fun queryTokens(question: String): Set<String> {
    val normalized = question.lowercase()
    val tokens = linkedSetOf<String>()

    Regex("[a-z0-9]{2,}").findAll(normalized).forEach { tokens += it.value }

    val cjk = normalized.filter { it in '\u4e00'..'\u9fff' }
    when {
        cjk.length >= 2 -> cjk.windowed(2).forEach { tokens += it }
        cjk.length == 1 -> tokens += cjk
    }

    return tokens.filterNot { it in STOP_TOKENS }.toSet()
}

internal fun extractSummary(markdown: String): String {
    val lines = markdown.lines()
    val headingIndex = lines.indexOfFirst { it.trim() == SUMMARY_HEADING }
    if (headingIndex >= 0) {
        val body = lines.drop(headingIndex + 1)
            .takeWhile { !it.trimStart().startsWith("## ") }
            .joinToString("\n")
            .trim()
        if (body.isNotEmpty()) return body.take(SUMMARY_MAX_CHARS)
    }

    return lines.asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(">") }
        .joinToString("\n")
        .take(SUMMARY_MAX_CHARS)
        .trim()
}

private const val SUMMARY_HEADING = "## 当日摘要"
private const val SUMMARY_MAX_CHARS = 400

private val STOP_TOKENS = setOf(
    "什么", "怎么", "哪些", "可以", "我的", "我们", "你的", "是否", "一下", "帮我",
)
