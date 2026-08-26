package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import java.util.Locale

@Composable
internal fun LiveVerticalContextPanel(
    context: LiveVerticalWorkoutContext,
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
            text = "VERTICAL PREVIEW CONTEXT",
            color = Cyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "TARGET ${context.target.liveLabel()}",
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        Text(
            text = "PROPOSED LIMIT ${context.proposedTimeLimit.minutes} MIN · NOT SESSION DURATION",
            color = PrimaryText,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.heightIn(min = 24.dp),
        )
        Text(
            text = "ELEVATION SOURCE ${context.elevationSource.liveLabel()}",
            color = MutedText,
            fontSize = 11.sp,
        )
        Text(
            text = "PROGRESS ${context.progressStatus.liveLabel()}",
            color = MutedText,
            fontSize = 11.sp,
        )
        Text(
            text = "${context.controlStatus.liveLabel()} · NO DEVICE COMMANDS",
            color = LiveWorkoutAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun VerticalTarget.liveLabel(): String =
    if (this == VerticalTarget.VERTICAL_MILE) {
        "5,280 FT · VERTICAL MILE"
    } else {
        "${String.format(Locale.US, "%,d", feet)} FT"
    }

private fun VerticalElevationSource.liveLabel(): String = when (this) {
    VerticalElevationSource.UNAVAILABLE -> "UNAVAILABLE"
}

private fun VerticalProgressStatus.liveLabel(): String = when (this) {
    VerticalProgressStatus.NOT_CALCULATED -> "NOT CALCULATED"
}

private fun VerticalWorkoutDraftControlStatus.liveLabel(): String = when (this) {
    VerticalWorkoutDraftControlStatus.PREVIEW_ONLY -> "PREVIEW ONLY"
}
