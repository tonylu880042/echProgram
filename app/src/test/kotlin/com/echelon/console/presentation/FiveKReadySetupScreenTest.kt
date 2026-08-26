package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.echelon.console.MainActivity
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraftRequest
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FiveKReadySetupScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `configuring screen requires user pace exposes duration actions and touch targets`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(
            ProgramSetupUiState.FiveKReadyConfiguring(
                detail = detail(),
                duration = DurationMinutes(30),
                baselinePaceText = "",
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(150),
            ),
            actions::add,
        )

        composeTestRule.onNodeWithText("SET YOUR RUN PACE").assertIsDisplayed()
        composeTestRule.onNodeWithText("The console will not infer 4.0 MPH", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("RUN PACE (MPH)").performTextInput("4.0")
        composeTestRule.onNodeWithContentDescription("Select 5K duration 40 minutes")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("GENERATE 5K PREVIEW")
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.SetFiveKReadyBaselinePace("4.0"),
                ProgramSetupAction.SetFiveKReadyDuration(DurationMinutes(40)),
                ProgramSetupAction.GenerateFiveKReadyPreview,
            ),
            actions,
        )
        val button = composeTestRule
            .onNodeWithContentDescription("GENERATE 5K PREVIEW")
            .fetchSemanticsNode()
        assertTrue(button.boundsInRoot.height >= 48f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `preview screen is scrollable and discloses typed profile before accept`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(
            ProgramSetupUiState.FiveKReadyDraftPreview(
                detail = detail(),
                draft = draft(),
                baselinePaceText = "4.0",
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(150),
            ),
            actions::add,
        )

        composeTestRule.onNodeWithText("5K READY PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("SINGLE SESSION").performScrollTo().assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("PREVIEW ONLY").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("NO DEVICE COMMANDS").fetchSemanticsNodes().isNotEmpty())
        composeTestRule.onNodeWithText("USER-ENTERED", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("RUN 1 OF 3").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("DOES NOT GUARANTEE 5K COMPLETION", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ACCEPT 5K PREVIEW")
            .performScrollTo()
            .performClick()

        assertEquals(listOf(ProgramSetupAction.AcceptFiveKReadyPlan), actions)
        assertTrue(
            composeTestRule.onNodeWithContentDescription("ACCEPT 5K PREVIEW")
                .fetchSemanticsNode()
                .boundsInRoot
                .height >= 48f,
        )
    }

    private fun setContent(
        state: ProgramSetupUiState,
        onAction: (ProgramSetupAction) -> Unit,
    ) {
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state,
                onAction = onAction,
                onNavigate = {},
                onShowLibrary = {},
            )
        }
    }

    private fun detail() = requireNotNull(
        StaticProgramCatalog().findProgramDetail(ProgramId("5K_READY")),
    )

    private fun draft() = when (
        val result = GenerateFiveKReadySessionDraft()(
            GenerateFiveKReadySessionDraftRequest(
                durationMinutes = 30,
                baselinePace = FiveKReadyBaselinePace(
                    speed = SpeedTenths(40),
                    source = FiveKReadyBaselineSource.USER_ENTERED,
                ),
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is FiveKReadySessionGenerationResult.Generated -> result.draft
        is FiveKReadySessionGenerationResult.Rejected -> error("test draft rejected: ${result.failure}")
    }
}
