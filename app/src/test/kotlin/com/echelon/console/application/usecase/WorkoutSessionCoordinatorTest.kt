package com.echelon.console.application.usecase

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
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionAction
import com.echelon.console.domain.WorkoutSessionError
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateKind
import com.echelon.console.domain.WorkoutTimelineCompileError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSessionCoordinatorTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(60), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `fixed profile starts at zero with selected duration and clamped targets`() {
        val coordinator = coordinator()

        val result = coordinator.start(validatedPlan())

        val started = assertStarted(result)
        val running: WorkoutSessionState.Running = started.state
        assertEquals(0, running.progress.elapsedSeconds)
        assertEquals(360, running.progress.remainingSeconds)
        assertEquals("Warm Up", running.progress.currentSegment.name)
        assertEquals(55, running.progress.target.speed.value)
        assertEquals(50, running.progress.target.incline.value)
        assertEquals(running, coordinator.currentState())
    }

    @Test
    fun `missing detail is an explicit start failure`() {
        val coordinator = coordinator { null }

        val result = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(ProgramId("FAT_BURN")),
            ),
            result,
        )
        assertNull(coordinator.currentState())
    }

    @Test
    fun `every non fixed preview mode is rejected`() {
        ProgramPreviewMode.values()
            .filterNot { it == ProgramPreviewMode.FIXED_PROFILE_PREVIEW }
            .forEach { mode ->
                val coordinator = coordinator { detail().copy(previewMode = mode) }

                val result = coordinator.start(validatedPlan())

                assertEquals(
                    WorkoutSessionStarterResult.Failed(
                        WorkoutSessionStartFailure.UnsupportedPreviewMode(mode),
                    ),
                    result,
                )
                assertNull(coordinator.currentState())
            }
    }

    @Test
    fun `empty profile compiler failure is propagated`() {
        val coordinator = coordinator { detail().copy(profile = emptyList()) }

        val result = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.TimelineCompileFailed(
                    WorkoutTimelineCompileError.EmptyProfile,
                ),
            ),
            result,
        )
        assertNull(coordinator.currentState())
    }

    @Test
    fun `second start is rejected without replacing the active session`() {
        val coordinator = coordinator()
        val first = assertStarted(coordinator.start(validatedPlan()))

        val second = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
            second,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    @Test
    fun `commands without an active session return explicit no session failures`() {
        val coordinator = coordinator()
        val commands = listOf<() -> WorkoutSessionCommandResult>(
            { coordinator.advance(1) },
            { coordinator.pause() },
            { coordinator.resume() },
            { coordinator.stop() },
        )

        commands.forEach { command ->
            assertEquals(
                WorkoutSessionCommandResult.Failed(WorkoutSessionCommandFailure.NoSession),
                command(),
            )
        }
        assertNull(coordinator.currentState())
    }

    @Test
    fun `advance pause resume sequence updates state and paused advance stays frozen`() {
        val coordinator = coordinator()
        assertStarted(coordinator.start(validatedPlan()))

        val advanced = assertRunning(coordinator.advance(120))
        assertEquals(120, advanced.progress.elapsedSeconds)

        val paused = assertPaused(coordinator.pause())
        assertEquals(120, paused.progress.elapsedSeconds)

        val frozen = assertPaused(coordinator.advance(90))
        assertEquals(paused, frozen)
        assertEquals(paused, coordinator.currentState())

        val resumed = assertRunning(coordinator.resume())
        assertEquals(120, resumed.progress.elapsedSeconds)

        val continued = assertRunning(coordinator.advance(60))
        assertEquals(180, continued.progress.elapsedSeconds)
        assertEquals(continued, coordinator.currentState())
    }

    @Test
    fun `exact and overshoot advance complete and completed or stopped states are terminal`() {
        val exactCoordinator = coordinator()
        assertStarted(exactCoordinator.start(validatedPlan()))

        val completed = assertCompleted(exactCoordinator.advance(360))
        assertEquals(360, completed.elapsedSeconds)
        assertEquals(completed, exactCoordinator.currentState())

        val completedStop = exactCoordinator.stop()
        assertEquals(
            WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(
                    WorkoutSessionError.InvalidTransition(
                        WorkoutSessionAction.STOP,
                        completed.kind,
                    ),
                ),
            ),
            completedStop,
        )
        assertEquals(completed, exactCoordinator.currentState())

        val restarted = assertStarted(exactCoordinator.start(validatedPlan()))
        assertEquals(0, restarted.state.progress.elapsedSeconds)

        val stopped = coordinator()
        assertStarted(stopped.start(validatedPlan()))
        assertRunning(stopped.advance(30))
        val stoppedState = assertStopped(stopped.stop())
        assertEquals(30, stoppedState.elapsedSeconds)
        assertEquals(stoppedState, stopped.currentState())

        val stoppedAdvance = stopped.advance(1)
        assertEquals(
            WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(
                    WorkoutSessionError.InvalidTransition(
                        WorkoutSessionAction.ADVANCE,
                        stoppedState.kind,
                    ),
                ),
            ),
            stoppedAdvance,
        )
        assertEquals(stoppedState, stopped.currentState())
        assertStarted(stopped.start(validatedPlan()))

        val overshootCoordinator = coordinator()
        assertStarted(overshootCoordinator.start(validatedPlan()))
        val overshot = assertCompleted(overshootCoordinator.advance(999))
        assertEquals(360, overshot.elapsedSeconds)
        assertEquals(overshot, overshootCoordinator.currentState())
        assertStarted(overshootCoordinator.start(validatedPlan()))
    }

    @Test
    fun `invalid commands preserve the last valid state`() {
        val coordinator = coordinator()
        assertStarted(coordinator.start(validatedPlan()))
        val running = assertRunning(coordinator.advance(30))

        val nonPositive = coordinator.advance(0)
        assertEquals(
            WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(
                    WorkoutSessionError.NonPositiveElapsed(0),
                ),
            ),
            nonPositive,
        )
        assertEquals(running, coordinator.currentState())

        val paused = assertPaused(coordinator.pause())
        val pauseAgain = coordinator.pause()
        assertTransitionFailure(pauseAgain, WorkoutSessionAction.PAUSE, paused.kind)
        assertEquals(paused, coordinator.currentState())

        val resumed = assertRunning(coordinator.resume())
        val resumeAgain = coordinator.resume()
        assertTransitionFailure(resumeAgain, WorkoutSessionAction.RESUME, resumed.kind)
        assertEquals(resumed, coordinator.currentState())
    }

    private fun coordinator(
        catalog: (ProgramId) -> ProgramDetail? = { requestedId ->
            detail().takeIf { it.programId == requestedId }
        },
    ): InMemoryWorkoutSessionCoordinator = InMemoryWorkoutSessionCoordinator(
        catalog = ProgramDetailCatalog(catalog),
    )

    private fun validatedPlan(): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(plan(), capabilities)
    ) {
        is ValidatedWorkoutPlanResult.Valid -> result.plan
        is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
    }

    private fun plan(): WorkoutPlan = WorkoutPlan(
        programId = ProgramId("FAT_BURN"),
        settings = detail().defaultSettings,
    )

    private fun detail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("FAT_BURN"),
        title = "FAT BURN",
        promise = "A fixed profile for a controlled burn.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(6),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(50),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(100)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(2), SpeedTenths(70), InclineTenths(80)),
            ProgramSegmentSummary("Build", DurationMinutes(2), SpeedTenths(60), InclineTenths(70)),
            ProgramSegmentSummary("Cool Down", DurationMinutes(2), SpeedTenths(25), InclineTenths(0)),
        ),
    )

    private fun assertStarted(
        result: WorkoutSessionStarterResult,
    ): WorkoutSessionStarterResult.Started = when (result) {
        is WorkoutSessionStarterResult.Started -> result
        is WorkoutSessionStarterResult.Failed -> error("Expected successful start, got $result")
    }

    private fun assertUpdated(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionCommandResult.Updated = when (result) {
        is WorkoutSessionCommandResult.Updated -> result
        is WorkoutSessionCommandResult.Failed -> error("Expected updated state, got $result")
    }

    private fun assertRunning(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Running = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Running -> state
        else -> error("Expected running state, got $state")
    }

    private fun assertPaused(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Paused = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Paused -> state
        else -> error("Expected paused state, got $state")
    }

    private fun assertCompleted(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Completed = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Completed -> state
        else -> error("Expected completed state, got $state")
    }

    private fun assertStopped(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Stopped = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Stopped -> state
        else -> error("Expected stopped state, got $state")
    }

    private fun assertTransitionFailure(
        result: WorkoutSessionCommandResult,
        action: WorkoutSessionAction,
        state: WorkoutSessionStateKind,
    ) {
        assertEquals(
            WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(
                    WorkoutSessionError.InvalidTransition(action, state),
                ),
            ),
            result,
        )
    }
}
