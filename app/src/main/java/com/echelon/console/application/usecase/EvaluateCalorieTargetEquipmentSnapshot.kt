package com.echelon.console.application.usecase

import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetEvaluation
import com.echelon.console.domain.CalorieTargetEvaluationFailure
import com.echelon.console.domain.CalorieTargetEvaluationResult
import com.echelon.console.domain.CalorieTargetEvaluator
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.FitOsCalorieSample
import com.echelon.console.domain.FitOsCalorieSampleFailure
import com.echelon.console.domain.FitOsCalorieSampleResult
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.WorkoutTimelineContext

data class EvaluateCalorieTargetEquipmentSnapshotRequest(
    val context: WorkoutTimelineContext.CalorieTargetPreview,
    val equipmentState: EquipmentReadState,
    val nowElapsedRealtimeMillis: Long,
    val staleAfterMillis: Long,
)

/** Evaluates read-only FitOS snapshot calories for an accepted CALORIE_TARGET preview. */
class EvaluateCalorieTargetEquipmentSnapshot(
    private val evaluator: CalorieTargetEvaluator = CalorieTargetEvaluator(),
) {
    operator fun invoke(
        request: EvaluateCalorieTargetEquipmentSnapshotRequest,
    ): CalorieTargetEquipmentSnapshotResult {
        validateContext(request.context)?.let { failure ->
            return CalorieTargetEquipmentSnapshotResult.Unavailable(failure)
        }

        return when (request.equipmentState.connection) {
            EquipmentConnection.Connecting,
            EquipmentConnection.Disconnected,
            is EquipmentConnection.ServiceUnavailable,
            is EquipmentConnection.UnsupportedApi,
            is EquipmentConnection.EquipmentDisconnected,
            -> CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.SourceUnavailable(
                    request.equipmentState.connection,
                ),
            )

            EquipmentConnection.Ready,
            is EquipmentConnection.Stale,
            -> evaluateConnectedTelemetry(request)
        }
    }

    private fun evaluateConnectedTelemetry(
        request: EvaluateCalorieTargetEquipmentSnapshotRequest,
    ): CalorieTargetEquipmentSnapshotResult {
        val equipment = request.equipmentState.equipment
            ?: return CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.MissingEquipmentDescriptor,
            )
        if (equipment.equipmentType != EquipmentType.RUN) {
            return CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.UnsupportedEquipment(equipment.equipmentType),
            )
        }

        val telemetry = request.equipmentState.telemetry
            ?: return CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.MissingTelemetry,
            )
        val sample = when (
            val sampleResult = FitOsCalorieSample.create(
                displayValue = telemetry.calories,
                elapsedRealtimeMillis = telemetry.elapsedRealtimeMillis,
            )
        ) {
            is FitOsCalorieSampleResult.Accepted -> sampleResult.sample
            is FitOsCalorieSampleResult.Rejected -> {
                return CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.InvalidCalorieSample(sampleResult.failure),
                )
            }
        }

        return when (
            val result = evaluator.evaluate(
                target = request.context.target,
                sample = sample,
                nowElapsedRealtimeMillis = request.nowElapsedRealtimeMillis,
                staleAfterMillis = request.staleAfterMillis,
            )
        ) {
            is CalorieTargetEvaluationResult.Evaluated ->
                CalorieTargetEquipmentSnapshotResult.Evaluated(result.evaluation)

            is CalorieTargetEvaluationResult.Unavailable ->
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.EvaluatorFailure(result.failure),
                )
        }
    }

    private fun validateContext(
        context: WorkoutTimelineContext.CalorieTargetPreview,
    ): CalorieTargetEquipmentSnapshotFailure? {
        if (context.programId != CALORIE_TARGET_PROGRAM_ID) {
            return CalorieTargetEquipmentSnapshotFailure.ContextContractMismatch(
                CalorieTargetEquipmentSnapshotContextField.PROGRAM_ID,
            )
        }
        return calorieContextMetadataMismatch(context)?.let(
            CalorieTargetEquipmentSnapshotFailure::ContextContractMismatch,
        )
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun calorieContextMetadataMismatch(
        context: WorkoutTimelineContext.CalorieTargetPreview,
    ): CalorieTargetEquipmentSnapshotContextField? {
        val targetSourceMismatch = when (context.target.source) {
            CalorieTargetSource.USER_SELECTED -> null
            else -> CalorieTargetEquipmentSnapshotContextField.TARGET_SOURCE
        }
        val estimateStatusMismatch = when (context.estimateStatus) {
            CalorieEstimateStatus.ESTIMATED -> null
            else -> CalorieTargetEquipmentSnapshotContextField.ESTIMATE_STATUS
        }
        val telemetrySourceMismatch = when (context.source) {
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES -> null
            else -> CalorieTargetEquipmentSnapshotContextField.TELEMETRY_SOURCE
        }
        val unitSemanticsMismatch = when (context.unitSemantics) {
            CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED -> null
            else -> CalorieTargetEquipmentSnapshotContextField.UNIT_SEMANTICS
        }
        val sessionResetSemanticsMismatch = when (context.sessionResetSemantics) {
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED -> null
            else -> CalorieTargetEquipmentSnapshotContextField.SESSION_RESET_SEMANTICS
        }
        val completionAuthorityMismatch = when (context.completionAuthority) {
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED -> null
            else -> CalorieTargetEquipmentSnapshotContextField.COMPLETION_AUTHORITY
        }
        val progressSemanticsMismatch = when (context.progressSemantics) {
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS -> null
            else -> CalorieTargetEquipmentSnapshotContextField.PROGRESS_SEMANTICS
        }
        val previewStatusMismatch = when (context.previewStatus) {
            CaloriePreviewStatus.PREVIEW_ONLY -> null
            else -> CalorieTargetEquipmentSnapshotContextField.PREVIEW_STATUS
        }
        val deviceCommandStatusMismatch = when (context.deviceCommandStatus) {
            CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS -> null
            else -> CalorieTargetEquipmentSnapshotContextField.DEVICE_COMMAND_STATUS
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

    private companion object {
        val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")
    }
}

enum class CalorieTargetEquipmentSnapshotContextField {
    PROGRAM_ID,
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

sealed interface CalorieTargetEquipmentSnapshotResult {
    data class Evaluated(
        val evaluation: CalorieTargetEvaluation,
    ) : CalorieTargetEquipmentSnapshotResult

    data class Unavailable(
        val failure: CalorieTargetEquipmentSnapshotFailure,
    ) : CalorieTargetEquipmentSnapshotResult
}

sealed interface CalorieTargetEquipmentSnapshotFailure {
    data class ContextContractMismatch(
        val field: CalorieTargetEquipmentSnapshotContextField,
    ) : CalorieTargetEquipmentSnapshotFailure

    data class SourceUnavailable(
        val connection: EquipmentConnection,
    ) : CalorieTargetEquipmentSnapshotFailure

    data object MissingEquipmentDescriptor : CalorieTargetEquipmentSnapshotFailure

    data class UnsupportedEquipment(
        val equipmentType: EquipmentType,
    ) : CalorieTargetEquipmentSnapshotFailure

    data object MissingTelemetry : CalorieTargetEquipmentSnapshotFailure

    data class InvalidCalorieSample(
        val failure: FitOsCalorieSampleFailure,
    ) : CalorieTargetEquipmentSnapshotFailure

    data class EvaluatorFailure(
        val failure: CalorieTargetEvaluationFailure,
    ) : CalorieTargetEquipmentSnapshotFailure
}
