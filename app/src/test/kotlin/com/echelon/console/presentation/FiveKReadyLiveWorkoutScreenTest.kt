package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.echelon.console.MainActivity
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutTimelineAnnotation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FiveKReadyLiveWorkoutScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `five k active screen shows typed coaching and planned run walk summary`() {
        val actions = mutableListOf<LiveWorkoutAction>()
        setContent(
            state = LiveWorkoutUiState.Active(
                workout = LiveWorkoutReadModel(
                    programId = ProgramId("5K_READY"),
                    elapsedSeconds = 0,
                    remainingSeconds = 1_800,
                    currentSegment = LiveWorkoutSegment(
                        index = 0,
                        name = "WARM UP WALK",
                        annotation = WorkoutTimelineAnnotation.WarmUpWalk,
                        displayLabel = "WARM UP WALK",
                    ),
                    nextSegment = LiveWorkoutSegment(
                        index = 1,
                        name = "EASY RUN",
                        annotation = WorkoutTimelineAnnotation.Run(1, 3),
                        displayLabel = "RUN 1 OF 3",
                    ),
                    secondsUntilNextSegment = 300,
                    targetSpeed = SpeedTenths(30),
                    targetIncline = InclineTenths(10),
                    isPaused = false,
                    programTitle = "5K READY",
                    previewMode = ProgramPreviewMode.BASELINE_PREVIEW,
                    runWalkSummary = LiveWorkoutRunWalkSummary(15, 15),
                ),
            ),
            onAction = actions::add,
        )

        composeTestRule.onNodeWithText("WARM UP WALK").assertIsDisplayed()
        composeTestRule.onNodeWithText("RUN 1 OF 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("COUNTDOWN").assertIsDisplayed()
        composeTestRule.onNodeWithText("05:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("PLANNED RUN").assertIsDisplayed()
        composeTestRule.onNodeWithText("PLANNED WALK").assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVIEW ONLY").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET INCLINE").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSE").performClick()
        composeTestRule.onNodeWithText("END WORKOUT").performClick()

        assertEquals(
            listOf(LiveWorkoutAction.PauseResume, LiveWorkoutAction.End),
            actions,
        )
    }

    private fun setContent(
        state: LiveWorkoutUiState,
        onAction: (LiveWorkoutAction) -> Unit,
    ) {
        composeTestRule.activity.setContent {
            LiveWorkoutScreen(
                state = state,
                equipmentState = EquipmentReadState(),
                onAction = onAction,
                onBackToPrograms = {},
                onNavigate = {},
            )
        }
    }
}
