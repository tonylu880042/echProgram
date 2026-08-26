package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import java.util.Locale

@Composable
internal fun LiveWorkoutPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun LiveWorkoutMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 18.sp,
) {
    Column(modifier = modifier) {
        Text(text = label, color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun LiveWorkoutActionBar(
    isPaused: Boolean,
    onAction: (LiveWorkoutAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveWorkoutActionButton(
            label = if (isPaused) "RESUME" else "PAUSE",
            onClick = { onAction(LiveWorkoutAction.PauseResume) },
            modifier = Modifier.weight(1f),
        )
        LiveWorkoutActionButton(
            label = "END WORKOUT",
            onClick = { onAction(LiveWorkoutAction.End) },
            modifier = Modifier.weight(1f),
            backgroundColor = LiveWorkoutEnd,
            borderColor = LiveWorkoutEnd,
        )
    }
}

@Composable
internal fun LiveWorkoutActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = CarbonHigh,
    borderColor: Color = RuleColor,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun formatSpeed(speed: SpeedTenths): String =
    String.format(Locale.US, "%.1f", speed.value / 10.0)

internal fun formatIncline(incline: InclineTenths): String =
    String.format(Locale.US, "%.1f", incline.value / 10.0)

internal val LiveWorkoutAmber = Color(0xFFFFC857)
internal val LiveWorkoutEnd = Color(0xFFE34B4B)
internal val LiveWorkoutError = Color(0xFFFF6B6B)
