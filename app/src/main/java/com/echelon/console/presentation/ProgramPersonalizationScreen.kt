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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanField
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.SpeedTenths
import java.util.Locale
import kotlin.math.max

private val PersonalizationError = Color(0xFFFF6B6B)

@Composable
fun ProgramPersonalizationScreen(
    state: ProgramSetupUiState.Personalizing,
    onAction: (ProgramSetupAction) -> Unit,
    onBack: () -> Unit,
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
                PersonalizationHeading(
                    detail = state.detail,
                    settings = state.settings,
                    onAction = onAction,
                    isWideLandscape = isWideLandscape,
                )
                if (isWideLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PersonalizationControls(
                            state = state,
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                        PersonalizationPreview(
                            detail = state.detail,
                            settings = state.settings,
                            modifier = Modifier.weight(2f),
                        )
                    }
                } else {
                    PersonalizationControls(state = state, onAction = onAction)
                    PersonalizationPreview(detail = state.detail, settings = state.settings)
                }
                PersonalizationStartButton(onClick = { onAction(ProgramSetupAction.StartCustomized) })
            }
        }
    }
}

@Composable
private fun PersonalizationHeading(
    detail: ProgramDetail,
    settings: PlanSettings,
    onAction: (ProgramSetupAction) -> Unit,
    isWideLandscape: Boolean,
) {
    if (isWideLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            HeadingCopy(detail = detail)
            AdaptToYouControl(settings = settings, onAction = onAction)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HeadingCopy(detail = detail)
            AdaptToYouControl(settings = settings, onAction = onAction)
        }
    }
}

@Composable
private fun HeadingCopy(detail: ProgramDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "MAKE IT YOURS",
            color = PrimaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = detail.title, color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(text = detail.promise, color = MutedText, fontSize = 14.sp)
    }
}

@Composable
private fun AdaptToYouControl(
    settings: PlanSettings,
    onAction: (ProgramSetupAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 64.dp)
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = RuleColor, shape = RoundedCornerShape(4.dp))
            .clickable { onAction(ProgramSetupAction.SetAdaptToYou(!settings.adaptToYou)) }
            .semantics {
                role = Role.Switch
                contentDescription = "Adapt to You"
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "ADAPT TO YOU",
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Switch(
            checked = settings.adaptToYou,
            onCheckedChange = { onAction(ProgramSetupAction.SetAdaptToYou(it)) },
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(
            text = if (settings.adaptToYou) "ON" else "OFF",
            color = if (settings.adaptToYou) Cyan else MutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PersonalizationControls(
    state: ProgramSetupUiState.Personalizing,
    onAction: (ProgramSetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepperCard(
            label = "DURATION (MIN)",
            value = state.settings.duration.value.toString(),
            decrementDescription = "Decrease duration",
            incrementDescription = "Increase duration",
            onDecrement = {
                onAction(ProgramSetupAction.SetDuration(DurationMinutes(state.settings.duration.value - 5)))
            },
            onIncrement = {
                onAction(ProgramSetupAction.SetDuration(DurationMinutes(state.settings.duration.value + 5)))
            },
            error = state.fieldErrors.firstOrNull { it.field == PlanField.DURATION },
        )
        IntensityCard(settings = state.settings, onAction = onAction)
        StepperCard(
            label = "MAX SPEED (MPH)",
            value = state.settings.maxSpeed.asDecimal(),
            decrementDescription = "Decrease max speed",
            incrementDescription = "Increase max speed",
            onDecrement = {
                onAction(ProgramSetupAction.SetMaxSpeed(SpeedTenths(state.settings.maxSpeed.value - 5)))
            },
            onIncrement = {
                onAction(ProgramSetupAction.SetMaxSpeed(SpeedTenths(state.settings.maxSpeed.value + 5)))
            },
            error = state.fieldErrors.firstOrNull { it.field == PlanField.MAX_SPEED },
        )
        StepperCard(
            label = "MAX INCLINE (%)",
            value = state.settings.maxIncline.asDecimal(),
            decrementDescription = "Decrease max incline",
            incrementDescription = "Increase max incline",
            onDecrement = {
                onAction(ProgramSetupAction.SetMaxIncline(InclineTenths(state.settings.maxIncline.value - 10)))
            },
            onIncrement = {
                onAction(ProgramSetupAction.SetMaxIncline(InclineTenths(state.settings.maxIncline.value + 10)))
            },
            error = state.fieldErrors.firstOrNull { it.field == PlanField.MAX_INCLINE },
        )
        FocusCard(settings = state.settings, onAction = onAction)
    }
}

@Composable
private fun StepperCard(
    label: String,
    value: String,
    decrementDescription: String,
    incrementDescription: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    error: PlanValidationError?,
) {
    SettingCard(label = label, error = error) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(description = decrementDescription, symbol = "−", onClick = onDecrement)
            Text(
                text = value,
                color = Cyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            StepperButton(description = incrementDescription, symbol = "+", onClick = onIncrement)
        }
    }
}

@Composable
private fun IntensityCard(
    settings: PlanSettings,
    onAction: (ProgramSetupAction) -> Unit,
) {
    SettingCard(label = "TARGET INTENSITY") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanIntensity.entries.forEach { intensity ->
                OptionButton(
                    label = intensity.label(),
                    selected = settings.intensity == intensity,
                    description = "Select intensity ${intensity.label()}",
                    modifier = Modifier.weight(1f),
                    onClick = { onAction(ProgramSetupAction.SetIntensity(intensity)) },
                )
            }
        }
    }
}

