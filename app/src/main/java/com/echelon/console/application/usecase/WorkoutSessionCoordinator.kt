package com.echelon.console.application.usecase

import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SurpriseWorkoutDraft
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

    data class TimelineCompileFailed(
        val error: WorkoutTimelineCompileError,
    ) : WorkoutSessionStartFailure

    data class SessionTransitionFailed(
        val error: WorkoutSessionError,
    ) : WorkoutSessionStartFailure

    data class DraftPlanMismatch(
        val field: DraftPlanMismatchField,
    ) : WorkoutSessionStartFailure

    data object ActiveSessionExists : WorkoutSessionStartFailure
}

enum class DraftPlanMismatchField {
    PROGRAM_ID,
    DURATION,
    MAX_SPEED,
    MAX_INCLINE,
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
) : WorkoutSessionStarter, SurpriseWorkoutDraftSessionStarter, WorkoutSessionController {
    private var sessionState: WorkoutSessionState? = null

    override fun start(plan: ValidatedWorkoutPlan): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }

        val detail = catalog.findProgramDetail(plan.plan.programId)
            ?: return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(plan.plan.programId),
            )
        return startTimeline(WorkoutTimelineCompiler.compile(detail, plan.plan.settings))
    }

    override fun start(
        draft: SurpriseWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }
        draftPlanMismatch(draft, plan)?.let { return it }
        return startTimeline(
            WorkoutTimelineCompiler.compile(
                programId = draft.metadata.programId,
                profile = draft.profile,
                settings = plan.plan.settings,
            ),
        )
    }

    private fun draftPlanMismatch(
        draft: SurpriseWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult.Failed? {
        val settings = plan.plan.settings
        val mismatch = when {
            plan.plan.programId != draft.metadata.programId -> DraftPlanMismatchField.PROGRAM_ID
            settings.duration.value != draft.metadata.durationMinutes -> DraftPlanMismatchField.DURATION
            settings.maxSpeed != draft.effectiveSpeedCap -> DraftPlanMismatchField.MAX_SPEED
            settings.maxIncline != draft.effectiveInclineCap -> DraftPlanMismatchField.MAX_INCLINE
            else -> null
        }
        return mismatch?.let {
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.DraftPlanMismatch(it))
        }
    }

    private fun startTimeline(
        compiled: WorkoutTimelineCompileResult,
    ): WorkoutSessionStarterResult {
        val timeline = when (compiled) {
            is WorkoutTimelineCompileResult.Valid -> compiled.timeline
            is WorkoutTimelineCompileResult.Invalid -> return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.TimelineCompileFailed(compiled.error),
            )
        }

        val notStarted = when (val result = WorkoutSessionStateMachine.create(timeline)) {
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

    private fun activeSessionFailure(): WorkoutSessionStarterResult.Failed? = when (sessionState) {
        is WorkoutSessionState.NotStarted,
        is WorkoutSessionState.Running,
        is WorkoutSessionState.Paused,
        -> WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists)

        null,
        is WorkoutSessionState.Completed,
        is WorkoutSessionState.Stopped,
        -> null
    }

    override fun advance(elapsedSeconds: Int): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.advance(state, elapsedSeconds)
    }

    override fun pause(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.pause(state)
    }

    override fun resume(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.resume(state)
    }

    override fun stop(): WorkoutSessionCommandResult = updateSession { state ->
        WorkoutSessionStateMachine.stop(state)
    }

    override fun currentState(): WorkoutSessionState? = sessionState

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
