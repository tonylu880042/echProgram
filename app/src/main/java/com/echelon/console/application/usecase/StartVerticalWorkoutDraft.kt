package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalWorkoutDraft
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.VerticalWorkoutDraftMode
import com.echelon.console.domain.VerticalWorkoutGenerationFailure
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import com.echelon.console.domain.VerticalWorkoutGenerator
import com.echelon.console.domain.VerticalWorkoutGeneratorInput
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState

/**
 * Explicitly accepts a deterministic VERTICAL representative preview draft.
 * Acceptance starts the exact profile in memory and sends no device command.
 */
class StartVerticalWorkoutDraft(
    private val sessionStarter: VerticalWorkoutDraftSessionStarter,
    private val generator: VerticalWorkoutGenerator = VerticalWorkoutGenerator(),
) {
    operator fun invoke(
        draft: VerticalWorkoutDraft,
        capabilities: DeviceCapabilities,
    ): StartVerticalWorkoutDraftResult {
        val draftFailure = validateDraft(draft)
        if (draftFailure != null) {
            return StartVerticalWorkoutDraftResult.InvalidDraft(draftFailure)
        }

        val plan = WorkoutPlan(
            programId = draft.metadata.programId,
            settings = PlanSettings(
                duration = DurationMinutes(VERTICAL_PROFILE_DURATION_MINUTES),
                intensity = PlanIntensity.HIGH,
                focus = PlanFocus.MORE_INCLINE,
                maxSpeed = draft.metadata.effectiveSpeedCap,
                maxIncline = draft.metadata.effectiveInclineCap,
                adaptToYou = false,
            ),
        )
        return when (val validation = ValidatedWorkoutPlan.create(plan, capabilities)) {
            is ValidatedWorkoutPlanResult.Invalid ->
                StartVerticalWorkoutDraftResult.CapabilityValidationFailed(validation.errors)

            is ValidatedWorkoutPlanResult.Valid -> when (
                val result = sessionStarter.start(draft, validation.plan)
            ) {
                is WorkoutSessionStarterResult.Started ->
                    StartVerticalWorkoutDraftResult.Started(
                        state = result.state,
                        plan = validation.plan,
                    )

                is WorkoutSessionStarterResult.Failed ->
                    StartVerticalWorkoutDraftResult.StarterFailed(result.failure)
            }
        }
    }

    private fun validateDraft(
        draft: VerticalWorkoutDraft,
    ): VerticalWorkoutDraftValidationFailure? {
        val metadata = draft.metadata
        if (metadata.programId != VERTICAL_PROGRAM_ID) {
            return VerticalWorkoutDraftValidationFailure.ProgramIdMismatch(metadata.programId)
        }
        if (metadata.mode != VerticalWorkoutDraftMode.REPRESENTATIVE_PROFILE_PREVIEW) {
            return VerticalWorkoutDraftValidationFailure.UnsupportedDraftMode(metadata.mode)
        }
        if (draft.controlStatus != VerticalWorkoutDraftControlStatus.PREVIEW_ONLY) {
            return VerticalWorkoutDraftValidationFailure.NotPreviewOnly
        }
        if (metadata.elevationSource != VerticalElevationSource.UNAVAILABLE) {
            return VerticalWorkoutDraftValidationFailure.ElevationSourceMismatch(metadata.elevationSource)
        }
        if (metadata.progressStatus != VerticalProgressStatus.NOT_CALCULATED) {
            return VerticalWorkoutDraftValidationFailure.ProgressStatusMismatch(metadata.progressStatus)
        }
        if (
            metadata.userMaxSpeed.value < 0 ||
            metadata.machineMaxSpeed.value < 0 ||
            metadata.userMaxIncline.value < 0 ||
            metadata.machineMaxIncline.value < 0 ||
            metadata.effectiveSpeedCap.value < 0 ||
            metadata.effectiveInclineCap.value < 0
        ) {
            return VerticalWorkoutDraftValidationFailure.InvalidCaps(
                userMaxSpeed = metadata.userMaxSpeed,
                machineMaxSpeed = metadata.machineMaxSpeed,
                userMaxIncline = metadata.userMaxIncline,
                machineMaxIncline = metadata.machineMaxIncline,
                effectiveSpeedCap = metadata.effectiveSpeedCap,
                effectiveInclineCap = metadata.effectiveInclineCap,
            )
        }
        if (
            metadata.effectiveSpeedCap.value >
            minOf(metadata.userMaxSpeed.value, metadata.machineMaxSpeed.value) ||
            metadata.effectiveInclineCap.value >
            minOf(metadata.userMaxIncline.value, metadata.machineMaxIncline.value)
        ) {
            return VerticalWorkoutDraftValidationFailure.EffectiveCapMismatch
        }

        draft.segments.forEachIndexed { index, segment ->
            if (segment.summary.duration.value <= 0) {
                return VerticalWorkoutDraftValidationFailure.NonPositiveProfileDuration(
                    segmentIndex = index,
                    durationMinutes = segment.summary.duration.value,
                )
            }
            if (
                segment.summary.speed.value < 0 ||
                segment.summary.incline.value < 0
            ) {
                return VerticalWorkoutDraftValidationFailure.InvalidProfileTarget(
                    segmentIndex = index,
                    speed = segment.summary.speed,
                    incline = segment.summary.incline,
                )
            }
            if (
                segment.summary.speed.value > metadata.effectiveSpeedCap.value ||
                segment.summary.incline.value > metadata.effectiveInclineCap.value
            ) {
                return VerticalWorkoutDraftValidationFailure.TargetExceedsEffectiveCap(
                    segmentIndex = index,
                    speed = segment.summary.speed,
                    incline = segment.summary.incline,
                    maxSpeed = metadata.effectiveSpeedCap,
                    maxIncline = metadata.effectiveInclineCap,
                )
            }
        }

        val totalProfileMinutes = draft.profile.sumOf { it.duration.value.toLong() }
        if (totalProfileMinutes != VERTICAL_PROFILE_DURATION_MINUTES.toLong()) {
            return VerticalWorkoutDraftValidationFailure.ProfileDurationMismatch(
                expectedMinutes = VERTICAL_PROFILE_DURATION_MINUTES,
                actualMinutes = totalProfileMinutes,
            )
        }
        return validateReplay(draft)
    }

    private fun validateReplay(
        draft: VerticalWorkoutDraft,
    ): VerticalWorkoutDraftValidationFailure? {
        val metadata = draft.metadata
        val input = VerticalWorkoutGeneratorInput(
            target = metadata.target,
            userMaxSpeed = metadata.userMaxSpeed,
            machineMaxSpeed = metadata.machineMaxSpeed,
            userMaxIncline = metadata.userMaxIncline,
            machineMaxIncline = metadata.machineMaxIncline,
        )
        return when (val result = generator.generate(input)) {
            is VerticalWorkoutGenerationResult.Rejected ->
                VerticalWorkoutDraftValidationFailure.ReplayGenerationRejected(result.failure)

            is VerticalWorkoutGenerationResult.Generated ->
                if (result.draft == draft) {
                    null
                } else {
                    VerticalWorkoutDraftValidationFailure.ReplayMismatch
                }
        }
    }

    private companion object {
        val VERTICAL_PROGRAM_ID = ProgramId("VERTICAL")
        const val VERTICAL_PROFILE_DURATION_MINUTES = 50
    }
}

