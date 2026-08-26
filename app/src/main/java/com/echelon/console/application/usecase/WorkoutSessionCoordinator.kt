package com.echelon.console.application.usecase

import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutSessionAction
import com.echelon.console.domain.WorkoutSessionError
import com.echelon.console.domain.WorkoutSessionResult
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateMachine
import com.echelon.console.domain.WorkoutTimelineCompileError
import com.echelon.console.domain.WorkoutTimelineCompileResult
import com.echelon.console.domain.WorkoutTimelineCompiler

sealed interface WorkoutSessionStarterResult {
    data class Started(
        val state: WorkoutSessionState.Running,
    ) : WorkoutSessionStarterResult

    data class Failed(
        val failure: WorkoutSessionStartFailure,
    ) : WorkoutSessionStarterResult
}

sealed interface WorkoutSessionStartFailure {
    data class ProgramNotFound(
        val programId: ProgramId,
    ) : WorkoutSessionStartFailure

    data class UnsupportedPreviewMode(
        val mode: ProgramPreviewMode,
    ) : WorkoutSessionStartFailure

    data class TimelineCompileFailed(
        val error: WorkoutTimelineCompileError,
    ) : WorkoutSessionStartFailure

    data class SessionTransitionFailed(
        val error: WorkoutSessionError,
    ) : WorkoutSessionStartFailure

    data object ActiveSessionExists : WorkoutSessionStartFailure
}

sealed interface WorkoutSessionCommandResult {
    data class Updated(
        val state: WorkoutSessionState,
    ) : WorkoutSessionCommandResult

    data class Failed(
        val failure: WorkoutSessionCommandFailure,
    ) : WorkoutSessionCommandResult
}

sealed interface WorkoutSessionCommandFailure {
    data object NoSession : WorkoutSessionCommandFailure

    data class Transition(
        val error: WorkoutSessionError,
    ) : WorkoutSessionCommandFailure
}

class InMemoryWorkoutSessionCoordinator(
    private val catalog: ProgramDetailCatalog,
) : WorkoutSessionStarter {
    private var sessionState: WorkoutSessionState? = null

    override fun start(plan: ValidatedWorkoutPlan): WorkoutSessionStarterResult {
        when (sessionState) {
            is WorkoutSessionState.NotStarted,
            is WorkoutSessionState.Running,
            is WorkoutSessionState.Paused,
            -> return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ActiveSessionExists,
            )

            null,
            is WorkoutSessionState.Completed,
            is WorkoutSessionState.Stopped,
            -> Unit
        }

        val detail = catalog.findProgramDetail(plan.plan.programId)
            ?: return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(plan.plan.programId),
            )
        if (detail.previewMode != ProgramPreviewMode.FIXED_PROFILE_PREVIEW) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.UnsupportedPreviewMode(detail.previewMode),
            )
        }

        val timeline = when (
            val result = WorkoutTimelineCompiler.compile(detail, plan.plan.settings)
        ) {
            is WorkoutTimelineCompileResult.Valid -> result.timeline
            is WorkoutTimelineCompileResult.Invalid -> return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.TimelineCompileFailed(result.error),
            )
        }

        val notStarted = when (
            val result = WorkoutSessionStateMachine.create(timeline)
        ) {
            is WorkoutSessionResult.Valid -> result.state
            is WorkoutSessionResult.Invalid -> return failedTransition(result.error)
        }
        val running = when (val result = WorkoutSessionStateMachine.start(notStarted)) {
            is WorkoutSessionResult.Valid -> when (val state = result.state) {
                is WorkoutSessionState.Running -> state
                else -> return failedTransition(
                    WorkoutSessionError.InvalidTransition(
                        action = WorkoutSessionAction.START,
                        state = state.kind,
                    ),
                )
            }
            is WorkoutSessionResult.Invalid -> return failedTransition(result.error)
        }

        sessionState = running
        return WorkoutSessionStarterResult.Started(running)
    }

    fun advance(elapsedSeconds: Int): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.advance(state, elapsedSeconds)
    }

    fun pause(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.pause(state)
    }

    fun resume(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.resume(state)
    }

    fun stop(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.stop(state)
    }

    fun currentState(): WorkoutSessionState? = sessionState

    private fun failedTransition(error: WorkoutSessionError): WorkoutSessionStarterResult =
        WorkoutSessionStarterResult.Failed(
            WorkoutSessionStartFailure.SessionTransitionFailed(error),
        )

    private fun updateSession(
        transition: (WorkoutSessionState) -> WorkoutSessionResult,
    ): WorkoutSessionCommandResult {
        val current = sessionState
            ?: return WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.NoSession,
            )
        return when (val result = transition(current)) {
            is WorkoutSessionResult.Valid -> {
                sessionState = result.state
                WorkoutSessionCommandResult.Updated(result.state)
            }
            is WorkoutSessionResult.Invalid -> WorkoutSessionCommandResult.Failed(
                WorkoutSessionCommandFailure.Transition(result.error),
            )
        }
    }
}
