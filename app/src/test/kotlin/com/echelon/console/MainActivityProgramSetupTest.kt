package com.echelon.console

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.domain.WorkoutSessionState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MainActivityProgramSetupTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `fat burn customized workout starts live preview`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("FAT BURN").performClick()

        waitForText("MAKE IT YOURS")
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().performClick()

        waitForText("PROJECTED TRAJECTORY")
        composeTestRule.onNodeWithContentDescription("Increase duration").performScrollTo().performClick()
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("PREVIEW ONLY")
        composeTestRule.onNodeWithText("WORKOUT READY").assertDoesNotExist()
        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.0 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME REMAINING").assertIsDisplayed()
        composeTestRule.onNodeWithText("35:00").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )
    }

    @Test
    fun `fat burn default workout controls live session and returns to library`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("FAT BURN").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("PREVIEW ONLY")
        composeTestRule.onNodeWithText("WORKOUT READY").assertDoesNotExist()
        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.0 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME REMAINING").assertIsDisplayed()
        composeTestRule.onNodeWithText("30:00").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )

        composeTestRule.onNodeWithText("PAUSE").performClick()
        waitForText("RESUME")
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Paused,
        )

        composeTestRule.onNodeWithText("RESUME").performClick()
        waitForText("PAUSE")
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )

        composeTestRule.onNodeWithText("END WORKOUT").performClick()
        waitForText("WORKOUT STOPPED")
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Stopped,
        )

        composeTestRule.onNodeWithText("DONE").performClick()
        waitForText("WHAT DO YOU WANT TODAY?")
    }

    @Test
    fun `glute blast default workout starts live preview`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("GLUTE BLAST").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("PREVIEW ONLY")
        composeTestRule.onNodeWithText("WORKOUT READY").assertDoesNotExist()
        composeTestRule.onNodeWithText("GLUTE BLAST").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.7 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME REMAINING").assertIsDisplayed()
        composeTestRule.onNodeWithText("30:00").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
