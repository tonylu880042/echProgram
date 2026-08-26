package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutEffort
import java.util.Locale

private val SurpriseWarning = Color(0xFFFFC857)
private val SurpriseError = Color(0xFFFF6B6B)

@Composable
internal fun SurpriseWorkoutConfiguringScreen(
    state: ProgramSetupUiState.Configuring,
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
            SurpriseHeading(
                title = "CONFIGURE SURPRISE ME",
                promise = state.detail.promise,
            )
            SurpriseDisclosure()
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 900.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        SurpriseDurationCard(state, onAction, Modifier.weight(1f))
                        SurpriseEffortCard(state, onAction, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SurpriseDurationCard(state, onAction)
                        SurpriseEffortCard(state, onAction)
                    }
                }
            }
            SurpriseCapsCard(state)
            state.errorMessage?.let { message ->
                Text(text = message, color = SurpriseError, fontSize = 14.sp)
            }
            SurpriseSetupButton(
                label = "GENERATE PREVIEW",
                onClick = { onAction(ProgramSetupAction.GenerateSurprisePreview) },
                primary = true,
            )
        }
    }
}

@Composable
internal fun SurpriseWorkoutDraftPreviewScreen(
    state: ProgramSetupUiState.DraftPreview,
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
            SurpriseHeading(
                title = "DRAFT PREVIEW",
                promise = state.detail.title,
            )
            PreviewOnlyBanner()
            state.errorMessage?.let { message ->
                Text(text = message, color = SurpriseError, fontSize = 14.sp)
            }
            SurpriseDraftMetadataCard(state.draft)
            SurpriseProfileCard(state.draft)
            SurpriseSetupButton(
                label = "REGENERATE",
                onClick = { onAction(ProgramSetupAction.RegenerateSurprisePreview) },
                primary = false,
            )
            SurpriseSetupButton(
                label = "ACCEPT PLAN",
                onClick = { onAction(ProgramSetupAction.AcceptSurprisePlan) },
                primary = true,
            )
        }
    }
}

@Composable
private fun SurpriseHeading(
    title: String,
    promise: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text = "SURPRISE ME", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = promise, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun SurpriseDisclosure() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurpriseWarning),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "NO HISTORY / PERSONAL PROFILE APPLIED",
                color = SurpriseWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "This preview uses the anonymous baseline profile and does not claim Echelon Adapt.",
                color = MutedText,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SurpriseDurationCard(
    state: ProgramSetupUiState.Configuring,
    onAction: (ProgramSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SurpriseChoiceCard(label = "DURATION", modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SurpriseWorkoutDurationOptions.forEach { duration ->
                SurpriseChoiceButton(
                    label = "${duration.value} MIN",
                    selected = duration == state.duration,
                    description = "Select duration ${duration.value} minutes",
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ProgramSetupAction.SetSurpriseDuration(duration)) },
                )
            }
        }
    }
}

@Composable
private fun SurpriseEffortCard(
    state: ProgramSetupUiState.Configuring,
    onAction: (ProgramSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SurpriseChoiceCard(label = "EFFORT", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurpriseWorkoutEffort.entries.take(2).forEach { effort ->
                    SurpriseChoiceButton(
                        label = effort.name,
                        selected = effort == state.effort,
                        description = "Select effort ${effort.name}",
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(ProgramSetupAction.SetSurpriseEffort(effort)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurpriseWorkoutEffort.entries.drop(2).forEach { effort ->
                    SurpriseChoiceButton(
                        label = effort.name,
                        selected = effort == state.effort,
                        description = "Select effort ${effort.name}",
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(ProgramSetupAction.SetSurpriseEffort(effort)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SurpriseChoiceCard(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RuleColor),
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
private fun SurpriseChoiceButton(
    label: String,
    selected: Boolean,
    description: String,
    modifier: Modifier = Modifier,
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
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (selected) Cyan else PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SurpriseCapsCard(state: ProgramSetupUiState.Configuring) {
    val effectiveSpeed = SpeedTenths(minOf(state.userMaxSpeed.value, state.machineMaxSpeed.value))
    val effectiveIncline = InclineTenths(minOf(state.userMaxIncline.value, state.machineMaxIncline.value))
    SurpriseChoiceCard(label = "CAPS AND PROFILE") {
        CapRow("BASELINE USER CAP", "${state.userMaxSpeed.asDecimal()} MPH / ${state.userMaxIncline.asDecimal()}%")
        CapRow("MACHINE CAP", "${state.machineMaxSpeed.asDecimal()} MPH / ${state.machineMaxIncline.asDecimal()}%")
        CapRow("EFFECTIVE INTERSECTION", "${effectiveSpeed.asDecimal()} MPH / ${effectiveIncline.asDecimal()}%")
        Text(
            text = "PROFILE REVISION: anonymous-baseline-r1",
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SurpriseDraftMetadataCard(draft: SurpriseWorkoutDraft) {
    val profileMinSpeed = draft.profile.minOf { it.speed.value }
    val profileMaxSpeed = draft.profile.maxOf { it.speed.value }
    val profileMinIncline = draft.profile.minOf { it.incline.value }
    val profileMaxIncline = draft.profile.maxOf { it.incline.value }
    SurpriseChoiceCard(label = "PREVIEW CONTRACT") {
        CapRow("DURATION", "${draft.metadata.durationMinutes} MIN")
        CapRow("EFFORT", draft.metadata.effort.name)
        CapRow("REGENERATION INDEX", draft.metadata.regenerationIndex.toString())
        CapRow("GENERATOR", draft.metadata.generatorVersion)
        CapRow("SEED", draft.metadata.stableSeed.toString())
        CapRow("PROFILE REVISION", draft.metadata.userProfileRevision)
        CapRow(
            "EFFECTIVE RANGE",
            "${SpeedTenths(profileMinSpeed).asDecimal()}–${SpeedTenths(profileMaxSpeed).asDecimal()} MPH / " +
                "${InclineTenths(profileMinIncline).asDecimal()}–${InclineTenths(profileMaxIncline).asDecimal()}%",
        )
        CapRow(
            "EFFECTIVE CAP",
            "${draft.effectiveSpeedCap.asDecimal()} MPH / ${draft.effectiveInclineCap.asDecimal()}% MAX",
        )
        Text(text = "BASELINE RAMP PROPOSAL", color = SurpriseWarning, fontSize = 12.sp)
        Text(
            text = "Preview only; no device command is sent.",
            color = MutedText,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SurpriseProfileCard(draft: SurpriseWorkoutDraft) {
    SurpriseChoiceCard(label = "GENERATED PROFILE · ${draft.profile.size} SEGMENTS") {
        draft.profile.forEach { segment ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = segment.name,
                    modifier = Modifier.weight(1.4f),
                    color = PrimaryText,
                    fontSize = 12.sp,
                )
                Text(
                    text = "${segment.duration.value} MIN",
                    modifier = Modifier.weight(0.7f),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = "${segment.speed.asDecimal()} MPH / ${segment.incline.asDecimal()}%",
                    modifier = Modifier.weight(1.2f),
                    color = Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PreviewOnlyBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(1.dp, SurpriseWarning, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "PREVIEW ONLY", color = SurpriseWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "NO DEVICE COMMANDS", color = MutedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CapRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 11.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun SurpriseSetupButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (primary) Cyan else CarbonHigh, RoundedCornerShape(4.dp))
            .border(1.dp, if (primary) Cyan else RuleColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) ConsoleCanvas else PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun SpeedTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)
