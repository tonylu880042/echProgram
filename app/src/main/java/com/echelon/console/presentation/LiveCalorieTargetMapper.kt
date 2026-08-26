package com.echelon.console.presentation

import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotContextField
import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotFailure
import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotResult
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSampleFreshness
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetEvaluation
import com.echelon.console.domain.CalorieTargetEvaluationFailure
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.FitOsCalorieSampleFailure
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineContext

/** Maps a calorie-target application result into a typed, read-only presentation contract. */
internal object LiveCalorieTargetMapper {
    fun map(
        timeline: WorkoutTimeline,
        result: CalorieTargetEquipmentSnapshotResult,
    ): LiveCalorieTargetContext? {
        val context = timeline.context as? WorkoutTimelineContext.CalorieTargetPreview ?: return null
        if (
            timeline.programId != CALORIE_TARGET_PROGRAM_ID ||
            context.programId != CALORIE_TARGET_PROGRAM_ID ||
            context.programId != timeline.programId
        ) {
            return null
        }

        return when (result) {
            is CalorieTargetEquipmentSnapshotResult.Evaluated -> {
                val evaluation = result.evaluation
                val mismatch = evaluationSnapshotMismatch(context, evaluation)
                if (mismatch != null) {
                    context.toLiveContext(
                        reading = LiveCalorieTargetReading.Unavailable(
                            LiveCalorieTargetUnavailableReason.EvaluationSnapshotMismatch(mismatch),
                        ),
                    )
                } else {
                    context.toLiveContext(
                        reading = LiveCalorieTargetReading.Evaluated(
                            displayValue = evaluation.displayValue,
                            sampleAgeMillis = evaluation.sampleAgeMillis,
                            freshness = evaluation.freshness,
                        ),
                    )
                }
            }

            is CalorieTargetEquipmentSnapshotResult.Unavailable ->
                context.toLiveContext(
                    reading = LiveCalorieTargetReading.Unavailable(mapFailure(result.failure)),
                )
        }
    }

    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    private fun evaluationSnapshotMismatch(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        evaluation: CalorieTargetEvaluation,
    ): LiveCalorieTargetSnapshotField? {
        if (evaluation.target != context.target) {
            return LiveCalorieTargetSnapshotField.TARGET
        }

        val estimateStatusMismatch = when (context.estimateStatus) {
            CalorieEstimateStatus.ESTIMATED -> when (evaluation.estimateStatus) {
                CalorieEstimateStatus.ESTIMATED -> null
                else -> LiveCalorieTargetSnapshotField.ESTIMATE_STATUS
            }
            else -> LiveCalorieTargetSnapshotField.ESTIMATE_STATUS
        }
        val telemetrySourceMismatch = when (context.source) {
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES -> when (evaluation.source) {
                CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES -> null
                else -> LiveCalorieTargetSnapshotField.TELEMETRY_SOURCE
            }
            else -> LiveCalorieTargetSnapshotField.TELEMETRY_SOURCE
        }
        val unitSemanticsMismatch = when (context.unitSemantics) {
            CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED -> when (evaluation.unitSemantics) {
                CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED -> null
                else -> LiveCalorieTargetSnapshotField.UNIT_SEMANTICS
            }
            else -> LiveCalorieTargetSnapshotField.UNIT_SEMANTICS
        }
        val sessionResetSemanticsMismatch = when (context.sessionResetSemantics) {
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED ->
                when (evaluation.sessionResetSemantics) {
                    CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED -> null
                    else -> LiveCalorieTargetSnapshotField.SESSION_RESET_SEMANTICS
                }
            else -> LiveCalorieTargetSnapshotField.SESSION_RESET_SEMANTICS
        }
        val completionAuthorityMismatch = when (context.completionAuthority) {
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED ->
                when (evaluation.completionAuthority) {
                    CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED -> null
                    else -> LiveCalorieTargetSnapshotField.COMPLETION_AUTHORITY
                }
            else -> LiveCalorieTargetSnapshotField.COMPLETION_AUTHORITY
        }
        val progressSemanticsMismatch = when (context.progressSemantics) {
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS ->
                when (evaluation.progressSemantics) {
                    CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS -> null
                    else -> LiveCalorieTargetSnapshotField.PROGRESS_SEMANTICS
                }
            else -> LiveCalorieTargetSnapshotField.PROGRESS_SEMANTICS
        }
        val previewStatusMismatch = when (context.previewStatus) {
            CaloriePreviewStatus.PREVIEW_ONLY -> when (evaluation.previewStatus) {
                CaloriePreviewStatus.PREVIEW_ONLY -> null
                else -> LiveCalorieTargetSnapshotField.PREVIEW_STATUS
            }
            else -> LiveCalorieTargetSnapshotField.PREVIEW_STATUS
        }
        val deviceCommandStatusMismatch = when (context.deviceCommandStatus) {
            CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS -> when (evaluation.deviceCommandStatus) {
                CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS -> null
                else -> LiveCalorieTargetSnapshotField.DEVICE_COMMAND_STATUS
            }
            else -> LiveCalorieTargetSnapshotField.DEVICE_COMMAND_STATUS
        }
        return estimateStatusMismatch
            ?: telemetrySourceMismatch
            ?: unitSemanticsMismatch
            ?: sessionResetSemanticsMismatch
            ?: completionAuthorityMismatch
            ?: progressSemanticsMismatch
            ?: previewStatusMismatch
            ?: deviceCommandStatusMismatch
    }

