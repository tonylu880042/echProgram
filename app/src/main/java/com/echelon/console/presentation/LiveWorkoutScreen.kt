package com.echelon.console.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.ProgramPreviewMode
import java.util.Locale

@Composable
fun LiveWorkoutRoute(
    viewModel: LiveWorkoutViewModel,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    onBackToPrograms: () -> Unit,
    equipmentState: EquipmentReadState = EquipmentReadState(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(viewModel) {
        viewModel.attachCurrentSession()
    }
    LaunchedEffect(viewModel, equipmentState) {
        viewModel.onEquipmentStateChanged(equipmentState)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LiveWorkoutScreen(
        state = state,
        equipmentState = equipmentState,
        onAction = viewModel::onAction,
        onBackToPrograms = onBackToPrograms,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}

@Composable
fun LiveWorkoutScreen(
    state: LiveWorkoutUiState,
    equipmentState: EquipmentReadState,
    onAction: (LiveWorkoutAction) -> Unit,
    onBackToPrograms: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActiveSession = state is LiveWorkoutUiState.Active
    ConsoleScaffold(
        onNavigate = if (isActiveSession) {
            { }
        } else {
            onNavigate
        },
        onBack = if (isActiveSession) null else onBackToPrograms,
        activeDestination = ProgramLibraryDestination.PROGRAMS,
        modifier = modifier,
    ) {
        when (state) {
            LiveWorkoutUiState.NoSession -> LiveWorkoutStatusContent(
                title = "NO ACTIVE WORKOUT",
                message = "Start a program before opening the live workout console.",
                actionLabel = "BACK TO PROGRAMS",
                onAction = onBackToPrograms,
            )

            is LiveWorkoutUiState.Error -> LiveWorkoutStatusContent(
                title = "WORKOUT UNAVAILABLE",
                message = state.message,
                actionLabel = "BACK TO PROGRAMS",
                onAction = onBackToPrograms,
                error = true,
            )

            is LiveWorkoutUiState.Active -> LiveWorkoutActiveContent(
                workout = state.workout,
                equipmentState = equipmentState,
                onAction = onAction,
            )

            is LiveWorkoutUiState.Completed -> LiveWorkoutTerminalContent(
                title = completionTitle(state.summary.previewMode),
                summary = state.summary,
                onDone = onBackToPrograms,
            )

            is LiveWorkoutUiState.Stopped -> LiveWorkoutTerminalContent(
                title = "WORKOUT STOPPED",
                summary = state.summary,
                onDone = onBackToPrograms,
            )
        }
    }
}

internal fun formatWorkoutClock(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val hours = safeSeconds / 3_600
    val minutes = (safeSeconds % 3_600) / 60
    val seconds = safeSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun LiveWorkoutStatusContent(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    error: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics { contentDescription = "$title. $message" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            color = if (error) LiveWorkoutError else Cyan,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = MutedText, fontSize = 16.sp)
        LiveWorkoutActionButton(
            label = actionLabel,
            onClick = onAction,
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(min = 180.dp),
        )
    }
}

@Composable
private fun LiveWorkoutTerminalContent(
    title: String,
    summary: LiveWorkoutSummary,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = title, color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            text = summary.programTitle,
            color = PrimaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (summary.previewMode != ProgramPreviewMode.FIXED_PROFILE_PREVIEW) {
            Text(
                text = summary.previewMode.disclosureMessage(),
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        summary.verticalContext?.let { context ->
            LiveVerticalContextPanel(context)
        }
        summary.zone2Context?.let { context ->
            LiveZone2HeartRatePanel(context)
        }
        summary.calorieTargetContext?.let { context ->
            LiveCalorieTargetPanel(context)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveWorkoutMetric(
                label = "ELAPSED",
                value = formatWorkoutClock(summary.elapsedSeconds),
                modifier = Modifier.weight(1f),
            )
            LiveWorkoutMetric(
                label = "TOTAL DURATION",
                value = formatWorkoutClock(summary.totalDurationSeconds),
                modifier = Modifier.weight(1f),
            )
        }
        summary.runWalkSummary?.let { runWalkSummary ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveWorkoutMetric(
                    label = "PLANNED RUN",
                    value = "${runWalkSummary.runMinutes} MIN",
                    modifier = Modifier.weight(1f),
                )
                LiveWorkoutMetric(
                    label = "PLANNED WALK",
                    value = "${runWalkSummary.walkMinutes} MIN",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        LiveWorkoutActionButton(
            label = "DONE",
            onClick = onDone,
            modifier = Modifier
                .padding(top = 24.dp)
                .widthIn(min = 140.dp),
        )
    }
}

private fun completionTitle(previewMode: ProgramPreviewMode): String = when (previewMode) {
    ProgramPreviewMode.FIXED_PROFILE_PREVIEW -> "WORKOUT COMPLETE"
    ProgramPreviewMode.BASELINE_PREVIEW,
    ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
    ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW,
    ProgramPreviewMode.HEART_RATE_PREVIEW,
    ProgramPreviewMode.GENERATED_PREVIEW,
    ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
    -> "PREVIEW COMPLETE"
}
