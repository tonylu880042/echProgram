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
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSampleFreshness
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetMaxTimeStatus
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.EquipmentType
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun LiveCalorieTargetPanel(
    context: LiveCalorieTargetContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = "CALORIE TARGET PREVIEW",
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        CaloriePanelPair(
            first = "TARGET" to context.target.target.liveLabel(),
            second = "TARGET ORIGIN" to context.target.source.liveLabel(),
        )
        CaloriePanelPair(
            first = "REPRESENTATIVE PROFILE" to "${context.representativeProfileDuration.value} MIN",
            second = "PROPOSED MAX TIME" to context.target.proposedMaxTime.minutes.toString() + " MIN",
        )
        CaloriePanelPair(
            first = "PROPOSAL MEANING" to "NOT SESSION DURATION",
            second = "PROPOSAL STATUS" to context.target.proposedMaxTime.status.liveLabel(),
            secondValueColor = LiveWorkoutAmber,
        )
        CaloriePanelPair(
            first = "ESTIMATE" to context.estimateStatus.liveLabel(),
            second = "SOURCE" to context.source.liveLabel(),
        )
        CaloriePanelPair(
            first = "UNIT" to context.unitSemantics.liveLabel(),
            second = "SESSION RESET" to context.sessionResetSemantics.liveLabel(),
        )
        CaloriePanelPair(
            first = "COMPLETION" to context.completionAuthority.liveLabel(),
            second = "PROGRESS" to context.progressSemantics.liveLabel(),
        )
        CaloriePanelPair(
            first = "PREVIEW" to context.previewStatus.liveLabel(),
            second = "COMMANDS" to context.deviceCommandStatus.liveLabel(),
        )
        when (val reading = context.reading) {
            is LiveCalorieTargetReading.Evaluated -> CalorieEvaluatedReading(reading)
            is LiveCalorieTargetReading.Unavailable -> CalorieUnavailableReading(reading)
        }
    }
}

@Composable
private fun CalorieEvaluatedReading(
    reading: LiveCalorieTargetReading.Evaluated,
) {
    val readingLabel = when (reading.freshness) {
        CalorieSampleFreshness.FRESH -> "FITOS SNAPSHOT DISPLAY"
        CalorieSampleFreshness.STALE -> "LAST FITOS SNAPSHOT DISPLAY"
    }
    CaloriePanelPair(
        first = readingLabel to formatCalorieTargetDisplay(reading.displayValue),
        second = "SAMPLE AGE" to formatCalorieTargetSampleAge(reading.sampleAgeMillis),
        firstValueColor = if (reading.freshness == CalorieSampleFreshness.STALE) {
            LiveWorkoutAmber
        } else {
            PrimaryText
        },
    )
    CaloriePanelPair(
        first = "FRESHNESS" to reading.freshness.liveLabel(),
        second = "READING" to "FITOS SNAPSHOT",
        firstValueColor = if (reading.freshness == CalorieSampleFreshness.STALE) {
            LiveWorkoutAmber
        } else {
            PrimaryText
        },
    )
}

@Composable
private fun CalorieUnavailableReading(
    reading: LiveCalorieTargetReading.Unavailable,
) {
    Text(
        text = "CALORIES DISPLAY UNAVAILABLE",
        color = LiveWorkoutAmber,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = formatLiveCalorieTargetUnavailableReason(reading.reason),
        color = MutedText,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.heightIn(min = 20.dp),
    )
}

@Composable
private fun CaloriePanelRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = PrimaryText,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 10.sp)
        Text(text = value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

@Composable
private fun CaloriePanelPair(
    first: Pair<String, String>,
    second: Pair<String, String>,
    firstValueColor: Color = PrimaryText,
    secondValueColor: Color = PrimaryText,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CaloriePanelRow(
            label = first.first,
            value = first.second,
            modifier = Modifier.weight(1f),
            valueColor = firstValueColor,
        )
        CaloriePanelRow(
            label = second.first,
            value = second.second,
            modifier = Modifier.weight(1f),
            valueColor = secondValueColor,
        )
    }
}

