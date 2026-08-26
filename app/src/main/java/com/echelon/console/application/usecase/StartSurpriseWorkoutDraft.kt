package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutDraftControlStatus
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState

class StartSurpriseWorkoutDraft(
    private val sessionStarter: WorkoutDraftSessionStarter,
) {
    operator fun invoke(
        draft: SurpriseWorkoutDraft,
        capabilities: DeviceCapabilities,
    ): StartSurpriseWorkoutDraftResult {
        val draftFailure = validateDraft(draft)
        if (draftFailure != null) {
            return StartSurpriseWorkoutDraftResult.InvalidDraft(draftFailure)
        }

        val plan = WorkoutPlan(
            programId = draft.metadata.programId,
            settings = PlanSettings(
                duration = DurationMinutes(draft.metadata.durationMinutes),
                intensity = draft.metadata.effort.toPlanIntensity(),
                focus = PlanFocus.BALANCED,
                maxSpeed = draft.effectiveSpeedCap,
                maxIncline = draft.effectiveInclineCap,
                adaptToYou = false,
            ),
        )
        return when (val validation = ValidatedWorkoutPlan.create(plan, capabilities)) {
            is ValidatedWorkoutPlanResult.Invalid ->
                StartSurpriseWorkoutDraftResult.CapabilityValidationFailed(validation.errors)

            is ValidatedWorkoutPlanResult.Valid -> when (
                val result = sessionStarter.start(draft, validation.plan)
            ) {
                is WorkoutSessionStarterResult.Started ->
                    StartSurpriseWorkoutDraftResult.Started(result.state)

                is WorkoutSessionStarterResult.Failed ->
                    StartSurpriseWorkoutDraftResult.StarterFailed(result.failure)
            }
        }
    }

    private fun validateDraft(
        draft: SurpriseWorkoutDraft,
    ): SurpriseWorkoutDraftValidationFailure? {
        if (draft.metadata.programId != SURPRISE_ME_PROGRAM_ID) {
            return SurpriseWorkoutDraftValidationFailure.ProgramIdMismatch(draft.metadata.programId)
        }
        if (draft.controlStatus != SurpriseWorkoutDraftControlStatus.PREVIEW_ONLY) {
            return SurpriseWorkoutDraftValidationFailure.NotPreviewOnly
        }
        if (draft.metadata.durationMinutes <= 0) {
            return SurpriseWorkoutDraftValidationFailure.NonPositiveMetadataDuration(
                draft.metadata.durationMinutes,
            )
        }
        if (draft.effectiveSpeedCap.value < 0 || draft.effectiveInclineCap.value < 0) {
            return SurpriseWorkoutDraftValidationFailure.InvalidEffectiveCap(
                speedCap = draft.effectiveSpeedCap,
                inclineCap = draft.effectiveInclineCap,
            )
        }

        draft.profile.forEachIndexed { index, segment ->
            if (segment.duration.value <= 0) {
                return SurpriseWorkoutDraftValidationFailure.NonPositiveProfileDuration(
                    segmentIndex = index,
                    durationMinutes = segment.duration.value,
                )
            }
            if (
                segment.speed.value > draft.effectiveSpeedCap.value ||
                segment.incline.value > draft.effectiveInclineCap.value
            ) {
                return SurpriseWorkoutDraftValidationFailure.TargetExceedsEffectiveCap(
                    segmentIndex = index,
                    speed = segment.speed,
                    incline = segment.incline,
                    maxSpeed = draft.effectiveSpeedCap,
                    maxIncline = draft.effectiveInclineCap,
                )
            }
        }

        val totalProfileMinutes = draft.profile.sumOf { it.duration.value.toLong() }
        return if (totalProfileMinutes != draft.metadata.durationMinutes.toLong()) {
            SurpriseWorkoutDraftValidationFailure.ProfileDurationMismatch(
                metadataDurationMinutes = draft.metadata.durationMinutes,
                profileDurationMinutes = totalProfileMinutes,
            )
        } else {
            null
        }
    }

    private fun SurpriseWorkoutEffort.toPlanIntensity(): PlanIntensity = when (this) {
        SurpriseWorkoutEffort.EASY -> PlanIntensity.LOW
        SurpriseWorkoutEffort.SWEAT -> PlanIntensity.MEDIUM
        SurpriseWorkoutEffort.BURN,
        SurpriseWorkoutEffort.HARD,
        -> PlanIntensity.HIGH
    }

    private companion object {
        val SURPRISE_ME_PROGRAM_ID = ProgramId("SURPRISE_ME")
    }
}

sealed interface StartSurpriseWorkoutDraftResult {
    data class Started(
        val state: WorkoutSessionState.Running,
    ) : StartSurpriseWorkoutDraftResult

    data class InvalidDraft(
        val failure: SurpriseWorkoutDraftValidationFailure,
    ) : StartSurpriseWorkoutDraftResult

    data class CapabilityValidationFailed(
        val errors: List<PlanValidationError>,
    ) : StartSurpriseWorkoutDraftResult

    data class StarterFailed(
        val failure: WorkoutSessionStartFailure,
    ) : StartSurpriseWorkoutDraftResult
}

sealed interface SurpriseWorkoutDraftValidationFailure {
    data class ProgramIdMismatch(
        val actual: ProgramId,
    ) : SurpriseWorkoutDraftValidationFailure

    data object NotPreviewOnly : SurpriseWorkoutDraftValidationFailure

    data class NonPositiveMetadataDuration(
        val durationMinutes: Int,
    ) : SurpriseWorkoutDraftValidationFailure

    data class InvalidEffectiveCap(
        val speedCap: SpeedTenths,
        val inclineCap: InclineTenths,
    ) : SurpriseWorkoutDraftValidationFailure

    data class NonPositiveProfileDuration(
        val segmentIndex: Int,
        val durationMinutes: Int,
    ) : SurpriseWorkoutDraftValidationFailure

    data class TargetExceedsEffectiveCap(
        val segmentIndex: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
        val maxSpeed: SpeedTenths,
        val maxIncline: InclineTenths,
    ) : SurpriseWorkoutDraftValidationFailure

    data class ProfileDurationMismatch(
        val metadataDurationMinutes: Int,
        val profileDurationMinutes: Long,
    ) : SurpriseWorkoutDraftValidationFailure
}