    private fun WorkoutTimelineContext.CalorieTargetPreview.toLiveContext(
        reading: LiveCalorieTargetReading,
    ): LiveCalorieTargetContext = LiveCalorieTargetContext(
        target = target,
        representativeProfileDuration = representativeProfileDuration,
        effectiveMaxSpeed = effectiveMaxSpeed,
        effectiveMaxIncline = effectiveMaxIncline,
        estimateStatus = estimateStatus,
        source = source,
        unitSemantics = unitSemantics,
        sessionResetSemantics = sessionResetSemantics,
        completionAuthority = completionAuthority,
        progressSemantics = progressSemantics,
        previewStatus = previewStatus,
        deviceCommandStatus = deviceCommandStatus,
        reading = reading,
    )

    private fun mapFailure(
        failure: CalorieTargetEquipmentSnapshotFailure,
    ): LiveCalorieTargetUnavailableReason = when (failure) {
        is CalorieTargetEquipmentSnapshotFailure.ContextContractMismatch ->
            LiveCalorieTargetUnavailableReason.ContextContractMismatch(
                field = mapContextField(failure.field),
            )

        is CalorieTargetEquipmentSnapshotFailure.SourceUnavailable ->
            LiveCalorieTargetUnavailableReason.SourceUnavailable(
                reason = mapSourceReason(failure.connection),
            )

        CalorieTargetEquipmentSnapshotFailure.MissingEquipmentDescriptor ->
            LiveCalorieTargetUnavailableReason.MissingEquipmentDescriptor

        is CalorieTargetEquipmentSnapshotFailure.UnsupportedEquipment ->
            LiveCalorieTargetUnavailableReason.UnsupportedEquipment(failure.equipmentType)

        CalorieTargetEquipmentSnapshotFailure.MissingTelemetry ->
            LiveCalorieTargetUnavailableReason.MissingTelemetry

        is CalorieTargetEquipmentSnapshotFailure.InvalidCalorieSample ->
            LiveCalorieTargetUnavailableReason.InvalidCalorieSample(
                reason = mapSampleReason(failure.failure),
            )

        is CalorieTargetEquipmentSnapshotFailure.EvaluatorFailure ->
            LiveCalorieTargetUnavailableReason.EvaluatorFailure(
                reason = mapEvaluatorReason(failure.failure),
            )
    }

