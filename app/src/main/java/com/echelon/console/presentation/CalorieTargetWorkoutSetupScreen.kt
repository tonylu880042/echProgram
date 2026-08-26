package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import java.util.Locale

private val CalorieTargetWarning = Color(0xFFFFC857)
private val CalorieTargetError = Color(0xFFFF6B6B)

@Composable
internal fun CalorieTargetWorkoutSetupScreen(
    state: ProgramSetupUiState.CalorieTargetConfiguring,
    onAction: (ProgramSetupAction) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CalorieTargetHeading(state.detail.promise)
            CalorieTargetDisclosure(state.representativeProfileDuration.value)
            CalorieTargetChoices(state, onAction)
            CalorieTargetProposal(state)
            CalorieTargetCapabilities(state)
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = CalorieTargetError,
                    fontSize = 14.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "CALORIE TARGET SETUP ERROR"
                    },
                )
            }
            CalorieTargetActionButton(
                label = "START CALORIE TARGET PREVIEW",
                onClick = { onAction(ProgramSetupAction.StartCalorieTargetPreview) },
            )
        }
    }
}

@Composable
private fun CalorieTargetHeading(promise: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "CONFIGURE CALORIE TARGET",
            color = PrimaryText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "CALORIE TARGET", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = promise, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun CalorieTargetDisclosure(representativeProfileDurationMinutes: Int) {
    CalorieTargetCard(label = "PREVIEW CONTRACT", borderColor = CalorieTargetWarning) {
        Text(
            text = "CALORIES ARE ESTIMATES",
            color = CalorieTargetWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "$representativeProfileDurationMinutes MIN REPRESENTATIVE PROFILE",
            color = PrimaryText,
            fontSize = 12.sp,
        )
        Text(text = "FITOS SNAPSHOT CALORIES", color = PrimaryText, fontSize = 12.sp)
        Text(text = "UNIT SEMANTICS UNCONFIRMED", color = MutedText, fontSize = 12.sp)
        Text(text = "SESSION RESET UNCONFIRMED", color = MutedText, fontSize = 12.sp)
        Text(text = "COMPLETION AUTHORITY NOT APPROVED", color = MutedText, fontSize = 12.sp)
        Text(text = "DISPLAY ONLY", color = MutedText, fontSize = 12.sp)
        Text(text = "NO TARGET PROGRESS", color = MutedText, fontSize = 12.sp)
        Text(text = "PREVIEW ONLY", color = MutedText, fontSize = 12.sp)
        Text(text = "NO DEVICE COMMANDS", color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun CalorieTargetChoices(
    state: ProgramSetupUiState.CalorieTargetConfiguring,
    onAction: (ProgramSetupAction) -> Unit,
) {
    CalorieTargetCard(label = "SELECT TARGET") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CalorieTargetOptions.forEach { option ->
                CalorieTargetOptionButton(
                    option = option,
                    selected = state.selectedTarget?.target == option,
                    onClick = { onAction(ProgramSetupAction.SelectCalorieTarget(option)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CalorieTargetOptionButton(
    option: CalorieTargetOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) CarbonHigh else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Cyan else RuleColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Select calorie target ${option.estimatedKcal} calories"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${option.estimatedKcal} CAL EST",
            color = if (selected) Cyan else PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CalorieTargetProposal(state: ProgramSetupUiState.CalorieTargetConfiguring) {
    CalorieTargetCard(label = "TARGET PROPOSAL") {
        val selection = state.selectedTarget
        if (selection == null) {
            Text(
                text = "SELECT ONE TARGET TO VIEW ITS PROPOSED MAX TIME",
                color = MutedText,
                fontSize = 12.sp,
            )
        } else {
            Text(
                text = "${selection.estimatedKcal} CAL TARGET",
                color = CalorieTargetWarning,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "PROPOSED MAX TIME: ${selection.proposedMaxTime.minutes} MIN",
                color = PrimaryText,
                fontSize = 12.sp,
            )
            Text(text = "NOT SESSION DURATION", color = MutedText, fontSize = 12.sp)
            Text(text = "NOT CLIENT APPROVED", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CalorieTargetCapabilities(state: ProgramSetupUiState.CalorieTargetConfiguring) {
    val effectiveSpeed = SpeedTenths(minOf(state.userMaxSpeed.value, state.machineMaxSpeed.value))
    val effectiveIncline = InclineTenths(minOf(state.userMaxIncline.value, state.machineMaxIncline.value))
    CalorieTargetCard(label = "CAPABILITIES") {
        CalorieTargetReadoutRow(
            label = "USER CAP",
            value = "${state.userMaxSpeed.displaySpeed()} MPH / ${state.userMaxIncline.displayIncline()}%",
        )
        CalorieTargetReadoutRow(
            label = "MACHINE CAP",
            value = "${state.machineMaxSpeed.displaySpeed()} MPH / " +
                "${state.machineMaxIncline.displayIncline()}%",
        )
        CalorieTargetReadoutRow(
            label = "EFFECTIVE CAP",
            value = "${effectiveSpeed.displaySpeed()} MPH / ${effectiveIncline.displayIncline()}%",
        )
    }
}

@Composable
private fun CalorieTargetReadoutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MutedText, fontSize = 11.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun CalorieTargetCard(
    label: String,
    modifier: Modifier = Modifier,
    borderColor: Color = RuleColor,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun CalorieTargetActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(CarbonHigh, RoundedCornerShape(4.dp))
            .border(1.dp, Cyan, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun SpeedTenths.displaySpeed(): String = "%.1f".format(Locale.US, value / 10f)

private fun InclineTenths.displayIncline(): String = "%.1f".format(Locale.US, value / 10f)
