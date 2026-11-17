package com.codeforge.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codeforge.agent.ui.CodeForgeTheme
import com.codeforge.agent.ui.chat.ChatScreen
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatScreenDisplaysModeChips() {
        composeTestRule.setContent {
            CodeForgeTheme {
                ChatScreen()
            }
        }
        composeTestRule.onNodeWithText("Plan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build").assertIsDisplayed()
    }
}