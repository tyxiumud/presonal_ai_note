package com.example.test0.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderErrorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readErrorShowsWorkingRetryAction() {
        var retried = false
        composeRule.setContent {
            ReaderScreen(
                state = ReaderUiState(
                    isLoading = false,
                    errorMessage = "读取日期失败，请重试",
                    canRetry = true,
                ),
                onDateSelected = {},
                onRetry = { retried = true },
            )
        }

        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }
}