internal fun formatCalorieTargetDisplay(displayValue: Double): String =
    NumberFormat.getNumberInstance(Locale.US).apply {
        isGroupingUsed = true
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(displayValue)

internal fun formatCalorieTargetSampleAge(sampleAgeMillis: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(sampleAgeMillis) + " MS"

internal fun formatLiveCalorieTargetUnavailableReason(
    reason: LiveCalorieTargetUnavailableReason,
): String = when (reason) {
    is LiveCalorieTargetUnavailableReason.EvaluationSnapshotMismatch ->
        "EVALUATION SNAPSHOT MISMATCH: ${reason.field.liveLabel()}"
    is LiveCalorieTargetUnavailableReason.ContextContractMismatch ->
        "CONTEXT CONTRACT MISMATCH: ${reason.field.liveLabel()}"
    is LiveCalorieTargetUnavailableReason.SourceUnavailable -> reason.reason.liveLabel()
    LiveCalorieTargetUnavailableReason.MissingEquipmentDescriptor ->
        "EQUIPMENT DESCRIPTOR MISSING"
    is LiveCalorieTargetUnavailableReason.UnsupportedEquipment ->
        "UNSUPPORTED EQUIPMENT: ${reason.equipmentType.liveLabel()}"
    LiveCalorieTargetUnavailableReason.MissingTelemetry -> "EQUIPMENT TELEMETRY MISSING"
    is LiveCalorieTargetUnavailableReason.InvalidCalorieSample ->
        "INVALID CALORIE SAMPLE: ${reason.reason.liveLabel()}"
    is LiveCalorieTargetUnavailableReason.EvaluatorFailure ->
        "CALORIE EVALUATION FAILED: ${reason.reason.liveLabel()}"
}

private fun CalorieTargetOption.liveLabel(): String = "$estimatedKcal CAL EST"

private fun CalorieTargetSource.liveLabel(): String = when (this) {
    CalorieTargetSource.USER_SELECTED -> "USER SELECTED"
}

private fun CalorieTargetMaxTimeStatus.liveLabel(): String = when (this) {
    CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED -> "NOT CLIENT APPROVED"
}

private fun CalorieEstimateStatus.liveLabel(): String = when (this) {
    CalorieEstimateStatus.ESTIMATED -> "ESTIMATED"
}

private fun CalorieTelemetrySource.liveLabel(): String = when (this) {
    CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES ->
        "FITOS EQUIPMENT SNAPSHOT CALORIES"
}

private fun CalorieUnitSemantics.liveLabel(): String = when (this) {
    CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED -> "UNIT SEMANTICS UNCONFIRMED"
}

private fun CalorieSessionResetSemantics.liveLabel(): String = when (this) {
    CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED ->
        "SESSION RESET UNCONFIRMED"
}

private fun CalorieCompletionAuthority.liveLabel(): String = when (this) {
    CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED ->
        "COMPLETION AUTHORITY NOT APPROVED"
}

private fun CalorieProgressSemantics.liveLabel(): String = when (this) {
    CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS ->
        "DISPLAY ONLY / NO TARGET PROGRESS"
}

private fun CaloriePreviewStatus.liveLabel(): String = when (this) {
    CaloriePreviewStatus.PREVIEW_ONLY -> "PREVIEW ONLY"
}

private fun CalorieDeviceCommandStatus.liveLabel(): String = when (this) {
    CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS -> "NO DEVICE COMMANDS"
}

private fun CalorieSampleFreshness.liveLabel(): String = when (this) {
    CalorieSampleFreshness.FRESH -> "FRESH"
    CalorieSampleFreshness.STALE -> "STALE"
}

private fun LiveCalorieTargetSnapshotField.liveLabel(): String = when (this) {
    LiveCalorieTargetSnapshotField.TARGET -> "TARGET"
    LiveCalorieTargetSnapshotField.ESTIMATE_STATUS -> "ESTIMATE STATUS"
    LiveCalorieTargetSnapshotField.TELEMETRY_SOURCE -> "TELEMETRY SOURCE"
    LiveCalorieTargetSnapshotField.UNIT_SEMANTICS -> "UNIT SEMANTICS"
    LiveCalorieTargetSnapshotField.SESSION_RESET_SEMANTICS -> "SESSION RESET SEMANTICS"
    LiveCalorieTargetSnapshotField.COMPLETION_AUTHORITY -> "COMPLETION AUTHORITY"
    LiveCalorieTargetSnapshotField.PROGRESS_SEMANTICS -> "PROGRESS SEMANTICS"
    LiveCalorieTargetSnapshotField.PREVIEW_STATUS -> "PREVIEW STATUS"
    LiveCalorieTargetSnapshotField.DEVICE_COMMAND_STATUS -> "DEVICE COMMAND STATUS"
}

private fun LiveCalorieTargetContextField.liveLabel(): String = when (this) {
    LiveCalorieTargetContextField.PROGRAM_ID -> "PROGRAM ID"
    LiveCalorieTargetContextField.TARGET_SOURCE -> "TARGET SOURCE"
    LiveCalorieTargetContextField.ESTIMATE_STATUS -> "ESTIMATE STATUS"
    LiveCalorieTargetContextField.TELEMETRY_SOURCE -> "TELEMETRY SOURCE"
    LiveCalorieTargetContextField.UNIT_SEMANTICS -> "UNIT SEMANTICS"
    LiveCalorieTargetContextField.SESSION_RESET_SEMANTICS -> "SESSION RESET SEMANTICS"
    LiveCalorieTargetContextField.COMPLETION_AUTHORITY -> "COMPLETION AUTHORITY"
    LiveCalorieTargetContextField.PROGRESS_SEMANTICS -> "PROGRESS SEMANTICS"
    LiveCalorieTargetContextField.PREVIEW_STATUS -> "PREVIEW STATUS"
    LiveCalorieTargetContextField.DEVICE_COMMAND_STATUS -> "DEVICE COMMAND STATUS"
}

private fun LiveCalorieTargetSourceReason.liveLabel(): String = when (this) {
    LiveCalorieTargetSourceReason.Connecting -> "FITOS SOURCE CONNECTING"
    LiveCalorieTargetSourceReason.Disconnected -> "FITOS SOURCE DISCONNECTED"
    is LiveCalorieTargetSourceReason.ServiceUnavailable ->
        "FITOS SERVICE UNAVAILABLE: $reason"
    is LiveCalorieTargetSourceReason.UnsupportedApi ->
        "FITOS API UNSUPPORTED: VERSION $apiVersion"
    is LiveCalorieTargetSourceReason.EquipmentDisconnected ->
        status?.let { "EQUIPMENT DISCONNECTED: $it" } ?: "EQUIPMENT DISCONNECTED"
    LiveCalorieTargetSourceReason.Ready -> "FITOS SOURCE READY"
    is LiveCalorieTargetSourceReason.Stale ->
        "FITOS CONNECTION STALE: ${formatCalorieTargetSampleAge(ageMillis)}"
}

private fun EquipmentType.liveLabel(): String = when (this) {
    EquipmentType.RUN -> "RUN"
    EquipmentType.BIKE -> "BIKE"
    EquipmentType.ROW -> "ROW"
    EquipmentType.STAIR_MILL -> "STAIR MILL"
    EquipmentType.UNKNOWN -> "UNKNOWN"
}

private fun LiveCalorieTargetSampleReason.liveLabel(): String = when (this) {
    LiveCalorieTargetSampleReason.MissingDisplayValue -> "DISPLAY VALUE MISSING"
    is LiveCalorieTargetSampleReason.NonFiniteDisplayValue ->
        "DISPLAY VALUE NON-FINITE: ${value}"
    is LiveCalorieTargetSampleReason.NegativeDisplayValue ->
        "DISPLAY VALUE NEGATIVE: ${value}"
    LiveCalorieTargetSampleReason.MissingTimestamp -> "TIMESTAMP MISSING"
    is LiveCalorieTargetSampleReason.NegativeTimestamp -> "TIMESTAMP NEGATIVE: ${value}"
}

private fun LiveCalorieTargetEvaluatorReason.liveLabel(): String = when (this) {
    LiveCalorieTargetEvaluatorReason.MissingTarget -> "TARGET MISSING"
    LiveCalorieTargetEvaluatorReason.MissingSample -> "CALORIE SAMPLE MISSING"
    is LiveCalorieTargetEvaluatorReason.InvalidNowElapsedRealtimeMillis ->
        "CLOCK VALUE INVALID: ${value} MS"
    is LiveCalorieTargetEvaluatorReason.InvalidStaleAfterMillis ->
        "FRESHNESS WINDOW INVALID: ${value} MS"
    is LiveCalorieTargetEvaluatorReason.FutureSampleTimestamp ->
        "SAMPLE IS FROM THE FUTURE: ${sampleElapsedRealtimeMillis} MS > " +
            "${nowElapsedRealtimeMillis} MS"
}
