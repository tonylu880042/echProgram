package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import java.util.Locale

@Composable
internal fun LiveZone2HeartRatePanel(
    context: LiveZone2HeartRateContext,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "ZONE 2 HEART RATE",
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Zone2PanelPair(
            first = "TARGET" to "${context.target.lowerBpm}–${context.target.upperBpm} BPM",
            second = "SOURCE" to context.intendedSource.liveLabel(),
        )
        Zone2PanelPair(
            first = "TARGET ORIGIN" to "USER-CONFIRMED TARGET",
            second = "PREVIEW STATUS" to context.previewStatus.liveLabel(),
        )
        Zone2PanelPair(
            first = "ADVICE MODE" to context.adviceMode.liveLabel(),
            second = "THRESHOLD MODE" to context.thresholdMode.liveLabel(),
        )
        Zone2PanelPair(
            first = "HYSTERESIS" to context.hysteresisStatus.liveLabel(),
            second = "" to "NO DEVICE COMMANDS",
            secondValueColor = LiveWorkoutAmber,
        )
        when (val reading = context.reading) {
            is LiveZone2HeartRateReading.Evaluated -> Zone2EvaluatedReading(reading)
            is LiveZone2HeartRateReading.Unavailable -> Zone2UnavailableReading(reading)
        }
    }
}

@Composable
private fun Zone2EvaluatedReading(
    reading: LiveZone2HeartRateReading.Evaluated,
) {
    Zone2PanelPair(
        first = (
            if (reading.status == Zone2HeartRateStatus.HR_SIGNAL_LOST) {
                "LAST HEART RATE"
            } else {
                "CURRENT HEART RATE"
            }
        ) to "${reading.currentBpm} BPM",
        firstValueColor = reading.status.liveColor(),
        second = "SAMPLE AGE" to formatZone2SampleAge(reading.sampleAgeMillis),
    )
    Zone2PanelPair(
        first = "STATUS" to reading.status.liveLabel(),
        firstValueColor = reading.status.liveColor(),
        second = "ADVICE" to reading.advice.liveLabel(),
        secondValueColor = LiveWorkoutAmber,
    )
}

@Composable
private fun Zone2UnavailableReading(
    reading: LiveZone2HeartRateReading.Unavailable,
) {
    Text(
        text = "HEART RATE UNAVAILABLE",
        color = LiveWorkoutAmber,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = formatZone2UnavailableReason(reading.reason),
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.heightIn(min = 20.dp),
    )
}

@Composable
private fun Zone2PanelRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = PrimaryText,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 10.sp)
        Text(text = value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun Zone2PanelPair(
    first: Pair<String, String>,
    second: Pair<String, String>,
    firstValueColor: Color = PrimaryText,
    secondValueColor: Color = PrimaryText,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Zone2PanelRow(
            label = first.first,
            value = first.second,
            modifier = Modifier.weight(1f),
            valueColor = firstValueColor,
        )
        Zone2PanelRow(
            label = second.first,
            value = second.second,
            modifier = Modifier.weight(1f),
            valueColor = secondValueColor,
        )
    }
}

internal fun formatZone2SampleAge(sampleAgeMillis: Long): String =
    String.format(Locale.US, "%,d MS", sampleAgeMillis)

internal fun formatZone2UnavailableReason(
    reason: LiveZone2HeartRateUnavailableReason,
): String = when (reason) {
    is LiveZone2HeartRateUnavailableReason.EvaluationSnapshotMismatch ->
        "EVALUATION SNAPSHOT MISMATCH: ${reason.field.liveLabel()}"

    is LiveZone2HeartRateUnavailableReason.ContextContractMismatch ->
        "CONTEXT CONTRACT MISMATCH: ${reason.field.liveLabel()}"

    is LiveZone2HeartRateUnavailableReason.SourceUnavailable ->
        reason.reason.liveLabel()

    LiveZone2HeartRateUnavailableReason.MissingEquipmentDescriptor ->
        "EQUIPMENT DESCRIPTOR MISSING"

    is LiveZone2HeartRateUnavailableReason.UnsupportedEquipment ->
        "UNSUPPORTED EQUIPMENT: ${reason.equipmentType.liveLabel()}"

    LiveZone2HeartRateUnavailableReason.MissingTelemetry ->
        "EQUIPMENT TELEMETRY MISSING"

    is LiveZone2HeartRateUnavailableReason.InvalidHeartRateSample ->
        "INVALID HEART RATE SAMPLE: ${reason.reason.liveLabel()}"

    is LiveZone2HeartRateUnavailableReason.EvaluatorFailure ->
        "HEART RATE EVALUATION FAILED: ${reason.reason.liveLabel()}"
}

private fun LiveZone2HeartRateSnapshotField.liveLabel(): String = when (this) {
    LiveZone2HeartRateSnapshotField.TARGET -> "TARGET"
    LiveZone2HeartRateSnapshotField.PREVIEW_STATUS -> "PREVIEW STATUS"
    LiveZone2HeartRateSnapshotField.ADVICE_MODE -> "ADVICE MODE"
    LiveZone2HeartRateSnapshotField.THRESHOLD_MODE -> "THRESHOLD MODE"
    LiveZone2HeartRateSnapshotField.HYSTERESIS_STATUS -> "HYSTERESIS STATUS"
}

