package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.MainActivity
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgramSetupScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `loading and terminal states are visible and terminal back emits action`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(ProgramSetupUiState.Loading(ProgramId("FAT_BURN")), actions::add)
        composeTestRule.onNodeWithText("LOADING PROGRAM DETAIL").assertIsDisplayed()

        setContent(ProgramSetupUiState.Unavailable(ProgramId("MISSING")), actions::add)
        composeTestRule.onNodeWithText("PROGRAM UNAVAILABLE").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        setContent(ProgramSetupUiState.DeviceUnavailable, actions::add)
        composeTestRule.onNodeWithText("DEVICE UNAVAILABLE").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        setContent(ProgramSetupUiState.Error("Unable to load program detail"), actions::add)
        composeTestRule.onNodeWithText("Unable to load program detail").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf(ProgramSetupAction.Back, ProgramSetupAction.Back, ProgramSetupAction.Back),
            actions,
        )
    }

    @Test
    fun `ready routes detail actions and personalizing routes personalization actions`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(ProgramSetupUiState.Ready(detail()), actions::add)

        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().performClick()
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.MakeItYours,
                ProgramSetupAction.StartDefault,
                ProgramSetupAction.Back,
            ),
            actions,
        )

        setContent(
            ProgramSetupUiState.Personalizing(detail(), detail().defaultSettings),
            actions::add,
        )
        composeTestRule.onNodeWithText("MAKE IT YOURS").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("START WORKOUT").performScrollTo().performClick()
        assertEquals(ProgramSetupAction.StartCustomized, actions.last())
    }

    @Test
    fun `started state shows exact selected settings without pretending to be live workout`() {
        val capabilities = DeviceCapabilities(
            duration = DurationLimits(DurationMinutes(10), DurationMinutes(60), DurationMinutes(5)),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )
        val plan = (ValidatedWorkoutPlan.create(
            plan = com.echelon.console.domain.WorkoutPlan(ProgramId("FAT_BURN"), detail().defaultSettings),
            capabilities = capabilities,
        ) as ValidatedWorkoutPlanResult.Valid).plan

        setContent(ProgramSetupUiState.Started(plan), onAction = {})

        composeTestRule.onNodeWithText("WORKOUT READY").assertIsDisplayed()
        composeTestRule.onNodeWithText("45 MIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("5.5 MPH").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("12.0%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `library state exposes root library callback`() {
        var showLibraryCount = 0
        setContent(
            state = ProgramSetupUiState.Library,
            onAction = {},
            onShowLibrary = { showLibraryCount++ },
        )

        composeTestRule.onNodeWithText("PROGRAM LIBRARY").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("BACK TO LIBRARY").performClick()

        assertEquals(1, showLibraryCount)
    }

    private fun setContent(
        state: ProgramSetupUiState,
        onAction: (ProgramSetupAction) -> Unit,
        onShowLibrary: () -> Unit = {},
    ) {
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state,
                onAction = onAction,
                onNavigate = {},
                onShowLibrary = onShowLibrary,
            )
        }
    }

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
        ),
    )
}
