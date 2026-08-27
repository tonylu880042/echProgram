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
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalProfileSegmentRole
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutDraft
import java.util.Locale

private val VerticalWarning = Color(0xFFFFC857)
private val VerticalError = Color(0xFFFF6B6B)

@Composable
internal fun VerticalWorkoutConfiguringScreen(
    state: ProgramSetupUiState.VerticalConfiguring,
    onAction: (ProgramSetupAction) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
) {
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        activeDestination = ProgramLibraryDestination.PROGRAMS,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VerticalHeading("CONFIGURE VERTICAL", state.detail.promise)
            VerticalDisclosure()
            VerticalTargetCard(state, onAction)
            VerticalCapsCard(
                userMaxSpeed = state.userMaxSpeed,
                machineMaxSpeed = state.machineMaxSpeed,
                userMaxIncline = state.userMaxIncline,
                machineMaxIncline = state.machineMaxIncline,
            )
            state.errorMessage?.let { message ->
                Text(text = message, color = VerticalError, fontSize = 14.sp)
            }
            ConsoleSetupActionButton(
                label = "GENERATE VERTICAL PREVIEW",
                onClick = { onAction(ProgramSetupAction.GenerateVerticalPreview) },
                variant = ConsoleSetupActionButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
internal fun VerticalWorkoutDraftPreviewScreen(
    state: ProgramSetupUiState.VerticalDraftPreview,
    onAction: (ProgramSetupAction) -> Unit,
    onBack: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
) {
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        activeDestination = ProgramLibraryDestination.PROGRAMS,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VerticalHeading("VERTICAL DRAFT PREVIEW", state.detail.title)
            VerticalDisclosure()
            VerticalPreviewMetadata(state.draft)
            VerticalProfileCard(state.draft)
            state.errorMessage?.let { message ->
                Text(text = message, color = VerticalError, fontSize = 14.sp)
            }
            ConsoleSetupActionButton(
                label = "BACK TO VERTICAL SETTINGS",
                onClick = onBack,
                variant = ConsoleSetupActionButtonVariant.SECONDARY,
            )
            ConsoleSetupActionButton(
                label = "ACCEPT VERTICAL PLAN",
                onClick = { onAction(ProgramSetupAction.AcceptVerticalPlan) },
                variant = ConsoleSetupActionButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun VerticalHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text = "VERTICAL", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = subtitle, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun VerticalDisclosure() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, VerticalWarning),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "REPRESENTATIVE 50-MIN SESSION",
                color = VerticalWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "PREVIEW ONLY", color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = "NO DEVICE COMMANDS", color = MutedText, fontSize = 12.sp)
            Text(text = "ELEVATION SOURCE UNAVAILABLE", color = MutedText, fontSize = 12.sp)
            Text(text = "PROGRESS NOT CALCULATED", color = MutedText, fontSize = 12.sp)
            Text(text = "NO ELEVATION FORMULA", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun VerticalTargetCard(
    state: ProgramSetupUiState.VerticalConfiguring,
    onAction: (ProgramSetupAction) -> Unit,
) {
    VerticalCard(label = "TARGET") {
        Text(
            text = state.target.verticalLabel(),
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "PROPOSED LIMIT ${state.target.proposedTimeLimit.minutes} MIN · NOT SESSION DURATION",
            color = VerticalWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VerticalTargetOptions.forEach { target ->
                VerticalTargetButton(
                    target = target,
                    selected = target == state.target,
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ProgramSetupAction.SetVerticalTarget(target)) },
                )
            }
        }
    }
}

@Composable
private fun VerticalTargetButton(
    target: VerticalTarget,
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
                contentDescription = target.selectionDescription()
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = target.buttonLabel(),
            color = if (selected) Cyan else PrimaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VerticalCapsCard(
    userMaxSpeed: SpeedTenths,
    machineMaxSpeed: SpeedTenths,
    userMaxIncline: InclineTenths,
    machineMaxIncline: InclineTenths,
) {
    val effectiveSpeed = SpeedTenths(minOf(userMaxSpeed.value, machineMaxSpeed.value))
    val effectiveIncline = InclineTenths(minOf(userMaxIncline.value, machineMaxIncline.value))
    VerticalCard(label = "CAPABILITIES") {
        VerticalRow("USER CAP", "${userMaxSpeed.verticalSpeed()} MPH / ${userMaxIncline.verticalIncline()}%")
        VerticalRow("MACHINE CAP", "${machineMaxSpeed.verticalSpeed()} MPH / ${machineMaxIncline.verticalIncline()}%")
        VerticalRow("EFFECTIVE CAP", "${effectiveSpeed.verticalSpeed()} MPH / ${effectiveIncline.verticalIncline()}%")
    }
}

@Composable
private fun VerticalPreviewMetadata(draft: VerticalWorkoutDraft) {
    VerticalCard(label = "PREVIEW CONTRACT") {
        Text(
            text = "TARGET ${draft.metadata.target.verticalLabel()}",
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "PROPOSED LIMIT ${draft.metadata.proposedTimeLimit.minutes} MIN · NOT SESSION DURATION",
            color = VerticalWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        VerticalRow("MODE", "REPRESENTATIVE PROFILE")
        VerticalRow("CONTROL", "PREVIEW ONLY")
        VerticalRow("ELEVATION SOURCE", "UNAVAILABLE")
        VerticalRow("PROGRESS", "NOT CALCULATED")
        VerticalRow(
            "EFFECTIVE CAP",
            "${draft.metadata.effectiveSpeedCap.verticalSpeed()} MPH / " +
                "${draft.metadata.effectiveInclineCap.verticalIncline()}%",
        )
        if (draft.metadata.wasClamped) {
            Text(
                text = "TARGETS CLAMPED TO EFFECTIVE CAPS",
                color = VerticalWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            draft.metadata.clampDisclosure.forEach { disclosure ->
                Text(
                    text = "SEGMENT ${disclosure.segmentIndex + 1} ${disclosure.role.displayLabel()} · " +
                        disclosure.dimensions.joinToString(" / ") { it.name },
                    color = MutedText,
                    fontSize = 11.sp,
                )
            }
        } else {
            Text(text = "NO TARGET CLAMPS", color = MutedText, fontSize = 12.sp)
        }
        Text(
            text = "NO DEVICE COMMANDS",
            color = VerticalWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "NO ELEVATION FORMULA",
            color = VerticalWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun VerticalProfileCard(draft: VerticalWorkoutDraft) {
    VerticalCard(label = "PROFILE · ${draft.segments.size} SEGMENTS") {
        draft.segments.forEach { segment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1.6f)) {
                    Text(
                        text = segment.role.displayLabel(),
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = segment.summary.name, color = PrimaryText, fontSize = 12.sp)
                }
                Text(
                    text = "${segment.summary.duration.value} MIN",
                    modifier = Modifier.weight(0.7f),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = "${segment.summary.speed.verticalSpeed()} MPH / " +
                        "${segment.summary.incline.verticalIncline()}%",
                    modifier = Modifier.weight(1.2f),
                    color = PrimaryText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun VerticalCard(label: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = label, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun VerticalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = MutedText, fontSize = 12.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

private fun VerticalTarget.buttonLabel(): String = when (this) {
    VerticalTarget.FIVE_HUNDRED_FEET -> "500 FT"
    VerticalTarget.ONE_THOUSAND_FEET -> "1,000 FT"
    VerticalTarget.TWO_THOUSAND_FEET -> "2,000 FT"
    VerticalTarget.VERTICAL_MILE -> "5,280 FT · VERTICAL MILE"
}

private fun VerticalTarget.selectionDescription(): String =
    "Select vertical target ${String.format(Locale.US, "%,d", feet)} feet"

private fun VerticalTarget.verticalLabel(): String = buttonLabel()

private fun VerticalProfileSegmentRole.displayLabel(): String = name.replace('_', ' ')

private fun SpeedTenths.verticalSpeed(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.verticalIncline(): String = String.format(Locale.US, "%.1f", value / 10f)
