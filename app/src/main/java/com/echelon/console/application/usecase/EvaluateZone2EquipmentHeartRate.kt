package com.echelon.console.application.usecase

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.HeartRateSample
import com.echelon.console.domain.HeartRateSampleFailure
import com.echelon.console.domain.HeartRateSampleResult
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.Zone2HeartRateEvaluationFailure
import com.echelon.console.domain.Zone2HeartRateEvaluationResult
import com.echelon.console.domain.Zone2HeartRateEvaluation
import com.echelon.console.domain.Zone2HeartRateEvaluator
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import com.echelon.console.domain.WorkoutTimelineContext

data class EvaluateZone2EquipmentHeartRateRequest(
    val context: WorkoutTimelineContext.Zone2Preview,
    val equipmentState: EquipmentReadState,
    val nowElapsedRealtimeMillis: Long,
    val staleAfterMillis: Long,
)

/** Evaluates read-only FitOS telemetry for an accepted ZONE 2 preview. */
class EvaluateZone2EquipmentHeartRate(
    private val evaluator: Zone2HeartRateEvaluator = Zone2HeartRateEvaluator(),
) {
    operator fun invoke(
        request: EvaluateZone2EquipmentHeartRateRequest,
    ): Zone2EquipmentHeartRateResult {
        validateContext(request.context)?.let { failure ->
            return Zone2EquipmentHeartRateResult.Unavailable(failure)
        }

        return when (request.equipmentState.connection) {
            EquipmentConnection.Connecting,
            EquipmentConnection.Disconnected,
            is EquipmentConnection.ServiceUnavailable,
            is EquipmentConnection.UnsupportedApi,
            is EquipmentConnection.EquipmentDisconnected,
            -> Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.SourceUnavailable(
                    request.equipmentState.connection,
                ),
            )

            EquipmentConnection.Ready,
            is EquipmentConnection.Stale,
            -> evaluateConnectedTelemetry(request)
        }
    }

    private fun evaluateConnectedTelemetry(
        request: EvaluateZone2EquipmentHeartRateRequest,
    ): Zone2EquipmentHeartRateResult {
        val equipment = request.equipmentState.equipment
            ?: return Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.MissingEquipmentDescriptor,
            )
        if (equipment.equipmentType != EquipmentType.RUN) {
            return Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.UnsupportedEquipment(equipment.equipmentType),
            )
        }

        val telemetry = request.equipmentState.telemetry
            ?: return Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.MissingTelemetry,
            )
        val sample = when (
            val sampleResult = HeartRateSample.create(
                bpm = telemetry.heartRateBpm,
                elapsedRealtimeMillis = telemetry.elapsedRealtimeMillis,
            )
        ) {
            is HeartRateSampleResult.Accepted -> sampleResult.sample
            is HeartRateSampleResult.Rejected -> {
                return Zone2EquipmentHeartRateResult.Unavailable(
                    Zone2EquipmentHeartRateFailure.InvalidHeartRateSample(sampleResult.failure),
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
            is Zone2HeartRateEvaluationResult.Evaluated ->
                Zone2EquipmentHeartRateResult.Evaluated(result.evaluation)

            is Zone2HeartRateEvaluationResult.Unavailable ->
                Zone2EquipmentHeartRateResult.Unavailable(
                    Zone2EquipmentHeartRateFailure.EvaluatorFailure(result.failure),
                )
        }
    }

    private fun validateContext(
        context: WorkoutTimelineContext.Zone2Preview,
    ): Zone2EquipmentHeartRateFailure? {
        val mismatch = when {
            context.programId != ZONE_2_PROGRAM_ID ->
                Zone2EquipmentHeartRateContextField.PROGRAM_ID

            context.intendedSource != Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT ->
                Zone2EquipmentHeartRateContextField.INTENDED_SOURCE

            context.previewStatus != Zone2HeartRatePreviewStatus.PREVIEW_ONLY ->
                Zone2EquipmentHeartRateContextField.PREVIEW_STATUS

            context.adviceMode != Zone2HeartRateAdviceMode.ADVISORY_ONLY ->
                Zone2EquipmentHeartRateContextField.ADVICE_MODE

            context.thresholdMode != Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW ->
                Zone2EquipmentHeartRateContextField.THRESHOLD_MODE

            context.hysteresisStatus != Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED ->
                Zone2EquipmentHeartRateContextField.HYSTERESIS_STATUS

            else -> null
        }
        return mismatch?.let(Zone2EquipmentHeartRateFailure::ContextContractMismatch)
    }

    private companion object {
        val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
    }
}

enum class Zone2EquipmentHeartRateContextField {
    PROGRAM_ID,
    INTENDED_SOURCE,
    PREVIEW_STATUS,
    ADVICE_MODE,
    THRESHOLD_MODE,
    HYSTERESIS_STATUS,
}

sealed interface Zone2EquipmentHeartRateResult {
    data class Evaluated(
        val evaluation: Zone2HeartRateEvaluation,
    ) : Zone2EquipmentHeartRateResult

    data class Unavailable(
        val failure: Zone2EquipmentHeartRateFailure,
    ) : Zone2EquipmentHeartRateResult
}

sealed interface Zone2EquipmentHeartRateFailure {
    data class ContextContractMismatch(
        val field: Zone2EquipmentHeartRateContextField,
    ) : Zone2EquipmentHeartRateFailure

    data class SourceUnavailable(
        val connection: EquipmentConnection,
    ) : Zone2EquipmentHeartRateFailure

    data object MissingEquipmentDescriptor : Zone2EquipmentHeartRateFailure

    data class UnsupportedEquipment(
        val equipmentType: EquipmentType,
    ) : Zone2EquipmentHeartRateFailure

    data object MissingTelemetry : Zone2EquipmentHeartRateFailure

    data class InvalidHeartRateSample(
        val failure: HeartRateSampleFailure,
    ) : Zone2EquipmentHeartRateFailure

    data class EvaluatorFailure(
        val failure: Zone2HeartRateEvaluationFailure,
    ) : Zone2EquipmentHeartRateFailure
}
