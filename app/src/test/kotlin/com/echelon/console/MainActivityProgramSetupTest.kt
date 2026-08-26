package com.echelon.console

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityProgramSetupTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `fat burn flows from library to personalization to ready`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("FAT BURN").performClick()

        waitForText("MAKE IT YOURS")
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().performClick()

        waitForText("PROJECTED TRAJECTORY")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("WORKOUT READY")
        composeTestRule.onNodeWithText("30 MIN").assertIsDisplayed()
    }

    @Test
    fun `fat burn detail can start default workout directly`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("FAT BURN").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("WORKOUT READY")
        composeTestRule.onNodeWithText("5.5 MPH").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `glute blast flows from library to workout ready`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("GLUTE BLAST").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("WORKOUT READY")
        composeTestRule.onNodeWithText("GLUTE_BLAST").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("4.0 MPH").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `started workout back returns to program library`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("FAT BURN").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("WORKOUT READY")
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("ALL PROGRAMS").performScrollTo().assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
