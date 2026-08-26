package com.echelon.console.presentation

import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateMachine
import com.echelon.console.domain.WorkoutSessionResult
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.WorkoutTimelineSegment
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Zone2WorkoutLiveViewModelTest {
    @Test
    fun `ready equipment event immediately maps evaluated heart rate into active read model`() = runTest {
        val running = runningState(zone2Timeline())
        val viewModel = viewModel(
            controller = FixedStateController(running),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(
            readyEquipment(
                bpm = 130,
                elapsedRealtimeMillis = 9_500L,
            ),
        )

        val active = assertActive(viewModel.state.value)
        assertEquals(
            LiveZone2HeartRateReading.Evaluated(
                currentBpm = 130,
                sampleAgeMillis = 500L,
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
            ),
            active.zone2Context?.reading,
        )
    }

    @Test
    fun `exact three second freshness boundary transitions active reading to signal lost`() = runTest {
        val clock = MutableClock(10_000L)
        val ticks = ManualTickSource()
        val viewModel = viewModel(
            controller = MutableStateController(runningState(zone2Timeline())),
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(readyEquipment(bpm = 130, elapsedRealtimeMillis = 9_500L))
        assertEquals(Zone2HeartRateStatus.IN_ZONE, evaluatedReading(viewModel).status)

        clock.value = 12_500L
        ticks.emit(1)
        advanceUntilIdle()

        val reading = evaluatedReading(viewModel)
        assertEquals(3_000L, reading.sampleAgeMillis)
        assertEquals(Zone2HeartRateStatus.HR_SIGNAL_LOST, reading.status)
    }

    @Test
    fun `paused equipment event updates reading without changing elapsed time`() = runTest {
        val clock = MutableClock(10_000L)
        val controller = MutableStateController(runningState(zone2Timeline()))
        val viewModel = viewModel(
            controller = controller,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()

        viewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()
        viewModel.onEquipmentStateChanged(readyEquipment(bpm = 135, elapsedRealtimeMillis = 9_500L))

        val paused = assertActive(viewModel.state.value)
        assertEquals(true, paused.isPaused)
        assertEquals(0, paused.elapsedSeconds)
        assertEquals(Zone2HeartRateStatus.IN_ZONE, evaluatedReading(viewModel).status)
    }

    @Test
    fun `paused tick refreshes sample age while session elapsed remains frozen`() = runTest {
        val clock = MutableClock(10_000L)
        val ticks = ManualTickSource()
        val controller = MutableStateController(runningState(zone2Timeline()))
        val viewModel = viewModel(
            controller = controller,
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()
        viewModel.onEquipmentStateChanged(readyEquipment(bpm = 130, elapsedRealtimeMillis = 9_500L))
        viewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()

        clock.value = 12_500L
        ticks.emit(60)
        advanceUntilIdle()

        val paused = assertActive(viewModel.state.value)
        assertEquals(true, paused.isPaused)
        assertEquals(0, paused.elapsedSeconds)
        assertEquals(3_000L, evaluatedReading(viewModel).sampleAgeMillis)
        assertEquals(Zone2HeartRateStatus.HR_SIGNAL_LOST, evaluatedReading(viewModel).status)
    }

    @Test
    fun `completed and stopped summaries preserve latest typed zone2 context`() = runTest {
        val completedTicks = ManualTickSource()
        val completedViewModel = viewModel(
            controller = MutableStateController(runningState(zone2Timeline())),
            tickSource = completedTicks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()
        completedViewModel.onEquipmentStateChanged(
            readyEquipment(bpm = 130, elapsedRealtimeMillis = 9_500L),
        )
        completedTicks.emit(1_800)
        advanceUntilIdle()

        val completed = viewModelSummary(completedViewModel)
        assertEquals(
            LiveZone2HeartRateReading.Evaluated(
                currentBpm = 130,
                sampleAgeMillis = 500L,
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
            ),
            completed.zone2Context?.reading,
        )

        val stoppedViewModel = viewModel(
            controller = MutableStateController(runningState(zone2Timeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()
        stoppedViewModel.onEquipmentStateChanged(
            readyEquipment(bpm = 135, elapsedRealtimeMillis = 9_500L),
        )
        stoppedViewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()

        assertEquals(135, evaluatedSummaryReading(stoppedViewModel).currentBpm)
    }

    @Test
    fun `representative equipment failure is retained as typed unavailable zone2 reading`() = runTest {
        val viewModel = viewModel(
            controller = MutableStateController(runningState(zone2Timeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(
            EquipmentReadState(connection = EquipmentConnection.Connecting),
        )

        assertEquals(
            LiveZone2HeartRateReading.Unavailable(
                LiveZone2HeartRateUnavailableReason.SourceUnavailable(
                    LiveZone2HeartRateSourceReason.Connecting,
                ),
            ),
            assertActive(viewModel.state.value).zone2Context?.reading,
        )
    }

    @Test
    fun `generic and vertical timelines never receive zone2 context`() = runTest {
        val ready = readyEquipment(bpm = 130, elapsedRealtimeMillis = 9_500L)
        val generic = viewModel(
            controller = MutableStateController(runningState(genericTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        val vertical = viewModel(
            controller = MutableStateController(runningState(verticalTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()

        generic.onEquipmentStateChanged(ready)
        vertical.onEquipmentStateChanged(ready)

        assertEquals(null, assertActive(generic.state.value).zone2Context)
        assertEquals(null, assertActive(vertical.state.value).zone2Context)
    }

    private fun viewModel(
        controller: WorkoutSessionController,
        tickSource: WorkoutSessionTickSource = WorkoutSessionTickSource { MutableSharedFlow<Int>() },
        dispatcher: CoroutineDispatcher,
        nowElapsedRealtimeMillis: () -> Long = { 10_000L },
    ): LiveWorkoutViewModel = LiveWorkoutViewModel(
        controller = controller,
        tickSource = tickSource,
        dispatcher = dispatcher,
        getProgramDetail = GetProgramDetail(ProgramDetailCatalog { detail() }),
        nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
    )

    private class MutableClock(var value: Long)

    private class ManualTickSource : WorkoutSessionTickSource {
        private val events = MutableSharedFlow<Int>(extraBufferCapacity = 16)

        override fun ticks(): Flow<Int> = events

        suspend fun emit(seconds: Int) {
            events.emit(seconds)
        }
    }

    private class MutableStateController(
        initialState: WorkoutSessionState,
    ) : WorkoutSessionController {
        private var state = initialState

        override fun currentState(): WorkoutSessionState = state

        override fun advance(elapsedSeconds: Int): WorkoutSessionCommandResult =
            transition(WorkoutSessionStateMachine.advance(state, elapsedSeconds))

        override fun pause(): WorkoutSessionCommandResult =
            transition(WorkoutSessionStateMachine.pause(state))

        override fun resume(): WorkoutSessionCommandResult =
            transition(WorkoutSessionStateMachine.resume(state))

        override fun stop(): WorkoutSessionCommandResult =
            transition(WorkoutSessionStateMachine.stop(state))

        private fun transition(result: WorkoutSessionResult): WorkoutSessionCommandResult = when (
            result
        ) {
            is WorkoutSessionResult.Valid -> {
                state = result.state
                WorkoutSessionCommandResult.Updated(result.state)
            }
            is WorkoutSessionResult.Invalid -> WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(result.error),
            )
        }
    }

    private class FixedStateController(
        private val state: WorkoutSessionState,
    ) : WorkoutSessionController {
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

    private fun assertActive(state: LiveWorkoutUiState): LiveWorkoutReadModel = when (state) {
        is LiveWorkoutUiState.Active -> state.workout
        else -> error("Expected active state, got $state")
    }

    private fun evaluatedReading(viewModel: LiveWorkoutViewModel): LiveZone2HeartRateReading.Evaluated {
        val reading = assertActive(viewModel.state.value).zone2Context?.reading
        return reading as? LiveZone2HeartRateReading.Evaluated
            ?: error("Expected evaluated Zone 2 reading, got $reading")
    }

    private fun viewModelSummary(viewModel: LiveWorkoutViewModel): LiveWorkoutSummary = when (
        val state = viewModel.state.value
    ) {
        is LiveWorkoutUiState.Completed -> state.summary
        else -> error("Expected completed state, got $state")
    }

    private fun evaluatedSummaryReading(
        viewModel: LiveWorkoutViewModel,
    ): LiveZone2HeartRateReading.Evaluated {
        val reading = when (val state = viewModel.state.value) {
            is LiveWorkoutUiState.Stopped -> state.summary.zone2Context?.reading
            else -> error("Expected stopped state, got $state")
        }
        return reading as? LiveZone2HeartRateReading.Evaluated
            ?: error("Expected evaluated Zone 2 reading, got $reading")
    }

    private fun runningState(timeline: WorkoutTimeline): WorkoutSessionState.Running = when (
        val result = WorkoutSessionStateMachine.start(WorkoutSessionState.NotStarted(timeline))
    ) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Running
        is WorkoutSessionResult.Invalid -> error("Expected running state, got $result")
    }

    private fun readyEquipment(
        bpm: Int,
        elapsedRealtimeMillis: Long,
    ): EquipmentReadState = EquipmentReadState(
        connection = EquipmentConnection.Ready,
        equipment = EquipmentDescriptor(
            connectionStatus = "CONNECTED",
            equipmentType = EquipmentType.RUN,
            runType = null,
            deviceName = "test-equipment",
            isMetric = false,
            isBindDevice = true,
            controlState = EquipmentControlState.STARTED,
        ),
        telemetry = EquipmentTelemetry(
            elapsedRealtimeMillis = elapsedRealtimeMillis,
            elapsedTime = null,
            speed = null,
            incline = null,
            heartRateBpm = bpm,
            distance = null,
            calories = null,
        ),
    )

    private fun zone2Timeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("ZONE_2"),
        totalDurationSeconds = 1_800,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "ZONE 2",
                startSecond = 0,
                endSecond = 1_800,
                targetSpeed = SpeedTenths(50),
                targetIncline = InclineTenths(80),
            ),
        ),
        context = WorkoutTimelineContext.Zone2Preview(
            programId = ProgramId("ZONE_2"),
            target = target(),
            intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
            previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
            adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
            thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
            hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            duration = DurationMinutes(30),
            effectiveMaxSpeed = SpeedTenths(50),
            effectiveMaxIncline = InclineTenths(80),
        ),
    )

    private fun genericTimeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("FAT_BURN"),
        totalDurationSeconds = 60,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "EASY",
                startSecond = 0,
                endSecond = 60,
                targetSpeed = SpeedTenths(30),
                targetIncline = InclineTenths(0),
            ),
        ),
    )

    private fun verticalTimeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("VERTICAL"),
        totalDurationSeconds = 60,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "CLIMB",
                startSecond = 0,
                endSecond = 60,
                targetSpeed = SpeedTenths(30),
                targetIncline = InclineTenths(50),
            ),
        ),
        context = WorkoutTimelineContext.VerticalPreview(
            programId = ProgramId("VERTICAL"),
            target = com.echelon.console.domain.VerticalTarget.FIVE_HUNDRED_FEET,
            proposedTimeLimit = com.echelon.console.domain.VerticalTimeLimitProposal(
                minutes = 45,
                status = com.echelon.console.domain.VerticalTimeLimitStatus.PROPOSED,
            ),
            elevationSource = com.echelon.console.domain.VerticalElevationSource.UNAVAILABLE,
            progressStatus = com.echelon.console.domain.VerticalProgressStatus.NOT_CALCULATED,
            controlStatus = com.echelon.console.domain.VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
        ),
    )

    private fun target(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }

    private fun detail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("ZONE_2"),
        title = "ZONE 2",
        promise = "A representative zone 2 preview.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(30),
            intensity = PlanIntensity.LOW,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(50),
            maxIncline = InclineTenths(80),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(25), SpeedTenths(50)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(80)),
        profile = listOf(
            ProgramSegmentSummary(
                name = "ZONE 2",
                duration = DurationMinutes(30),
                speed = SpeedTenths(50),
                incline = InclineTenths(80),
            ),
        ),
        previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
    )
}