@Composable
private fun FocusCard(
    settings: PlanSettings,
    onAction: (ProgramSetupAction) -> Unit,
) {
    SettingCard(label = "FOCUS") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanFocus.entries.forEach { focus ->
                OptionButton(
                    label = focus.label(),
                    selected = settings.focus == focus,
                    description = "Select focus ${focus.label()}",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAction(ProgramSetupAction.SetFocus(focus)) },
                )
            }
        }
    }
}

@Composable
private fun SettingCard(
    label: String,
    error: PlanValidationError? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = label, color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            content()
            error?.let {
                Text(text = it.asMessage(), color = PersonalizationError, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StepperButton(
    description: String,
    symbol: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(CarbonHigh, RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = RuleColor, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OptionButton(
    label: String,
    selected: Boolean,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) Cyan else CarbonHigh, RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = if (selected) Cyan else RuleColor, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) ConsoleCanvas else PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PersonalizationPreview(
    detail: ProgramDetail,
    settings: PlanSettings,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "PROJECTED TRAJECTORY",
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
                    .height(260.dp)
                    .padding(16.dp)
                    .semantics { contentDescription = "Projected trajectory profile" },
            ) {
                ProjectedTrajectoryCanvas(detail = detail)
            }
            SelectedConfiguration(settings = settings)
        }
    }
}

@Composable
private fun ProjectedTrajectoryCanvas(detail: ProgramDetail) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Projected trajectory canvas" },
    ) {
        val maxSpeed = max(1f, detail.speedRange.max.value.toFloat())
        val points = detail.profile.mapIndexed { index, segment ->
            val x = if (detail.profile.size == 1) 0f else size.width * index / (detail.profile.lastIndex)
            val y = size.height * (1f - (segment.speed.value / maxSpeed).coerceIn(0.08f, 1f))
            Offset(x, y)
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = Cyan,
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SelectedConfiguration(settings: PlanSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleCanvas)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "SELECTED CONFIGURATION", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        ConfigurationRow("DURATION", "${settings.duration.value} MIN")
        ConfigurationRow("INTENSITY", settings.intensity.label())
        ConfigurationRow("MAX SPEED", "${settings.maxSpeed.asDecimal()} MPH")
        ConfigurationRow("MAX INCLINE", "${settings.maxIncline.asDecimal()}%")
        ConfigurationRow("FOCUS", settings.focus.label())
        ConfigurationRow("ADAPT TO YOU", if (settings.adaptToYou) "ON" else "OFF")
    }
}

@Composable
private fun ConfigurationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 12.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun PersonalizationStartButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(Cyan, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "START WORKOUT"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "START WORKOUT", color = ConsoleCanvas, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun PlanIntensity.label(): String = when (this) {
    PlanIntensity.LOW -> "LOW"
    PlanIntensity.MEDIUM -> "MEDIUM"
    PlanIntensity.HIGH -> "HIGH"
}

private fun PlanFocus.label(): String = when (this) {
    PlanFocus.MORE_INCLINE -> "MORE INCLINE"
    PlanFocus.BALANCED -> "BALANCED"
    PlanFocus.MORE_SPEED -> "MORE SPEED"
}

private fun PlanValidationError.asMessage(): String = when (this) {
    is PlanValidationError.DurationOutOfRange ->
        "Duration must be between ${limits.min.value} and ${limits.max.value} min."
    is PlanValidationError.DurationStepMismatch ->
        "Duration must use ${limits.step.value} min steps."
    is PlanValidationError.MaxSpeedOutOfRange ->
        "Speed must be between ${limits.min.asDecimal()} and ${limits.max.asDecimal()} MPH."
    is PlanValidationError.MaxInclineOutOfRange ->
        "Incline must be between ${limits.min.asDecimal()} and ${limits.max.asDecimal()}%."
}

private fun SpeedTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)
