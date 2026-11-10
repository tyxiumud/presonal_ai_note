package com.example.test0.chat.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.test0.chat.model.ChatMessage
import com.example.test0.chat.model.ChatRole
import com.example.test0.chat.model.Conversation
import com.example.test0.journal.model.Capture
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearActionAsksForConfirmationBeforeClearing() {
        var cleared = false
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    messages = listOf(
                        ChatMessage(
                            id = 1,
                            role = ChatRole.ASSISTANT,
                            content = "上周你在准备考试。",
                            createdAt = Instant.parse("2026-11-05T09:00:00Z"),
                            referencedDays = listOf(LocalDate.parse("2026-11-03")),
                        ),
                    ),
                ),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
                onClearConversation = { cleared = true },
            )
        }

        composeRule.onNodeWithText("上周你在准备考试。").assertIsDisplayed()
        composeRule.onNodeWithText("参考：11月3日").assertIsDisplayed()

        composeRule.onNodeWithTag("chat_clear").performClick()
        composeRule.onNodeWithText("清空当前对话？").assertIsDisplayed()
        assertFalse(cleared)

        composeRule.onNodeWithTag("chat_clear_confirm").performClick()

        assertTrue(cleared)
    }

    @Test
    fun longPressingMessageAsksBeforeDeletingIt() {
        var deleted: ChatMessage? = null
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    messages = listOf(
                        ChatMessage(
                            id = 1,
                            role = ChatRole.USER,
                            content = "这是我想删掉的问题",
                            createdAt = Instant.parse("2026-11-05T09:00:00Z"),
                        ),
                    ),
                ),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
                onDeleteMessage = { deleted = it },
            )
        }

        composeRule.onNodeWithTag("chat_message_1").performTouchInput { longClick() }
        composeRule.onNodeWithText("删除这条消息？").assertIsDisplayed()
        assertNull(deleted)

        composeRule.onNodeWithTag("chat_delete_confirm").performClick()

        assertEquals(1L, deleted?.id)
    }

    @Test
    fun conversationSheetSwitchesAndCreates() {
        var selected: Long? = null
        var created = false
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    conversations = listOf(
                        Conversation(
                            id = 1,
                            title = "第一个话题",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                        Conversation(
                            id = 2,
                            title = "第二个话题",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                    ),
                    currentConversationId = 1,
                ),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
                onSelectConversation = { selected = it },
                onCreateConversation = { created = true },
            )
        }

        composeRule.onNodeWithTag("chat_conversations").performClick()
        composeRule.onNodeWithText("所有会话").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_conversation_2").performClick()
        assertEquals(2L, selected)

        composeRule.onNodeWithTag("chat_conversations").performClick()
        composeRule.onNodeWithTag("chat_new_conversation").performClick()
        assertTrue(created)
    }

    @Test
    fun deletingConversationAsksForConfirmation() {
        var deleted: Long? = null
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    conversations = listOf(
                        Conversation(
                            id = 1,
                            title = "唯一话题",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                    ),
                    currentConversationId = 1,
                ),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
                onDeleteConversation = { deleted = it },
            )
        }

        composeRule.onNodeWithTag("chat_conversations").performClick()
        composeRule.onNodeWithTag("chat_delete_conversation_1").performClick()
        composeRule.onNodeWithText("删除这个会话？").assertIsDisplayed()
        assertNull(deleted)

        composeRule.onNodeWithTag("chat_delete_conversation_confirm").performClick()
        assertEquals(1L, deleted)
    }

    @Test
    fun viewingReferencesCallsBackWithTheMessage() {
        var clicked: ChatMessage? = null
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    messages = listOf(
                        ChatMessage(
                            id = 1,
                            role = ChatRole.ASSISTANT,
                            content = "上周你在准备考试。",
                            createdAt = Instant.EPOCH,
                            referencedDays = listOf(LocalDate.parse("2026-11-03")),
                            referencedCaptureIds = listOf(7L),
                        ),
                    ),
                ),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
                onReferenceClicked = { clicked = it },
            )
        }

        composeRule.onNodeWithTag("chat_view_references_1").performClick()
        assertEquals(1L, clicked?.id)
    }

    @Test
    fun referenceSheetListsOriginalCaptures() {
        val capture = Capture(
            7L,
            "那天在准备考试",
            Instant.EPOCH,
            Instant.EPOCH,
            Instant.EPOCH,
            LocalDate.parse("2026-11-03"),
        )
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(referenceSheet = listOf(capture)),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
            )
        }

        composeRule.onNodeWithText("这次回答参考的原始记录").assertIsDisplayed()
        composeRule.onNodeWithText("那天在准备考试").assertIsDisplayed()
    }

    @Test
    fun emptyConversationHasNoClearAction() {
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(),
                onDraftChanged = {},
                onSend = {},
                onRetry = {},
                onSuggestionSelected = {},
            )
        }

        composeRule.onNodeWithTag("chat_clear").assertDoesNotExist()
    }
}
