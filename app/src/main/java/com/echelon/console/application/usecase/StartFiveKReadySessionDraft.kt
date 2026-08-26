package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadyDraftMode
import com.echelon.console.domain.FiveKReadySessionControlStatus
import com.echelon.console.domain.FiveKReadySessionDraft
import com.echelon.console.domain.FiveKReadySessionGenerationFailure
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.FiveKReadySessionGenerator
import com.echelon.console.domain.FiveKReadySessionGeneratorInput
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState

/**
 * Accepts a deterministic 5K READY preview draft and starts that exact
 * profile. Generation and acceptance remain separate; this use case does not
 * send device commands.
 */
class StartFiveKReadySessionDraft(
    private val sessionStarter: FiveKReadySessionDraftSessionStarter,
    private val generator: FiveKReadySessionGenerator = FiveKReadySessionGenerator(),
) {
    operator fun invoke(
        draft: FiveKReadySessionDraft,
        capabilities: DeviceCapabilities,
    ): StartFiveKReadySessionDraftResult {
        val draftFailure = validateDraft(draft)
        if (draftFailure != null) {
            return StartFiveKReadySessionDraftResult.InvalidDraft(draftFailure)
        }

        val plan = WorkoutPlan(
            programId = draft.metadata.programId,
            settings = PlanSettings(
                duration = DurationMinutes(draft.metadata.durationMinutes),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = draft.effectiveSpeedCap,
                maxIncline = draft.effectiveInclineCap,
                adaptToYou = false,
            ),
        )
        return when (val validation = ValidatedWorkoutPlan.create(plan, capabilities)) {
            is ValidatedWorkoutPlanResult.Invalid ->
                StartFiveKReadySessionDraftResult.CapabilityValidationFailed(validation.errors)

            is ValidatedWorkoutPlanResult.Valid -> when (
                val result = sessionStarter.start(draft, validation.plan)
            ) {
                is WorkoutSessionStarterResult.Started ->
                    StartFiveKReadySessionDraftResult.Started(
                        state = result.state,
                        plan = validation.plan,
                    )

                is WorkoutSessionStarterResult.Failed ->
                    StartFiveKReadySessionDraftResult.StarterFailed(result.failure)
            }
        }
    }

    private fun validateDraft(
        draft: FiveKReadySessionDraft,
    ): FiveKReadySessionDraftValidationFailure? {
        if (draft.metadata.programId != FIVE_K_READY_PROGRAM_ID) {
            return FiveKReadySessionDraftValidationFailure.ProgramIdMismatch(draft.metadata.programId)
        }
        if (draft.metadata.mode != FiveKReadyDraftMode.SINGLE_SESSION_PREVIEW) {
            return FiveKReadySessionDraftValidationFailure.UnsupportedDraftMode(draft.metadata.mode)
        }
        if (draft.controlStatus != FiveKReadySessionControlStatus.PREVIEW_ONLY) {
            return FiveKReadySessionDraftValidationFailure.NotPreviewOnly
        }
        if (draft.metadata.durationMinutes <= 0) {
            return FiveKReadySessionDraftValidationFailure.NonPositiveMetadataDuration(
                draft.metadata.durationMinutes,
            )
        }
        if (draft.metadata.baselinePace.source != FiveKReadyBaselineSource.USER_ENTERED) {
            return FiveKReadySessionDraftValidationFailure.BaselineSourceNotUserEntered(
                draft.metadata.baselinePace.source,
            )
        }
        if (
            draft.effectiveSpeedCap.value < 0 ||
            draft.effectiveInclineCap.value < 0
        ) {
            return FiveKReadySessionDraftValidationFailure.InvalidEffectiveCap(
                speedCap = draft.effectiveSpeedCap,
                inclineCap = draft.effectiveInclineCap,
            )
        }

        draft.profile.forEachIndexed { index, segment ->
            if (segment.duration.value <= 0) {
                return FiveKReadySessionDraftValidationFailure.NonPositiveProfileDuration(
                    segmentIndex = index,
                    durationMinutes = segment.duration.value,
                )
            }
            if (segment.speed.value < 0 || segment.incline.value < 0) {
                return FiveKReadySessionDraftValidationFailure.InvalidProfileTarget(
                    segmentIndex = index,
                    speed = segment.speed,
                    incline = segment.incline,
                )
            }
            if (
                segment.speed.value > draft.effectiveSpeedCap.value ||
                segment.incline.value > draft.effectiveInclineCap.value
            ) {
                return FiveKReadySessionDraftValidationFailure.TargetExceedsEffectiveCap(
                    segmentIndex = index,
                    speed = segment.speed,
                    incline = segment.incline,
                    maxSpeed = draft.effectiveSpeedCap,
                    maxIncline = draft.effectiveInclineCap,
                )
            }
        }

        val totalProfileMinutes = draft.profile.sumOf { it.duration.value.toLong() }
        if (totalProfileMinutes != draft.metadata.durationMinutes.toLong()) {
            return FiveKReadySessionDraftValidationFailure.ProfileDurationMismatch(
                metadataDurationMinutes = draft.metadata.durationMinutes,
                profileDurationMinutes = totalProfileMinutes,
            )
        }
        if (
            draft.runWalkSummary.runMinutes < 0 ||
            draft.runWalkSummary.walkMinutes < 0 ||
            draft.runWalkSummary.runMinutes + draft.runWalkSummary.walkMinutes !=
            draft.metadata.durationMinutes
        ) {
            return FiveKReadySessionDraftValidationFailure.InvalidRunWalkSummary
        }

        return validateReplay(draft)
    }

    private fun validateReplay(
        draft: FiveKReadySessionDraft,
    ): FiveKReadySessionDraftValidationFailure? {
        val input = FiveKReadySessionGeneratorInput(
            durationMinutes = draft.metadata.durationMinutes,
            baselinePace = draft.metadata.baselinePace,
            userMaxSpeed = draft.metadata.userMaxSpeed,
            machineMaxSpeed = draft.metadata.machineMaxSpeed,
            userMaxIncline = draft.metadata.userMaxIncline,
            machineMaxIncline = draft.metadata.machineMaxIncline,
        )
        return when (val result = generator.generate(input)) {
            is FiveKReadySessionGenerationResult.Rejected ->
                FiveKReadySessionDraftValidationFailure.ReplayGenerationRejected(result.failure)

            is FiveKReadySessionGenerationResult.Generated ->
                if (result.draft == draft) {
                    null
                } else {
                    FiveKReadySessionDraftValidationFailure.ReplayMismatch
                }
        }
    }

    private companion object {
        val FIVE_K_READY_PROGRAM_ID = ProgramId("5K_READY")
    }
}

