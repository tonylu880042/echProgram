package com.echelon.console.presentation

import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraftResult
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.FiveKReadySessionGenerator
import com.echelon.console.domain.FiveKReadySessionGeneratorInput
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
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.VerticalWorkoutDraft
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import com.echelon.console.domain.VerticalWorkoutGenerator
import com.echelon.console.domain.VerticalWorkoutGeneratorInput
import com.echelon.console.domain.WorkoutSessionResult
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateMachine
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineAnnotation
import com.echelon.console.domain.WorkoutTimelineCompiler
import com.echelon.console.domain.WorkoutTimelineCompileResult
import com.echelon.console.domain.WorkoutTimelineSegment
import com.echelon.console.domain.toWorkoutTimelineProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VerticalWorkoutLiveContextTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(90), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `active paused completed and stopped states preserve vertical preview context`() = runTest {
        val activeTicks = ManualTickSource()
        val activeViewModel = viewModel(verticalCoordinator(), activeTicks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        assertEquals(verticalContext(), assertActive(activeViewModel.state.value).verticalContext)

        activeViewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()
        assertEquals(verticalContext(), assertActive(activeViewModel.state.value).verticalContext)

        val completedTicks = ManualTickSource()
        val completedViewModel = viewModel(
            verticalCoordinator(),
            completedTicks,
            StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        completedTicks.emit(3_000)
        advanceUntilIdle()
        assertEquals(
            verticalContext(),
            (completedViewModel.state.value as LiveWorkoutUiState.Completed).summary.verticalContext,
        )

        val stoppedViewModel = viewModel(
            verticalCoordinator(),
            ManualTickSource(),
            StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        stoppedViewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()
        assertEquals(
            verticalContext(),
            (stoppedViewModel.state.value as LiveWorkoutUiState.Stopped).summary.verticalContext,
        )
    }

    @Test
    fun `generic and five k live models have no vertical context`() = runTest {
        val genericViewModel = viewModel(
            fixedStateController(genericRunningState()),
            ManualTickSource(),
            StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        assertNull(assertActive(genericViewModel.state.value).verticalContext)

        val fiveKViewModel = viewModel(
            fixedStateController(fiveKRunningState()),
            ManualTickSource(),
            StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        val fiveK = assertActive(fiveKViewModel.state.value)
        assertNull(fiveK.verticalContext)
        assertEquals(LiveWorkoutRunWalkSummary(runMinutes = 15, walkMinutes = 15), fiveK.runWalkSummary)
    }

    private fun viewModel(
        controller: WorkoutSessionController,
        ticks: ManualTickSource,
        dispatcher: CoroutineDispatcher,
    ): LiveWorkoutViewModel = LiveWorkoutViewModel(
        controller = controller,
        tickSource = ticks,
        dispatcher = dispatcher,
        getProgramDetail = GetProgramDetail(ProgramDetailCatalog { programId ->
            when (programId) {
                ProgramId("VERTICAL") -> verticalDetail()
                ProgramId("5K_READY") -> fiveKDetail()
                else -> null
            }
        }),
    )

    private fun verticalCoordinator(): InMemoryWorkoutSessionCoordinator {
        val coordinator = InMemoryWorkoutSessionCoordinator(ProgramDetailCatalog { verticalDetail() })
        val result = StartVerticalWorkoutDraft(coordinator)(verticalDraft(), capabilities)
        check(result is StartVerticalWorkoutDraftResult.Started) { "Expected running VERTICAL draft, got $result" }
        return coordinator
    }

    private fun genericRunningState(): WorkoutSessionState.Running {
        val timeline = WorkoutTimeline(
            programId = ProgramId("FAT_BURN"),
            totalDurationSeconds = 60,
            segments = listOf(
                WorkoutTimelineSegment(
                    name = "WARM UP",
                    startSecond = 0,
                    endSecond = 60,
                    targetSpeed = SpeedTenths(25),
                    targetIncline = InclineTenths(0),
                ),
            ),
        )
        return running(timeline)
    }

    private fun fiveKRunningState(): WorkoutSessionState.Running {
        val draft = when (
            val result = FiveKReadySessionGenerator().generate(
                FiveKReadySessionGeneratorInput(
                    durationMinutes = 30,
                    baselinePace = FiveKReadyBaselinePace(
                        speed = SpeedTenths(40),
                        source = FiveKReadyBaselineSource.USER_ENTERED,
                    ),
                    userMaxSpeed = SpeedTenths(60),
                    machineMaxSpeed = SpeedTenths(60),
                    userMaxIncline = InclineTenths(60),
                    machineMaxIncline = InclineTenths(60),
                ),
            )
        ) {
            is FiveKReadySessionGenerationResult.Generated -> result.draft
            is FiveKReadySessionGenerationResult.Rejected -> error("Expected 5K draft, got ${result.failure}")
        }
        val compiled = WorkoutTimelineCompiler.compile(
            programId = ProgramId("5K_READY"),
            profile = draft.toWorkoutTimelineProfile(),
            settings = PlanSettings(
                duration = DurationMinutes(30),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = SpeedTenths(60),
                maxIncline = InclineTenths(60),
                adaptToYou = false,
            ),
        )
        return running(assertValid(compiled))
    }

    private fun running(timeline: WorkoutTimeline): WorkoutSessionState.Running = when (
        val result = WorkoutSessionStateMachine.start(WorkoutSessionState.NotStarted(timeline))
    ) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Running
        is WorkoutSessionResult.Invalid -> error("Expected running state, got $result")
    }

    private fun fixedStateController(state: WorkoutSessionState): WorkoutSessionController =
        object : WorkoutSessionController {
            override fun currentState(): WorkoutSessionState = state

            override fun advance(elapsedSeconds: Int): WorkoutSessionCommandResult =
                WorkoutSessionCommandResult.Failed(WorkoutSessionCommandFailure.NoSession)

            override fun pause(): WorkoutSessionCommandResult =
                WorkoutSessionCommandResult.Failed(WorkoutSessionCommandFailure.NoSession)

            override fun resume(): WorkoutSessionCommandResult =
                WorkoutSessionCommandResult.Failed(WorkoutSessionCommandFailure.NoSession)

            override fun stop(): WorkoutSessionCommandResult =
                WorkoutSessionCommandResult.Failed(WorkoutSessionCommandFailure.NoSession)
        }

    private fun verticalDraft(): VerticalWorkoutDraft = when (
        val result = VerticalWorkoutGenerator().generate(
            VerticalWorkoutGeneratorInput(
                target = VerticalTarget.VERTICAL_MILE,
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is VerticalWorkoutGenerationResult.Generated -> result.draft
        is VerticalWorkoutGenerationResult.Rejected -> error("Expected VERTICAL draft, got ${result.failure}")
    }

    private fun verticalContext(): LiveVerticalWorkoutContext = LiveVerticalWorkoutContext(
        target = VerticalTarget.VERTICAL_MILE,
        proposedTimeLimit = VerticalTimeLimitProposal(240, VerticalTimeLimitStatus.PROPOSED),
        elevationSource = VerticalElevationSource.UNAVAILABLE,
        progressStatus = VerticalProgressStatus.NOT_CALCULATED,
        controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
    )

    private fun verticalDetail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("VERTICAL"),
        title = "VERTICAL",
        promise = "Representative target preview.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(50),
            intensity = PlanIntensity.HIGH,
            focus = PlanFocus.MORE_INCLINE,
            maxSpeed = SpeedTenths(40),
            maxIncline = InclineTenths(150),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(150)),
        profile = listOf(
            ProgramSegmentSummary(
                name = "STATIC CATALOG",
                duration = DurationMinutes(50),
                speed = SpeedTenths(20),
                incline = InclineTenths(0),
            ),
        ),
    )

    private fun fiveKDetail(): ProgramDetail = verticalDetail().copy(
        programId = ProgramId("5K_READY"),
        title = "5K READY",
        defaultSettings = verticalDetail().defaultSettings.copy(
            duration = DurationMinutes(30),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(60),
            maxIncline = InclineTenths(60),
        ),
        profile = listOf(
            ProgramSegmentSummary(
                name = "STATIC CATALOG",
                duration = DurationMinutes(30),
                speed = SpeedTenths(20),
                incline = InclineTenths(0),
            ),
        ),
        supportedDurations = listOf(DurationMinutes(30)),
    )

    private fun assertActive(state: LiveWorkoutUiState): LiveWorkoutReadModel = when (state) {
        is LiveWorkoutUiState.Active -> state.workout
        else -> error("Expected active state, got $state")
    }

    private fun assertValid(result: WorkoutTimelineCompileResult): WorkoutTimeline = when (result) {
        is WorkoutTimelineCompileResult.Valid -> result.timeline
        is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
    }

    private class ManualTickSource : WorkoutSessionTickSource {
        private val events = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        override fun ticks(): Flow<Int> = events

        suspend fun emit(seconds: Int) {
            events.emit(seconds)
        }
    }

}
