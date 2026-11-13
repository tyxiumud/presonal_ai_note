package io.github.tyxiumud.personalainote.journal.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tyxiumud.personalainote.ai.settings.AiSettingsStore
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.domain.DailyAnalysisRunner
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.data.MediaStore
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import io.github.tyxiumud.personalainote.media.MediaUnderstandingService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JournalUiState(
    val selectedDate: LocalDate,
    val draft: String = "",
    val draftTags: List<String> = emptyList(),
    val captures: List<Capture> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val validationMessage: String? = null,
    val readErrorMessage: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisMessage: String? = null,
    val markedDays: Set<LocalDate> = emptySet(),
    /** 由设置里的开关决定；默认 false，即记下之后不能改。 */
    val canEdit: Boolean = false,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val availableTags: List<String> = emptyList(),
    val searchResults: List<Capture> = emptyList(),
) {
    /** 有搜索词或选中标签时，列表改显示跨日期的搜索结果。 */
    val isFiltering: Boolean get() = searchQuery.isNotBlank() || selectedTag != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repository: CaptureRepository,
    private val analysisRepository: DailyAnalysisRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val savedStateHandle: SavedStateHandle,
    private val analysisRunner: DailyAnalysisRunner? = null,
    private val mediaStore: MediaStore? = null,
    private val settingsStore: AiSettingsStore? = null,
    private val mediaUnderstanding: MediaUnderstandingService? = null,
) : ViewModel() {
    private val today: LocalDate
        get() = LocalDate.now(clock)

    private val selectedDateText = savedStateHandle.getStateFlow(
        key = KEY_SELECTED_DATE,
        initialValue = today.toString(),
    )
    private val draft = savedStateHandle.getStateFlow(
        key = KEY_DRAFT,
        initialValue = "",
    )
    private val searchQuery = savedStateHandle.getStateFlow(
        key = KEY_SEARCH,
        initialValue = "",
    )
    private val draftTags = MutableStateFlow<List<String>>(emptyList())
    private val selectedTag = MutableStateFlow<String?>(null)
    private val isSaving = MutableStateFlow(value = false)
    private val validationMessage = MutableStateFlow<String?>(null)
    private val retryVersion = MutableStateFlow(0)
    private val isAnalyzing = MutableStateFlow(value = false)
    private val analysisMessage = MutableStateFlow<String?>(null)

    private val selectedDate: Flow<LocalDate> =
        selectedDateText
            .map(LocalDate::parse)
            .distinctUntilChanged()

    private val dayContent: Flow<DayContentState> = combine(
        selectedDate,
        retryVersion,
    ) { date, _ -> date }
        .flatMapLatest { date ->
            repository.observeByDay(date)
                .map { captures ->
                    DayContentState(
                        date = date,
                        captures = captures,
                        isLoading = false,
                        readErrorMessage = null,
                    )
                }
                .onStart {
                    emit(
                        DayContentState(
                            date = date,
                            captures = emptyList(),
                            isLoading = true,
                            readErrorMessage = null,
                        ),
                    )
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(
                        DayContentState(
                            date = date,
                            captures = emptyList(),
                            isLoading = false,
                            readErrorMessage = READ_ERROR_MESSAGE,
                        ),
                    )
                }
        }

    private val markedDays: Flow<Set<LocalDate>> = repository.observeDays()
        .map { days -> days.toSet() }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(emptySet())
        }

    private val canEdit: Flow<Boolean> =
        settingsStore?.settings
            ?.map { settings -> settings.allowEditing }
            ?.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(false)
            }
            ?: flowOf(false)

    // 跨日期搜索：全部随记来自 Room 的 Flow，关键词与标签在内存里过滤。
    // 个人笔记量级很小，这样比写复杂的 LIKE / 标签精确匹配 SQL 更直白，也更好测。
    private val allCaptures: Flow<List<Capture>> = repository.observeAll()
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(emptyList())
        }

    private val availableTags: Flow<List<String>> = allCaptures.map { captures ->
        captures.flatMap(Capture::tags).distinct().sorted()
    }

    private val searchResults: Flow<List<Capture>> = combine(
        allCaptures,
        searchQuery,
        selectedTag,
    ) { captures, query, tag ->
        val keyword = query.trim()
        captures.filter { capture ->
            val matchesKeyword = keyword.isEmpty() ||
                capture.text.contains(keyword, ignoreCase = true) ||
                capture.tags.any { it.contains(keyword, ignoreCase = true) }
            val matchesTag = tag == null || tag in capture.tags
            matchesKeyword && matchesTag
        }
    }

    private data class SearchState(
        val query: String,
        val tag: String?,
        val tags: List<String>,
        val results: List<Capture>,
    )

    private val searchState: Flow<SearchState> = combine(
        searchQuery,
        selectedTag,
        availableTags,
        searchResults,
    ) { query, tag, tags, results -> SearchState(query, tag, tags, results) }

    private data class ContentState(
        val day: DayContentState,
        val search: SearchState,
    )

    private val contentState: Flow<ContentState> = combine(
        dayContent,
        searchState,
    ) { day, search -> ContentState(day, search) }

    private val interactionState = combine(
        draft,
        isSaving,
        validationMessage,
        draftTags,
    ) { text, saving, message, tags ->
        InteractionState(text, saving, message, tags)
    }

    private val analysisInteraction = combine(
        isAnalyzing,
        analysisMessage,
    ) { analyzing, message -> AnalysisInteraction(analyzing, message) }

    val uiState: StateFlow<JournalUiState> = combine(
        contentState,
        interactionState,
        analysisInteraction,
        markedDays,
        canEdit,
    ) { content, interaction, analysis, marked, editable ->
        JournalUiState(
            selectedDate = content.day.date,
            draft = interaction.draft,
            draftTags = interaction.draftTags,
            captures = content.day.captures,
            isLoading = content.day.isLoading,
            isSaving = interaction.isSaving,
            validationMessage = interaction.validationMessage,
            readErrorMessage = content.day.readErrorMessage,
            isAnalyzing = analysis.isAnalyzing,
            analysisMessage = analysis.message,
            markedDays = marked,
            canEdit = editable,
            searchQuery = content.search.query,
            selectedTag = content.search.tag,
            availableTags = content.search.tags,
            searchResults = content.search.results,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = JournalUiState(
            selectedDate = currentSelectedDate(),
            draft = draft.value,
            isLoading = true,
        ),
    )

    fun onDraftChanged(value: String) {
        savedStateHandle[KEY_DRAFT] = value
        validationMessage.value = null
    }

    /** 写随记时先挑好标签，保存时一起写进新记录。 */
    fun onDraftTagsChanged(tags: List<String>) {
        draftTags.value = tags
    }

    fun onSearchQueryChanged(value: String) {
        savedStateHandle[KEY_SEARCH] = value
    }

    /** 再点一次已选中的标签就取消筛选。 */
    fun onTagSelected(tag: String) {
        selectedTag.value = if (selectedTag.value == tag) null else tag
    }

    fun onClearFilters() {
        savedStateHandle[KEY_SEARCH] = ""
        selectedTag.value = null
    }

    fun onDateSelected(date: LocalDate) {
        if (date.isAfter(today)) {
            validationMessage.value = "不能选择未来日期"
            return
        }
        savedStateHandle[KEY_SELECTED_DATE] = date.toString()
        validationMessage.value = null
        analysisMessage.value = null
    }

    fun onRetry() {
        retryVersion.value += 1
    }

    fun analyzeSelectedDay() {
        if (isAnalyzing.value) return
        val runner = analysisRunner
        if (runner == null) {
            analysisMessage.value = "AI 整理尚未启用"
            return
        }

        viewModelScope.launch {
            isAnalyzing.value = true
            analysisMessage.value = null
            try {
                runner.analyze(currentSelectedDate())
                analysisMessage.value = "整理完成，可到“阅览”查看"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                analysisMessage.value = throwable.message
                    ?.takeIf(String::isNotBlank)
                    ?: "整理失败，请稍后重试"
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    /** 把系统选中的图片或语音复制进应用私有目录并归档；失败时删掉半成品文件。 */
    fun importMedia(uri: Uri, type: CaptureType) {
        val store = mediaStore
        if (store == null) {
            validationMessage.value = "媒体导入尚未启用"
            return
        }

        viewModelScope.launch {
            validationMessage.value = null
            val path = withContext(Dispatchers.IO) { store.import(uri, type) }
            if (path == null) {
                validationMessage.value = "无法读取所选文件"
                return@launch
            }
            try {
                repository.addMedia(
                    type = type,
                    mediaPath = path,
                    caption = "",
                    contentDate = currentSelectedDate(),
                    now = ZonedDateTime.now(clock),
                )
                analysisMessage.value = if (type == CaptureType.IMAGE) {
                    "已添加图片"
                } else {
                    "已添加语音"
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                withContext(Dispatchers.IO) { store.delete(path) }
                validationMessage.value = "添加失败，请稍后重试"
            }
        }
    }

    /** 给任意类型的记录打标签；不改正文，所以当天整理不会失效。 */
    fun setTags(capture: Capture, tags: List<String>) {
        viewModelScope.launch {
            try {
                repository.updateTags(capture.id, tags)
                analysisMessage.value = "标签已更新"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                validationMessage.value = "标签保存失败，请稍后重试"
            }
        }
    }

    /** 把图片/语音识别成文字并写回随记正文；之后整理与聊天就能读到。 */
    fun recognizeMedia(capture: Capture) {
        val service = mediaUnderstanding
        if (service == null) {
            validationMessage.value = "媒体识别尚未启用"
            return
        }
        viewModelScope.launch {
            validationMessage.value = null
            analysisMessage.value = "正在识别…"
            try {
                val text = service.recognize(capture)
                analysisMessage.value = "识别完成：${text.take(24)}"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                analysisMessage.value = null
                validationMessage.value = throwable.message ?: "识别失败，请稍后重试"
            }
        }
    }

    fun save() {
        if (isSaving.value) return
        val text = draft.value.trim()
        if (text.isEmpty()) {
            validationMessage.value = "请输入随记内容"
            return
        }

        val date = currentSelectedDate()
        if (date.isAfter(today)) {
            validationMessage.value = "不能选择未来日期"
            return
        }

        viewModelScope.launch {
            isSaving.value = true
            validationMessage.value = null
            try {
                val id = repository.addText(
                    text = text,
                    contentDate = date,
                    now = ZonedDateTime.now(clock),
                )
                if (draftTags.value.isNotEmpty()) {
                    repository.updateTags(id, draftTags.value)
                }
                savedStateHandle[KEY_DRAFT] = ""
                draftTags.value = emptyList()
            } catch (_: Exception) {
                validationMessage.value = "保存失败，请稍后重试"
            } finally {
                isSaving.value = false
            }
        }
    }

    /**
     * 修改一条随记。与删除一样先清掉当天 AI 整理，再写回原始记录，
     * 避免衍生文件里残留旧正文。
     */
    fun editCapture(capture: Capture, text: String) {
        if (!uiState.value.canEdit) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            validationMessage.value = "随记内容不能为空"
            return
        }

        viewModelScope.launch {
            validationMessage.value = null
            try {
                analysisRepository.deleteByDay(capture.dayKey)
                repository.updateText(capture.id, trimmed, Instant.now(clock))
                analysisMessage.value = "已更新这条随记"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                validationMessage.value = "修改失败，请稍后重试"
            }
        }
    }

    /**
     * 删除一条随记。先删掉当天的 AI 整理，再删原始记录：万一中途失败，
     * 最多丢掉可以重新生成的整理结果，不会留下包含已删内容的衍生文件。
     */
    fun deleteCapture(capture: Capture) {
        viewModelScope.launch {
            validationMessage.value = null
            try {
                analysisRepository.deleteByDay(capture.dayKey)
                repository.delete(capture.id)
                analysisMessage.value = "已删除这条随记"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                validationMessage.value = "删除失败，请稍后重试"
            }
        }
    }

    private fun currentSelectedDate(): LocalDate =
        LocalDate.parse(selectedDateText.value)

    private data class DayContentState(
        val date: LocalDate,
        val captures: List<Capture>,
        val isLoading: Boolean,
        val readErrorMessage: String?,
    )

    private data class InteractionState(
        val draft: String,
        val isSaving: Boolean,
        val validationMessage: String?,
        val draftTags: List<String>,
    )

    private data class AnalysisInteraction(
        val isAnalyzing: Boolean,
        val message: String?,
    )

    private companion object {
        const val KEY_SELECTED_DATE = "selected_date"
        const val KEY_DRAFT = "draft"
        const val KEY_SEARCH = "search"
        const val READ_ERROR_MESSAGE = "读取记录失败，请重试"
    }
}
