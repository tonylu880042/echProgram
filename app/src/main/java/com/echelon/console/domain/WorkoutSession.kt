package com.echelon.console.domain

enum class WorkoutSessionStateKind {
    NOT_STARTED,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
}

enum class WorkoutSessionAction {
    START,
    ADVANCE,
    PAUSE,
    RESUME,
    MANUAL_OVERRIDE,
    STOP,
}

enum class WorkoutSessionTargetMode {
    PROFILE,
    MANUAL,
}

data class WorkoutSessionTarget(
    val speed: SpeedTenths,
    val incline: InclineTenths,
    val mode: WorkoutSessionTargetMode,
)

data class WorkoutSessionProgress(
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
    val currentSegmentIndex: Int,
    val currentSegment: WorkoutTimelineSegment,
    val nextSegment: WorkoutTimelineSegment?,
    val secondsUntilNextSegment: Int?,
    val target: WorkoutSessionTarget,
)

sealed interface WorkoutSessionState {
    val timeline: WorkoutTimeline
    val kind: WorkoutSessionStateKind

    data class NotStarted(
        override val timeline: WorkoutTimeline,
    ) : WorkoutSessionState {
        override val kind: WorkoutSessionStateKind = WorkoutSessionStateKind.NOT_STARTED
    }

    data class Running(
        override val timeline: WorkoutTimeline,
        val progress: WorkoutSessionProgress,
    ) : WorkoutSessionState {
        override val kind: WorkoutSessionStateKind = WorkoutSessionStateKind.RUNNING
    }

    data class Paused(
        override val timeline: WorkoutTimeline,
        val progress: WorkoutSessionProgress,
    ) : WorkoutSessionState {
        override val kind: WorkoutSessionStateKind = WorkoutSessionStateKind.PAUSED
    }

    data class Completed(
        override val timeline: WorkoutTimeline,
        val elapsedSeconds: Int,
        val remainingSeconds: Int,
    ) : WorkoutSessionState {
        override val kind: WorkoutSessionStateKind = WorkoutSessionStateKind.COMPLETED
    }

    data class Stopped(
        override val timeline: WorkoutTimeline,
        val elapsedSeconds: Int,
    ) : WorkoutSessionState {
        override val kind: WorkoutSessionStateKind = WorkoutSessionStateKind.STOPPED
    }
}

sealed interface WorkoutSessionError {
    data object EmptyTimeline : WorkoutSessionError

    data object NonPositiveTimelineDuration : WorkoutSessionError

    data class InvalidTimelineSegment(
        val segmentIndex: Int,
    ) : WorkoutSessionError

    data class InvalidTransition(
        val action: WorkoutSessionAction,
        val state: WorkoutSessionStateKind,
    ) : WorkoutSessionError

    data class NonPositiveElapsed(
        val seconds: Int,
    ) : WorkoutSessionError
}

sealed interface WorkoutSessionResult {
    data class Valid(val state: WorkoutSessionState) : WorkoutSessionResult

    data class Invalid(val error: WorkoutSessionError) : WorkoutSessionResult
}

object WorkoutSessionStateMachine {
    fun create(timeline: WorkoutTimeline): WorkoutSessionResult {
        val validationError = validateTimeline(timeline)
        return if (validationError == null) {
            WorkoutSessionResult.Valid(WorkoutSessionState.NotStarted(timeline))
        } else {
            WorkoutSessionResult.Invalid(validationError)
        }
    }

    fun start(state: WorkoutSessionState): WorkoutSessionResult = when (state) {
        is WorkoutSessionState.NotStarted -> WorkoutSessionResult.Valid(
            runningStateAt(state.timeline, elapsedSeconds = 0),
        )

        else -> invalidTransition(WorkoutSessionAction.START, state)
    }

    fun advance(
        state: WorkoutSessionState,
        elapsedSeconds: Int,
    ): WorkoutSessionResult {
        if (elapsedSeconds <= 0) {
            return WorkoutSessionResult.Invalid(
                WorkoutSessionError.NonPositiveElapsed(elapsedSeconds),
            )
        }

        return when (state) {
            is WorkoutSessionState.Running -> {
                val totalDurationSeconds = state.timeline.totalDurationSeconds
                val nextElapsedSeconds = minOf(
                    totalDurationSeconds.toLong(),
                    state.progress.elapsedSeconds.toLong() + elapsedSeconds.toLong(),
                ).toInt()
                if (nextElapsedSeconds >= totalDurationSeconds) {
                    WorkoutSessionResult.Valid(
                        WorkoutSessionState.Completed(
                            timeline = state.timeline,
                            elapsedSeconds = totalDurationSeconds,
                            remainingSeconds = 0,
                        ),
                    )
                } else {
                    val nextSegmentIndex = segmentIndexAt(state.timeline, nextElapsedSeconds)
                    val retainedManualTarget = state.progress.target
                        .takeIf {
                            it.mode == WorkoutSessionTargetMode.MANUAL &&
                                state.progress.currentSegmentIndex == nextSegmentIndex
                        }
                    WorkoutSessionResult.Valid(
                        runningStateAt(
                            timeline = state.timeline,
                            elapsedSeconds = nextElapsedSeconds,
                            manualTarget = retainedManualTarget,
                        ),
                    )
                }
            }

            is WorkoutSessionState.Paused -> WorkoutSessionResult.Valid(state)

            else -> invalidTransition(WorkoutSessionAction.ADVANCE, state)
        }
    }