sealed interface StartFiveKReadySessionDraftResult {
    data class Started(
        val state: WorkoutSessionState.Running,
        val plan: ValidatedWorkoutPlan,
    ) : StartFiveKReadySessionDraftResult

    data class InvalidDraft(
        val failure: FiveKReadySessionDraftValidationFailure,
    ) : StartFiveKReadySessionDraftResult

    data class CapabilityValidationFailed(
        val errors: List<PlanValidationError>,
    ) : StartFiveKReadySessionDraftResult

    data class StarterFailed(
        val failure: WorkoutSessionStartFailure,
    ) : StartFiveKReadySessionDraftResult
}

sealed interface FiveKReadySessionDraftValidationFailure {
    data class ProgramIdMismatch(
        val actual: ProgramId,
    ) : FiveKReadySessionDraftValidationFailure

    data class UnsupportedDraftMode(
        val actual: FiveKReadyDraftMode,
    ) : FiveKReadySessionDraftValidationFailure

    data object NotPreviewOnly : FiveKReadySessionDraftValidationFailure

    data class NonPositiveMetadataDuration(
        val durationMinutes: Int,
    ) : FiveKReadySessionDraftValidationFailure

    data class BaselineSourceNotUserEntered(
        val source: FiveKReadyBaselineSource,
    ) : FiveKReadySessionDraftValidationFailure

    data class InvalidEffectiveCap(
        val speedCap: SpeedTenths,
        val inclineCap: InclineTenths,
    ) : FiveKReadySessionDraftValidationFailure

    data class NonPositiveProfileDuration(
        val segmentIndex: Int,
        val durationMinutes: Int,
    ) : FiveKReadySessionDraftValidationFailure

    data class InvalidProfileTarget(
        val segmentIndex: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
    ) : FiveKReadySessionDraftValidationFailure

    data class TargetExceedsEffectiveCap(
        val segmentIndex: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
        val maxSpeed: SpeedTenths,
        val maxIncline: InclineTenths,
    ) : FiveKReadySessionDraftValidationFailure

    data class ProfileDurationMismatch(
        val metadataDurationMinutes: Int,
        val profileDurationMinutes: Long,
    ) : FiveKReadySessionDraftValidationFailure

    data object InvalidRunWalkSummary : FiveKReadySessionDraftValidationFailure

    data class ReplayGenerationRejected(
        val failure: FiveKReadySessionGenerationFailure,
    ) : FiveKReadySessionDraftValidationFailure

    data object ReplayMismatch : FiveKReadySessionDraftValidationFailure
}
