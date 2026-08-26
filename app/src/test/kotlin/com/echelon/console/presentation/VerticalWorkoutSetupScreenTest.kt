package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.MainActivity
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraftRequest
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VerticalWorkoutSetupScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `vertical configuration exposes four targets and safe preview disclosures`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(
            ProgramSetupUiState.VerticalConfiguring(
                detail = detail(),
                target = VerticalTarget.ONE_THOUSAND_FEET,
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
            actions::add,
        )

        composeTestRule.onNodeWithText("REPRESENTATIVE 50-MIN SESSION").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROPOSED LIMIT 60 MIN · NOT SESSION DURATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ELEVATION SOURCE UNAVAILABLE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PROGRESS NOT CALCULATED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVIEW ONLY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NO DEVICE COMMANDS").performScrollTo().assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("NO ELEVATION FORMULA").fetchSemanticsNodes().isNotEmpty())
        VerticalTarget.values().forEach { target ->
            composeTestRule.onNodeWithContentDescription(target.description()).performScrollTo().assertIsDisplayed()
        }

        composeTestRule.onNodeWithContentDescription("Select vertical target 5,280 feet")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("GENERATE VERTICAL PREVIEW")
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.SetVerticalTarget(VerticalTarget.VERTICAL_MILE),
                ProgramSetupAction.GenerateVerticalPreview,
            ),
            actions,
        )
        assertTrue(
            composeTestRule.onNodeWithContentDescription("GENERATE VERTICAL PREVIEW")
                .fetchSemanticsNode()
                .boundsInRoot
                .height >= 48f,
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `vertical draft preview is scrollable and exposes exact six blocks before accept`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(
            ProgramSetupUiState.VerticalDraftPreview(
                detail = detail(),
                draft = draft(),
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
            actions::add,
        )

        composeTestRule.onNodeWithText("VERTICAL DRAFT PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET 1,000 FT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PROPOSED LIMIT 60 MIN · NOT SESSION DURATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PROFILE · 6 SEGMENTS").performScrollTo().assertIsDisplayed()
        listOf("WARM UP", "BASE CLIMB", "BUILD", "STEEP BLOCK", "FINISH PUSH", "COOL DOWN").forEach { name ->
            assertTrue(composeTestRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty())
        }
        assertTrue(composeTestRule.onAllNodesWithText("NO ELEVATION FORMULA").fetchSemanticsNodes().isNotEmpty())
        composeTestRule.onNodeWithContentDescription("BACK TO VERTICAL SETTINGS").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("ACCEPT VERTICAL PLAN").performScrollTo().performClick()

        assertEquals(
            listOf(ProgramSetupAction.Back, ProgramSetupAction.AcceptVerticalPlan),
            actions,
        )
        assertTrue(
            composeTestRule.onNodeWithContentDescription("ACCEPT VERTICAL PLAN")
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
        StaticProgramCatalog().findProgramDetail(ProgramId("VERTICAL")),
    )

    private fun draft() = when (
        val result = GenerateVerticalWorkoutDraft()(
            GenerateVerticalWorkoutDraftRequest(
                target = VerticalTarget.ONE_THOUSAND_FEET,
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is VerticalWorkoutGenerationResult.Generated -> result.draft
        is VerticalWorkoutGenerationResult.Rejected -> error("test draft rejected: ${result.failure}")
    }

    private fun VerticalTarget.description(): String = when (this) {
        VerticalTarget.FIVE_HUNDRED_FEET -> "Select vertical target 500 feet"
        VerticalTarget.ONE_THOUSAND_FEET -> "Select vertical target 1,000 feet"
        VerticalTarget.TWO_THOUSAND_FEET -> "Select vertical target 2,000 feet"
        VerticalTarget.VERTICAL_MILE -> "Select vertical target 5,280 feet"
    }
}
