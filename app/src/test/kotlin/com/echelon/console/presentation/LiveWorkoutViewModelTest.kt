package com.echelon.console.presentation

import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.application.usecase.WorkoutSessionStarterResult
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LiveWorkoutViewModelTest {
    @Test
    fun `initial running session maps complete active read model`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = ManualTickSource()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        val active = assertActive(viewModel.state.value)
        assertEquals(ProgramId("FAT_BURN"), active.programId)
        assertEquals(0, active.elapsedSeconds)
        assertEquals(360, active.remainingSeconds)
        assertEquals(LiveWorkoutSegment(0, "Warm Up"), active.currentSegment)
        assertEquals(LiveWorkoutSegment(1, "Build"), active.nextSegment)
        assertEquals(120, active.secondsUntilNextSegment)
        assertEquals(SpeedTenths(55), active.targetSpeed)
        assertEquals(InclineTenths(50), active.targetIncline)
        assertEquals(false, active.isPaused)
    }

    @Test
    fun `read model and summary use the catalog official title`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = ManualTickSource()
        val viewModel = viewModel(
            controller = coordinator,
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            getProgramDetail = GetProgramDetail(
                ProgramDetailCatalog {
                    detail().copy(
                        title = "CLIENT CATALOG TITLE",
                        previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
                    )
                },
            ),
        )
        advanceUntilIdle()

        val active = assertActive(viewModel.state.value)
        assertEquals("CLIENT CATALOG TITLE", active.programTitle)
        assertEquals(ProgramPreviewMode.HEART_RATE_PREVIEW, active.previewMode)

        ticks.emit(360)
        advanceUntilIdle()

        val completed = viewModel.state.value as LiveWorkoutUiState.Completed
        assertEquals("CLIENT CATALOG TITLE", completed.summary.programTitle)
    }

    @Test
    fun `missing catalog detail uses the program id as a safe title fallback`() = runTest {
        val coordinator = startedCoordinator()
        val viewModel = viewModel(
            controller = coordinator,
            tickSource = ManualTickSource(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        val active = assertActive(viewModel.state.value)
        assertEquals("FAT BURN", active.programTitle)
        assertEquals(ProgramPreviewMode.FIXED_PROFILE_PREVIEW, active.previewMode)
    }

    @Test
    fun `ticks update elapsed state and crossing a segment updates countdown`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = ManualTickSource()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        ticks.emit(90)
        advanceUntilIdle()
        val beforeBoundary = assertActive(viewModel.state.value)
        assertEquals(90, beforeBoundary.elapsedSeconds)
        assertEquals(LiveWorkoutSegment(0, "Warm Up"), beforeBoundary.currentSegment)
        assertEquals(30, beforeBoundary.secondsUntilNextSegment)

        ticks.emit(30)
        advanceUntilIdle()
        val atBoundary = assertActive(viewModel.state.value)
        assertEquals(120, atBoundary.elapsedSeconds)
        assertEquals(LiveWorkoutSegment(1, "Build"), atBoundary.currentSegment)
        assertEquals(LiveWorkoutSegment(2, "Cool Down"), atBoundary.nextSegment)
        assertEquals(120, atBoundary.secondsUntilNextSegment)
    }

    @Test
    fun `pause blocks ticks and resume continues from the paused position`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = ManualTickSource()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()
        val paused = assertActive(viewModel.state.value)
        assertTrue(paused.isPaused)
        assertEquals(0, paused.elapsedSeconds)

        ticks.emit(60)
        advanceUntilIdle()
        assertEquals(paused, assertActive(viewModel.state.value))

        viewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()
        assertEquals(false, assertActive(viewModel.state.value).isPaused)

        ticks.emit(60)
        advanceUntilIdle()
        assertEquals(60, assertActive(viewModel.state.value).elapsedSeconds)
    }

    @Test
    fun `end action maps a running session to stopped`() = runTest {
        val coordinator = startedCoordinator()
        val viewModel = viewModel(coordinator, ManualTickSource(), StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()

        val stopped = viewModel.state.value as LiveWorkoutUiState.Stopped
        assertEquals(ProgramId("FAT_BURN"), stopped.summary.programId)
        assertEquals(0, stopped.summary.elapsedSeconds)
        assertEquals(360, stopped.summary.totalDurationSeconds)
    }

    @Test
    fun `exact finish maps completed state and later ticks are ignored`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = ManualTickSource()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        ticks.emit(360)
        advanceUntilIdle()
        val completed = viewModel.state.value as LiveWorkoutUiState.Completed
        assertEquals(360, completed.summary.elapsedSeconds)

        ticks.emit(30)
        advanceUntilIdle()
        assertEquals(completed, viewModel.state.value)
        assertEquals(completed.summary.elapsedSeconds, (coordinator.currentState() as WorkoutSessionState.Completed).elapsedSeconds)
    }

    @Test
    fun `exact completion cancels the tick subscription`() = runTest {
        val coordinator = startedCoordinator()
        val ticks = TrackingTickSource()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertTrue(ticks.subscribed)

        ticks.emit(360)
        advanceUntilIdle()

        assertTrue(ticks.cancelled)
        assertTrue(viewModel.state.value is LiveWorkoutUiState.Completed)
    }

    @Test
    fun `end action cancels the tick subscription`() = runTest {
        val ticks = TrackingTickSource()
        val viewModel = viewModel(startedCoordinator(), ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        assertTrue(ticks.subscribed)

        viewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()

        assertTrue(ticks.cancelled)
        assertTrue(viewModel.state.value is LiveWorkoutUiState.Stopped)
    }

    @Test
    fun `reattach refreshes a new session after stopped without duplicate subscriptions`() = runTest {
        val ticks = TrackingTickSource()
        val coordinator = startedCoordinator()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()
        assertTrue(ticks.cancelled)

        assertTrue(coordinator.start(validatedPlan()) is WorkoutSessionStarterResult.Started)
        viewModel.attachCurrentSession()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is LiveWorkoutUiState.Active)
        assertEquals(2, ticks.subscriptionCount)
        assertEquals(1, ticks.activeSubscriptionCount)

        viewModel.attachCurrentSession()
        advanceUntilIdle()
        assertEquals(2, ticks.subscriptionCount)
        assertEquals(1, ticks.activeSubscriptionCount)

        ticks.emit(15)
        advanceUntilIdle()
        assertEquals(15, assertActive(viewModel.state.value).elapsedSeconds)
    }

    @Test
    fun `reattach refreshes a new session after completion`() = runTest {
        val ticks = TrackingTickSource()
        val coordinator = startedCoordinator()
        val viewModel = viewModel(coordinator, ticks, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        ticks.emit(360)
        advanceUntilIdle()
        assertTrue(viewModel.state.value is LiveWorkoutUiState.Completed)
        assertTrue(ticks.cancelled)

        assertTrue(coordinator.start(validatedPlan()) is WorkoutSessionStarterResult.Started)
        viewModel.attachCurrentSession()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is LiveWorkoutUiState.Active)
        assertEquals(2, ticks.subscriptionCount)
        assertEquals(1, ticks.activeSubscriptionCount)
    }

    @Test
    fun `no session remains explicit and transition failure maps safe error`() = runTest {
        val noSessionTicks = TrackingTickSource()
        val noSession = viewModel(
            controller = InMemoryWorkoutSessionCoordinator(ProgramDetailCatalog { null }),
            tickSource = noSessionTicks,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertEquals(LiveWorkoutUiState.NoSession, noSession.state.value)
        assertTrue(noSessionTicks.subscribed)
        noSession.onAction(LiveWorkoutAction.PauseResume)
        noSession.onAction(LiveWorkoutAction.End)
        noSessionTicks.emit(1)
        advanceUntilIdle()
        assertEquals(LiveWorkoutUiState.NoSession, noSession.state.value)

        val coordinator = startedCoordinator()
        coordinator.advance(360)
        val failed = viewModel(
            controller = coordinator,
            tickSource = ManualTickSource(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()
        failed.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()
        assertEquals(
            LiveWorkoutUiState.Error("Workout controls are unavailable right now."),
            failed.state.value,
        )
        assertEquals(
            WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(
                    WorkoutSessionError.InvalidTransition(
                        WorkoutSessionAction.STOP,
                        WorkoutSessionStateKind.COMPLETED,
                    ),
                ),
            ),
            coordinator.stop(),
        )
    }

    @Test
    fun `tick cancellation does not become a user-facing error`() = runTest {
        val viewModel = viewModel(
            controller = startedCoordinator(),
            tickSource = object : WorkoutSessionTickSource {
                override fun ticks(): Flow<Int> = flow {
                    throw CancellationException("test cancellation")
                }
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value is LiveWorkoutUiState.Active)
        assertTrue(viewModel.state.value !is LiveWorkoutUiState.Error)
    }

    @Test
    fun `tick source failure maps to a safe error`() = runTest {
        val viewModel = viewModel(
            controller = startedCoordinator(),
            tickSource = FailingTickSource(),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        assertEquals(
            LiveWorkoutUiState.Error("Workout session updates are unavailable right now."),
            viewModel.state.value,
        )
    }

    private fun viewModel(
        controller: InMemoryWorkoutSessionCoordinator,
        tickSource: WorkoutSessionTickSource,
        dispatcher: CoroutineDispatcher,
        getProgramDetail: GetProgramDetail = GetProgramDetail(ProgramDetailCatalog { null }),
    ): LiveWorkoutViewModel = LiveWorkoutViewModel(
        controller,
        tickSource,
        dispatcher,
        getProgramDetail,
    )

    private fun startedCoordinator(): InMemoryWorkoutSessionCoordinator {
        val coordinator = InMemoryWorkoutSessionCoordinator(
            ProgramDetailCatalog { requestedId -> detail().takeIf { it.programId == requestedId } },
        )
        val result = ValidatedWorkoutPlan.create(plan(), capabilities)
        val validated = when (result) {
            is ValidatedWorkoutPlanResult.Valid -> result.plan
            is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
        }
        coordinator.start(validated)
        return coordinator
    }

    private fun assertActive(state: LiveWorkoutUiState): LiveWorkoutReadModel = when (state) {
        is LiveWorkoutUiState.Active -> state.workout
        else -> error("Expected active state, got $state")
    }

    private class ManualTickSource : WorkoutSessionTickSource {
        private val events = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        override fun ticks(): Flow<Int> = events

        suspend fun emit(seconds: Int) {
            events.emit(seconds)
        }
    }

    private class TrackingTickSource : WorkoutSessionTickSource {
        private val events = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        var subscribed = false
            private set
        var cancelled = false
            private set
        var subscriptionCount = 0
            private set
        var activeSubscriptionCount = 0
            private set

        override fun ticks(): Flow<Int> = flow {
            subscribed = true
            subscriptionCount += 1
            activeSubscriptionCount += 1
            try {
                events.collect { emit(it) }
            } finally {
                activeSubscriptionCount -= 1
                cancelled = true
            }
        }

        suspend fun emit(seconds: Int) {
            events.emit(seconds)
        }
    }

    private class FailingTickSource : WorkoutSessionTickSource {
        override fun ticks(): Flow<Int> = flow {
            throw IllegalStateException("private tick source details")
        }
    }

    private companion object {
        val capabilities = DeviceCapabilities(
            duration = DurationLimits(DurationMinutes(1), DurationMinutes(60), DurationMinutes(1)),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )

        fun plan(): WorkoutPlan = WorkoutPlan(
            programId = ProgramId("FAT_BURN"),
            settings = detail().defaultSettings,
        )

        fun validatedPlan(): ValidatedWorkoutPlan = when (
            val result = ValidatedWorkoutPlan.create(plan(), capabilities)
        ) {
            is ValidatedWorkoutPlanResult.Valid -> result.plan
            is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
        }

        fun detail(): ProgramDetail = ProgramDetail(
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
    }
}
