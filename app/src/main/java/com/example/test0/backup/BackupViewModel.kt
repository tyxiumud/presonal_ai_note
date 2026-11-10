package com.example.test0.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test0.journal.data.CaptureRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BackupUiState(
    val days: List<LocalDate> = emptyList(),
    val selectedDays: Set<LocalDate> = emptySet(),
    val isWorking: Boolean = false,
    val message: String? = null,
) {
    val canExport: Boolean get() = selectedDays.isNotEmpty() && !isWorking
}

class BackupViewModel(
    private val repository: BackupRepository,
    captureRepository: CaptureRepository,
) : ViewModel() {
    private val selection = MutableStateFlow<Set<LocalDate>?>(null)
    private val working = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BackupUiState> = kotlinx.coroutines.flow.combine(
        captureRepository.observeDays(),
        selection,
        working,
        message,
    ) { days, explicitSelection, busy, text ->
        // null 表示用户还没动过勾选，默认全选；动过之后就完全听用户的。
        val selected = explicitSelection ?: days.toSet()
        BackupUiState(
            days = days,
            selectedDays = selected.intersect(days.toSet()),
            isWorking = busy,
            message = text,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    fun toggleDay(day: LocalDate) {
        val current = uiState.value.selectedDays
        selection.value = if (day in current) current - day else current + day
        message.value = null
    }

    fun selectAll() {
        selection.value = uiState.value.days.toSet()
        message.value = null
    }

    fun clearSelection() {
        selection.value = emptySet()
        message.value = null
    }
    fun export(uri: Uri) {
        val days = uiState.value.selectedDays.sortedDescending()
        if (days.isEmpty()) {
            message.value = "请先选择要导出的日期"
            return
        }
        run("备份导出失败，请稍后重试") {
            val result = repository.export(uri, days)
            "已导出 ${result.dayCount} 天、${result.captureCount} 条记录、" +
                "${result.conversationCount} 个会话、${result.messageCount} 条聊天消息"
        }
    }

    fun import(uri: Uri) {
        run("导入失败，请检查备份包") {
            val result = repository.import(uri)
            val skipped = result.skippedDays.size
            val tail = "、${result.conversationCount} 个会话、${result.messageCount} 条聊天消息"
            if (skipped > 0) {
                "已导入 ${result.dayCount} 天、${result.captureCount} 条记录$tail；" +
                    "$skipped 天已有记录，已跳过"
            } else {
                "已导入 ${result.dayCount} 天、${result.captureCount} 条记录$tail"
            }
        }
    }

    private fun run(errorText: String, block: suspend () -> String) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            message.value = null
            try {
                message.value = block()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                message.value = (throwable as? BackupException)?.message ?: errorText
            } finally {
                working.value = false
            }
        }
    }
}
