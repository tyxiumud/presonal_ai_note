package com.example.test0.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Rule
import org.junit.Test

class PersonalVaultAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigationSwitchesBetweenThreeSectionsAndSettingsOpensSheet() {
        composeRule.setContent {
            PersonalVaultApp(
                journalContent = { Text("记录页面内容") },
                readerContent = { Text("阅览页面内容") },
                chatContent = { Text("聊天页面内容") },
                settingsContent = { Text("设置页面内容") },
            )
        }

        composeRule.onNodeWithText("记录页面内容").assertIsDisplayed()
        composeRule.onNodeWithText("阅览").performClick()
        composeRule.onNodeWithText("阅览页面内容").assertIsDisplayed()
        composeRule.onNodeWithText("聊天").performClick()
        composeRule.onNodeWithText("聊天页面内容").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_entry").performClick()
        composeRule.onNodeWithText("设置页面内容").assertIsDisplayed()
    }

    @Test
    fun swipingLeftMovesFromJournalToReader() {
        composeRule.setContent {
            PersonalVaultApp(
                journalContent = { Text("记录页面内容") },
                readerContent = { Text("阅览页面内容") },
                chatContent = { Text("聊天页面内容") },
                settingsContent = { Text("设置页面内容") },
            )
        }

        composeRule.onNodeWithTag("main_pager").performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("阅览页面内容").assertIsDisplayed()
    }

    @Test
    fun settingsIsNotAMainTab() {
        composeRule.setContent {
            PersonalVaultApp(
                journalContent = { Text("记录页面内容") },
                readerContent = { Text("阅览页面内容") },
                chatContent = { Text("聊天页面内容") },
                settingsContent = { Text("设置页面内容") },
            )
        }

        composeRule.onNodeWithText("设置").assertDoesNotExist()
    }

    @Test
    fun backupEntrySitsNextToSettings() {
        composeRule.setContent {
            PersonalVaultApp(
                journalContent = { Text("记录页面内容") },
                readerContent = { Text("阅览页面内容") },
                chatContent = { Text("聊天页面内容") },
                settingsContent = { Text("设置页面内容") },
                backupContent = { Text("备份页面内容") },
            )
        }

        composeRule.onNodeWithTag("backup_entry").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_entry").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_entry").performClick()
        composeRule.onNodeWithText("备份页面内容").assertIsDisplayed()
    }
}
