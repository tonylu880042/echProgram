package com.echelon.console.application.usecase

import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutTimelineContext

private val zone2ProgramId = ProgramId("ZONE_2")

internal sealed interface Zone2PreviewSessionValidationResult {
    data class Accepted(
        val detail: ProgramDetail,
    ) : Zone2PreviewSessionValidationResult

    data class Rejected(
        val failure: WorkoutSessionStartFailure,
    ) : Zone2PreviewSessionValidationResult
}

internal class Zone2PreviewSessionValidator(
    private val catalog: ProgramDetailCatalog,
) {
    fun validate(
        context: WorkoutTimelineContext.Zone2Preview,
        plan: ValidatedWorkoutPlan,
    ): Zone2PreviewSessionValidationResult {
        zone2ContextPlanMismatch(context, plan)?.let { field ->
            return Zone2PreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(field),
            )
        }

        val detail = catalog.findProgramDetail(zone2ProgramId)
            ?.takeIf { it.programId == zone2ProgramId }
            ?: return Zone2PreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.ProgramNotFound(zone2ProgramId),
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
            return Zone2PreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(it),
            )
        }
        if (
            settings.duration !in ZONE_2_PREVIEW_SUPPORTED_DURATIONS ||
            settings.duration !in detail.supportedDurations
        ) {
            return Zone2PreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.UnsupportedDuration(
                    programId = zone2ProgramId,
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
            return Zone2PreviewSessionValidationResult.Rejected(
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
            return Zone2PreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(
                    Zone2PreviewContextPlanMismatchField.MAX_INCLINE,
                ),
            )
        }

        return Zone2PreviewSessionValidationResult.Accepted(detail)
    }

    private fun zone2ContextPlanMismatch(
        context: WorkoutTimelineContext.Zone2Preview,
        plan: ValidatedWorkoutPlan,
    ): Zone2PreviewContextPlanMismatchField? {
        val settings = plan.plan.settings
        val mismatch = when {
            context.programId != zone2ProgramId || plan.plan.programId != zone2ProgramId ->
                Zone2PreviewContextPlanMismatchField.PROGRAM_ID

            context.duration != settings.duration ->
                Zone2PreviewContextPlanMismatchField.DURATION

            context.effectiveMaxSpeed != settings.maxSpeed ->
                Zone2PreviewContextPlanMismatchField.MAX_SPEED

            context.effectiveMaxIncline != settings.maxIncline ->
                Zone2PreviewContextPlanMismatchField.MAX_INCLINE

            else -> null
        }
        return mismatch
    }
}
