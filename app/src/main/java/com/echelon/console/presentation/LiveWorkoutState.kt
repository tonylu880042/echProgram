package com.echelon.console.presentation

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.HeartRateSampleFailure
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.WorkoutTimelineAnnotation
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateEvaluationFailure
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode

sealed interface LiveWorkoutUiState {
    data object NoSession : LiveWorkoutUiState

    data class Active(
        val workout: LiveWorkoutReadModel,
    ) : LiveWorkoutUiState

    data class Completed(
        val summary: LiveWorkoutSummary,
    ) : LiveWorkoutUiState

    data class Stopped(
        val summary: LiveWorkoutSummary,
    ) : LiveWorkoutUiState

    data class Error(
        val message: String,
    ) : LiveWorkoutUiState
}

data class LiveWorkoutReadModel(
    val programId: ProgramId,
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
    val currentSegment: LiveWorkoutSegment,
    val nextSegment: LiveWorkoutSegment?,
    val secondsUntilNextSegment: Int?,
    val targetSpeed: SpeedTenths,
    val targetIncline: InclineTenths,
    val isPaused: Boolean,
    val programTitle: String,
    val previewMode: ProgramPreviewMode,
    val runWalkSummary: LiveWorkoutRunWalkSummary? = null,
    val verticalContext: LiveVerticalWorkoutContext? = null,
    val zone2Context: LiveZone2HeartRateContext? = null,
)

data class LiveWorkoutSegment(
    val index: Int,
    val name: String,
    val annotation: WorkoutTimelineAnnotation = WorkoutTimelineAnnotation.Unannotated,
    val displayLabel: String = name,
)

data class LiveWorkoutRunWalkSummary(
    val runMinutes: Int,
    val walkMinutes: Int,
)

data class LiveVerticalWorkoutContext(
    val target: VerticalTarget,
    val proposedTimeLimit: VerticalTimeLimitProposal,
    val elevationSource: VerticalElevationSource,
    val progressStatus: VerticalProgressStatus,
    val controlStatus: VerticalWorkoutDraftControlStatus,
)

data class LiveZone2HeartRateContext(
    val target: HeartRateTargetRange,
    val intendedSource: Zone2HeartRateIntendedSource,
    val previewStatus: Zone2HeartRatePreviewStatus,
    val adviceMode: Zone2HeartRateAdviceMode,
    val thresholdMode: Zone2HeartRateThresholdMode,
    val hysteresisStatus: Zone2HeartRateHysteresisStatus,
    val reading: LiveZone2HeartRateReading,
)

sealed interface LiveZone2HeartRateReading {
    data class Evaluated(
        val currentBpm: Int,
        val sampleAgeMillis: Long,
        val status: Zone2HeartRateStatus,
        val advice: Zone2HeartRateAdvice,
    ) : LiveZone2HeartRateReading

    data class Unavailable(
        val reason: LiveZone2HeartRateUnavailableReason,
    ) : LiveZone2HeartRateReading
}

sealed interface LiveZone2HeartRateUnavailableReason {
    data class ContextContractMismatch(
        val field: LiveZone2HeartRateContextField,
    ) : LiveZone2HeartRateUnavailableReason

    data class SourceUnavailable(
        val reason: LiveZone2HeartRateSourceReason,
    ) : LiveZone2HeartRateUnavailableReason

    data object MissingEquipmentDescriptor : LiveZone2HeartRateUnavailableReason

    data class UnsupportedEquipment(
        val equipmentType: EquipmentType,
    ) : LiveZone2HeartRateUnavailableReason

    data object MissingTelemetry : LiveZone2HeartRateUnavailableReason

    data class InvalidHeartRateSample(
        val reason: LiveZone2HeartRateSampleReason,
    ) : LiveZone2HeartRateUnavailableReason

    data class EvaluatorFailure(
        val reason: LiveZone2HeartRateEvaluatorReason,
    ) : LiveZone2HeartRateUnavailableReason
}

enum class LiveZone2HeartRateContextField {
    PROGRAM_ID,
    INTENDED_SOURCE,
    PREVIEW_STATUS,
    ADVICE_MODE,
    THRESHOLD_MODE,
    HYSTERESIS_STATUS,
}

sealed interface LiveZone2HeartRateSourceReason {
    data object Connecting : LiveZone2HeartRateSourceReason

    data object Disconnected : LiveZone2HeartRateSourceReason

    data class ServiceUnavailable(val reason: String) : LiveZone2HeartRateSourceReason

    data class UnsupportedApi(val apiVersion: Int) : LiveZone2HeartRateSourceReason

    data class EquipmentDisconnected(val status: String?) : LiveZone2HeartRateSourceReason

    data object Ready : LiveZone2HeartRateSourceReason

    data class Stale(val ageMillis: Long) : LiveZone2HeartRateSourceReason
}

sealed interface LiveZone2HeartRateSampleReason {
    data object MissingBpm : LiveZone2HeartRateSampleReason

    data object MissingTimestamp : LiveZone2HeartRateSampleReason

    data class NonPositiveBpm(val value: Int) : LiveZone2HeartRateSampleReason

    data class NegativeTimestamp(val value: Long) : LiveZone2HeartRateSampleReason
}

sealed interface LiveZone2HeartRateEvaluatorReason {
    data object MissingTarget : LiveZone2HeartRateEvaluatorReason

    data object MissingHeartRate : LiveZone2HeartRateEvaluatorReason

    data class InvalidNowElapsedRealtimeMillis(val value: Long) : LiveZone2HeartRateEvaluatorReason

    data class InvalidStaleAfterMillis(val value: Long) : LiveZone2HeartRateEvaluatorReason

    data class FutureSampleTimestamp(
        val sampleElapsedRealtimeMillis: Long,
        val nowElapsedRealtimeMillis: Long,
    ) : LiveZone2HeartRateEvaluatorReason
}

data class LiveWorkoutSummary(
    val programId: ProgramId,
    val elapsedSeconds: Int,
    val totalDurationSeconds: Int,
    val programTitle: String,
    val previewMode: ProgramPreviewMode,
    val runWalkSummary: LiveWorkoutRunWalkSummary? = null,
    val verticalContext: LiveVerticalWorkoutContext? = null,
    val zone2Context: LiveZone2HeartRateContext? = null,
)
