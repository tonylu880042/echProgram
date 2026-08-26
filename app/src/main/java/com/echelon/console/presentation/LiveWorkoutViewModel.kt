package com.echelon.console.presentation

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.EvaluateZone2EquipmentHeartRate
import com.echelon.console.application.usecase.EvaluateZone2EquipmentHeartRateRequest
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailResult
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.WorkoutSessionProgress
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineAnnotation
import com.echelon.console.domain.WorkoutTimelineSegment
import com.echelon.console.domain.WorkoutTimelineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class LiveWorkoutViewModel(
    private val controller: WorkoutSessionController,
    private val tickSource: WorkoutSessionTickSource = DefaultWorkoutSessionTickSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val getProgramDetail: GetProgramDetail,
    private val evaluateZone2EquipmentHeartRate: EvaluateZone2EquipmentHeartRate =
        EvaluateZone2EquipmentHeartRate(),
    private val nowElapsedRealtimeMillis: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {
    private val _state = MutableStateFlow<LiveWorkoutUiState>(LiveWorkoutUiState.NoSession)
    private var tickJob: Job? = null
    private var equipmentState: EquipmentReadState = EquipmentReadState()

    val state: StateFlow<LiveWorkoutUiState> = _state.asStateFlow()

    init {
        attachCurrentSession()
    }

    fun attachCurrentSession() {
        val current = controller.currentState()
        _state.value = stateFor(current)
        when (current) {
            null,
            is WorkoutSessionState.NotStarted,
            is WorkoutSessionState.Running,
            is WorkoutSessionState.Paused,
            -> ensureTicking()

            is WorkoutSessionState.Completed,
            is WorkoutSessionState.Stopped,
            -> stopTicking()
        }
    }

    fun onEquipmentStateChanged(equipmentState: EquipmentReadState) {
        this.equipmentState = equipmentState
        _state.value = stateFor(controller.currentState())
    }

    fun onAction(action: LiveWorkoutAction) {
        when (action) {
            LiveWorkoutAction.PauseResume -> pauseOrResume()
            LiveWorkoutAction.End -> apply(controller.stop())
        }
    }

    private fun pauseOrResume() {
        val current = controller.currentState()
        if (current == null) {
            _state.value = LiveWorkoutUiState.NoSession
            return
        }
        when (current) {
            is WorkoutSessionState.Paused -> apply(controller.resume())
            is WorkoutSessionState.Running -> apply(controller.pause())
            is WorkoutSessionState.NotStarted,
            is WorkoutSessionState.Completed,
            is WorkoutSessionState.Stopped,
            -> apply(controller.pause())
        }
    }

    private fun advanceIfRunning(elapsedSeconds: Int) {
        if (elapsedSeconds <= 0 || controller.currentState() !is WorkoutSessionState.Running) {
            return
        }
        apply(controller.advance(elapsedSeconds))
    }

    private fun refreshIfPausedOrRunning() {
        val current = controller.currentState()
        when (current) {
            is WorkoutSessionState.Paused,
            is WorkoutSessionState.Running,
            -> _state.value = stateFor(current)

            null,
            is WorkoutSessionState.NotStarted,
            is WorkoutSessionState.Completed,
            is WorkoutSessionState.Stopped,
            -> Unit
        }
    }

    private fun apply(result: WorkoutSessionCommandResult) {
        when (result) {
            is WorkoutSessionCommandResult.Updated -> {
                _state.value = stateFor(result.state)
                if (result.state is WorkoutSessionState.Completed ||
                    result.state is WorkoutSessionState.Stopped
                ) {
                    stopTicking()
                }
            }
            is WorkoutSessionCommandResult.Failed -> when (result.failure) {
                WorkoutSessionCommandFailure.NoSession -> {
                    _state.value = LiveWorkoutUiState.NoSession
                }
                is WorkoutSessionCommandFailure.Transition -> {
                    _state.value = LiveWorkoutUiState.Error(COMMAND_ERROR_MESSAGE)
                }
            }
        }
    }

    private fun stateFor(state: WorkoutSessionState?): LiveWorkoutUiState = when (state) {
        null,
        is WorkoutSessionState.NotStarted,
        -> LiveWorkoutUiState.NoSession

        is WorkoutSessionState.Running -> LiveWorkoutUiState.Active(
            workout = readModel(state.timeline, state.progress, isPaused = false),
        )
        is WorkoutSessionState.Paused -> LiveWorkoutUiState.Active(
            workout = readModel(state.timeline, state.progress, isPaused = true),
        )
        is WorkoutSessionState.Completed -> LiveWorkoutUiState.Completed(
            summary = summaryFor(state.timeline, state.elapsedSeconds),
        )
        is WorkoutSessionState.Stopped -> LiveWorkoutUiState.Stopped(
            summary = summaryFor(state.timeline, state.elapsedSeconds),
        )
    }

    private fun readModel(
        timeline: WorkoutTimeline,
        progress: WorkoutSessionProgress,
        isPaused: Boolean,
    ): LiveWorkoutReadModel {
        val presentation = presentationFor(timeline.programId)
        return LiveWorkoutReadModel(
            programId = timeline.programId,
            elapsedSeconds = progress.elapsedSeconds,
            remainingSeconds = progress.remainingSeconds,
            currentSegment = LiveWorkoutSegment(
                index = progress.currentSegmentIndex,
                name = progress.currentSegment.name,
                annotation = progress.currentSegment.annotation,
                displayLabel = displayLabel(progress.currentSegment),
            ),
            nextSegment = progress.nextSegment?.let { segment ->
                LiveWorkoutSegment(
                    index = progress.currentSegmentIndex + 1,
                    name = segment.name,
                    annotation = segment.annotation,
                    displayLabel = displayLabel(segment),
                )
            },
            secondsUntilNextSegment = progress.secondsUntilNextSegment,
            targetSpeed = progress.target.speed,
            targetIncline = progress.target.incline,
            isPaused = isPaused,
            programTitle = presentation.title,
            previewMode = presentation.previewMode,
            runWalkSummary = runWalkSummaryFor(timeline),
            verticalContext = verticalContextFor(timeline),
            zone2Context = zone2ContextFor(timeline),
        )
    }

    private fun summaryFor(
        timeline: WorkoutTimeline,
        elapsedSeconds: Int,
    ): LiveWorkoutSummary {
        val presentation = presentationFor(timeline.programId)
        return LiveWorkoutSummary(
            programId = timeline.programId,
            elapsedSeconds = elapsedSeconds,
            totalDurationSeconds = timeline.totalDurationSeconds,
            programTitle = presentation.title,
            previewMode = presentation.previewMode,
            runWalkSummary = runWalkSummaryFor(timeline),
            verticalContext = verticalContextFor(timeline),
            zone2Context = zone2ContextFor(timeline),
        )
    }

    private fun zone2ContextFor(timeline: WorkoutTimeline): LiveZone2HeartRateContext? {
        val context = timeline.context as? WorkoutTimelineContext.Zone2Preview ?: return null
        if (
            timeline.programId != ZONE_2_PROGRAM_ID ||
            context.programId != ZONE_2_PROGRAM_ID ||
            context.programId != timeline.programId
        ) {
            return null
        }
        val result = evaluateZone2EquipmentHeartRate(
            EvaluateZone2EquipmentHeartRateRequest(
                context = context,
                equipmentState = equipmentState,
                nowElapsedRealtimeMillis = nowElapsedRealtimeMillis(),
                staleAfterMillis = ZONE_2_PREVIEW_STALE_AFTER_MILLIS,
            ),
        )
        return LiveZone2HeartRateMapper.map(timeline, result)
    }

    private fun verticalContextFor(timeline: WorkoutTimeline): LiveVerticalWorkoutContext? =
        when (val context = timeline.context) {
            WorkoutTimelineContext.None -> null
            is WorkoutTimelineContext.VerticalPreview -> {
                if (
                    context.programId != timeline.programId ||
                    timeline.programId != VERTICAL_PROGRAM_ID
                ) {
                    null
                } else {
                    LiveVerticalWorkoutContext(
                        target = context.target,
                        proposedTimeLimit = context.proposedTimeLimit,
                        elevationSource = context.elevationSource,
                        progressStatus = context.progressStatus,
                        controlStatus = context.controlStatus,
                    )
                }
            }
            is WorkoutTimelineContext.Zone2Preview -> null
        }

    private fun displayLabel(segment: WorkoutTimelineSegment): String = when (
        val annotation = segment.annotation
    ) {
        WorkoutTimelineAnnotation.Unannotated -> segment.name
        WorkoutTimelineAnnotation.WarmUpWalk -> "WARM UP WALK"
        is WorkoutTimelineAnnotation.Run -> "RUN ${annotation.ordinal} OF ${annotation.total}"
        WorkoutTimelineAnnotation.WalkRecovery -> "WALK RECOVERY"
        WorkoutTimelineAnnotation.EasyWalk -> "EASY WALK"
        WorkoutTimelineAnnotation.CoolDown -> "COOL DOWN"
    }

    private fun runWalkSummaryFor(timeline: WorkoutTimeline): LiveWorkoutRunWalkSummary? {
        if (timeline.segments.none { it.annotation != WorkoutTimelineAnnotation.Unannotated }) {
            return null
        }
        val runSeconds = timeline.segments
            .filter { it.annotation is WorkoutTimelineAnnotation.Run }
            .sumOf { it.durationSeconds }
        val walkSeconds = timeline.segments
            .filter { isExplicitWalkAnnotation(it.annotation) }
            .sumOf { it.durationSeconds }
        return LiveWorkoutRunWalkSummary(
            runMinutes = runSeconds / SECONDS_PER_MINUTE,
            walkMinutes = walkSeconds / SECONDS_PER_MINUTE,
        )
    }

    private fun isExplicitWalkAnnotation(annotation: WorkoutTimelineAnnotation): Boolean = when (
        annotation
    ) {
        WorkoutTimelineAnnotation.Unannotated,
        is WorkoutTimelineAnnotation.Run,
        -> false
        WorkoutTimelineAnnotation.WarmUpWalk,
        WorkoutTimelineAnnotation.WalkRecovery,
        WorkoutTimelineAnnotation.EasyWalk,
        WorkoutTimelineAnnotation.CoolDown,
        -> true
    }

    private fun presentationFor(programId: ProgramId): LiveWorkoutPresentation =
        when (val result = getProgramDetail(programId)) {
            is ProgramDetailResult.Ready -> LiveWorkoutPresentation(
                title = result.detail.title,
                previewMode = result.detail.previewMode,
            )
            is ProgramDetailResult.NotFound -> LiveWorkoutPresentation(
                title = result.programId.value
                    .replace('_', ' ')
                    .uppercase(Locale.US),
                previewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
            )
        }

    private fun ensureTicking() {
        if (tickJob?.isActive == true) {
            return
        }
        // Keep NoSession subscribed so a coordinator started elsewhere can be observed.
        tickJob = viewModelScope.launch(dispatcher) {
            tickSource.ticks()
                .catch { exception ->
                    if (exception is CancellationException) {
                        throw exception
                    }
                    _state.value = LiveWorkoutUiState.Error(TICK_SOURCE_ERROR_MESSAGE)
                }
                .collect(::onTick)
        }
    }

    private fun onTick(elapsedSeconds: Int) {
        if (controller.currentState() is WorkoutSessionState.Running && elapsedSeconds > 0) {
            advanceIfRunning(elapsedSeconds)
        } else {
            refreshIfPausedOrRunning()
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val COMMAND_ERROR_MESSAGE = "Workout controls are unavailable right now."
        const val TICK_SOURCE_ERROR_MESSAGE = "Workout session updates are unavailable right now."
        val VERTICAL_PROGRAM_ID = ProgramId("VERTICAL")
        val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
        const val ZONE_2_PREVIEW_STALE_AFTER_MILLIS = 3_000L
    }
}

private data class LiveWorkoutPresentation(
    val title: String,
    val previewMode: ProgramPreviewMode,
)