    private fun mapContextField(
        field: CalorieTargetEquipmentSnapshotContextField,
    ): LiveCalorieTargetContextField = when (field) {
        CalorieTargetEquipmentSnapshotContextField.PROGRAM_ID ->
            LiveCalorieTargetContextField.PROGRAM_ID
        CalorieTargetEquipmentSnapshotContextField.TARGET_SOURCE ->
            LiveCalorieTargetContextField.TARGET_SOURCE
        CalorieTargetEquipmentSnapshotContextField.ESTIMATE_STATUS ->
            LiveCalorieTargetContextField.ESTIMATE_STATUS
        CalorieTargetEquipmentSnapshotContextField.TELEMETRY_SOURCE ->
            LiveCalorieTargetContextField.TELEMETRY_SOURCE
        CalorieTargetEquipmentSnapshotContextField.UNIT_SEMANTICS ->
            LiveCalorieTargetContextField.UNIT_SEMANTICS
        CalorieTargetEquipmentSnapshotContextField.SESSION_RESET_SEMANTICS ->
            LiveCalorieTargetContextField.SESSION_RESET_SEMANTICS
        CalorieTargetEquipmentSnapshotContextField.COMPLETION_AUTHORITY ->
            LiveCalorieTargetContextField.COMPLETION_AUTHORITY
        CalorieTargetEquipmentSnapshotContextField.PROGRESS_SEMANTICS ->
            LiveCalorieTargetContextField.PROGRESS_SEMANTICS
        CalorieTargetEquipmentSnapshotContextField.PREVIEW_STATUS ->
            LiveCalorieTargetContextField.PREVIEW_STATUS
        CalorieTargetEquipmentSnapshotContextField.DEVICE_COMMAND_STATUS ->
            LiveCalorieTargetContextField.DEVICE_COMMAND_STATUS
    }

    private fun mapSourceReason(
        connection: EquipmentConnection,
    ): LiveCalorieTargetSourceReason = when (connection) {
        EquipmentConnection.Connecting -> LiveCalorieTargetSourceReason.Connecting
        EquipmentConnection.Disconnected -> LiveCalorieTargetSourceReason.Disconnected
        is EquipmentConnection.ServiceUnavailable ->
            LiveCalorieTargetSourceReason.ServiceUnavailable(connection.reason)
        is EquipmentConnection.UnsupportedApi ->
            LiveCalorieTargetSourceReason.UnsupportedApi(connection.apiVersion)
        is EquipmentConnection.EquipmentDisconnected ->
            LiveCalorieTargetSourceReason.EquipmentDisconnected(connection.status)
        EquipmentConnection.Ready -> LiveCalorieTargetSourceReason.Ready
        is EquipmentConnection.Stale ->
            LiveCalorieTargetSourceReason.Stale(connection.ageMillis)
    }

    private fun mapSampleReason(
        failure: FitOsCalorieSampleFailure,
    ): LiveCalorieTargetSampleReason = when (failure) {
        FitOsCalorieSampleFailure.MissingDisplayValue ->
            LiveCalorieTargetSampleReason.MissingDisplayValue
        is FitOsCalorieSampleFailure.NonFiniteDisplayValue ->
            LiveCalorieTargetSampleReason.NonFiniteDisplayValue(failure.value)
        is FitOsCalorieSampleFailure.NegativeDisplayValue ->
            LiveCalorieTargetSampleReason.NegativeDisplayValue(failure.value)
        FitOsCalorieSampleFailure.MissingTimestamp ->
            LiveCalorieTargetSampleReason.MissingTimestamp
        is FitOsCalorieSampleFailure.NegativeTimestamp ->
            LiveCalorieTargetSampleReason.NegativeTimestamp(failure.value)
    }

    private fun mapEvaluatorReason(
        failure: CalorieTargetEvaluationFailure,
    ): LiveCalorieTargetEvaluatorReason = when (failure) {
        CalorieTargetEvaluationFailure.MissingTarget ->
            LiveCalorieTargetEvaluatorReason.MissingTarget
        CalorieTargetEvaluationFailure.MissingSample ->
            LiveCalorieTargetEvaluatorReason.MissingSample
        is CalorieTargetEvaluationFailure.InvalidNowElapsedRealtimeMillis ->
            LiveCalorieTargetEvaluatorReason.InvalidNowElapsedRealtimeMillis(failure.value)
        is CalorieTargetEvaluationFailure.InvalidStaleAfterMillis ->
            LiveCalorieTargetEvaluatorReason.InvalidStaleAfterMillis(failure.value)
        is CalorieTargetEvaluationFailure.FutureSampleTimestamp ->
            LiveCalorieTargetEvaluatorReason.FutureSampleTimestamp(
                sampleElapsedRealtimeMillis = failure.sampleElapsedRealtimeMillis,
                nowElapsedRealtimeMillis = failure.nowElapsedRealtimeMillis,
            )
    }

    private val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")
}

