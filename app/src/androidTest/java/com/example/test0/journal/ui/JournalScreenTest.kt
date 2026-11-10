package com.example.test0.journal.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.test0.journal.model.Capture
import com.example.test0.journal.model.CaptureType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class JournalScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun blankDraftDisablesSave() {
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("journal_save").assertIsNotEnabled()
    }

    @Test
    fun typingEnablesSaveAndDispatchesDraft() {
        var state by mutableStateOf(JournalUiState(selectedDate = today))
        composeRule.setContent {
            JournalScreen(
                state = state,
                today = today,
                onDraftChanged = { state = state.copy(draft = it) },
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("journal_input").performTextInput("第一次记录")

        assertEquals("第一次记录", state.draft)
        composeRule.onNodeWithTag("journal_save").assertIsEnabled()
    }

    @Test
    fun selectedPastDateIsVisible() {
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = LocalDate.of(2026, 10, 31)),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("2026年10月31日").assertIsDisplayed()
    }

    @Test
    fun futureDatesAreNotSelectableAndDateFieldOpensPicker() {
        val policy = PastOrPresentSelectableDates(today)
        val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val tomorrowUtc = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        assertTrue(policy.isSelectableDate(todayUtc))
        assertFalse(policy.isSelectableDate(tomorrowUtc))

        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("journal_date").performClick()

        composeRule.onNodeWithText("确定").assertIsDisplayed()
    }

    @Test
    fun readErrorShowsRetryAndDispatches() {
        var retried = false
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(
                    selectedDate = today,
                    readErrorMessage = "读取记录失败，请重试",
                ),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = { retried = true },
            )
        }

        composeRule.onNodeWithText("读取记录失败，请重试").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()

        assertTrue(retried)
    }

    @Test
    fun timelineShowsSavedCaptures() {
        val captures = listOf(
            capture(1, "上午完成了第一次记录", "2026-11-04T01:00:00Z"),
            capture(2, "下午继续学习 Compose", "2026-11-04T08:00:00Z"),
        )
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = captures),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("上午完成了第一次记录").assertIsDisplayed()
        composeRule.onNodeWithText("下午继续学习 Compose").assertIsDisplayed()
    }

    @Test
    fun multiLineNoteKeepsOneTitleAndFullBody() {
        val multiline = capture(1, "第一行标题\n\n第二段内容", "2026-11-04T01:00:00Z")
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = listOf(multiline)),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        // 正文完整显示（含首行），但首行不再被单独拎出来做成第二个标题
        composeRule.onNodeWithText("第一行标题", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("第一行标题").assertDoesNotExist()
        composeRule.onNodeWithText("第二段内容", substring = true).assertIsDisplayed()
    }

    @Test
    fun imageAndAudioCapturesRenderTheirOwnCards() {
        val instant = Instant.parse("2026-11-04T01:00:00Z")
        val captures = listOf(
            Capture(1, "", instant, instant, instant, today, CaptureType.IMAGE, "/no/such/image"),
            Capture(2, "", instant, instant, instant, today, CaptureType.AUDIO, "/no/such/audio"),
        )
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = captures),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("图片记录").assertIsDisplayed()
        composeRule.onNodeWithText("语音记录").assertIsDisplayed()
        composeRule.onNodeWithText("语音文件不可用").assertIsDisplayed()
    }

    @Test
    fun tappingCaptureOpensDetailSheet() {
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(
                    selectedDate = today,
                    captures = listOf(capture(1, "点开看看", "2026-11-04T01:00:00Z")),
                ),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("点开看看").performClick()

        composeRule.onNodeWithText("随记详情").assertIsDisplayed()
        composeRule.onNodeWithText("你亲手记下的原始内容").assertIsDisplayed()
    }

    @Test
    fun deletingCaptureRequiresConfirmation() {
        val target = capture(1, "要删除的记录", "2026-11-04T01:00:00Z")
        var deleted: Capture? = null
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = listOf(target)),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
                onDeleteCapture = { deleted = it },
            )
        }

        composeRule.onNodeWithTag("capture_delete_${target.id}").performClick()
        composeRule.onNodeWithText("删除这条随记？").assertIsDisplayed()
        assertNull(deleted)

        composeRule.onNodeWithText("删除").performClick()

        assertEquals(target.id, deleted?.id)
    }

    @Test
    fun composerIsASingleBoxWithoutLongShortSplit() {
        var state by mutableStateOf(
            JournalUiState(selectedDate = today, draft = "草稿内容"),
        )
        composeRule.setContent {
            JournalScreen(
                state = state,
                today = today,
                onDraftChanged = { state = state.copy(draft = it) },
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("journal_input").assertIsDisplayed()
        composeRule.onNodeWithTag("journal_expand").assertDoesNotExist()
        composeRule.onNodeWithText("短笔记").assertDoesNotExist()
        composeRule.onNodeWithText("长笔记").assertDoesNotExist()
    }

    @Test
    fun detailShowsTagsAndCanAddOne() {
        val target = capture(1, "带标签的正文", "2026-11-04T01:00:00Z").copy(
            tags = listOf("工作"),
        )
        var changedTags: List<String>? = null
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = listOf(target)),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
                onTagsChanged = { _, tags -> changedTags = tags },
            )
        }

        composeRule.onNodeWithText("带标签的正文").performClick()

        composeRule.onNodeWithTag("tag_chip_工作").assertIsDisplayed()
        composeRule.onNodeWithTag("tag_add").performClick()
        composeRule.onNodeWithTag("tag_input").performTextInput("灵感")
        composeRule.onNodeWithTag("tag_confirm").performClick()

        assertEquals(listOf("工作", "灵感"), changedTags)
    }

    @Test
    fun fabKeepsSingleComposerOnScreen() {
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("journal_add").performClick()

        composeRule.onNodeWithTag("journal_input").assertIsDisplayed()
        composeRule.onNodeWithTag("long_note_input").assertDoesNotExist()
    }

    @Test
    fun detailShowsFullNoteWithoutEnlarge() {
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(
                    selectedDate = today,
                    captures = listOf(capture(1, "可复制的正文", "2026-11-04T01:00:00Z")),
                ),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("可复制的正文").performClick()

        composeRule.onNodeWithTag("capture_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("capture_close").assertIsDisplayed()
        composeRule.onNodeWithText("放大").assertDoesNotExist()
    }

    @Test
    fun editButtonIsHiddenWhenEditingIsOffByDefault() {
        val target = capture(1, "原文", "2026-11-04T01:00:00Z")
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(selectedDate = today, captures = listOf(target)),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithTag("capture_edit_${target.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("capture_delete_${target.id}").assertIsDisplayed()
    }

    @Test
    fun editingCapturePrefillsTextAndDispatchesNewValue() {
        val target = capture(1, "原来的内容", "2026-11-04T01:00:00Z")
        var editedText: String? = null
        composeRule.setContent {
            JournalScreen(
                state = JournalUiState(
                    selectedDate = today,
                    canEdit = true,
                    captures = listOf(target),
                ),
                today = today,
                onDraftChanged = {},
                onDateSelected = {},
                onSave = {},
                onRetry = {},
                onEditCapture = { _, text -> editedText = text },
            )
        }

        composeRule.onNodeWithTag("capture_edit_${target.id}").performClick()
        composeRule.onNodeWithTag("journal_edit_input")
            .assertTextContains("原来的内容")
        composeRule.onNodeWithTag("journal_edit_input")
            .performTextClearance()
        composeRule.onNodeWithTag("journal_edit_input")
            .performTextInput("改写后的内容")
        composeRule.onNodeWithTag("journal_edit_confirm").performClick()

        assertEquals("改写后的内容", editedText)
    }

    private fun capture(id: Long, text: String, timestamp: String): Capture {
        val instant = Instant.parse(timestamp)
        return Capture(
            id = id,
            text = text,
            contentAt = instant,
            importedAt = instant,
            updatedAt = instant,
            dayKey = today,
        )
    }
}