sealed interface StartVerticalWorkoutDraftResult {
    data class Started(
        val state: WorkoutSessionState.Running,
        val plan: ValidatedWorkoutPlan,
    ) : StartVerticalWorkoutDraftResult

    data class InvalidDraft(
        val failure: VerticalWorkoutDraftValidationFailure,
    ) : StartVerticalWorkoutDraftResult

    data class CapabilityValidationFailed(
        val errors: List<PlanValidationError>,
    ) : StartVerticalWorkoutDraftResult

    data class StarterFailed(
        val failure: WorkoutSessionStartFailure,
    ) : StartVerticalWorkoutDraftResult
}

sealed interface VerticalWorkoutDraftValidationFailure {
    data class ProgramIdMismatch(
        val actual: ProgramId,
    ) : VerticalWorkoutDraftValidationFailure

    data class UnsupportedDraftMode(
        val actual: VerticalWorkoutDraftMode,
    ) : VerticalWorkoutDraftValidationFailure

    data object NotPreviewOnly : VerticalWorkoutDraftValidationFailure

    data class ElevationSourceMismatch(
        val actual: VerticalElevationSource,
    ) : VerticalWorkoutDraftValidationFailure

    data class ProgressStatusMismatch(
        val actual: VerticalProgressStatus,
    ) : VerticalWorkoutDraftValidationFailure

    data class InvalidCaps(
        val userMaxSpeed: SpeedTenths,
        val machineMaxSpeed: SpeedTenths,
        val userMaxIncline: InclineTenths,
        val machineMaxIncline: InclineTenths,
        val effectiveSpeedCap: SpeedTenths,
        val effectiveInclineCap: InclineTenths,
    ) : VerticalWorkoutDraftValidationFailure

    data object EffectiveCapMismatch : VerticalWorkoutDraftValidationFailure

    data class NonPositiveProfileDuration(
        val segmentIndex: Int,
        val durationMinutes: Int,
    ) : VerticalWorkoutDraftValidationFailure

    data class InvalidProfileTarget(
        val segmentIndex: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
    ) : VerticalWorkoutDraftValidationFailure

    data class TargetExceedsEffectiveCap(
        val segmentIndex: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
        val maxSpeed: SpeedTenths,
        val maxIncline: InclineTenths,
    ) : VerticalWorkoutDraftValidationFailure

    data class ProfileDurationMismatch(
        val expectedMinutes: Int,
        val actualMinutes: Long,
    ) : VerticalWorkoutDraftValidationFailure

    data class ReplayGenerationRejected(
        val failure: VerticalWorkoutGenerationFailure,
    ) : VerticalWorkoutDraftValidationFailure

    data object ReplayMismatch : VerticalWorkoutDraftValidationFailure
}
