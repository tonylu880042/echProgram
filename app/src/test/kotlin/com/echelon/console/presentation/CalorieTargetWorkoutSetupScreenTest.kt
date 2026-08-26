package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.MainActivity
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class CalorieTargetWorkoutSetupScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `calorie setup renders exact target choices and safety disclosures`() {
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state(),
                onAction = {},
                onNavigate = {},
                onShowLibrary = {},
            )
        }

        composeTestRule.onNodeWithText("CONFIGURE CALORIE TARGET").assertIsDisplayed()
        listOf("100 CAL EST", "200 CAL EST", "300 CAL EST", "500 CAL EST").forEach { label ->
            composeTestRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        listOf(
            "CALORIES ARE ESTIMATES",
            "40 MIN REPRESENTATIVE PROFILE",
            "FITOS SNAPSHOT CALORIES",
            "UNIT SEMANTICS UNCONFIRMED",
            "SESSION RESET UNCONFIRMED",
            "COMPLETION AUTHORITY NOT APPROVED",
            "DISPLAY ONLY",
            "NO TARGET PROGRESS",
            "PREVIEW ONLY",
            "NO DEVICE COMMANDS",
        ).forEach { label ->
            composeTestRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        listOf("TO GO", "REMAINING", "PERCENT", "REACHED").forEach { forbidden ->
            composeTestRule.onNodeWithText(forbidden).assertDoesNotExist()
        }
    }

    @Test
    fun `calorie target controls emit selections and start action with selected semantics`() {
        val actions = mutableListOf<ProgramSetupAction>()
        var renderedState by mutableStateOf(state())
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = renderedState,
                onAction = { action ->
                    actions += action
                    if (action is ProgramSetupAction.SelectCalorieTarget) {
                        renderedState = renderedState.copy(
                            selectedTarget = target(action.target.estimatedKcal),
                            errorMessage = null,
                        )
                    }
                },
                onNavigate = {},
                onShowLibrary = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Select calorie target 300 calories")
            .assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Select calorie target 100 calories")
            .assertIsNotSelected()
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("Select calorie target 100 calories")
            .assertIsSelected()
        composeTestRule.onNodeWithContentDescription("START CALORIE TARGET PREVIEW")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.SelectCalorieTarget(CalorieTargetOption.ONE_HUNDRED_KCAL),
                ProgramSetupAction.StartCalorieTargetPreview,
                ProgramSetupAction.Back,
            ),
            actions,
        )
    }

    @Test
    fun `selected target shows its proposal time and explicit non-session policy`() {
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state(selectedTarget = target(500)),
                onAction = {},
                onNavigate = {},
                onShowLibrary = {},
            )
        }

        composeTestRule.onNodeWithText("500 CAL TARGET").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PROPOSED MAX TIME: 90 MIN").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NOT SESSION DURATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NOT CLIENT APPROVED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("60 MIN").assertDoesNotExist()
    }

    @Test
    fun `one hundred two hundred and three hundred targets show sixty minute proposal`() {
        listOf(100, 200, 300).forEach { estimatedKcal ->
            composeTestRule.activity.setContent {
                ProgramSetupScreen(
                    state = state(selectedTarget = target(estimatedKcal)),
                    onAction = {},
                    onNavigate = {},
                    onShowLibrary = {},
                )
            }

            composeTestRule.onNodeWithText("PROPOSED MAX TIME: 60 MIN")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("NOT SESSION DURATION").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("NOT CLIENT APPROVED").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `missing selection displays error and CTA remains actionable at supported landscape size`() {
        val actions = mutableListOf<ProgramSetupAction>()
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state(selectedTarget = null, errorMessage = "SELECT A CALORIE TARGET BEFORE STARTING"),
                onAction = actions::add,
                onNavigate = {},
                onShowLibrary = {},
            )
        }

        composeTestRule.onNodeWithText("SELECT A CALORIE TARGET BEFORE STARTING")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("START CALORIE TARGET PREVIEW")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf(ProgramSetupAction.StartCalorieTargetPreview), actions)
    }

    private fun state(
        selectedTarget: CalorieTargetSelection? = target(300),
        errorMessage: String? = null,
    ): ProgramSetupUiState.CalorieTargetConfiguring =
        ProgramSetupUiState.CalorieTargetConfiguring(
            detail = requireNotNull(
                StaticProgramCatalog().findProgramDetail(ProgramId("CALORIE_TARGET")),
            ),
            representativeProfileDuration = DurationMinutes(40),
            selectedTarget = selectedTarget,
            userMaxSpeed = SpeedTenths(60),
            machineMaxSpeed = SpeedTenths(120),
            userMaxIncline = InclineTenths(100),
            machineMaxIncline = InclineTenths(150),
            errorMessage = errorMessage,
        )

    private fun target(estimatedKcal: Int): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target")
    }
}
