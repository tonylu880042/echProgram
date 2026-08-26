package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailResult
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.domain.WorkoutSessionProgress
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.ProgramId
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
) : ViewModel() {
    private val _state = MutableStateFlow<LiveWorkoutUiState>(LiveWorkoutUiState.NoSession)
    private var tickJob: Job? = null

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

    private fun summaryFor(
        timeline: WorkoutTimeline,
        elapsedSeconds: Int,
    ): LiveWorkoutSummary = LiveWorkoutSummary(
        programId = timeline.programId,
        elapsedSeconds = elapsedSeconds,
        totalDurationSeconds = timeline.totalDurationSeconds,
        programTitle = titleFor(timeline.programId),
    )

    private fun readModel(
        timeline: WorkoutTimeline,
        progress: WorkoutSessionProgress,
        isPaused: Boolean,
    ): LiveWorkoutReadModel = LiveWorkoutReadModel(
        programId = timeline.programId,
        elapsedSeconds = progress.elapsedSeconds,
        remainingSeconds = progress.remainingSeconds,
        currentSegment = LiveWorkoutSegment(
            index = progress.currentSegmentIndex,
            name = progress.currentSegment.name,
        ),
        nextSegment = progress.nextSegment?.let { segment ->
            LiveWorkoutSegment(
                index = progress.currentSegmentIndex + 1,
                name = segment.name,
            )
        },
        secondsUntilNextSegment = progress.secondsUntilNextSegment,
        targetSpeed = progress.target.speed,
        targetIncline = progress.target.incline,
        isPaused = isPaused,
        programTitle = titleFor(timeline.programId),
    )

    private fun titleFor(programId: ProgramId): String =
        when (val result = getProgramDetail(programId)) {
            is ProgramDetailResult.Ready -> result.detail.title
            is ProgramDetailResult.NotFound -> result.programId.value
                .replace('_', ' ')
                .uppercase(Locale.US)
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
                .collect { elapsedSeconds ->
                    advanceIfRunning(elapsedSeconds)
                }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private companion object {
        const val COMMAND_ERROR_MESSAGE = "Workout controls are unavailable right now."
        const val TICK_SOURCE_ERROR_MESSAGE = "Workout session updates are unavailable right now."
    }
}
