package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraftRequest
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartFiveKReadySessionDraftResult
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutTimelineAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FiveKReadyLiveWorkoutViewModelTest {
    @Test
    fun `5K live read model keeps typed coaching across run boundaries and planned summary`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val draft = draft()
            val started = StartFiveKReadySessionDraft(coordinator)(draft, capabilities)
            assertTrue(started is StartFiveKReadySessionDraftResult.Started)
            val ticks = ManualTickSource()
            val viewModel = LiveWorkoutViewModel(
                controller = coordinator,
                tickSource = ticks,
                dispatcher = dispatcher,
                getProgramDetail = GetProgramDetail(catalog),
            )
            advanceUntilIdle()

            var active = assertActive(viewModel.state.value)
            assertEquals(WorkoutTimelineAnnotation.WarmUpWalk, active.currentSegment.annotation)
            assertEquals("WARM UP WALK", active.currentSegment.displayLabel)
            assertEquals(WorkoutTimelineAnnotation.Run(1, 3), active.nextSegment?.annotation)
            assertEquals(LiveWorkoutRunWalkSummary(runMinutes = 15, walkMinutes = 15), active.runWalkSummary)

            ticks.emit(300)
            advanceUntilIdle()
            active = assertActive(viewModel.state.value)
            assertEquals(WorkoutTimelineAnnotation.Run(1, 3), active.currentSegment.annotation)
            assertEquals("RUN 1 OF 3", active.currentSegment.displayLabel)
            assertEquals(WorkoutTimelineAnnotation.WalkRecovery, active.nextSegment?.annotation)
            assertEquals(300, active.secondsUntilNextSegment)

            ticks.emit(300)
            advanceUntilIdle()
            active = assertActive(viewModel.state.value)
            assertEquals(WorkoutTimelineAnnotation.WalkRecovery, active.currentSegment.annotation)
            assertEquals("WALK RECOVERY", active.currentSegment.displayLabel)

            ticks.emit(180)
            advanceUntilIdle()
            active = assertActive(viewModel.state.value)
            assertEquals(WorkoutTimelineAnnotation.Run(2, 3), active.currentSegment.annotation)
            assertEquals("RUN 2 OF 3", active.currentSegment.displayLabel)

            ticks.emit(420)
            advanceUntilIdle()
            active = assertActive(viewModel.state.value)
            assertEquals(WorkoutTimelineAnnotation.Run(3, 3), active.currentSegment.annotation)
            assertEquals("RUN 3 OF 3", active.currentSegment.displayLabel)

            viewModel.onAction(LiveWorkoutAction.PauseResume)
            advanceUntilIdle()
            val paused = assertActive(viewModel.state.value)
            assertTrue(paused.isPaused)
            assertEquals("RUN 3 OF 3", paused.currentSegment.displayLabel)
            ticks.emit(30)
            advanceUntilIdle()
            assertEquals(paused, assertActive(viewModel.state.value))

            viewModel.onAction(LiveWorkoutAction.PauseResume)
            advanceUntilIdle()
            ticks.emit(600)
            advanceUntilIdle()
            val completed = viewModel.state.value as LiveWorkoutUiState.Completed
            assertEquals(LiveWorkoutRunWalkSummary(runMinutes = 15, walkMinutes = 15), completed.summary.runWalkSummary)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun assertActive(state: LiveWorkoutUiState): LiveWorkoutReadModel = when (state) {
        is LiveWorkoutUiState.Active -> state.workout
        else -> error("Expected active state, got $state")
    }

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
        is FiveKReadySessionGenerationResult.Rejected -> error("Expected generated 5K draft")
    }

    private class ManualTickSource : WorkoutSessionTickSource {
        private val events = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        override fun ticks(): Flow<Int> = events

        suspend fun emit(seconds: Int) {
            events.emit(seconds)
        }
    }

    private companion object {
        val capabilities = DeviceCapabilities(
            duration = DurationLimits(DurationMinutes(10), DurationMinutes(60), DurationMinutes(5)),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )
    }
}
