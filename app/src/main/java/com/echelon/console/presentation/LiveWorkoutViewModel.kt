package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.WorkoutSessionController
import com.echelon.console.application.usecase.WorkoutSessionCommandFailure
import com.echelon.console.application.usecase.WorkoutSessionCommandResult
import com.echelon.console.domain.WorkoutSessionProgress
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimeline
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

class LiveWorkoutViewModel(
    private val controller: WorkoutSessionController,
    private val tickSource: WorkoutSessionTickSource = DefaultWorkoutSessionTickSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val initialSessionState = controller.currentState()
    private val _state = MutableStateFlow<LiveWorkoutUiState>(
        stateFor(initialSessionState),
    )
    private var tickJob: Job? = null

    val state: StateFlow<LiveWorkoutUiState> = _state.asStateFlow()

    init {
        if (initialSessionState !is WorkoutSessionState.Completed &&
            initialSessionState !is WorkoutSessionState.Stopped
        ) {
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
                    tickJob?.cancel()
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

    private companion object {
        const val COMMAND_ERROR_MESSAGE = "Workout controls are unavailable right now."
        const val TICK_SOURCE_ERROR_MESSAGE = "Workout session updates are unavailable right now."

        fun stateFor(state: WorkoutSessionState?): LiveWorkoutUiState = when (state) {
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
                summary = LiveWorkoutSummary(
                    programId = state.timeline.programId,
                    elapsedSeconds = state.elapsedSeconds,
                    totalDurationSeconds = state.timeline.totalDurationSeconds,
                ),
            )
            is WorkoutSessionState.Stopped -> LiveWorkoutUiState.Stopped(
                summary = LiveWorkoutSummary(
                    programId = state.timeline.programId,
                    elapsedSeconds = state.elapsedSeconds,
                    totalDurationSeconds = state.timeline.totalDurationSeconds,
                ),
            )
        }

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
        )
    }
}
