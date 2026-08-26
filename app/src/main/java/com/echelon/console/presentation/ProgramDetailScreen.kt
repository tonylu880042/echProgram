package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedTenths
import java.util.Locale
import kotlin.math.max

@Composable
fun ProgramDetailScreen(
    detail: ProgramDetail,
    onBack: () -> Unit,
    onMakeItYours: () -> Unit,
    onStartDefault: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
) {
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        activeDestination = ProgramLibraryDestination.PROGRAMS,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            val isWideLandscape = maxWidth >= 920.dp
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgramDetailHeading(detail = detail)
                ProgramDetailBody(
                    detail = detail,
                    isWideLandscape = isWideLandscape,
                    onMakeItYours = onMakeItYours,
                    onStartDefault = onStartDefault,
                )
            }
        }
    }
}

@Composable
private fun ProgramDetailHeading(detail: ProgramDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = detail.title,
            color = PrimaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = detail.promise,
            color = MutedText,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun ProgramDetailBody(
    detail: ProgramDetail,
    isWideLandscape: Boolean,
    onMakeItYours: () -> Unit,
    onStartDefault: () -> Unit,
) {
    if (isWideLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailMetrics(detail = detail)
                IntensityProfile(detail = detail, modifier = Modifier.heightIn(min = 300.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SegmentBreakdown(detail.profile)
                DetailActions(
                    onMakeItYours = onMakeItYours,
                    onStartDefault = onStartDefault,
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetrics(detail = detail)
            IntensityProfile(detail = detail, modifier = Modifier.heightIn(min = 260.dp))
            SegmentBreakdown(detail.profile)
            DetailActions(
                onMakeItYours = onMakeItYours,
                onStartDefault = onStartDefault,
            )
        }
    }
}

@Composable
private fun DetailMetrics(detail: ProgramDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DetailMetric(
            label = "TOTAL DURATION",
            value = detail.defaultDuration.asClock(),
            modifier = Modifier.weight(1f),
        )
        DetailMetric(
            label = "SPEED RANGE (MPH)",
            value = "${detail.speedRange.min.asDecimal()} - ${detail.speedRange.max.asDecimal()}",
            modifier = Modifier.weight(1f),
        )
        DetailMetric(
            label = "INCLINE RANGE (%)",
            value = "${detail.inclineRange.min.asDecimal()} - ${detail.inclineRange.max.asDecimal()}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = if (label == "TOTAL DURATION") 36.sp else 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun IntensityProfile(
    detail: ProgramDetail,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "INTENSITY PROFILE",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleCanvas)
                    .padding(16.dp),
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Intensity profile chart" },
            ) {
                IntensityProfileCanvas(detail = detail)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleCanvas)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "0:00", color = MutedText, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text(
                    text = detail.defaultDuration.asHalfClock(),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    text = detail.defaultDuration.asClock(),
                    color = MutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun IntensityProfileCanvas(detail: ProgramDetail) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxSpeed = max(1f, detail.speedRange.max.value.toFloat())
        val count = max(1, detail.profile.size)
        val slotWidth = size.width / count
        detail.profile.forEachIndexed { index, segment ->
            val heightRatio = (segment.speed.value / maxSpeed).coerceIn(0.08f, 1f)
            val barHeight = size.height * heightRatio
            val barWidth = (slotWidth * 0.7f).coerceAtLeast(8.dp.toPx())
            val x = index * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = if (heightRatio > 0.7f) Cyan else Cyan.copy(alpha = 0.62f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = Fill,
            )
        }
    }
}

@Composable
private fun SegmentBreakdown(segments: List<ProgramSegmentSummary>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "SEGMENT BREAKDOWN",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleCanvas)
                    .padding(16.dp),
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "PHASE", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "TIME", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(text = "TARGET", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            segments.forEach { segment ->
                SegmentRow(segment)
            }
        }
    }
}

@Composable
private fun SegmentRow(segment: ProgramSegmentSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .border(width = 1.dp, color = RuleColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = segment.name,
            modifier = Modifier.weight(1.5f),
            color = PrimaryText,
            fontSize = 14.sp,
        )
        Text(
            text = segment.duration.asClock(),
            modifier = Modifier.weight(0.7f),
            color = MutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Text(
            text = "${segment.speed.asDecimal()} MPH / ${segment.incline.asDecimal()}%",
            modifier = Modifier.weight(1.2f),
            color = Cyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun DetailActions(
    onMakeItYours: () -> Unit,
    onStartDefault: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CarbonHigh, RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = RuleColor, shape = RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConsoleActionButton(
            label = "START WORKOUT",
            primary = true,
            onClick = onStartDefault,
        )
        ConsoleActionButton(
            label = "MAKE IT YOURS",
            primary = false,
            onClick = onMakeItYours,
        )
    }
}

@Composable
private fun ConsoleActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                color = if (primary) Cyan else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .border(width = 1.dp, color = if (primary) Cyan else RuleColor, shape = RoundedCornerShape(4.dp))
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

private fun DurationMinutes.asClock(): String = String.format(Locale.US, "%d:%02d", value, 0)

private fun DurationMinutes.asHalfClock(): String {
    val totalSeconds = value * 60 / 2
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun SpeedTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun com.echelon.console.domain.InclineTenths.asDecimal(): String =
    String.format(Locale.US, "%.1f", value / 10f)
