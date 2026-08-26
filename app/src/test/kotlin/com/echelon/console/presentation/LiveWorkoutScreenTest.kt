package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.echelon.console.MainActivity
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.WorkoutSessionStarterResult
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.WorkoutPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveWorkoutScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `active screen exposes preview timing profile targets and equipment status`() {
        setContent(
            state = activeState(),
            equipmentState = EquipmentReadState(connection = EquipmentConnection.Connecting),
        )

        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVIEW ONLY").assertIsDisplayed()
        composeTestRule.onNodeWithText("TIME REMAINING").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:00:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("ELAPSED").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:59").assertIsDisplayed()
        composeTestRule.onNodeWithText("CURRENT SEGMENT").assertIsDisplayed()
        composeTestRule.onNodeWithText("BUILD").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEXT SEGMENT").assertIsDisplayed()
        composeTestRule.onNodeWithText("PUSH").assertIsDisplayed()
        composeTestRule.onNodeWithText("COUNTDOWN").assertIsDisplayed()
        composeTestRule.onNodeWithText("01:00").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET SPEED").assertIsDisplayed()
        composeTestRule.onNodeWithText("5.5 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("TARGET INCLINE").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("CONNECTING TO FITOS").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSE").assertIsDisplayed()
        composeTestRule.onNodeWithText("END WORKOUT").assertIsDisplayed()
    }

    @Test
    fun `active fixed preview explains that displayed targets are manual`() {
        setContent(state = activeState())

        composeTestRule
            .onNodeWithText("Follow the displayed targets manually; FitOS control is not enabled.")
            .assertIsDisplayed()
    }

    @Test
    fun `active preview banner uses the matching mode disclosure`() {
        val modes = ProgramPreviewMode.values()

        modes.forEach { mode ->
            setContent(
                state = activeState().copy(
                    workout = activeState().workout.copy(previewMode = mode),
                ),
            )

            composeTestRule.onNodeWithText(mode.disclosureMessage()).assertIsDisplayed()
        }
    }

    @Test
    fun `paused state says paused and exposes resume action`() {
        val actions = mutableListOf<LiveWorkoutAction>()
        setContent(
            state = activeState().copy(
                workout = activeState().workout.copy(isPaused = true),
            ),
            onAction = actions::add,
        )

        composeTestRule.onNodeWithText("PAUSED").assertIsDisplayed()
        composeTestRule.onNodeWithText("RESUME").performClick()

        assertEquals(listOf(LiveWorkoutAction.PauseResume), actions)
    }

    @Test
    fun `end action is available and forwards callback`() {
        val actions = mutableListOf<LiveWorkoutAction>()
        setContent(state = activeState(), onAction = actions::add)

        composeTestRule.onNodeWithText("END WORKOUT").performClick()

        assertEquals(listOf(LiveWorkoutAction.End), actions)
    }

    @Test
    fun `active state hides back while safe states retain a return action`() {
        setContent(state = activeState())
        composeTestRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)

        setContent(state = LiveWorkoutUiState.NoSession)
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        setContent(state = LiveWorkoutUiState.Error("Workout controls are unavailable right now."))
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        setContent(
            state = LiveWorkoutUiState.Completed(
                summary = LiveWorkoutSummary(
                    ProgramId("FAT_BURN"),
                    3_600,
                    3_600,
                    "FAT BURN",
                    ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
        )
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        setContent(
            state = LiveWorkoutUiState.Stopped(
                summary = LiveWorkoutSummary(
                    ProgramId("FAT_BURN"),
                    59,
                    3_600,
                    "FAT BURN",
                    ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
        )
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun `active navigation rail does not emit a destination callback`() {
        val destinations = mutableListOf<ProgramLibraryDestination>()
        setContent(state = activeState(), onNavigate = destinations::add)

        composeTestRule.onNodeWithContentDescription("Dashboard").performClick()

        assertEquals(emptyList<ProgramLibraryDestination>(), destinations)
    }

    @Test
    fun `no session offers a clear return path`() {
        var backCount = 0
        setContent(state = LiveWorkoutUiState.NoSession, onBackToPrograms = { backCount++ })

        composeTestRule.onNodeWithText("NO ACTIVE WORKOUT").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK TO PROGRAMS").performClick()

        assertEquals(1, backCount)
    }

    @Test
    fun `error state uses safe copy and return path`() {
        var backCount = 0
        setContent(
            state = LiveWorkoutUiState.Error("Workout controls are unavailable right now."),
            onBackToPrograms = { backCount++ },
        )

        composeTestRule.onNodeWithText("WORKOUT UNAVAILABLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout controls are unavailable right now.").assertIsDisplayed()
        composeTestRule.onNodeWithText("BACK TO PROGRAMS").performClick()

        assertEquals(1, backCount)
    }

    @Test
    fun `completed and stopped states offer done callback`() {
        var doneCount = 0
        setContent(
            state = LiveWorkoutUiState.Completed(
                summary = LiveWorkoutSummary(
                    ProgramId("FAT_BURN"),
                    3_600,
                    3_600,
                    "FAT BURN",
                    ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
            onBackToPrograms = { doneCount++ },
        )

        composeTestRule.onNodeWithText("WORKOUT COMPLETE").assertIsDisplayed()
        composeTestRule.onNodeWithText("DONE").performClick()
        assertEquals(1, doneCount)

        setContent(
            state = LiveWorkoutUiState.Stopped(
                summary = LiveWorkoutSummary(
                    ProgramId("FAT_BURN"),
                    59,
                    3_600,
                    "FAT BURN",
                    ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
            onBackToPrograms = { doneCount++ },
        )
        composeTestRule.onNodeWithText("WORKOUT STOPPED").assertIsDisplayed()
        composeTestRule.onNodeWithText("DONE").performClick()
        assertEquals(2, doneCount)
    }

    @Test
    fun `non fixed completion is presented as preview with its mode caveat`() {
        val mode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW
        setContent(
            state = LiveWorkoutUiState.Completed(
                summary = LiveWorkoutSummary(
                    programId = ProgramId("VERTICAL"),
                    elapsedSeconds = 3_000,
                    totalDurationSeconds = 3_000,
                    programTitle = "VERTICAL",
                    previewMode = mode,
                ),
            ),
        )

        composeTestRule.onNodeWithText("PREVIEW COMPLETE").assertIsDisplayed()
        composeTestRule.onNodeWithText("WORKOUT COMPLETE").assertDoesNotExist()
        composeTestRule.onNodeWithText(mode.disclosureMessage()).assertIsDisplayed()
    }

    @Test
    fun `vertical active and terminal states show source aware context without fake progress`() {
        val context = LiveVerticalWorkoutContext(
            target = VerticalTarget.VERTICAL_MILE,
            proposedTimeLimit = VerticalTimeLimitProposal(240, VerticalTimeLimitStatus.PROPOSED),
            elevationSource = VerticalElevationSource.UNAVAILABLE,
            progressStatus = VerticalProgressStatus.NOT_CALCULATED,
            controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
        )
        val verticalActive = activeState().copy(
            workout = activeState().workout.copy(
                programId = ProgramId("VERTICAL"),
                programTitle = "VERTICAL",
                previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                verticalContext = context,
            ),
        )
        setContent(verticalActive)
        assertVerticalContextIsDisplayed()
        composeTestRule.onNodeWithText("0 FT").assertDoesNotExist()

        setContent(
            LiveWorkoutUiState.Completed(
                summary = LiveWorkoutSummary(
                    programId = ProgramId("VERTICAL"),
                    elapsedSeconds = 3_000,
                    totalDurationSeconds = 3_000,
                    programTitle = "VERTICAL",
                    previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                    verticalContext = context,
                ),
            ),
        )
        assertVerticalContextIsDisplayed()
        composeTestRule.onNodeWithText("0 FT").assertDoesNotExist()

        setContent(
            LiveWorkoutUiState.Stopped(
                summary = LiveWorkoutSummary(
                    programId = ProgramId("VERTICAL"),
                    elapsedSeconds = 59,
                    totalDurationSeconds = 3_000,
                    programTitle = "VERTICAL",
                    previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                    verticalContext = context,
                ),
            ),
        )
        assertVerticalContextIsDisplayed()
        composeTestRule.onNodeWithText("0 FT").assertDoesNotExist()
    }

    @Test
    fun `terminal state renders the title supplied by the read model`() {
        setContent(
            state = LiveWorkoutUiState.Completed(
                summary = LiveWorkoutSummary(
                    programId = ProgramId("TWELVE_3_30"),
                    elapsedSeconds = 1_800,
                    totalDurationSeconds = 1_800,
                    programTitle = "12-3-30",
                    previewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
        )

        composeTestRule.onNodeWithText("12-3-30").assertIsDisplayed()
    }

    @Test
    fun `route collects view model state and forwards pause action`() {
        val coordinator = startedCoordinator()
        val viewModel = LiveWorkoutViewModel(
            controller = coordinator,
            tickSource = WorkoutSessionTickSource { emptyFlow() },
            dispatcher = Dispatchers.Unconfined,
            getProgramDetail = GetProgramDetail(StaticProgramCatalog()),
        )
        composeTestRule.activity.setContent {
            LiveWorkoutRoute(
                viewModel = viewModel,
                onNavigate = {},
                onBackToPrograms = {},
                equipmentState = EquipmentReadState(connection = EquipmentConnection.Disconnected),
            )
        }

        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule.onNodeWithText("PAUSE").performClick()

        composeTestRule.onNodeWithText("RESUME").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `active end action is displayed without scrolling at supported landscape height`() {
        setContent(state = activeState())

        composeTestRule.onNodeWithText("END WORKOUT").assertIsDisplayed()
    }

    @Test
    fun `workout clock formatting is stable at minute and hour boundaries`() {
        assertEquals("00:00", formatWorkoutClock(0))
        assertEquals("00:59", formatWorkoutClock(59))
        assertEquals("01:00", formatWorkoutClock(60))
        assertEquals("1:00:00", formatWorkoutClock(3_600))
    }

    private fun setContent(
        state: LiveWorkoutUiState,
        equipmentState: EquipmentReadState = EquipmentReadState(),
        onAction: (LiveWorkoutAction) -> Unit = {},
        onBackToPrograms: () -> Unit = {},
        onNavigate: (ProgramLibraryDestination) -> Unit = {},
    ) {
        composeTestRule.activity.setContent {
            LiveWorkoutScreen(
                state = state,
                equipmentState = equipmentState,
                onAction = onAction,
                onBackToPrograms = onBackToPrograms,
                onNavigate = onNavigate,
            )
        }
    }

    private fun assertVerticalContextIsDisplayed() {
        composeTestRule.onNodeWithText("TARGET 5,280 FT · VERTICAL MILE").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROPOSED LIMIT 240 MIN · NOT SESSION DURATION").assertIsDisplayed()
        composeTestRule.onNodeWithText("ELEVATION SOURCE UNAVAILABLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROGRESS NOT CALCULATED").assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVIEW ONLY · NO DEVICE COMMANDS").assertIsDisplayed()
    }

    private fun activeState(): LiveWorkoutUiState.Active = LiveWorkoutUiState.Active(
        workout = LiveWorkoutReadModel(
            programId = ProgramId("FAT_BURN"),
            elapsedSeconds = 59,
            remainingSeconds = 3_600,
            currentSegment = LiveWorkoutSegment(index = 1, name = "BUILD"),
            nextSegment = LiveWorkoutSegment(index = 2, name = "PUSH"),
            secondsUntilNextSegment = 60,
            targetSpeed = SpeedTenths(55),
            targetIncline = InclineTenths(80),
            isPaused = false,
            programTitle = "FAT BURN",
            previewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
        ),
    )

    private fun startedCoordinator(): InMemoryWorkoutSessionCoordinator {
        val catalog = StaticProgramCatalog()
        val detail = checkNotNull(catalog.findProgramDetail(ProgramId("FAT_BURN")))
        val validated = when (
            val result = ValidatedWorkoutPlan.create(
                WorkoutPlan(
                    programId = detail.programId,
                    settings = detail.defaultSettings,
                ),
                DeviceCapabilities(
                    duration = DurationLimits(DurationMinutes(10), DurationMinutes(60), DurationMinutes(5)),
                    speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
                    incline = InclineRange(InclineTenths(0), InclineTenths(150)),
                ),
            )
        ) {
            is ValidatedWorkoutPlanResult.Valid -> result.plan
            is ValidatedWorkoutPlanResult.Invalid -> error("Expected a valid workout plan")
        }
        return InMemoryWorkoutSessionCoordinator(catalog).also { coordinator ->
            check(coordinator.start(validated) is WorkoutSessionStarterResult.Started)
        }
    }
}
