package com.devekene.notebook

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `bottom bar displays Notes Insights Settings and no Vault tab`() {
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("nav_notes")).assertExists()
        composeRule.onNode(hasTestTag("nav_insights")).assertExists()
        composeRule.onNode(hasTestTag("nav_settings")).assertExists()
        composeRule.onNode(hasText("Vault")).assertDoesNotExist()

        composeRule.onNode(hasTestTag("nav_settings")).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Biometric & Passcode Lock")).assertExists()
    }

    @Test
    fun `unified editor exposes mic icons on title description and note body and saves cleanly`() {
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("fab_new_note")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasTestTag("editor_title_voice_btn")).assertExists()
        composeRule.onNode(hasTestTag("editor_description_voice_btn")).assertExists()
        composeRule.onNode(hasTestTag("editor_voice_btn")).assertExists()

        composeRule.onNode(hasTestTag("editor_title_voice_btn")).performClick()
        composeRule.onNode(hasTestTag("editor_description_voice_btn")).performClick()
        composeRule.onNode(hasTestTag("editor_voice_btn")).performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasTestTag("editor_title_input"))
            .performTextInput("Light Mode Checklist")
        composeRule.onNode(hasTestTag("editor_description_input"))
            .performTextInput("Quick summary description")
        composeRule.onNode(hasTestTag("editor_content_input"))
            .performTextInput("Inline voice dictation beside all three fields")

        composeRule.onNode(hasTestTag("editor_save_btn")).performClick()
        composeRule.waitUntil(timeoutMillis = 5000L) {
            Thread.sleep(20)
            composeRule.onAllNodes(hasText("Saved & encrypted")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasTestTag("editor_back_btn")).performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5000L) {
            Thread.sleep(20)
            composeRule.onAllNodes(hasTestTag("dashboard_notes_list")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5000L) {
            Thread.sleep(20)
            runCatching {
                composeRule.onNode(hasTestTag("dashboard_notes_list"))
                    .performScrollToNode(hasText("Light Mode Checklist"))
            }.isSuccess
        }

        composeRule.onNode(hasText("Light Mode Checklist")).assertExists()
        composeRule.onNode(hasText("Quick summary description")).assertExists()
    }
}
