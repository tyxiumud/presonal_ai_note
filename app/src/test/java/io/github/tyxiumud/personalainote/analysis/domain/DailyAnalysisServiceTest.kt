package io.github.tyxiumud.personalainote.analysis.domain

import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisApi
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisOptions
import io.github.tyxiumud.personalainote.ai.api.DailyAnalysisRequest
import io.github.tyxiumud.personalainote.ai.api.StructuredDailyAnalysis
import io.github.tyxiumud.personalainote.ai.settings.AiConnectionSettings
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsInput
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsSnapshot
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsStore
import io.github.tyxiumud.personalainote.ai.settings.AppSkin
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyAnalysisServiceTest {
    private val day = LocalDate.parse("2026-11-05")
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun missingSettingsAndEmptyDayFailBeforeCallingApi() = runTest {
        val api = FakeDailyAnalysisApi()
        val noSettings = service(
            api = api,
            settings = FakeSettingsStore(connection = null),
            captures = listOf(capture(1, "记录")),
        )

        val noSettingsError = runCatching { noSettings.analyze(day) }.exceptionOrNull()
        assertTrue(noSettingsError?.message.orEmpty().contains("设置"))
        assertEquals(0, api.calls)

        val empty = service(
            api = api,
            settings = FakeSettingsStore(),
            captures = emptyList(),
        )
        val emptyError = runCatching { empty.analyze(day) }.exceptionOrNull()
        assertTrue(emptyError?.message.orEmpty().contains("没有记录"))
        assertEquals(0, api.calls)
    }

    @Test
    fun mediaCapturesBecomePlaceholdersAndChangeFingerprint() = runTest {
        val text = capture(1, "今天完成了工作")
        val image = capture(2, "", type = CaptureType.IMAGE, mediaPath = "/files/media/a")
        val api = FakeDailyAnalysisApi()
        val service = service(api, FakeSettingsStore(), listOf(text, image))

        service.analyze(day)

        assertTrue(api.lastRequest!!.entries.contains("[图片记录]"))
        assertTrue(sourceFingerprint(listOf(text)) != sourceFingerprint(listOf(text, image)))
    }

    @Test
    fun successfulAnalysisRendersSelectedSectionsAndSavesMetadata() = runTest {
        val repository = FakeDailyAnalysisRepository()
        val settings = FakeSettingsStore(
            snapshot = AiSettingsSnapshot(
                baseUrl = "https://api.example/v1",
                model = "demo",
                hasApiKey = true,
                includeTasks = true,
                includeSuggestions = false,
                includeReflections = true,
            ),
        )
        val api = FakeDailyAnalysisApi(
            result = StructuredDailyAnalysis(
                summary = "完成了重要工作",
                tasks = listOf("明天散步"),
                gentleSuggestions = listOf("早点休息"),
                reflectionQuestions = listOf("什么值得坚持？"),
            ),
        )
        val service = service(api, settings, listOf(capture(1, "今天完成了工作")), repository)

        val result = service.analyze(day)

        assertTrue(result.markdown.contains("# 2026-11-05 每日整理"))
        assertTrue(result.markdown.contains("明天散步"))
        assertTrue(!result.markdown.contains("早点休息"))
        assertTrue(result.markdown.contains("什么值得坚持？"))
        assertEquals("demo", result.modelName)
        assertSame(result, repository.saved)
        assertEquals(false, api.lastRequest?.options?.includeSuggestions)
    }

    @Test
    fun apiFailureLeavesPreviousAnalysisUntouched() = runTest {
        val old = DailyAnalysis(
            day = day,
            markdownPath = "analysis/daily/old.md",
            markdown = "旧内容",
            generatedAt = Instant.EPOCH,
            sourceFingerprint = "old",
            modelName = "old-model",
        )
        val repository = FakeDailyAnalysisRepository(saved = old)
        val api = FakeDailyAnalysisApi(error = IllegalStateException("网络失败"))
        val service = service(api, FakeSettingsStore(), listOf(capture(1, "记录")), repository)

        val error = runCatching { service.analyze(day) }.exceptionOrNull()

        assertEquals("网络失败", error?.message)
        assertSame(old, repository.saved)
    }

    @Test
    fun sourceFingerprintIsStableAcrossInputOrderAndChangesWithText() {
        val first = capture(1, "A")
        val second = capture(2, "B")

        val normal = sourceFingerprint(listOf(first, second))
        val reversed = sourceFingerprint(listOf(second, first))
        val changed = sourceFingerprint(listOf(first, second.copy(text = "C")))

        assertEquals(normal, reversed)
        assertTrue(normal != changed)
    }

    private fun service(
        api: FakeDailyAnalysisApi,
        settings: FakeSettingsStore,
        captures: List<Capture>,
        repository: FakeDailyAnalysisRepository = FakeDailyAnalysisRepository(),
    ) = DailyAnalysisService(
        captureRepository = FakeCaptureRepository(captures),
        settingsStore = settings,
        api = api,
        analysisRepository = repository,
        clock = clock,
    )

    private fun capture(
        id: Long,
        text: String,
        type: CaptureType = CaptureType.TEXT,
        mediaPath: String? = null,
    ) = Capture(
        id = id,
        text = text,
        contentAt = Instant.parse("2026-11-05T08:00:00Z"),
        importedAt = Instant.parse("2026-11-05T09:00:00Z"),
        updatedAt = Instant.parse("2026-11-05T09:00:00Z"),
        dayKey = day,
        type = type,
        mediaPath = mediaPath,
    )
}