private fun LiveZone2HeartRateContextField.liveLabel(): String = when (this) {
    LiveZone2HeartRateContextField.PROGRAM_ID -> "PROGRAM ID"
    LiveZone2HeartRateContextField.INTENDED_SOURCE -> "INTENDED SOURCE"
    LiveZone2HeartRateContextField.PREVIEW_STATUS -> "PREVIEW STATUS"
    LiveZone2HeartRateContextField.ADVICE_MODE -> "ADVICE MODE"
    LiveZone2HeartRateContextField.THRESHOLD_MODE -> "THRESHOLD MODE"
    LiveZone2HeartRateContextField.HYSTERESIS_STATUS -> "HYSTERESIS STATUS"
}

private fun LiveZone2HeartRateSourceReason.liveLabel(): String = when (this) {
    LiveZone2HeartRateSourceReason.Connecting -> "FITOS SOURCE CONNECTING"
    LiveZone2HeartRateSourceReason.Disconnected -> "FITOS SOURCE DISCONNECTED"
    is LiveZone2HeartRateSourceReason.ServiceUnavailable ->
        "FITOS SERVICE UNAVAILABLE: ${reason}"

    is LiveZone2HeartRateSourceReason.UnsupportedApi ->
        "FITOS API UNSUPPORTED: VERSION ${apiVersion}"

    is LiveZone2HeartRateSourceReason.EquipmentDisconnected ->
        status?.let { "EQUIPMENT DISCONNECTED: $it" } ?: "EQUIPMENT DISCONNECTED"

    LiveZone2HeartRateSourceReason.Ready -> "FITOS SOURCE READY"
    is LiveZone2HeartRateSourceReason.Stale -> "FITOS SAMPLE STALE: ${ageMillis} MS OLD"
}

private fun EquipmentType.liveLabel(): String = when (this) {
    EquipmentType.RUN -> "RUN"
    EquipmentType.BIKE -> "BIKE"
    EquipmentType.ROW -> "ROW"
    EquipmentType.STAIR_MILL -> "STAIR MILL"
    EquipmentType.UNKNOWN -> "UNKNOWN"
}

private fun LiveZone2HeartRateSampleReason.liveLabel(): String = when (this) {
    LiveZone2HeartRateSampleReason.MissingBpm -> "BPM MISSING"
    LiveZone2HeartRateSampleReason.MissingTimestamp -> "TIMESTAMP MISSING"
    is LiveZone2HeartRateSampleReason.NonPositiveBpm -> "BPM NOT POSITIVE: ${value}"
    is LiveZone2HeartRateSampleReason.NegativeTimestamp -> "TIMESTAMP NEGATIVE: ${value}"
}

private fun LiveZone2HeartRateEvaluatorReason.liveLabel(): String = when (this) {
    LiveZone2HeartRateEvaluatorReason.MissingTarget -> "TARGET MISSING"
    LiveZone2HeartRateEvaluatorReason.MissingHeartRate -> "HEART RATE MISSING"
    is LiveZone2HeartRateEvaluatorReason.InvalidNowElapsedRealtimeMillis ->
        "CLOCK VALUE INVALID: ${value} MS"

    is LiveZone2HeartRateEvaluatorReason.InvalidStaleAfterMillis ->
        "FRESHNESS WINDOW INVALID: ${value} MS"

    is LiveZone2HeartRateEvaluatorReason.FutureSampleTimestamp ->
        "SAMPLE IS FROM THE FUTURE: ${sampleElapsedRealtimeMillis} MS > ${nowElapsedRealtimeMillis} MS"
}

private fun Zone2HeartRateIntendedSource.liveLabel(): String = when (this) {
    Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT -> "FITOS EQUIPMENT SNAPSHOT"
}

private fun Zone2HeartRatePreviewStatus.liveLabel(): String = when (this) {
    Zone2HeartRatePreviewStatus.PREVIEW_ONLY -> "PREVIEW ONLY"
}

private fun Zone2HeartRateAdviceMode.liveLabel(): String = when (this) {
    Zone2HeartRateAdviceMode.ADVISORY_ONLY -> "ADVISORY ONLY"
}

private fun Zone2HeartRateThresholdMode.liveLabel(): String = when (this) {
    Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW -> "DIRECT THRESHOLD PREVIEW"
}

private fun Zone2HeartRateHysteresisStatus.liveLabel(): String = when (this) {
    Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED -> "NO HYSTERESIS APPROVED"
}

private fun Zone2HeartRateStatus.liveLabel(): String = when (this) {
    Zone2HeartRateStatus.TOO_LOW -> "TOO LOW"
    Zone2HeartRateStatus.IN_ZONE -> "IN ZONE"
    Zone2HeartRateStatus.TOO_HIGH -> "TOO HIGH"
    Zone2HeartRateStatus.HR_SIGNAL_LOST -> "HR SIGNAL LOST"
}

private fun Zone2HeartRateStatus.liveColor(): Color = when (this) {
    Zone2HeartRateStatus.TOO_LOW -> LiveWorkoutAmber
    Zone2HeartRateStatus.IN_ZONE -> Cyan
    Zone2HeartRateStatus.TOO_HIGH -> LiveWorkoutError
    Zone2HeartRateStatus.HR_SIGNAL_LOST -> LiveWorkoutAmber
}

private fun Zone2HeartRateAdvice.liveLabel(): String = when (this) {
    Zone2HeartRateAdvice.SUGGEST_INCLINE -> "SUGGEST INCLINE MANUALLY"
    Zone2HeartRateAdvice.HOLD -> "HOLD CURRENT EFFORT MANUALLY"
    Zone2HeartRateAdvice.SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE ->
        "REDUCE EFFORT MANUALLY · MANUAL STOP AVAILABLE"

    Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE -> "NO ADJUSTMENT · USE MANUAL MODE"
}
