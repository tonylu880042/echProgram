package com.echelon.console.application.usecase

import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineContext

private val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")
private val CALORIE_TARGET_REPRESENTATIVE_DURATION = DurationMinutes(40)

data class StartCalorieTargetPreviewRequest(
    val target: CalorieTargetSelection,
    val capabilities: DeviceCapabilities,
)

/**
 * Builds and starts the reviewed 40-minute CALORIE TARGET representative
 * profile. Calorie telemetry remains a later read-only increment.
 */
class StartCalorieTargetPreview(
    private val programCatalog: ProgramDetailCatalog,
    private val sessionStarter: CalorieTargetPreviewSessionStarter,
) {
    operator fun invoke(
        request: StartCalorieTargetPreviewRequest,
    ): StartCalorieTargetPreviewResult {
        val detail = programCatalog.findProgramDetail(CALORIE_TARGET_PROGRAM_ID)
            ?: return StartCalorieTargetPreviewResult.ProgramNotFound(CALORIE_TARGET_PROGRAM_ID)
        if (detail.programId != CALORIE_TARGET_PROGRAM_ID) {
            return StartCalorieTargetPreviewResult.ProgramDetailMismatch(
                expected = CALORIE_TARGET_PROGRAM_ID,
                actual = detail.programId,
            )
        }
        if (
            detail.defaultSettings.duration != CALORIE_TARGET_REPRESENTATIVE_DURATION ||
            CALORIE_TARGET_REPRESENTATIVE_DURATION !in detail.supportedDurations
        ) {
            return StartCalorieTargetPreviewResult.UnsupportedRepresentativeDuration(
                duration = CALORIE_TARGET_REPRESENTATIVE_DURATION,
                supportedDurations = detail.supportedDurations,
            )
        }
        val profileDurationMinutes = detail.profile.sumOf { it.duration.value.toLong() }
        if (
            profileDurationMinutes != CALORIE_TARGET_REPRESENTATIVE_DURATION.value.toLong() ||
            detail.profile.any { it.duration.value <= 0 }
        ) {
            return StartCalorieTargetPreviewResult.RepresentativeProfileDurationMismatch(
                expectedMinutes = CALORIE_TARGET_REPRESENTATIVE_DURATION.value,
                actualMinutes = profileDurationMinutes,
            )
        }

        val plan = WorkoutPlan(
            programId = CALORIE_TARGET_PROGRAM_ID,
            settings = PlanSettings(
                duration = CALORIE_TARGET_REPRESENTATIVE_DURATION,
                intensity = detail.defaultSettings.intensity,
                focus = detail.defaultSettings.focus,
                maxSpeed = SpeedTenths(
                    minOf(
                        detail.defaultSettings.maxSpeed.value,
                        detail.speedRange.max.value,
                        request.capabilities.speed.max.value,
                    ),
                ),
                maxIncline = InclineTenths(
                    minOf(
                        detail.defaultSettings.maxIncline.value,
                        detail.inclineRange.max.value,
                        request.capabilities.incline.max.value,
                    ),
                ),
                adaptToYou = false,
            ),
        )

        return when (
            val validation = ValidatedWorkoutPlan.create(
                plan = plan,
                capabilities = request.capabilities,
                supportedDurations = detail.supportedDurations,
            )
        ) {
            is ValidatedWorkoutPlanResult.Invalid ->
                StartCalorieTargetPreviewResult.CapabilityValidationFailed(validation.errors)

            is ValidatedWorkoutPlanResult.Valid -> {
                val context = WorkoutTimelineContext.CalorieTargetPreview(
                    programId = CALORIE_TARGET_PROGRAM_ID,
                    target = request.target,
                    estimateStatus = CalorieEstimateStatus.ESTIMATED,
                    source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
                    unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
                    sessionResetSemantics = CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
                    completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
                    progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
                    previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
                    deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
                    representativeProfileDuration = validation.plan.plan.settings.duration,
                    effectiveMaxSpeed = validation.plan.plan.settings.maxSpeed,
                    effectiveMaxIncline = validation.plan.plan.settings.maxIncline,
                )
                when (val result = sessionStarter.start(context, validation.plan)) {
                    is WorkoutSessionStarterResult.Started ->
                        StartCalorieTargetPreviewResult.Started(
                            state = result.state,
                            plan = validation.plan,
                            context = context,
                        )

                    is WorkoutSessionStarterResult.Failed ->
                        StartCalorieTargetPreviewResult.StarterFailed(result.failure)
                }
            }
        }
    }
}

sealed interface StartCalorieTargetPreviewResult {
    data class Started(
        val state: WorkoutSessionState.Running,
        val plan: ValidatedWorkoutPlan,
        val context: WorkoutTimelineContext.CalorieTargetPreview,
    ) : StartCalorieTargetPreviewResult

    data class ProgramNotFound(val programId: ProgramId) : StartCalorieTargetPreviewResult

    data class ProgramDetailMismatch(
        val expected: ProgramId,
        val actual: ProgramId,
    ) : StartCalorieTargetPreviewResult

    data class UnsupportedRepresentativeDuration(
        val duration: DurationMinutes,
        val supportedDurations: List<DurationMinutes>,
    ) : StartCalorieTargetPreviewResult

    data class RepresentativeProfileDurationMismatch(
        val expectedMinutes: Int,
        val actualMinutes: Long,
    ) : StartCalorieTargetPreviewResult

    data class CapabilityValidationFailed(
        val errors: List<PlanValidationError>,
    ) : StartCalorieTargetPreviewResult

    data class StarterFailed(
        val failure: WorkoutSessionStartFailure,
    ) : StartCalorieTargetPreviewResult
}