    fun pause(state: WorkoutSessionState): WorkoutSessionResult = when (state) {
        is WorkoutSessionState.Running -> WorkoutSessionResult.Valid(
            WorkoutSessionState.Paused(state.timeline, state.progress),
        )

        else -> invalidTransition(WorkoutSessionAction.PAUSE, state)
    }

    fun resume(state: WorkoutSessionState): WorkoutSessionResult = when (state) {
        is WorkoutSessionState.Paused -> WorkoutSessionResult.Valid(
            WorkoutSessionState.Running(state.timeline, state.progress),
        )

        else -> invalidTransition(WorkoutSessionAction.RESUME, state)
    }

    fun applyManualOverride(
        state: WorkoutSessionState,
        speed: SpeedTenths,
        incline: InclineTenths,
    ): WorkoutSessionResult = when (state) {
        is WorkoutSessionState.Running -> WorkoutSessionResult.Valid(
            state.copy(
                progress = state.progress.copy(
                    target = WorkoutSessionTarget(
                        speed = speed,
                        incline = incline,
                        mode = WorkoutSessionTargetMode.MANUAL,
                    ),
                ),
            ),
        )

        else -> invalidTransition(WorkoutSessionAction.MANUAL_OVERRIDE, state)
    }

    fun stop(state: WorkoutSessionState): WorkoutSessionResult = when (state) {
        is WorkoutSessionState.NotStarted -> WorkoutSessionResult.Valid(
            WorkoutSessionState.Stopped(state.timeline, elapsedSeconds = 0),
        )

        is WorkoutSessionState.Running -> WorkoutSessionResult.Valid(
            WorkoutSessionState.Stopped(state.timeline, state.progress.elapsedSeconds),
        )

        is WorkoutSessionState.Paused -> WorkoutSessionResult.Valid(
            WorkoutSessionState.Stopped(state.timeline, state.progress.elapsedSeconds),
        )

        is WorkoutSessionState.Completed,
        is WorkoutSessionState.Stopped,
        -> invalidTransition(WorkoutSessionAction.STOP, state)
    }

    private fun runningStateAt(
        timeline: WorkoutTimeline,
        elapsedSeconds: Int,
        manualTarget: WorkoutSessionTarget? = null,
    ): WorkoutSessionState.Running {
        val segmentIndex = segmentIndexAt(timeline, elapsedSeconds)
        val currentSegment = timeline.segments[segmentIndex]
        val nextSegment = timeline.segments.getOrNull(segmentIndex + 1)
        return WorkoutSessionState.Running(
            timeline = timeline,
            progress = WorkoutSessionProgress(
                elapsedSeconds = elapsedSeconds,
                remainingSeconds = timeline.totalDurationSeconds - elapsedSeconds,
                currentSegmentIndex = segmentIndex,
                currentSegment = currentSegment,
                nextSegment = nextSegment,
                secondsUntilNextSegment = nextSegment?.let {
                    currentSegment.endSecond - elapsedSeconds
                },
                target = manualTarget ?: WorkoutSessionTarget(
                    speed = currentSegment.targetSpeed,
                    incline = currentSegment.targetIncline,
                    mode = WorkoutSessionTargetMode.PROFILE,
                ),
            ),
        )
    }

    private fun segmentIndexAt(
        timeline: WorkoutTimeline,
        elapsedSeconds: Int,
    ): Int = timeline.segments.indexOfFirst { elapsedSeconds < it.endSecond }

    private fun validateTimeline(timeline: WorkoutTimeline): WorkoutSessionError? {
        if (timeline.segments.isEmpty()) {
            return WorkoutSessionError.EmptyTimeline
        }
        if (timeline.totalDurationSeconds <= 0) {
            return WorkoutSessionError.NonPositiveTimelineDuration
        }

        var expectedStartSecond = 0
        timeline.segments.forEachIndexed { index, segment ->
            if (
                segment.startSecond != expectedStartSecond ||
                segment.endSecond <= segment.startSecond
            ) {
                return WorkoutSessionError.InvalidTimelineSegment(index)
            }
            expectedStartSecond = segment.endSecond
        }
        return if (expectedStartSecond == timeline.totalDurationSeconds) {
            null
        } else {
            WorkoutSessionError.InvalidTimelineSegment(timeline.segments.lastIndex)
        }
    }

    private fun invalidTransition(
        action: WorkoutSessionAction,
        state: WorkoutSessionState,
    ): WorkoutSessionResult = WorkoutSessionResult.Invalid(
        WorkoutSessionError.InvalidTransition(action, state.kind),
    )
}
