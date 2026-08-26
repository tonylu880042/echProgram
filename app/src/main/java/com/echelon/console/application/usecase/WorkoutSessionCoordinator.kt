package com.echelon.console.application.usecase

import com.echelon.console.domain.FiveKReadySessionDraft
import com.echelon.console.domain.AnnotatedWorkoutProfile
import com.echelon.console.domain.AnnotatedWorkoutProfileSegment
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.VerticalWorkoutDraft
import com.echelon.console.domain.WorkoutSessionAction
import com.echelon.console.domain.WorkoutSessionError
import com.echelon.console.domain.WorkoutSessionResult
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionStateMachine
import com.echelon.console.domain.WorkoutTimelineCompileError
import com.echelon.console.domain.WorkoutTimelineCompileResult
import com.echelon.console.domain.WorkoutTimelineCompiler
import com.echelon.console.domain.WorkoutTimelineAnnotation
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.toWorkoutTimelineProfile
import com.echelon.console.domain.toZone2WorkoutTimelineProfile

private const val VERTICAL_PROFILE_DURATION_MINUTES = 50
private val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")

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

    data class UnsupportedDuration(
        val programId: ProgramId,
        val duration: DurationMinutes,
        val supportedDurations: List<DurationMinutes>,
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

    data class Zone2PreviewContextPlanMismatch(
        val field: Zone2PreviewContextPlanMismatchField,
    ) : WorkoutSessionStartFailure

    data object ActiveSessionExists : WorkoutSessionStartFailure
}

enum class DraftPlanMismatchField {
    PROGRAM_ID,
    DURATION,
    MAX_SPEED,
    MAX_INCLINE,
}

