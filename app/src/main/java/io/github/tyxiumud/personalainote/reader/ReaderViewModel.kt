package io.github.tyxiumud.personalainote.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisFileMissingException
import io.github.tyxiumud.personalainote.analysis.data.DailyAnalysisRepository
import io.github.tyxiumud.personalainote.analysis.domain.sourceFingerprint
import io.github.tyxiumud.personalainote.analysis.model.DailyAnalysis
import io.github.tyxiumud.personalainote.journal.data.CaptureRepository
import io.github.tyxiumud.personalainote.journal.model.Capture
import io.github.tyxiumud.personalainote.media.MediaUnderstandingService
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val days: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val captures: List<Capture> = emptyList(),
    val analysis: DailyAnalysis? = null,
    val analyses: List<DailyAnalysis> = emptyList(),
    val isAnalysisStale: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val actionMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModel(
    private val captureRepository: CaptureRepository,
    private val analysisRepository: DailyAnalysisRepository,
    private val mediaUnderstanding: MediaUnderstandingService? = null,
) : ViewModel() {
    private val requestedDate = MutableStateFlow<LocalDate?>(null)
    private val retryVersion = MutableStateFlow(0)
    private val actionMessage = MutableStateFlow<String?>(null)

    private val history: Flow<HistoryState> = retryVersion.flatMapLatest {
        captureRepository.observeDays()
            .map<List<LocalDate>, HistoryState>(HistoryState::Ready)
            .onStart { emit(HistoryState.Loading) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(HistoryState.Error)
            }
    }

    private val analyses: Flow<List<DailyAnalysis>> = analysisRepository.observeAll()
        .onStart { emit(emptyList()) }
        .catch { throwable ->
            if (throwable is CancellationException) throw throwable
            emit(emptyList())
        }

    private val selection: Flow<Selection> = combine(
        history,
        requestedDate,
    ) { historyState, requested ->
        when (historyState) {
            HistoryState.Loading -> Selection(ReaderUiState(isLoading = true))
            HistoryState.Error -> Selection(
                ReaderUiState(
                    isLoading = false,
                    errorMessage = "读取日期失败，请重试",
                    canRetry = true,
                ),
            )
            is HistoryState.Ready -> {
                val selected = requested
                    ?.takeIf(historyState.days::contains)
                    ?: historyState.days.firstOrNull()
                Selection(
                    base = ReaderUiState(
                        days = historyState.days,
                        selectedDate = selected,
                        isLoading = selected != null,
                    ),
                    day = selected,
                )
            }
        }
    }

    private val selectedDayContent: Flow<ReaderUiState> = selection
        .flatMapLatest { selected ->
            val day = selected.day
            if (day == null) {
                flowOf(selected.base.copy(isLoading = false))
            } else {
                combine(
                    captureRepository.observeByDay(day),
                    analysisRepository.observeByDay(day),
                ) { captures, analysis ->
                    selected.base.copy(
                        captures = captures,
                        analysis = analysis,
                        isAnalysisStale = analysis != null &&
                            analysis.sourceFingerprint != sourceFingerprint(captures),
                        isLoading = false,
                        errorMessage = null,
                        canRetry = false,
                    )
                }
                    .onStart {
                        emit(
                            selected.base.copy(
                                captures = emptyList(),
                                analysis = null,
                                isAnalysisStale = false,
                                isLoading = true,
                            ),
                        )
                    }
                    .catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        emit(
                            selected.base.copy(
                                captures = emptyList(),
                                analysis = null,
                                isAnalysisStale = false,
                                isLoading = false,
                                errorMessage = if (throwable is DailyAnalysisFileMissingException) {
                                    throwable.message
                                } else {
                                    "读取每日内容失败，请重试"
                                },
                                canRetry = true,
                            ),
                        )
                    }
            }
        }

    val uiState: StateFlow<ReaderUiState> = combine(
        selectedDayContent,
        analyses,
        actionMessage,
    ) { content, allAnalyses, message ->
        content.copy(analyses = allAnalyses, actionMessage = message)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReaderUiState(),
    )

    fun onDateSelected(day: LocalDate) {
        if (day in uiState.value.days) {
            requestedDate.value = day
            actionMessage.value = null
        }
    }

    fun onRetry() {
        retryVersion.value += 1
    }

    /** 只删 AI 整理，原始随记保持原样；之后可以重新整理这一天。 */
    fun deleteAnalysis(day: LocalDate) {
        viewModelScope.launch {
            try {
                analysisRepository.deleteByDay(day)
                actionMessage.value = "已删除这一天的整理"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                actionMessage.value = "删除失败，请稍后重试"
            }
        }
    }

    /** 给任意类型的记录打标签。 */
    fun setTags(capture: Capture, tags: List<String>) {
        viewModelScope.launch {
            try {
                captureRepository.updateTags(capture.id, tags)
                actionMessage.value = "标签已更新"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                actionMessage.value = "标签保存失败，请稍后重试"
            }
        }
    }

    /** 把图片/语音识别成文字，写回原始随记正文。 */
    fun recognizeMedia(capture: Capture) {
        val service = mediaUnderstanding ?: return
        viewModelScope.launch {
            actionMessage.value = "正在识别…"
            try {
                service.recognize(capture)
                actionMessage.value = "识别完成，已写入随记正文"
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                actionMessage.value = throwable.message ?: "识别失败，请稍后重试"
            }
        }
    }

    private sealed interface HistoryState {
        data object Loading : HistoryState
        data object Error : HistoryState
        data class Ready(val days: List<LocalDate>) : HistoryState
    }

    private data class Selection(
        val base: ReaderUiState,
        val day: LocalDate? = null,
    )
}
