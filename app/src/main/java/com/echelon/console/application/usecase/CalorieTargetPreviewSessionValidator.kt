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
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutTimelineContext

private val calorieTargetProgramId = ProgramId("CALORIE_TARGET")
private const val CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES = 40
private val calorieTargetRepresentativeDuration = DurationMinutes(
    CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES,
)

internal sealed interface CalorieTargetPreviewSessionValidationResult {
    data class Accepted(
        val detail: ProgramDetail,
    ) : CalorieTargetPreviewSessionValidationResult

    data class Rejected(
        val failure: WorkoutSessionStartFailure,
    ) : CalorieTargetPreviewSessionValidationResult
}

internal class CalorieTargetPreviewSessionValidator(
    private val catalog: ProgramDetailCatalog,
) {
    fun validate(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        plan: ValidatedWorkoutPlan,
    ): CalorieTargetPreviewSessionValidationResult {
        calorieContextPlanMismatch(context, plan)?.let { field ->
            return CalorieTargetPreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
            )
        }

        val detail = catalog.findProgramDetail(calorieTargetProgramId)
            ?: return CalorieTargetPreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.CalorieTargetPreviewProgramNotFound(
                    calorieTargetProgramId,
                ),
            )
        if (detail.programId != calorieTargetProgramId) {
            return CalorieTargetPreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.CalorieTargetPreviewDetailMismatch(
                    expected = calorieTargetProgramId,
                    actual = detail.programId,
                ),
            )
        }
        if (
            detail.defaultSettings.duration != calorieTargetRepresentativeDuration ||
            calorieTargetRepresentativeDuration !in detail.supportedDurations
        ) {
            return CalorieTargetPreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.CalorieTargetPreviewUnsupportedDuration(
                    duration = calorieTargetRepresentativeDuration,
                    supportedDurations = detail.supportedDurations,
                ),
            )
        }

        val profileDurationMinutes = detail.profile.sumOf { it.duration.value.toLong() }
        if (
            profileDurationMinutes != CALORIE_TARGET_REPRESENTATIVE_PROFILE_DURATION_MINUTES.toLong() ||
            detail.profile.any { it.duration.value <= 0 }
        ) {
            return CalorieTargetPreviewSessionValidationResult.Rejected(
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
            return CalorieTargetPreviewSessionValidationResult.Rejected(
                WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
            )
        }

        return CalorieTargetPreviewSessionValidationResult.Accepted(detail)
    }

    private fun calorieContextPlanMismatch(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        plan: ValidatedWorkoutPlan,
    ): CalorieTargetPreviewContextPlanMismatchField? {
        val settings = plan.plan.settings
        val mismatch = when {
            context.programId != calorieTargetProgramId ||
                plan.plan.programId != calorieTargetProgramId ->
                CalorieTargetPreviewContextPlanMismatchField.PROGRAM_ID

            context.representativeProfileDuration != calorieTargetRepresentativeDuration ||
                settings.duration != calorieTargetRepresentativeDuration ||
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
}
