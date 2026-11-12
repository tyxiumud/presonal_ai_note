package com.example.test0.journal.ui

import androidx.lifecycle.SavedStateHandle
import com.example.test0.MainDispatcherRule
import com.example.test0.ai.settings.AiConnectionSettings
import com.example.test0.ai.settings.AiSettingsInput
import com.example.test0.ai.settings.AiSettingsSnapshot
import com.example.test0.ai.settings.AiSettingsStore
import com.example.test0.ai.settings.AppSkin
import com.example.test0.analysis.data.DailyAnalysisRepository
import com.example.test0.analysis.model.DailyAnalysis
import com.example.test0.journal.data.CaptureRepository
import com.example.test0.journal.model.Capture
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class JournalViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-11-04T02:30:00Z"), zone)
    private val today = LocalDate.of(2026, 11, 4)
    private lateinit var repository: FakeCaptureRepository
    private lateinit var analysisRepository: FakeAnalysisRepository
    private lateinit var viewModel: JournalViewModel

    @Before
    fun setUp() {
        repository = FakeCaptureRepository()
        analysisRepository = FakeAnalysisRepository()
        viewModel = createViewModel(repository)
    }

    @Test
    fun startsOnTodayAndObservesToday() {
        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertTrue(repository.observedDays.contains(today))
    }

    @Test
    fun blankDraftCannotBeSaved() = runTest {
        viewModel.onDraftChanged("   ")

        viewModel.save()

        assertTrue(repository.saved.isEmpty())
        assertNotNull(viewModel.uiState.value.validationMessage)
    }

    @Test
    fun futureDateIsRejected() {
        viewModel.onDateSelected(today.plusDays(1))

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertNotNull(viewModel.uiState.value.validationMessage)
    }

    @Test
    fun pastDateIsObservedAndSaved() = runTest {
        val past = LocalDate.of(2026, 10, 31)
        viewModel.onDateSelected(past)
        viewModel.onDraftChanged("  补录以前的内容  ")

        viewModel.save()

        assertTrue(repository.observedDays.contains(past))
        assertEquals(past, repository.saved.single().contentDate)
        assertEquals("补录以前的内容", repository.saved.single().text)
    }

    @Test
    fun successfulSaveClearsDraftOnlyAfterRepositoryCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.saveGate = gate
        viewModel.onDraftChanged("需要保存的内容")

        viewModel.save()

        assertEquals("需要保存的内容", viewModel.uiState.value.draft)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)

        assertEquals("", viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun saveFailureKeepsDraft() = runTest {
        repository.failure = IllegalStateException("database unavailable")
        viewModel.onDraftChanged("不能丢失的内容")

        viewModel.save()

        assertEquals("不能丢失的内容", viewModel.uiState.value.draft)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.validationMessage)
    }

    @Test
    fun readFailureShowsErrorAndRetryRecovers() {
        val failingRepository = FakeCaptureRepository().apply {
            failuresRemaining = 1
        }
        val failingViewModel = createViewModel(failingRepository)

        assertEquals("读取记录失败，请重试", failingViewModel.uiState.value.readErrorMessage)
        assertTrue(failingViewModel.uiState.value.captures.isEmpty())

        failingViewModel.onRetry()

        assertNull(failingViewModel.uiState.value.readErrorMessage)
        assertFalse(failingViewModel.uiState.value.isLoading)
        assertTrue(failingRepository.observedDays.count { it == today } >= 2)
    }

    @Test
    fun switchingDateNeverShowsPreviousDatesCaptures() {
        val previousCapture = capture(1, today, "今天的记录")
        repository.emitNow(today, listOf(previousCapture))
        assertEquals(listOf(previousCapture), viewModel.uiState.value.captures)

        val past = LocalDate.of(2026, 10, 31)
        repository.delayDay(past)

        viewModel.onDateSelected(past)

        assertEquals(past, viewModel.uiState.value.selectedDate)
        assertTrue(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.captures.isEmpty())

        val pastCapture = capture(2, past, "过去的记录")
        repository.emitNow(past, listOf(pastCapture))

        assertEquals(past, viewModel.uiState.value.selectedDate)
        assertEquals(listOf(pastCapture), viewModel.uiState.value.captures)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun restoresDraftAndSelectedDateFromSavedStateHandle() {
        val handle = SavedStateHandle()
        val first = createViewModel(repository, handle)
        val past = LocalDate.of(2026, 10, 31)
        first.onDateSelected(past)
        first.onDraftChanged("进程重建后不能丢失")

        val recreated = createViewModel(repository, handle)

        assertEquals(past, recreated.uiState.value.selectedDate)
        assertEquals("进程重建后不能丢失", recreated.uiState.value.draft)
    }

    @Test
    fun editingCaptureUpdatesTextAndInvalidatesDaysAnalysis() = runTest {
        val target = capture(1, today, "原来的内容")

        viewModel.editCapture(target, "  改写后的内容  ")

        assertEquals(listOf(1L to "改写后的内容"), repository.edits)
        assertEquals(listOf(today), analysisRepository.deletedDays)
        assertEquals("已更新这条随记", viewModel.uiState.value.analysisMessage)
    }

    @Test
    fun blankEditIsRejectedBeforeTouchingData() = runTest {
        val target = capture(1, today, "原来的内容")

        viewModel.editCapture(target, "   ")

        assertTrue(repository.edits.isEmpty())
        assertTrue(analysisRepository.deletedDays.isEmpty())
        assertEquals("随记内容不能为空", viewModel.uiState.value.validationMessage)
    }

    @Test
    fun tagsAreSavedWithoutChangingText() = runTest {
        val target = capture(1, today, "原文")

        viewModel.setTags(target, listOf("工作", "灵感"))

        assertEquals(listOf(1L to "工作,灵感"), repository.tagged)
        assertTrue(repository.edits.isEmpty())
    }

    @Test
    fun multiLineNoteIsSavedAsOneCapture() = runTest {
        val longText = "第一行\n\n## 小标题\n\n第二段内容"

        viewModel.onDraftChanged(longText)
        viewModel.save()

        assertEquals(longText.trim(), repository.saved.single().text)
        assertEquals("", viewModel.uiState.value.draft)
    }

    @Test
    fun editingIsBlockedWhenSwitchIsOff() = runTest {
        val blocked = JournalViewModel(
            repository = repository,
            analysisRepository = analysisRepository,
            clock = clock,
            savedStateHandle = SavedStateHandle(),
            settingsStore = FakeSettingsStore(allowEditing = false),
        )

        blocked.editCapture(capture(1, today, "原文"), "改写")

        assertTrue(repository.edits.isEmpty())
        assertTrue(analysisRepository.deletedDays.isEmpty())
    }

    @Test
    fun deletingCaptureRemovesItAndTheDaysAnalysis() = runTest {        val target = capture(1, today, "要删除的记录")
        repository.emitNow(today, listOf(target))

        viewModel.deleteCapture(target)

        assertEquals(listOf(target.id), repository.deletedIds)
        assertEquals(listOf(today), analysisRepository.deletedDays)
        assertEquals("已删除这条随记", viewModel.uiState.value.analysisMessage)
    }

    @Test
    fun deleteFailureReportsMessage() = runTest {
        repository.deleteFailure = IllegalStateException("database unavailable")

        viewModel.deleteCapture(capture(2, today, "删不掉的记录"))

        assertEquals("删除失败，请稍后重试", viewModel.uiState.value.validationMessage)
    }

    @Test
    fun searchFiltersAcrossAllDaysByKeyword() = runTest {
        val older = capture(9, today.minusDays(3), "上周在研究 Compose 布局")
        repository.setAllCaptures(
            listOf(capture(1, today, "今天写了点别的"), older),
        )

        viewModel.onSearchQueryChanged("Compose")

        assertTrue(viewModel.uiState.value.isFiltering)
        assertEquals(listOf(older), viewModel.uiState.value.searchResults)
    }

    @Test
    fun searchAlsoMatchesTags() = runTest {
        val tagged = capture(1, today, "一段正文", listOf("灵感"))
        repository.setAllCaptures(listOf(tagged, capture(2, today, "另一段正文")))

        viewModel.onSearchQueryChanged("灵感")

        assertEquals(listOf(tagged), viewModel.uiState.value.searchResults)
    }

    @Test
    fun availableTagsAreCollectedAndSelectingOneFilters() = runTest {
        val work = capture(1, today, "工作记录", listOf("工作"))
        val life = capture(2, today, "生活记录", listOf("生活"))
        repository.setAllCaptures(listOf(work, life))

        assertEquals(listOf("工作", "生活"), viewModel.uiState.value.availableTags)

        viewModel.onTagSelected("工作")
        assertEquals(listOf(work), viewModel.uiState.value.searchResults)

        // 再点一次同一个标签就取消筛选
        viewModel.onTagSelected("工作")
        assertFalse(viewModel.uiState.value.isFiltering)
    }

    @Test
    fun clearingFiltersResetsQueryAndTag() = runTest {
        repository.setAllCaptures(listOf(capture(1, today, "一条记录", listOf("灵感"))))
        viewModel.onSearchQueryChanged("一条")
        viewModel.onTagSelected("灵感")

        viewModel.onClearFilters()

        assertFalse(viewModel.uiState.value.isFiltering)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertNull(viewModel.uiState.value.selectedTag)
    }

    @Test
    fun savingWithDraftTagsWritesThemToTheNewCapture() = runTest {
        viewModel.onDraftChanged("新随记")
        viewModel.onDraftTagsChanged(listOf("工作", "灵感"))

        viewModel.save()

        assertEquals(listOf(1L to "工作,灵感"), repository.tagged)
        assertTrue(viewModel.uiState.value.draftTags.isEmpty())
    }

    private fun createViewModel(
        captureRepository: CaptureRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = JournalViewModel(
        repository = captureRepository,
        analysisRepository = analysisRepository,
        clock = clock,
        savedStateHandle = savedStateHandle,
        settingsStore = FakeSettingsStore(allowEditing = true),
    )

    private fun capture(
        id: Long,
        day: LocalDate,
        text: String,
        tags: List<String> = emptyList(),
    ): Capture {
        val instant = day.atStartOfDay(zone).toInstant()
        return Capture(
            id = id,
            text = text,
            contentAt = instant,
            importedAt = instant,
            updatedAt = instant,
            dayKey = day,
        ).copy(tags = tags)
    }

    private class FakeCaptureRepository : CaptureRepository {
        data class Saved(
            val text: String,
            val contentDate: LocalDate,
            val now: ZonedDateTime,
        )

        val observedDays = mutableListOf<LocalDate>()
        val saved = mutableListOf<Saved>()
        val deletedIds = mutableListOf<Long>()
        val edits = mutableListOf<Pair<Long, String>>()
        val tagged = mutableListOf<Pair<Long, String>>()
        private val allCaptures = MutableStateFlow<List<Capture>>(emptyList())
        var saveGate: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null
        var deleteFailure: Throwable? = null
        var failuresRemaining: Int = 0

        private val streams = mutableMapOf<LocalDate, MutableSharedFlow<List<Capture>>>()

        override fun observeAll(): Flow<List<Capture>> = allCaptures

        fun setAllCaptures(captures: List<Capture>) {
            allCaptures.value = captures
        }

        override fun observeByDay(day: LocalDate): Flow<List<Capture>> {
            observedDays += day
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                return flow { throw IllegalStateException("read unavailable") }
            }
            return stream(day)
        }

        override suspend fun addText(
            text: String,
            contentDate: LocalDate,
            now: ZonedDateTime,
        ): Long {
            saveGate?.await()
            failure?.let { throw it }
            saved += Saved(text, contentDate, now)
            return saved.size.toLong()
        }

        override suspend fun delete(id: Long) {
            deleteFailure?.let { throw it }
            deletedIds += id
        }

        override suspend fun updateText(id: Long, text: String, now: Instant) {
            edits += id to text
        }

        override suspend fun updateTags(id: Long, tags: List<String>) {
            tagged += id to tags.joinToString(",")
        }

        fun delayDay(day: LocalDate) {
            streams[day] = MutableSharedFlow(replay = 1)
        }

        fun emitNow(day: LocalDate, captures: List<Capture>) {
            stream(day).tryEmit(captures)
        }

        private fun stream(day: LocalDate): MutableSharedFlow<List<Capture>> =
            streams.getOrPut(day) {
                MutableSharedFlow<List<Capture>>(replay = 1).apply {
                    tryEmit(emptyList())
                }
            }
    }

    private class FakeSettingsStore(
        allowEditing: Boolean,
    ) : AiSettingsStore {
        override val settings = MutableStateFlow(
            AiSettingsSnapshot(allowEditing = allowEditing),
        )
        override suspend fun save(input: AiSettingsInput) = Unit
        override suspend fun connection(): AiConnectionSettings? = null
        override suspend fun clearApiKey() = Unit
        override suspend fun setAllowEditing(value: Boolean) = Unit
        override suspend fun setSkin(skin: AppSkin) = Unit
    }

    private class FakeAnalysisRepository : DailyAnalysisRepository {
        val deletedDays = mutableListOf<LocalDate>()

        override fun observeByDay(day: LocalDate): Flow<DailyAnalysis?> = flowOf(null)

        override suspend fun save(analysis: DailyAnalysis): DailyAnalysis = analysis

        override suspend fun deleteByDay(day: LocalDate) {
            deletedDays += day
        }
    }
}
