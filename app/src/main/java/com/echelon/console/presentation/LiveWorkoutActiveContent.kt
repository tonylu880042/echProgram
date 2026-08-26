package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.ProgramPreviewMode

@Composable
internal fun LiveWorkoutActiveContent(
    workout: LiveWorkoutReadModel,
    equipmentState: EquipmentReadState,
    onAction: (LiveWorkoutAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveWorkoutTitleBlock(workout, equipmentState)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (maxWidth >= 720.dp) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    LiveWorkoutSessionPanel(
                        workout = workout,
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight(),
                    )
                    EquipmentTelemetryPanel(
                        state = equipmentState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LiveWorkoutSessionPanel(
                        workout = workout,
                        modifier = Modifier,
                    )
                }
            }
        }
        LiveWorkoutActionBar(
            isPaused = workout.isPaused,
            onAction = onAction,
        )
    }
}

private fun equipmentConnectionTitle(connection: EquipmentConnection): String = when (connection) {
    EquipmentConnection.Connecting -> "CONNECTING TO FITOS"
    EquipmentConnection.Disconnected -> "EQUIPMENT NOT CONNECTED"
    EquipmentConnection.Ready -> "FITOS TELEMETRY READY"
    is EquipmentConnection.EquipmentDisconnected -> "EQUIPMENT DISCONNECTED"
    is EquipmentConnection.ServiceUnavailable -> "EQUIPMENT UNAVAILABLE"
    is EquipmentConnection.UnsupportedApi -> "FITOS API UNSUPPORTED"
    is EquipmentConnection.Stale -> "TELEMETRY STALE"
}

@Composable
private fun LiveWorkoutTitleBlock(
    workout: LiveWorkoutReadModel,
    equipmentState: EquipmentReadState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = workout.programTitle,
                color = PrimaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (workout.isPaused) "PAUSED" else "LIVE PREVIEW",
                color = if (workout.isPaused) LiveWorkoutAmber else Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        LiveWorkoutPreviewNotice(
            previewMode = workout.previewMode,
            equipmentState = equipmentState,
        )
    }
}

@Composable
private fun LiveWorkoutPreviewNotice(
    previewMode: ProgramPreviewMode,
    equipmentState: EquipmentReadState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(1.dp, LiveWorkoutAmber, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PREVIEW ONLY",
            color = LiveWorkoutAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = previewMode.disclosureMessage(),
            color = MutedText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        )
        Text(
            text = equipmentConnectionTitle(equipmentState.connection),
            color = MutedText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun LiveWorkoutSessionPanel(
    workout: LiveWorkoutReadModel,
    modifier: Modifier = Modifier,
) {
    LiveWorkoutPanel(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LiveWorkoutMetric(
                label = "TIME REMAINING",
                value = formatWorkoutClock(workout.remainingSeconds),
                modifier = Modifier.weight(1f),
                valueSize = 24.sp,
            )
            LiveWorkoutMetric(
                label = "ELAPSED",
                value = formatWorkoutClock(workout.elapsedSeconds),
                modifier = Modifier.weight(1f),
                valueSize = 24.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveWorkoutMetric(
                label = "CURRENT SEGMENT",
                value = workout.currentSegment.name,
                modifier = Modifier.weight(1f),
                valueSize = 14.sp,
            )
            LiveWorkoutMetric(
                label = "NEXT SEGMENT",
                value = workout.nextSegment?.name ?: "FINISH",
                modifier = Modifier.weight(1f),
                valueSize = 14.sp,
            )
            LiveWorkoutMetric(
                label = "COUNTDOWN",
                value = workout.secondsUntilNextSegment?.let(::formatWorkoutClock) ?: "--",
                modifier = Modifier.weight(0.8f),
                valueSize = 14.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveWorkoutMetric(
                label = "TARGET SPEED",
                value = "${formatSpeed(workout.targetSpeed)} MPH",
                modifier = Modifier.weight(1f),
                valueSize = 20.sp,
            )
            LiveWorkoutMetric(
                label = "TARGET INCLINE",
                value = "${formatIncline(workout.targetIncline)}%",
                modifier = Modifier.weight(1f),
                valueSize = 20.sp,
            )
        }
    }
}
