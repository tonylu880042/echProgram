package com.echelon.console.presentation

import com.echelon.console.application.usecase.EvaluateCalorieTargetEquipmentSnapshot
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSampleFreshness
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
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
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.WorkoutSessionResult
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateMachine
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.WorkoutTimelineSegment
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalorieTargetWorkoutLiveViewModelTest {
    @Test
    fun readyEquipmentEventImmediatelyMapsCalorieSnapshotIntoActiveReadModel() = runTest {
        val clock = MutableClock(10_000L)
        val viewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(
            readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L),
        )

        val active = assertActive(viewModel.state.value)
        val calorieContext = requireNotNull(active.calorieTargetContext)
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            calorieContext.reading,
        )
        assertEquals(DurationMinutes(40), calorieContext.representativeProfileDuration)
        assertEquals(SpeedTenths(60), calorieContext.effectiveMaxSpeed)
        assertEquals(InclineTenths(100), calorieContext.effectiveMaxIncline)
        assertEquals(300, calorieContext.target.estimatedKcal)
        assertEquals(CalorieEstimateStatus.ESTIMATED, calorieContext.estimateStatus)
        assertEquals(
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            calorieContext.source,
        )
        assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, calorieContext.unitSemantics)
        assertEquals(
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            calorieContext.sessionResetSemantics,
        )
        assertEquals(
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            calorieContext.completionAuthority,
        )
        assertEquals(
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            calorieContext.progressSemantics,
        )
        assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, calorieContext.previewStatus)
        assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, calorieContext.deviceCommandStatus)
    }

    @Test
    fun exactFreshnessBoundaryUsesInjectedClockOnZeroTick() = runTest {
        val clock = MutableClock(10_000L)
        val ticks = ManualTickSource()
        val viewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(
            readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L),
        )
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            calorieReading(viewModel),
        )

        clock.value = 12_499L
        ticks.emit(0)
        advanceUntilIdle()
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 2_999L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            calorieReading(viewModel),
        )

        clock.value = 12_500L
        ticks.emit(0)
        advanceUntilIdle()
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 3_000L,
                freshness = CalorieSampleFreshness.STALE,
            ),
            calorieReading(viewModel),
        )
        assertEquals(0, assertActive(viewModel.state.value).elapsedSeconds)
    }

    @Test
    fun pausedEquipmentAndTicksRefreshCalorieReadingWithoutAdvancingElapsed() = runTest {
        val clock = MutableClock(10_000L)
        val ticks = ManualTickSource()
        val viewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()

        viewModel.onAction(LiveWorkoutAction.PauseResume)
        advanceUntilIdle()
        viewModel.onEquipmentStateChanged(
            readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L),
        )
        assertEquals(0, assertActive(viewModel.state.value).elapsedSeconds)
        assertEquals(CalorieSampleFreshness.FRESH, calorieEvaluatedReading(viewModel).freshness)

        clock.value = 12_499L
        ticks.emit(60)
        advanceUntilIdle()
        assertEquals(0, assertActive(viewModel.state.value).elapsedSeconds)
        assertEquals(2_999L, calorieEvaluatedReading(viewModel).sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.FRESH, calorieEvaluatedReading(viewModel).freshness)

        clock.value = 12_500L
        ticks.emit(0)
        advanceUntilIdle()
        assertEquals(0, assertActive(viewModel.state.value).elapsedSeconds)
        assertEquals(3_000L, calorieEvaluatedReading(viewModel).sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.STALE, calorieEvaluatedReading(viewModel).freshness)
    }

    @Test
    fun runningPositiveTickAdvancesSessionAndReevaluatesCalorieAge() = runTest {
        val clock = MutableClock(10_000L)
        val ticks = ManualTickSource()
        val viewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            tickSource = ticks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = clock::value,
        )
        advanceUntilIdle()
        viewModel.onEquipmentStateChanged(
            readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L),
        )

        clock.value = 10_750L
        ticks.emit(7)
        advanceUntilIdle()

        val active = assertActive(viewModel.state.value)
        assertEquals(7, active.elapsedSeconds)
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 1_250L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            active.calorieTargetContext?.reading,
        )
    }

    @Test
    fun completedAndStoppedSummariesPreserveLatestTypedCalorieContext() = runTest {
        val completedTicks = ManualTickSource()
        val completedViewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            tickSource = completedTicks,
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()
        completedViewModel.onEquipmentStateChanged(
            readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L),
        )
        completedTicks.emit(2_400)
        advanceUntilIdle()

        val completed = when (val state = completedViewModel.state.value) {
            is LiveWorkoutUiState.Completed -> state.summary
            else -> error("Expected completed state, got $state")
        }
        assertEquals(2_400, completed.elapsedSeconds)
        assertEquals(300, completed.calorieTargetContext?.target?.estimatedKcal)
        assertEquals(DurationMinutes(40), completed.calorieTargetContext?.representativeProfileDuration)
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            completed.calorieTargetContext?.reading,
        )

        val stoppedViewModel = viewModel(
            controller = MutableStateController(
                runningState(calorieTimeline(target = CalorieTargetOption.FIVE_HUNDRED_KCAL)),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()
        stoppedViewModel.onEquipmentStateChanged(
            readyEquipment(calories = 244.0, elapsedRealtimeMillis = 9_500L),
        )
        stoppedViewModel.onAction(LiveWorkoutAction.End)
        advanceUntilIdle()

        val stopped = when (val state = stoppedViewModel.state.value) {
            is LiveWorkoutUiState.Stopped -> state.summary
            else -> error("Expected stopped state, got $state")
        }
        assertEquals(500, stopped.calorieTargetContext?.target?.estimatedKcal)
        assertEquals(90, stopped.calorieTargetContext?.target?.proposedMaxTime?.minutes)
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 244.0,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            stopped.calorieTargetContext?.reading,
        )
    }

    @Test
    fun sourceAndSampleFailuresRemainTypedUnavailableInActiveModel() = runTest {
        val viewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()

        viewModel.onEquipmentStateChanged(EquipmentReadState(connection = EquipmentConnection.Connecting))
        assertEquals(
            LiveCalorieTargetReading.Unavailable(
                LiveCalorieTargetUnavailableReason.SourceUnavailable(
                    LiveCalorieTargetSourceReason.Connecting,
                ),
            ),
            calorieReading(viewModel),
        )

        viewModel.onEquipmentStateChanged(readyEquipment(calories = null, elapsedRealtimeMillis = 9_500L))
        assertEquals(
            LiveCalorieTargetReading.Unavailable(
                LiveCalorieTargetUnavailableReason.InvalidCalorieSample(
                    LiveCalorieTargetSampleReason.MissingDisplayValue,
                ),
            ),
            calorieReading(viewModel),
        )
    }

    @Test
    fun genericVerticalAndZone2TimelinesNeverReceiveCalorieContext() = runTest {
        val ready = readyEquipment(calories = 183.5, elapsedRealtimeMillis = 9_500L)
        val genericViewModel = viewModel(
            controller = MutableStateController(runningState(genericTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        val verticalViewModel = viewModel(
            controller = MutableStateController(runningState(verticalTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        val zone2ViewModel = viewModel(
            controller = MutableStateController(runningState(zone2Timeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        val calorieViewModel = viewModel(
            controller = MutableStateController(runningState(calorieTimeline())),
            dispatcher = StandardTestDispatcher(testScheduler),
            nowElapsedRealtimeMillis = { 10_000L },
        )
        advanceUntilIdle()

        genericViewModel.onEquipmentStateChanged(ready)
        verticalViewModel.onEquipmentStateChanged(ready)
        zone2ViewModel.onEquipmentStateChanged(ready)
        calorieViewModel.onEquipmentStateChanged(ready)

        assertNull(assertActive(genericViewModel.state.value).calorieTargetContext)
        assertNull(assertActive(verticalViewModel.state.value).calorieTargetContext)
        assertNull(assertActive(zone2ViewModel.state.value).calorieTargetContext)
        val calorieActive = assertActive(calorieViewModel.state.value)
        assertNull(calorieActive.verticalContext)
        assertNull(calorieActive.zone2Context)
    }

    private fun viewModel(
        controller: WorkoutSessionController,
        tickSource: WorkoutSessionTickSource = WorkoutSessionTickSource { MutableSharedFlow() },
        dispatcher: CoroutineDispatcher,
        nowElapsedRealtimeMillis: () -> Long,
    ): LiveWorkoutViewModel = LiveWorkoutViewModel(
        controller = controller,
        tickSource = tickSource,
        dispatcher = dispatcher,
        getProgramDetail = GetProgramDetail(ProgramDetailCatalog { requestedId ->
            if (requestedId == ProgramId("CALORIE_TARGET")) {
                calorieDetail()
            } else {
                null
            }
        }),
        evaluateCalorieTargetEquipmentSnapshot = EvaluateCalorieTargetEquipmentSnapshot(),
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

    private fun assertActive(state: LiveWorkoutUiState): LiveWorkoutReadModel = when (state) {
        is LiveWorkoutUiState.Active -> state.workout
        else -> error("Expected active state, got $state")
    }

    private fun calorieReading(viewModel: LiveWorkoutViewModel): LiveCalorieTargetReading =
        assertActive(viewModel.state.value).calorieTargetContext?.reading
            ?: error("Expected calorie target context")

    private fun calorieEvaluatedReading(
        viewModel: LiveWorkoutViewModel,
    ): LiveCalorieTargetReading.Evaluated =
        calorieReading(viewModel) as? LiveCalorieTargetReading.Evaluated
            ?: error("Expected evaluated calorie reading, got ${calorieReading(viewModel)}")

    private fun runningState(timeline: WorkoutTimeline): WorkoutSessionState.Running = when (
        val result = WorkoutSessionStateMachine.start(WorkoutSessionState.NotStarted(timeline))
    ) {
        is WorkoutSessionResult.Valid -> result.state as WorkoutSessionState.Running
        is WorkoutSessionResult.Invalid -> error("Expected running state, got $result")
    }

    private fun readyEquipment(
        calories: Double?,
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
            heartRateBpm = null,
            distance = null,
            calories = calories,
        ),
    )

    private fun calorieTimeline(
        target: CalorieTargetOption = CalorieTargetOption.THREE_HUNDRED_KCAL,
    ): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("CALORIE_TARGET"),
        totalDurationSeconds = 2_400,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "CALORIE TARGET",
                startSecond = 0,
                endSecond = 2_400,
                targetSpeed = SpeedTenths(60),
                targetIncline = InclineTenths(100),
            ),
        ),
        context = WorkoutTimelineContext.CalorieTargetPreview(
            programId = ProgramId("CALORIE_TARGET"),
            target = acceptedTarget(target),
            estimateStatus = CalorieEstimateStatus.ESTIMATED,
            source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
            sessionResetSemantics =
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
            deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            representativeProfileDuration = DurationMinutes(40),
            effectiveMaxSpeed = SpeedTenths(60),
            effectiveMaxIncline = InclineTenths(100),
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
            target = VerticalTarget.FIVE_HUNDRED_FEET,
            proposedTimeLimit = VerticalTimeLimitProposal(45, VerticalTimeLimitStatus.PROPOSED),
            elevationSource = VerticalElevationSource.UNAVAILABLE,
            progressStatus = VerticalProgressStatus.NOT_CALCULATED,
            controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
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
            target = acceptedHeartRateTarget(),
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

    private fun calorieDetail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("CALORIE_TARGET"),
        title = "CALORIE TARGET",
        promise = "A representative calorie preview.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(40),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(60),
            maxIncline = InclineTenths(100),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(150)),
        profile = listOf(
            ProgramSegmentSummary(
                name = "CALORIE TARGET",
                duration = DurationMinutes(40),
                speed = SpeedTenths(60),
                incline = InclineTenths(100),
            ),
        ),
    )

    private fun acceptedTarget(
        target: CalorieTargetOption = CalorieTargetOption.THREE_HUNDRED_KCAL,
    ): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(
            target.estimatedKcal,
        )
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected target, got $result")
    }

    private fun acceptedHeartRateTarget(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }
}