private class FakeCaptureRepository(
    private val captures: List<Capture>,
) : CaptureRepository {
    override fun observeByDay(day: LocalDate): Flow<List<Capture>> = flowOf(captures)
    override suspend fun getByDay(day: LocalDate): List<Capture> = captures
    override suspend fun addText(text: String, contentDate: LocalDate, now: ZonedDateTime) = 1L
    override suspend fun delete(id: Long) = Unit
    override suspend fun updateText(id: Long, text: String, now: java.time.Instant) = Unit
}

private class FakeSettingsStore(
    snapshot: AiSettingsSnapshot = AiSettingsSnapshot(
        baseUrl = "https://api.example/v1",
        model = "demo",
        hasApiKey = true,
    ),
    private val connection: AiConnectionSettings? = AiConnectionSettings(
        "https://api.example/v1",
        "demo",
        "secret",
    ),
) : AiSettingsStore {
    override val settings = MutableStateFlow(snapshot)
    override suspend fun save(input: AiSettingsInput) = Unit
    override suspend fun connection() = connection
    override suspend fun clearApiKey() = Unit
    override suspend fun setAllowEditing(value: Boolean) = Unit
    override suspend fun setSkin(skin: AppSkin) = Unit
}

private class FakeDailyAnalysisApi(
    private val result: StructuredDailyAnalysis = StructuredDailyAnalysis("摘要", emptyList(), emptyList(), emptyList()),
    private val error: Exception? = null,
) : DailyAnalysisApi {
    var calls = 0
    var lastRequest: DailyAnalysisRequest? = null

    override suspend fun testConnection(connection: AiConnectionSettings) = Unit

    override suspend fun analyze(
        connection: AiConnectionSettings,
        request: DailyAnalysisRequest,
    ): StructuredDailyAnalysis {
        calls += 1
        lastRequest = request
        error?.let { throw it }
        return result
    }
}

private class FakeDailyAnalysisRepository(
    var saved: DailyAnalysis? = null,
) : DailyAnalysisRepository {
    override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> = flowOf(saved)
    override suspend fun save(analysis: DailyAnalysis): DailyAnalysis {
        saved = analysis
        return analysis
    }

    override suspend fun deleteByDay(day: LocalDate) = Unit
}
