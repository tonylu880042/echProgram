package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.echelon.console.MainActivity
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
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
class ProgramPersonalizationScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `screen exposes goal current values preview and selected configuration`() {
        setContent()

        composeTestRule.onNodeWithText("MAKE IT YOURS").assertIsDisplayed()
        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Sustained calorie-burning work without requiring hard running.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("DURATION (MIN)").assertIsDisplayed()
        composeTestRule.onNodeWithText("45").assertIsDisplayed()
        composeTestRule.onNodeWithText("MAX SPEED (MPH)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("5.5").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("MAX INCLINE (%)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("12.0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PROJECTED TRAJECTORY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SELECTED CONFIGURATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Select focus BALANCED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Adapt to You").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `controls emit typed actions and every primary control keeps a touch target`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(onAction = actions::add)

        composeTestRule.onNodeWithContentDescription("Increase duration").performClick()
        composeTestRule.onNodeWithContentDescription("Decrease duration").performClick()
        composeTestRule.onNodeWithContentDescription("Select intensity HIGH").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Increase max speed").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Decrease max incline").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Select focus MORE SPEED").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Adapt to You").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("START WORKOUT").performScrollTo().performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.SetDuration(DurationMinutes(50)),
                ProgramSetupAction.SetDuration(DurationMinutes(40)),
                ProgramSetupAction.SetIntensity(PlanIntensity.HIGH),
                ProgramSetupAction.SetMaxSpeed(SpeedTenths(60)),
                ProgramSetupAction.SetMaxIncline(InclineTenths(110)),
                ProgramSetupAction.SetFocus(PlanFocus.MORE_SPEED),
                ProgramSetupAction.SetAdaptToYou(true),
                ProgramSetupAction.StartCustomized,
            ),
            actions,
        )

        listOf(
            "Increase duration",
            "Select intensity HIGH",
            "Adapt to You",
            "START WORKOUT",
        ).forEach { description ->
            val node = composeTestRule
                .onNodeWithContentDescription(description)
                .performScrollTo()
                .fetchSemanticsNode()
            assertTrue("$description is smaller than 48dp", node.boundsInRoot.height >= 48f)
        }
    }

    @Test
    fun `field errors render beside matching controls`() {
        setContent(
            state = personalizingState().copy(
                fieldErrors = listOf(
                    PlanValidationError.DurationStepMismatch(
                        value = DurationMinutes(47),
                        limits = DurationLimits(DurationMinutes(10), DurationMinutes(60), DurationMinutes(5)),
                    ),
                    PlanValidationError.MaxSpeedOutOfRange(
                        value = SpeedTenths(121),
                        limits = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
                    ),
                    PlanValidationError.MaxInclineOutOfRange(
                        value = InclineTenths(160),
                        limits = InclineRange(InclineTenths(0), InclineTenths(150)),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Duration must use 5 min steps.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Speed must be between 2.0 and 12.0 MPH.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Incline must be between 0.0 and 15.0%.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Unable to start workout").assertDoesNotExist()
    }

    @Test
    fun `back callback is exposed and compact landscape can reach start`() {
        var backCount = 0
        setContent(onBack = { backCount++ })

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithContentDescription("START WORKOUT").performScrollTo().assertIsDisplayed()

        assertEquals(1, backCount)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `wide landscape keeps main personalization controls reachable`() {
        setContent()

        composeTestRule.onNodeWithText("MAKE IT YOURS").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROJECTED TRAJECTORY").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("START WORKOUT").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `projected trajectory renders meaningful vertical variation`() {
        setContent()

        val image = composeTestRule
            .onNodeWithContentDescription("Projected trajectory profile")
            .performScrollTo()
            .captureToImage()
        val pixelMap = image.toPixelMap()
        val cyanRows = (0 until pixelMap.height).filter { y ->
            (0 until pixelMap.width).any { x ->
                val color = pixelMap[x, y]
                color.isTrajectoryCyan()
            }
        }

        assertTrue("projected trajectory did not render cyan pixels", cyanRows.isNotEmpty())
        assertTrue(
            "projected trajectory vertical span was ${cyanRows.maxOrNull()!! - cyanRows.minOrNull()!!}px",
            cyanRows.maxOrNull()!! - cyanRows.minOrNull()!! > 20,
        )
    }

    @Test
    @Config(qualifiers = "w720dp-h400dp-land")
    fun `compact landscape scrolls to action`() {
        setContent()

        composeTestRule.onNodeWithContentDescription("START WORKOUT").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        state: ProgramSetupUiState.Personalizing = personalizingState(),
        onAction: (ProgramSetupAction) -> Unit = {},
        onBack: () -> Unit = {},
        onNavigate: (ProgramLibraryDestination) -> Unit = {},
    ) {
        composeTestRule.activity.setContent {
            ProgramPersonalizationScreen(
                state = state,
                onAction = onAction,
                onBack = onBack,
                onNavigate = onNavigate,
            )
        }
    }

    private fun personalizingState(): ProgramSetupUiState.Personalizing =
        ProgramSetupUiState.Personalizing(
            detail = detail(),
            settings = detail().defaultSettings,
        )

    private fun detail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("FAT_BURN"),
        title = "FAT BURN",
        promise = "Sustained calorie-burning work without requiring hard running.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(45),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(120),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(28), SpeedTenths(55)),
        inclineRange = InclineRange(InclineTenths(10), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
            ProgramSegmentSummary("Steady Burn", DurationMinutes(10), SpeedTenths(40), InclineTenths(20)),
            ProgramSegmentSummary("Climb", DurationMinutes(10), SpeedTenths(36), InclineTenths(45)),
            ProgramSegmentSummary("Push", DurationMinutes(10), SpeedTenths(55), InclineTenths(30)),
            ProgramSegmentSummary("Recovery", DurationMinutes(5), SpeedTenths(30), InclineTenths(10)),
            ProgramSegmentSummary("Cool Down", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
        ),
    )

    private fun Color.isTrajectoryCyan(): Boolean =
        alpha > 0.7f && red < 0.35f && green > 0.45f && blue > 0.8f
}
