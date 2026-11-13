package io.github.tyxiumud.personalainote.analysis.domain

import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisApi
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisOptions
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisRequest
import io.github.tyxiumud.personalainote.ai.api.StructuredDailyAnalysis
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsStore
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

fun interface DailyAnalysisRunner {
    suspend fun analyze(day: LocalDate): DailyAnalysis
}

class DailyAnalysisService(
    private val captureRepository: CaptureRepository,
    private val settingsStore: AiSettingsStore,
    private val api: DailyAnalysisApi,
    private val analysisRepository: DailyAnalysisRepository,
    private val clock: Clock = Clock.systemUTC(),
) : DailyAnalysisRunner {
    override suspend fun analyze(day: LocalDate): DailyAnalysis {
        val connection = settingsStore.connection()
            ?: throw DailyAnalysisException("请先在设置中填写并保存 API 配置")
        val captures = captureRepository.getByDay(day)
        if (captures.isEmpty()) {
            throw DailyAnalysisException("这一天没有记录，暂时无法整理")
        }
        val snapshot = settingsStore.settings.value
        val options = DailyAnalysisOptions(
            includeTasks = snapshot.includeTasks,
            includeSuggestions = snapshot.includeSuggestions,
            includeReflections = snapshot.includeReflections,
        )
        val structured = api.analyze(
            connection = connection,
            request = DailyAnalysisRequest(
                day = day,
                entries = captures.map(::analysisEntry),
                options = options,
            ),
        )
        val analysis = DailyAnalysis(
            day = day,
            markdownPath = "analysis/daily/$day.md",
            markdown = renderMarkdown(day, structured, options),
            generatedAt = Instant.now(clock),
            sourceFingerprint = sourceFingerprint(captures),
            modelName = connection.model,
        )
        return analysisRepository.save(analysis)
    }
}

class DailyAnalysisException(message: String) : Exception(message)

/** 图片和语音没有正文时给模型一个明确占位；识别过的记录直接用它的文字。 */
private fun analysisEntry(capture: Capture): String = when (capture.type) {
    CaptureType.TEXT -> capture.text
    CaptureType.IMAGE -> capture.text.takeIf(String::isNotBlank) ?: "[图片记录]"
    CaptureType.AUDIO -> capture.text.takeIf(String::isNotBlank) ?: "[语音记录]"
}

fun sourceFingerprint(captures: List<Capture>): String {
    val canonical = captures
        .sortedBy(Capture::id)
        .joinToString(separator = "\n") { capture ->
            val base = listOf(
                capture.id,
                capture.text,
                capture.contentAt.toEpochMilli(),
                capture.importedAt.toEpochMilli(),
                capture.updatedAt.toEpochMilli(),
                capture.dayKey,
            ).joinToString(separator = "|")
            // 纯文字记录保持原有指纹，避免升级后旧整理全部误判为过期。
            if (capture.type == CaptureType.TEXT) {
                base
            } else {
                "$base|${capture.type.name}|${capture.mediaPath.orEmpty()}"
            }
        }
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun renderMarkdown(
    day: LocalDate,
    result: StructuredDailyAnalysis,
    options: DailyAnalysisOptions,
): String = buildString {
    appendLine("# $day 每日整理")
    appendLine()
    appendLine("> 此内容由 AI 根据当天原始随记生成；原始记录没有被修改。")
    appendLine()
    appendLine("## 当日摘要")
    appendLine()
    appendLine(result.summary.trim())
    if (options.includeTasks) {
        appendSection("任务", result.tasks, taskList = true)
    }
    if (options.includeSuggestions) {
        appendSection("温和建议", result.gentleSuggestions)
    }
    if (options.includeReflections) {
        appendSection("值得继续思考", result.reflectionQuestions)
    }
}

private fun StringBuilder.appendSection(
    title: String,
    items: List<String>,
    taskList: Boolean = false,
) {
    appendLine()
    appendLine("## $title")
    appendLine()
    if (items.isEmpty()) {
        appendLine("- 暂无")
    } else {
        items.forEach { item ->
            appendLine(if (taskList) "- [ ] ${item.trim()}" else "- ${item.trim()}")
        }
    }
}