data class LiveCalorieTargetContext(
    val target: CalorieTargetSelection,
    val representativeProfileDuration: DurationMinutes,
    val effectiveMaxSpeed: SpeedTenths,
    val effectiveMaxIncline: InclineTenths,
    val estimateStatus: CalorieEstimateStatus,
    val source: CalorieTelemetrySource,
    val unitSemantics: CalorieUnitSemantics,
    val sessionResetSemantics: CalorieSessionResetSemantics,
    val completionAuthority: CalorieCompletionAuthority,
    val progressSemantics: CalorieProgressSemantics,
    val previewStatus: CaloriePreviewStatus,
    val deviceCommandStatus: CalorieDeviceCommandStatus,
    val reading: LiveCalorieTargetReading,
)

sealed interface LiveCalorieTargetReading {
    data class Evaluated(
        val displayValue: Double,
        val sampleAgeMillis: Long,
        val freshness: CalorieSampleFreshness,
    ) : LiveCalorieTargetReading

    data class Unavailable(
        val reason: LiveCalorieTargetUnavailableReason,
    ) : LiveCalorieTargetReading
}

sealed interface LiveCalorieTargetUnavailableReason {
    data class EvaluationSnapshotMismatch(
        val field: LiveCalorieTargetSnapshotField,
    ) : LiveCalorieTargetUnavailableReason

    data class ContextContractMismatch(
        val field: LiveCalorieTargetContextField,
    ) : LiveCalorieTargetUnavailableReason

    data class SourceUnavailable(
        val reason: LiveCalorieTargetSourceReason,
    ) : LiveCalorieTargetUnavailableReason

    data object MissingEquipmentDescriptor : LiveCalorieTargetUnavailableReason

    data class UnsupportedEquipment(
        val equipmentType: EquipmentType,
    ) : LiveCalorieTargetUnavailableReason

    data object MissingTelemetry : LiveCalorieTargetUnavailableReason

    data class InvalidCalorieSample(
        val reason: LiveCalorieTargetSampleReason,
    ) : LiveCalorieTargetUnavailableReason

    data class EvaluatorFailure(
        val reason: LiveCalorieTargetEvaluatorReason,
    ) : LiveCalorieTargetUnavailableReason
}

enum class LiveCalorieTargetSnapshotField {
    TARGET,
    ESTIMATE_STATUS,
    TELEMETRY_SOURCE,
    UNIT_SEMANTICS,
    SESSION_RESET_SEMANTICS,
    COMPLETION_AUTHORITY,
    PROGRESS_SEMANTICS,
    PREVIEW_STATUS,
    DEVICE_COMMAND_STATUS,
}

enum class LiveCalorieTargetContextField {
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

sealed interface LiveCalorieTargetSourceReason {
    data object Connecting : LiveCalorieTargetSourceReason

    data object Disconnected : LiveCalorieTargetSourceReason

    data class ServiceUnavailable(val reason: String) : LiveCalorieTargetSourceReason

    data class UnsupportedApi(val apiVersion: Int) : LiveCalorieTargetSourceReason

    data class EquipmentDisconnected(val status: String?) : LiveCalorieTargetSourceReason

    data object Ready : LiveCalorieTargetSourceReason

    data class Stale(val ageMillis: Long) : LiveCalorieTargetSourceReason
}

sealed interface LiveCalorieTargetSampleReason {
    data object MissingDisplayValue : LiveCalorieTargetSampleReason

    data class NonFiniteDisplayValue(val value: Double) : LiveCalorieTargetSampleReason

    data class NegativeDisplayValue(val value: Double) : LiveCalorieTargetSampleReason

    data object MissingTimestamp : LiveCalorieTargetSampleReason

    data class NegativeTimestamp(val value: Long) : LiveCalorieTargetSampleReason
}

sealed interface LiveCalorieTargetEvaluatorReason {
    data object MissingTarget : LiveCalorieTargetEvaluatorReason

    data object MissingSample : LiveCalorieTargetEvaluatorReason

    data class InvalidNowElapsedRealtimeMillis(val value: Long) : LiveCalorieTargetEvaluatorReason

    data class InvalidStaleAfterMillis(val value: Long) : LiveCalorieTargetEvaluatorReason

    data class FutureSampleTimestamp(
        val sampleElapsedRealtimeMillis: Long,
        val nowElapsedRealtimeMillis: Long,
    ) : LiveCalorieTargetEvaluatorReason
}
