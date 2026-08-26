package com.echelon.console.application.usecase

import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
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
import com.echelon.console.domain.toCalorieTargetWorkoutTimelineProfile
import com.echelon.console.domain.toZone2WorkoutTimelineProfile

private const val VERTICAL_PROFILE_DURATION_MINUTES = 50
private const val CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES = 40
private val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
private val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")

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

    data class CalorieTargetPreviewProgramNotFound(
        val programId: ProgramId,
    ) : WorkoutSessionStartFailure

    data class CalorieTargetPreviewDetailMismatch(
        val expected: ProgramId,
        val actual: ProgramId,
    ) : WorkoutSessionStartFailure

    data class CalorieTargetPreviewUnsupportedDuration(
        val duration: DurationMinutes,
        val supportedDurations: List<DurationMinutes>,
    ) : WorkoutSessionStartFailure

    data class CalorieTargetPreviewProfileDurationMismatch(
        val expectedMinutes: Int,
        val actualMinutes: Long,
    ) : WorkoutSessionStartFailure

    data class CalorieTargetPreviewContextPlanMismatch(
        val field: CalorieTargetPreviewContextPlanMismatchField,
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
    INTENSITY,
    FOCUS,
    ADAPT_TO_YOU,
}

enum class CalorieTargetPreviewContextPlanMismatchField {
    PROGRAM_ID,
    DURATION,
    MAX_SPEED,
    MAX_INCLINE,
    INTENSITY,
    FOCUS,
    ADAPT_TO_YOU,
    TARGET_SOURCE,
    ESTIMATE_STATUS,
    TELEMETRY_SOURCE,
    UNIT_SEMANTICS,
    SESSION_RESET_SEMANTICS,
    COMPLETION_AUTHORITY,
    PROGRESS_SEMANTICS,
    PREVIEW_STATUS,
    DEVICE_COMMAND_STATUS,
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
    CalorieTargetPreviewSessionStarter,
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
        val settings = plan.plan.settings
        val reviewedSettingsMismatch = when {
            settings.intensity != detail.defaultSettings.intensity ->
                Zone2PreviewContextPlanMismatchField.INTENSITY

            settings.focus != detail.defaultSettings.focus ->
                Zone2PreviewContextPlanMismatchField.FOCUS

            settings.adaptToYou -> Zone2PreviewContextPlanMismatchField.ADAPT_TO_YOU
            else -> null
        }
        reviewedSettingsMismatch?.let {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(it),
            )
        }
        if (
            settings.duration !in ZONE_2_PREVIEW_SUPPORTED_DURATIONS ||
            settings.duration !in detail.supportedDurations
        ) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.UnsupportedDuration(
                    programId = ZONE_2_PROGRAM_ID,
                    duration = settings.duration,
                    supportedDurations = ZONE_2_PREVIEW_SUPPORTED_DURATIONS,
                ),
            )
        }

        val detailMaxSpeed = minOf(
            detail.defaultSettings.maxSpeed.value,
            detail.speedRange.max.value,
        )
        if (settings.maxSpeed.value > detailMaxSpeed) {
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
        if (settings.maxIncline.value > detailMaxIncline) {
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

    override fun start(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult {
        activeSessionFailure()?.let { return it }
        calorieContextPlanMismatch(context, plan)?.let { field ->
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
            )
        }

        val detail = catalog.findProgramDetail(CALORIE_TARGET_PROGRAM_ID)
            ?: return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewProgramNotFound(
                    CALORIE_TARGET_PROGRAM_ID,
                ),
            )
        if (detail.programId != CALORIE_TARGET_PROGRAM_ID) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewDetailMismatch(
                    expected = CALORIE_TARGET_PROGRAM_ID,
                    actual = detail.programId,
                ),
            )
        }
        val representativeDuration = DurationMinutes(
            CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES,
        )
        if (
            detail.defaultSettings.duration != representativeDuration ||
            representativeDuration !in detail.supportedDurations
        ) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewUnsupportedDuration(
                    duration = representativeDuration,
                    supportedDurations = detail.supportedDurations,
                ),
            )
        }

        val profileDurationMinutes = detail.profile.sumOf { it.duration.value.toLong() }
        if (
            profileDurationMinutes != CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES.toLong() ||
            detail.profile.any { it.duration.value <= 0 }
        ) {
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewProfileDurationMismatch(
                    expectedMinutes = CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES,
                    actualMinutes = profileDurationMinutes,
                ),
            )
        }

        val settings = plan.plan.settings
        val reviewedSettingsMismatch = when {
            settings.intensity != detail.defaultSettings.intensity ->
                CalorieTargetPreviewContextPlanMismatchField.INTENSITY

            settings.focus != detail.defaultSettings.focus ->
                CalorieTargetPreviewContextPlanMismatchField.FOCUS

            settings.adaptToYou -> CalorieTargetPreviewContextPlanMismatchField.ADAPT_TO_YOU
            settings.maxSpeed.value > minOf(
                detail.defaultSettings.maxSpeed.value,
                detail.speedRange.max.value,
            ) -> CalorieTargetPreviewContextPlanMismatchField.MAX_SPEED

            settings.maxIncline.value > minOf(
                detail.defaultSettings.maxIncline.value,
                detail.inclineRange.max.value,
            ) -> CalorieTargetPreviewContextPlanMismatchField.MAX_INCLINE

            else -> null
        }
        reviewedSettingsMismatch?.let { field ->
            return WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
            )
        }

        return startTimeline(
            WorkoutTimelineCompiler.compile(
                programId = CALORIE_TARGET_PROGRAM_ID,
                profile = detail.toCalorieTargetWorkoutTimelineProfile(context),
                settings = settings,
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

    private fun calorieContextPlanMismatch(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        plan: ValidatedWorkoutPlan,
    ): CalorieTargetPreviewContextPlanMismatchField? {
        val settings = plan.plan.settings
        val representativeDuration = DurationMinutes(
            CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES,
        )
        val mismatch = when {
            context.programId != CALORIE_TARGET_PROGRAM_ID ||
                plan.plan.programId != CALORIE_TARGET_PROGRAM_ID ->
                CalorieTargetPreviewContextPlanMismatchField.PROGRAM_ID

            context.representativeProfileDuration != representativeDuration ||
                settings.duration != representativeDuration ||
                context.representativeProfileDuration != settings.duration ->
                CalorieTargetPreviewContextPlanMismatchField.DURATION

            context.effectiveMaxSpeed != settings.maxSpeed ->
                CalorieTargetPreviewContextPlanMismatchField.MAX_SPEED

            context.effectiveMaxIncline != settings.maxIncline ->
                CalorieTargetPreviewContextPlanMismatchField.MAX_INCLINE

            else -> null
        }
        return mismatch ?: calorieContextMetadataMismatch(context)
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun calorieContextMetadataMismatch(
        context: WorkoutTimelineContext.CalorieTargetPreview,
    ): CalorieTargetPreviewContextPlanMismatchField? {
        val targetSourceMismatch = when (context.target.source) {
            CalorieTargetSource.USER_SELECTED -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.TARGET_SOURCE
        }
        val estimateStatusMismatch = when (context.estimateStatus) {
            CalorieEstimateStatus.ESTIMATED -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.ESTIMATE_STATUS
        }
        val telemetrySourceMismatch = when (context.source) {
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.TELEMETRY_SOURCE
        }
        val unitSemanticsMismatch = when (context.unitSemantics) {
            CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.UNIT_SEMANTICS
        }
        val sessionResetSemanticsMismatch = when (context.sessionResetSemantics) {
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.SESSION_RESET_SEMANTICS
        }
        val completionAuthorityMismatch = when (context.completionAuthority) {
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.COMPLETION_AUTHORITY
        }
        val progressSemanticsMismatch = when (context.progressSemantics) {
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.PROGRESS_SEMANTICS
        }
        val previewStatusMismatch = when (context.previewStatus) {
            CaloriePreviewStatus.PREVIEW_ONLY -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.PREVIEW_STATUS
        }
        val deviceCommandStatusMismatch = when (context.deviceCommandStatus) {
            CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS -> null
            else -> CalorieTargetPreviewContextPlanMismatchField.DEVICE_COMMAND_STATUS
        }
        return targetSourceMismatch
            ?: estimateStatusMismatch
            ?: telemetrySourceMismatch
            ?: unitSemanticsMismatch
            ?: sessionResetSemanticsMismatch
            ?: completionAuthorityMismatch
            ?: progressSemanticsMismatch
            ?: previewStatusMismatch
            ?: deviceCommandStatusMismatch
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
