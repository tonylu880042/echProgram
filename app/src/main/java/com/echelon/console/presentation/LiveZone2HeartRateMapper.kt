package com.echelon.console.presentation

import com.echelon.console.application.usecase.Zone2EquipmentHeartRateContextField
import com.echelon.console.application.usecase.Zone2EquipmentHeartRateFailure
import com.echelon.console.application.usecase.Zone2EquipmentHeartRateResult
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.HeartRateSampleFailure
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.Zone2HeartRateEvaluationFailure

/** Maps an evaluated application result into the read-only Zone 2 presentation contract. */
internal object LiveZone2HeartRateMapper {
    fun map(
        timeline: WorkoutTimeline,
        result: Zone2EquipmentHeartRateResult,
    ): LiveZone2HeartRateContext? {
        val context = timeline.context as? WorkoutTimelineContext.Zone2Preview ?: return null
        if (
            timeline.programId != ZONE_2_PROGRAM_ID ||
            context.programId != timeline.programId
        ) {
            return null
        }

        val reading = when (result) {
            is Zone2EquipmentHeartRateResult.Evaluated -> {
                val evaluation = result.evaluation
                LiveZone2HeartRateReading.Evaluated(
                    currentBpm = evaluation.currentBpm,
                    sampleAgeMillis = evaluation.sampleAgeMillis,
                    status = evaluation.status,
                    advice = evaluation.advice,
                )
            }

            is Zone2EquipmentHeartRateResult.Unavailable ->
                LiveZone2HeartRateReading.Unavailable(mapFailure(result.failure))
        }

        return LiveZone2HeartRateContext(
            target = context.target,
            intendedSource = context.intendedSource,
            previewStatus = context.previewStatus,
            adviceMode = context.adviceMode,
            thresholdMode = context.thresholdMode,
            hysteresisStatus = context.hysteresisStatus,
            reading = reading,
        )
    }

    private fun mapFailure(
        failure: Zone2EquipmentHeartRateFailure,
    ): LiveZone2HeartRateUnavailableReason = when (failure) {
        is Zone2EquipmentHeartRateFailure.ContextContractMismatch ->
            LiveZone2HeartRateUnavailableReason.ContextContractMismatch(
                field = mapContextField(failure.field),
            )

        is Zone2EquipmentHeartRateFailure.SourceUnavailable ->
            LiveZone2HeartRateUnavailableReason.SourceUnavailable(
                reason = mapSourceReason(failure.connection),
            )

        Zone2EquipmentHeartRateFailure.MissingEquipmentDescriptor ->
            LiveZone2HeartRateUnavailableReason.MissingEquipmentDescriptor

        is Zone2EquipmentHeartRateFailure.UnsupportedEquipment ->
            LiveZone2HeartRateUnavailableReason.UnsupportedEquipment(failure.equipmentType)

        Zone2EquipmentHeartRateFailure.MissingTelemetry ->
            LiveZone2HeartRateUnavailableReason.MissingTelemetry

        is Zone2EquipmentHeartRateFailure.InvalidHeartRateSample ->
            LiveZone2HeartRateUnavailableReason.InvalidHeartRateSample(
                reason = mapSampleReason(failure.failure),
            )

        is Zone2EquipmentHeartRateFailure.EvaluatorFailure ->
            LiveZone2HeartRateUnavailableReason.EvaluatorFailure(
                reason = mapEvaluatorReason(failure.failure),
            )
    }

    private fun mapContextField(
        field: Zone2EquipmentHeartRateContextField,
    ): LiveZone2HeartRateContextField = when (field) {
        Zone2EquipmentHeartRateContextField.PROGRAM_ID -> LiveZone2HeartRateContextField.PROGRAM_ID
        Zone2EquipmentHeartRateContextField.INTENDED_SOURCE ->
            LiveZone2HeartRateContextField.INTENDED_SOURCE
        Zone2EquipmentHeartRateContextField.PREVIEW_STATUS ->
            LiveZone2HeartRateContextField.PREVIEW_STATUS
        Zone2EquipmentHeartRateContextField.ADVICE_MODE -> LiveZone2HeartRateContextField.ADVICE_MODE
        Zone2EquipmentHeartRateContextField.THRESHOLD_MODE ->
            LiveZone2HeartRateContextField.THRESHOLD_MODE
        Zone2EquipmentHeartRateContextField.HYSTERESIS_STATUS ->
            LiveZone2HeartRateContextField.HYSTERESIS_STATUS
    }

    private fun mapSourceReason(
        connection: EquipmentConnection,
    ): LiveZone2HeartRateSourceReason = when (connection) {
        EquipmentConnection.Connecting -> LiveZone2HeartRateSourceReason.Connecting
        EquipmentConnection.Disconnected -> LiveZone2HeartRateSourceReason.Disconnected
        is EquipmentConnection.ServiceUnavailable ->
            LiveZone2HeartRateSourceReason.ServiceUnavailable(connection.reason)
        is EquipmentConnection.UnsupportedApi ->
            LiveZone2HeartRateSourceReason.UnsupportedApi(connection.apiVersion)
        is EquipmentConnection.EquipmentDisconnected ->
            LiveZone2HeartRateSourceReason.EquipmentDisconnected(connection.status)
        EquipmentConnection.Ready -> LiveZone2HeartRateSourceReason.Ready
        is EquipmentConnection.Stale -> LiveZone2HeartRateSourceReason.Stale(connection.ageMillis)
    }

    private fun mapSampleReason(
        failure: HeartRateSampleFailure,
    ): LiveZone2HeartRateSampleReason = when (failure) {
        HeartRateSampleFailure.MissingBpm -> LiveZone2HeartRateSampleReason.MissingBpm
        HeartRateSampleFailure.MissingTimestamp -> LiveZone2HeartRateSampleReason.MissingTimestamp
        is HeartRateSampleFailure.NonPositiveBpm ->
            LiveZone2HeartRateSampleReason.NonPositiveBpm(failure.value)
        is HeartRateSampleFailure.NegativeTimestamp ->
            LiveZone2HeartRateSampleReason.NegativeTimestamp(failure.value)
    }

    private fun mapEvaluatorReason(
        failure: Zone2HeartRateEvaluationFailure,
    ): LiveZone2HeartRateEvaluatorReason = when (failure) {
        Zone2HeartRateEvaluationFailure.MissingTarget -> LiveZone2HeartRateEvaluatorReason.MissingTarget
        Zone2HeartRateEvaluationFailure.MissingHeartRate ->
            LiveZone2HeartRateEvaluatorReason.MissingHeartRate
        is Zone2HeartRateEvaluationFailure.InvalidNowElapsedRealtimeMillis ->
            LiveZone2HeartRateEvaluatorReason.InvalidNowElapsedRealtimeMillis(failure.value)
        is Zone2HeartRateEvaluationFailure.InvalidStaleAfterMillis ->
            LiveZone2HeartRateEvaluatorReason.InvalidStaleAfterMillis(failure.value)
        is Zone2HeartRateEvaluationFailure.FutureSampleTimestamp ->
            LiveZone2HeartRateEvaluatorReason.FutureSampleTimestamp(
                sampleElapsedRealtimeMillis = failure.sampleElapsedRealtimeMillis,
                nowElapsedRealtimeMillis = failure.nowElapsedRealtimeMillis,
            )
    }

    private val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
}