enum class Zone2PreviewContextPlanMismatchField {
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
) : WorkoutSessionStarter,
    SurpriseWorkoutDraftSessionStarter,
    FiveKReadySessionDraftSessionStarter,
    VerticalWorkoutDraftSessionStarter,
    Zone2WorkoutPreviewSessionStarter,
    WorkoutSessionController {
    private var sessionState: WorkoutSessionState? = null

    override fun start(plan: ValidatedWorkoutPlan): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }

        val detail = catalog.findProgramDetail(plan.plan.programId)
            ?: return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(plan.plan.programId),
            )
        if (plan.plan.settings.duration !in detail.supportedDurations) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.UnsupportedDuration(
                    programId = detail.programId,
                    duration = plan.plan.settings.duration,
                    supportedDurations = detail.supportedDurations,
                ),
            )
        }
        return startTimeline(WorkoutTimelineCompiler.compile(detail, plan.plan.settings))
    }

    override fun start(
        draft: SurpriseWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult = startDraft(
        draftProgramId = draft.metadata.programId,
        draftDurationMinutes = draft.metadata.durationMinutes,
        draftMaxSpeed = draft.effectiveSpeedCap,
        draftMaxIncline = draft.effectiveInclineCap,
        profile = unannotatedProfile(draft.metadata.programId, draft.profile),
        plan = plan,
    )

    override fun start(
        draft: FiveKReadySessionDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult = startDraft(
        draftProgramId = draft.metadata.programId,
        draftDurationMinutes = draft.metadata.durationMinutes,
        draftMaxSpeed = draft.effectiveSpeedCap,
        draftMaxIncline = draft.effectiveInclineCap,
        profile = draft.toWorkoutTimelineProfile(),
        plan = plan,
    )

    override fun start(
        draft: VerticalWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult = startDraft(
        draftProgramId = draft.metadata.programId,
        draftDurationMinutes = VERTICAL_PROFILE_DURATION_MINUTES,
        draftMaxSpeed = draft.metadata.effectiveSpeedCap,
        draftMaxIncline = draft.metadata.effectiveInclineCap,
        profile = draft.toWorkoutTimelineProfile(),
        plan = plan,
    )

    override fun start(
        context: WorkoutTimelineContext.Zone2Preview,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }
        zone2ContextPlanMismatch(context, plan)?.let { return it }

        val detail = catalog.findProgramDetail(ZONE_2_PROGRAM_ID)
            ?.takeIf { it.programId == ZONE_2_PROGRAM_ID }
            ?: return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(ZONE_2_PROGRAM_ID),
            )
        if (
            plan.plan.settings.duration !in ZONE_2_PREVIEW_SUPPORTED_DURATIONS ||
            plan.plan.settings.duration !in detail.supportedDurations
        ) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.UnsupportedDuration(
                    programId = ZONE_2_PROGRAM_ID,
                    duration = plan.plan.settings.duration,
                    supportedDurations = ZONE_2_PREVIEW_SUPPORTED_DURATIONS,
                ),
            )
        }

        val detailMaxSpeed = minOf(
            detail.defaultSettings.maxSpeed.value,
            detail.speedRange.max.value,
        )
        if (plan.plan.settings.maxSpeed.value > detailMaxSpeed) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(
                    Zone2PreviewContextPlanMismatchField.MAX_SPEED,
                ),
            )
        }
        val detailMaxIncline = minOf(
            detail.defaultSettings.maxIncline.value,
            detail.inclineRange.max.value,
        )
        if (plan.plan.settings.maxIncline.value > detailMaxIncline) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(
                    Zone2PreviewContextPlanMismatchField.MAX_INCLINE,
                ),
            )
        }

        return startTimeline(
            WorkoutTimelineCompiler.compile(
                programId = ZONE_2_PROGRAM_ID,
                profile = detail.toZone2WorkoutTimelineProfile(context),
                settings = plan.plan.settings,
            ),
        )
    }

    private fun startDraft(
        draftProgramId: ProgramId,
        draftDurationMinutes: Int,
        draftMaxSpeed: SpeedTenths,
        draftMaxIncline: InclineTenths,
        profile: AnnotatedWorkoutProfile,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }
        draftPlanMismatch(
            draftProgramId = draftProgramId,
            draftDurationMinutes = draftDurationMinutes,
            draftMaxSpeed = draftMaxSpeed,
            draftMaxIncline = draftMaxIncline,
            plan = plan,
        )?.let { return it }
        return startTimeline(
            WorkoutTimelineCompiler.compile(
                programId = draftProgramId,
                profile = profile,
                settings = plan.plan.settings,
            ),
        )
    }

    private fun draftPlanMismatch(
        draftProgramId: ProgramId,
        draftDurationMinutes: Int,
        draftMaxSpeed: SpeedTenths,
        draftMaxIncline: InclineTenths,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult.Failed? {
        val settings = plan.plan.settings
        val mismatch = when {
            plan.plan.programId != draftProgramId -> DraftPlanMismatchField.PROGRAM_ID
            settings.duration.value != draftDurationMinutes -> DraftPlanMismatchField.DURATION
            settings.maxSpeed != draftMaxSpeed -> DraftPlanMismatchField.MAX_SPEED
            settings.maxIncline != draftMaxIncline -> DraftPlanMismatchField.MAX_INCLINE
            else -> null
        }
        return mismatch?.let {
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.DraftPlanMismatch(it))
        }
    }

    private fun zone2ContextPlanMismatch(
        context: WorkoutTimelineContext.Zone2Preview,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult.Failed? {
        val settings = plan.plan.settings
        val mismatch = when {
            context.programId != ZONE_2_PROGRAM_ID || plan.plan.programId != ZONE_2_PROGRAM_ID ->
                Zone2PreviewContextPlanMismatchField.PROGRAM_ID

            context.duration != settings.duration ->
                Zone2PreviewContextPlanMismatchField.DURATION

            context.effectiveMaxSpeed != settings.maxSpeed ->
                Zone2PreviewContextPlanMismatchField.MAX_SPEED

            context.effectiveMaxIncline != settings.maxIncline ->
                Zone2PreviewContextPlanMismatchField.MAX_INCLINE

            else -> null
        }
        return mismatch?.let {
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(it),
            )
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

    private fun unannotatedProfile(
        programId: ProgramId,
        profile: List<ProgramSegmentSummary>,
    ): AnnotatedWorkoutProfile = AnnotatedWorkoutProfile(
        programId = programId,
        segments = profile.map { summary ->
            AnnotatedWorkoutProfileSegment(
                summary = summary,
                annotation = WorkoutTimelineAnnotation.Unannotated,
            )
        },
    )

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
