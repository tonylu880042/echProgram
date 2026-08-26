package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateTargetRange
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
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode

internal val ZONE_2_PREVIEW_SUPPORTED_DURATIONS = listOf(
    DurationMinutes(20),
    DurationMinutes(30),
    DurationMinutes(45),
    DurationMinutes(60),
)

data class StartZone2WorkoutPreviewRequest(
    val target: HeartRateTargetRange,
    val duration: DurationMinutes,
    val capabilities: DeviceCapabilities,
)

/**
 * Builds and explicitly accepts the reviewed static ZONE 2 profile.
 *
 * The target is user-confirmed input; this use case does not derive it from
 * age or a maximum-heart-rate formula, observe telemetry, or issue commands.
 */
class StartZone2WorkoutPreview(
    private val programCatalog: ProgramDetailCatalog,
    private val sessionStarter: Zone2WorkoutPreviewSessionStarter,
) {
    operator fun invoke(
        request: StartZone2WorkoutPreviewRequest,
    ): StartZone2WorkoutPreviewResult {
        val detail = programCatalog.findProgramDetail(ZONE_2_PROGRAM_ID)
            ?.takeIf { it.programId == ZONE_2_PROGRAM_ID }
            ?: return StartZone2WorkoutPreviewResult.ProgramNotFound(ZONE_2_PROGRAM_ID)

        if (
            request.duration !in ZONE_2_PREVIEW_SUPPORTED_DURATIONS ||
            request.duration !in detail.supportedDurations
        ) {
            return StartZone2WorkoutPreviewResult.UnsupportedDuration(
                duration = request.duration,
                supportedDurations = ZONE_2_PREVIEW_SUPPORTED_DURATIONS,
            )
        }

        val plan = WorkoutPlan(
            programId = ZONE_2_PROGRAM_ID,
            settings = PlanSettings(
                duration = request.duration,
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
                StartZone2WorkoutPreviewResult.CapabilityValidationFailed(validation.errors)

            is ValidatedWorkoutPlanResult.Valid -> {
                val context = WorkoutTimelineContext.Zone2Preview(
                    programId = ZONE_2_PROGRAM_ID,
                    target = request.target,
                    intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
                    previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                    adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                    thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                    hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
                    duration = validation.plan.plan.settings.duration,
                    effectiveMaxSpeed = validation.plan.plan.settings.maxSpeed,
                    effectiveMaxIncline = validation.plan.plan.settings.maxIncline,
                )
                when (val result = sessionStarter.start(context, validation.plan)) {
                    is WorkoutSessionStarterResult.Started ->
                        StartZone2WorkoutPreviewResult.Started(
                            state = result.state,
                            plan = validation.plan,
                            context = context,
                        )

                    is WorkoutSessionStarterResult.Failed ->
                        StartZone2WorkoutPreviewResult.StarterFailed(result.failure)
                }
            }
        }
    }

    private companion object {
        val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
    }
}

sealed interface StartZone2WorkoutPreviewResult {
    data class Started(
        val state: WorkoutSessionState.Running,
        val plan: ValidatedWorkoutPlan,
        val context: WorkoutTimelineContext.Zone2Preview,
    ) : StartZone2WorkoutPreviewResult

    data class ProgramNotFound(val programId: ProgramId) : StartZone2WorkoutPreviewResult

    data class UnsupportedDuration(
        val duration: DurationMinutes,
        val supportedDurations: List<DurationMinutes>,
    ) : StartZone2WorkoutPreviewResult

    data class CapabilityValidationFailed(
        val errors: List<PlanValidationError>,
    ) : StartZone2WorkoutPreviewResult

    data class StarterFailed(
        val failure: WorkoutSessionStartFailure,
    ) : StartZone2WorkoutPreviewResult
}
