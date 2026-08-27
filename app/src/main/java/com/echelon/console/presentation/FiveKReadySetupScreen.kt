package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.FiveKReadySegmentRole
import com.echelon.console.domain.FiveKReadySessionDraft
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import java.util.Locale

private val FiveKWarning = Color(0xFFFFC857)
private val FiveKError = Color(0xFFFF6B6B)

@Composable
internal fun FiveKReadyConfiguringScreen(
    state: ProgramSetupUiState.FiveKReadyConfiguring,
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
            FiveKHeading(
                title = "CONFIGURE 5K READY",
                promise = state.detail.promise,
            )
            FiveKDisclosure()
            FiveKBaselineInput(
                text = state.baselinePaceText,
                onTextChange = { onAction(ProgramSetupAction.SetFiveKReadyBaselinePace(it)) },
            )
            FiveKDurationCard(state = state, onAction = onAction)
            FiveKCapCard(
                userMaxSpeed = state.userMaxSpeed,
                machineMaxSpeed = state.machineMaxSpeed,
                userMaxIncline = state.userMaxIncline,
                machineMaxIncline = state.machineMaxIncline,
            )
            state.errorMessage?.let { message ->
                Text(text = message, color = FiveKError, fontSize = 14.sp)
            }
            ConsoleSetupActionButton(
                label = "GENERATE 5K PREVIEW",
                onClick = { onAction(ProgramSetupAction.GenerateFiveKReadyPreview) },
                variant = ConsoleSetupActionButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
internal fun FiveKReadyDraftPreviewScreen(
    state: ProgramSetupUiState.FiveKReadyDraftPreview,
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
            FiveKHeading(
                title = "5K READY PREVIEW",
                promise = state.detail.title,
            )
            FiveKPreviewBanner()
            Text(
                text = "SINGLE SESSION · DOES NOT GUARANTEE 5K COMPLETION",
                color = FiveKWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            FiveKPreviewMetadataCard(state.draft)
            if (state.draft.metadata.wasClamped) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FiveKWarning),
                    colors = CardDefaults.cardColors(containerColor = CarbonLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "TARGETS CLAMPED TO EFFECTIVE CAPS",
                            color = FiveKWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        state.draft.metadata.clampSummary?.let { summary ->
                            val names = (summary.speedSegmentNames + summary.inclineSegmentNames)
                                .distinct()
                            Text(
                                text = "AFFECTED BLOCKS: ${names.joinToString()}",
                                color = MutedText,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            FiveKProfileCard(state.draft)
            state.errorMessage?.let { message ->
                Text(text = message, color = FiveKError, fontSize = 14.sp)
            }
            ConsoleSetupActionButton(
                label = "BACK TO SETTINGS",
                onClick = onBack,
                variant = ConsoleSetupActionButtonVariant.SECONDARY,
            )
            ConsoleSetupActionButton(
                label = "ACCEPT 5K PREVIEW",
                onClick = { onAction(ProgramSetupAction.AcceptFiveKReadyPlan) },
                variant = ConsoleSetupActionButtonVariant.PRIMARY,
            )
        }
    }
}

@Composable
private fun FiveKHeading(title: String, promise: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(text = "5K READY", color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = promise, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun FiveKDisclosure() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FiveKWarning),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "SINGLE SESSION PREVIEW",
                color = FiveKWarning,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = "NO HISTORY OR PROGRESSION", color = MutedText, fontSize = 12.sp)
            Text(text = "DOES NOT GUARANTEE 5K COMPLETION", color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FiveKBaselineInput(
    text: String,
    onTextChange: (String) -> Unit,
) {
    ConsoleSetupCard(label = "BASELINE PACE") {
        Text(
            text = "SET YOUR RUN PACE",
            color = FiveKWarning,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Enter your own baseline in MPH. The console will not infer 4.0 MPH.",
            color = MutedText,
            fontSize = 12.sp,
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(CarbonHigh, RoundedCornerShape(4.dp))
                .border(1.dp, RuleColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = "RUN PACE (MPH)"
                },
            textStyle = TextStyle(
                color = PrimaryText,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(text = "MPH, for example 4.0", color = MutedText, fontSize = 14.sp)
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun FiveKDurationCard(
    state: ProgramSetupUiState.FiveKReadyConfiguring,
    onAction: (ProgramSetupAction) -> Unit,
) {
    ConsoleSetupCard(label = "SESSION DURATION") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FiveKReadyDurationOptions.forEach { duration ->
                FiveKChoiceButton(
                    label = "${duration.value} MIN",
                    selected = duration == state.duration,
                    description = "Select 5K duration ${duration.value} minutes",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onAction(ProgramSetupAction.SetFiveKReadyDuration(duration))
                    },
                )
            }
        }
    }
}

@Composable
private fun FiveKCapCard(
    userMaxSpeed: SpeedTenths,
    machineMaxSpeed: SpeedTenths,
    userMaxIncline: InclineTenths,
    machineMaxIncline: InclineTenths,
) {
    val effectiveSpeed = SpeedTenths(minOf(userMaxSpeed.value, machineMaxSpeed.value))
    val effectiveIncline = InclineTenths(minOf(userMaxIncline.value, machineMaxIncline.value))
    ConsoleSetupCard(label = "CAPABILITIES") {
        ConsoleSetupReadoutRow("USER CAP", "${userMaxSpeed.fiveKSpeed()} MPH / ${userMaxIncline.fiveKIncline()}%")
        ConsoleSetupReadoutRow("MACHINE CAP", "${machineMaxSpeed.fiveKSpeed()} MPH / ${machineMaxIncline.fiveKIncline()}%")
        ConsoleSetupReadoutRow(
            "EFFECTIVE CAP",
            "${effectiveSpeed.fiveKSpeed()} MPH / ${effectiveIncline.fiveKIncline()}%",
        )
    }
}

@Composable
private fun FiveKPreviewMetadataCard(draft: FiveKReadySessionDraft) {
    ConsoleSetupCard(label = "PREVIEW CONTRACT") {
        ConsoleSetupReadoutRow("MODE", "SINGLE SESSION")
        ConsoleSetupReadoutRow("CONTROL", "PREVIEW ONLY")
        Text(
            text = "USER-ENTERED BASELINE: ${draft.metadata.baselinePace.speed.fiveKSpeed()} MPH",
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        ConsoleSetupReadoutRow("DURATION", "${draft.metadata.durationMinutes} MIN")
        ConsoleSetupReadoutRow(
            "EFFECTIVE CAP",
            "${draft.effectiveSpeedCap.fiveKSpeed()} MPH / ${draft.effectiveInclineCap.fiveKIncline()}%",
        )
        ConsoleSetupReadoutRow("RUN MINUTES", "${draft.runWalkSummary.runMinutes} MIN")
        ConsoleSetupReadoutRow("WALK MINUTES", "${draft.runWalkSummary.walkMinutes} MIN")
        Text(
            text = "NO DEVICE COMMANDS",
            color = FiveKWarning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FiveKProfileCard(draft: FiveKReadySessionDraft) {
    ConsoleSetupCard(label = "SESSION PROFILE · ${draft.segments.size} SEGMENTS") {
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
                Column(modifier = Modifier.weight(1.4f)) {
                    Text(
                        text = segment.role.displayName(),
                        color = Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = segment.summary.name, color = PrimaryText, fontSize = 12.sp)
                    if (segment.role == FiveKReadySegmentRole.RUN) {
                        val ordinal = segment.runOrdinal
                        val total = segment.totalRuns
                        if (ordinal != null && total != null) {
                            Text(
                                text = "RUN $ordinal OF $total",
                                color = FiveKWarning,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                Text(
                    text = "${segment.summary.duration.value} MIN",
                    modifier = Modifier.weight(0.7f),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = "${segment.summary.speed.fiveKSpeed()} MPH / ${segment.summary.incline.fiveKIncline()}%",
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
private fun FiveKChoiceButton(
    label: String,
    selected: Boolean,
    description: String,
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
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Cyan else PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FiveKPreviewBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(1.dp, FiveKWarning, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "PREVIEW ONLY", color = FiveKWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = "NO DEVICE COMMANDS", color = MutedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun FiveKReadySegmentRole.displayName(): String = name.replace('_', ' ')

private fun SpeedTenths.fiveKSpeed(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.fiveKIncline(): String = String.format(Locale.US, "%.1f", value / 10f)
