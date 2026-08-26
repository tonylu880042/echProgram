package com.echelon.console

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.SurpriseWorkoutGenerator
import com.echelon.console.domain.SurpriseWorkoutGeneratorInput
import com.echelon.console.domain.WorkoutSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        composeTestRule.onNodeWithText("FAT BURN").performClick()
        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("PREVIEW ONLY")
        composeTestRule.onNodeWithText("WORKOUT STOPPED").assertDoesNotExist()
        composeTestRule.onNodeWithText("PAUSE").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )
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

    @Test
    fun `surprise me default start opens a draft preview before live workout`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("SURPRISE ME").performClick()

        waitForText("START WORKOUT")
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        waitForText("DRAFT PREVIEW")
        assertNull(composeTestRule.activity.workoutSessionCoordinator.currentState())
    }

    @Test
    fun `surprise customized draft regenerates and accepts exact profile into live`() {
        waitForText("WHAT DO YOU WANT TODAY?")
        composeTestRule.onNodeWithText("SURPRISE ME").performClick()

        waitForText("MAKE IT YOURS")
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().performClick()
        waitForText("CONFIGURE SURPRISE ME")

        composeTestRule.onNodeWithContentDescription("Select duration 45 minutes").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Select effort HARD").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("GENERATE PREVIEW").performScrollTo().performClick()

        waitForText("DRAFT PREVIEW")
        composeTestRule.onNodeWithText("PREVIEW ONLY").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROFILE REVISION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("BASELINE RAMP PROPOSAL").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("REGENERATE").performScrollTo().performClick()
        composeTestRule.onNodeWithText("1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ACCEPT PLAN").performScrollTo().performClick()

        waitForText("LIVE PREVIEW")
        composeTestRule.onNodeWithText("SURPRISE ME").assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVIEW ONLY").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.8 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME REMAINING").assertIsDisplayed()
        composeTestRule.onNodeWithText("45:00").assertIsDisplayed()
        assertTrue(
            composeTestRule.activity.workoutSessionCoordinator.currentState()
                is WorkoutSessionState.Running,
        )

        val expectedDraft = when (
            val result = SurpriseWorkoutGenerator().generate(
                SurpriseWorkoutGeneratorInput(
                    durationMinutes = 45,
                    effort = SurpriseWorkoutEffort.HARD,
                    userProfileRevision = "anonymous-baseline-r1",
                    regenerationIndex = 1,
                    generatorVersion = "v1",
                    userMaxSpeed = SpeedTenths(80),
                    machineMaxSpeed = SpeedTenths(120),
                    userMaxIncline = InclineTenths(100),
                    machineMaxIncline = InclineTenths(150),
                ),
            )
        ) {
            is SurpriseWorkoutGenerationResult.Generated -> result.draft
            is SurpriseWorkoutGenerationResult.Rejected -> error("Expected generated draft")
        }
        val running = composeTestRule.activity.workoutSessionCoordinator.currentState() as WorkoutSessionState.Running
        assertEquals(
            expectedDraft.profile.map { it.name },
            running.timeline.segments.map { it.name },
        )
        assertEquals(
            expectedDraft.profile.map { it.speed.value },
            running.timeline.segments.map { it.targetSpeed.value },
        )
        assertEquals(
            expectedDraft.profile.map { it.incline.value },
            running.timeline.segments.map { it.targetIncline.value },
        )

        composeTestRule.onNodeWithText("END WORKOUT").performClick()
        waitForText("WORKOUT STOPPED")
        composeTestRule.onNodeWithText("DONE").performClick()
        waitForText("WHAT DO YOU WANT TODAY?")
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
