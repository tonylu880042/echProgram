package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import java.util.Locale

private val Zone2Warning = Color(0xFFFFC857)
private val Zone2Error = Color(0xFFFF6B6B)

@Composable
internal fun Zone2WorkoutSetupScreen(
    state: ProgramSetupUiState.Zone2Configuring,
    onAction: (ProgramSetupAction) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    equipmentState: EquipmentReadState = EquipmentReadState(),
    modifier: Modifier = Modifier,
) {
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        activeDestination = ProgramLibraryDestination.PROGRAMS,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Zone2Heading(state.detail.promise)
            Zone2Disclosure()
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Zone2TargetCard(state, onAction, Modifier.weight(1f))
                        Zone2DurationCard(state, onAction, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Zone2TargetCard(state, onAction)
                        Zone2DurationCard(state, onAction)
                    }
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Zone2SourceCard(equipmentState, Modifier.weight(1f))
                        Zone2CapsCard(state, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Zone2SourceCard(equipmentState)
                        Zone2CapsCard(state)
                    }
                }
            }
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = Zone2Error,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics { contentDescription = "ZONE 2 SETUP ERROR" },
                )
            }
            ConsoleSetupActionButton(
                label = "START ZONE 2 PREVIEW",
                onClick = { onAction(ProgramSetupAction.StartZone2Preview) },
                variant = ConsoleSetupActionButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun Zone2Heading(promise: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "CONFIGURE ZONE 2",
            color = PrimaryText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "ZONE 2", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = promise, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun Zone2Disclosure() {
    ConsoleSetupCard(label = "PREVIEW CONTRACT", borderColor = Zone2Warning) {
        Text(text = "PREVIEW ONLY", color = Zone2Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = "USER-CONFIRMED TARGET", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = "NO AGE OR MAX-HR FORMULA", color = MutedText, fontSize = 12.sp)
        Text(text = "ADVISORY ONLY", color = MutedText, fontSize = 12.sp)
        Text(text = "NO DEVICE COMMANDS", color = MutedText, fontSize = 12.sp)
        Text(
            text = "You confirm the target range; FitOS telemetry does not supply it or evaluate thresholds here.",
            color = MutedText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Zone2TargetCard(
    state: ProgramSetupUiState.Zone2Configuring,
    onAction: (ProgramSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConsoleSetupCard(label = "TARGET HEART RATE", modifier = modifier) {
        Text(
            text = "ENTER THE RANGE YOU CONFIRM",
            color = Zone2Warning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Zone2BpmField(
                label = "LOWER TARGET BPM",
                text = state.lowerBpmText,
                onTextChange = { onAction(ProgramSetupAction.SetZone2LowerBpm(it)) },
                modifier = Modifier.weight(1f),
            )
            Zone2BpmField(
                label = "UPPER TARGET BPM",
                text = state.upperBpmText,
                onTextChange = { onAction(ProgramSetupAction.SetZone2UpperBpm(it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Zone2BpmField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text = label, color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(CarbonHigh, RoundedCornerShape(4.dp))
                .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics { contentDescription = label },
            textStyle = TextStyle(
                color = PrimaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(text = "BPM", color = MutedText, fontSize = 14.sp)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun Zone2DurationCard(
    state: ProgramSetupUiState.Zone2Configuring,
    onAction: (ProgramSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConsoleSetupCard(label = "SESSION DURATION", modifier = modifier) {
        Zone2DurationOptions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { duration ->
                    Zone2DurationButton(
                        duration = duration,
                        selected = duration == state.duration,
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(ProgramSetupAction.SetZone2Duration(duration)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Zone2DurationButton(
    duration: DurationMinutes,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) CarbonHigh else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Cyan else RuleColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Select Zone 2 duration ${duration.value} minutes"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${duration.value} MIN",
            color = if (selected) Cyan else PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Zone2SourceCard(
    equipmentState: EquipmentReadState,
    modifier: Modifier = Modifier,
) {
    ConsoleSetupCard(label = "SOURCE / READINESS", modifier = modifier) {
        Zone2ReadoutRow(label = "TARGET SOURCE", value = "USER CONFIRMED")
        Zone2ReadoutRow(label = "INTENDED HR SOURCE", value = "FITOS EQUIPMENT SNAPSHOT")
        Zone2ReadoutRow(label = "FITOS STATUS", value = equipmentState.connection.zone2Status())
        val bpm = equipmentState.telemetry
            ?.heartRateBpm
            ?.takeIf {
                equipmentState.connection == EquipmentConnection.Ready &&
                    equipmentState.equipment?.equipmentType == EquipmentType.RUN &&
                    it > 0
            }
        Zone2ReadoutRow(label = "CURRENT EQUIPMENT BPM", value = bpm?.let { "$it BPM" } ?: "NOT AVAILABLE")
        Text(
            text = "Telemetry is read-only and does not change the user-confirmed target.",
            color = MutedText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Zone2CapsCard(
    state: ProgramSetupUiState.Zone2Configuring,
    modifier: Modifier = Modifier,
) {
    val effectiveSpeed = SpeedTenths(minOf(state.userMaxSpeed.value, state.machineMaxSpeed.value))
    val effectiveIncline = InclineTenths(minOf(state.userMaxIncline.value, state.machineMaxIncline.value))
    ConsoleSetupCard(label = "CAPABILITIES", modifier = modifier) {
        Zone2ReadoutRow(
            label = "USER CAP",
            value = "${state.userMaxSpeed.zone2Speed()} MPH / ${state.userMaxIncline.zone2Incline()}%",
        )
        Zone2ReadoutRow(
            label = "MACHINE CAP",
            value = "${state.machineMaxSpeed.zone2Speed()} MPH / ${state.machineMaxIncline.zone2Incline()}%",
        )
        Zone2ReadoutRow(
            label = "EFFECTIVE CAP",
            value = "${effectiveSpeed.zone2Speed()} MPH / ${effectiveIncline.zone2Incline()}%",
        )
    }
}

@Composable
private fun Zone2ReadoutRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MutedText, fontSize = 11.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

private fun EquipmentConnection.zone2Status(): String = when (this) {
    EquipmentConnection.Connecting -> "CONNECTING TO FITOS"
    EquipmentConnection.Disconnected -> "EQUIPMENT NOT CONNECTED"
    EquipmentConnection.Ready -> "FITOS TELEMETRY READY"
    is EquipmentConnection.EquipmentDisconnected -> "EQUIPMENT DISCONNECTED"
    is EquipmentConnection.ServiceUnavailable -> "EQUIPMENT UNAVAILABLE"
    is EquipmentConnection.UnsupportedApi -> "FITOS API UNSUPPORTED"
    is EquipmentConnection.Stale -> "TELEMETRY STALE"
}

private fun SpeedTenths.zone2Speed(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.zone2Incline(): String = String.format(Locale.US, "%.1f", value / 10f)
