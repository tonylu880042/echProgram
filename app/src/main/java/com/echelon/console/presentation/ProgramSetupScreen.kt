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
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import java.util.Locale

private val SetupError = Color(0xFFFF6B6B)

@Composable
fun ProgramSetupRoute(
    viewModel: ProgramSetupViewModel,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    onShowLibrary: () -> Unit,
    equipmentState: EquipmentReadState = EquipmentReadState(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgramSetupScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigate = onNavigate,
        onShowLibrary = onShowLibrary,
        equipmentState = equipmentState,
        modifier = modifier,
    )
}

@Composable
fun ProgramSetupScreen(
    state: ProgramSetupUiState,
    onAction: (ProgramSetupAction) -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    onShowLibrary: () -> Unit,
    equipmentState: EquipmentReadState = EquipmentReadState(),
    modifier: Modifier = Modifier,
) {
    when (state) {
        ProgramSetupUiState.Library -> ProgramSetupStatus(
            title = "PROGRAM LIBRARY",
            message = "Select a telemetry profile to configure your workout.",
            onBack = null,
            actionLabel = "BACK TO LIBRARY",
            onAction = onShowLibrary,
            onNavigate = onNavigate,
            modifier = modifier,
        )

        is ProgramSetupUiState.Loading -> ProgramSetupStatus(
            title = "LOADING PROGRAM DETAIL",
            message = "Reading the selected telemetry profile.",
            onBack = null,
            onNavigate = onNavigate,
            modifier = modifier,
        )

        is ProgramSetupUiState.Ready -> ProgramDetailScreen(
            detail = state.detail,
            onBack = { onAction(ProgramSetupAction.Back) },
            onMakeItYours = { onAction(ProgramSetupAction.MakeItYours) },
            onStartDefault = { onAction(ProgramSetupAction.StartDefault) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.Personalizing -> ProgramPersonalizationScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.Configuring -> SurpriseWorkoutConfiguringScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.DraftPreview -> SurpriseWorkoutDraftPreviewScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.FiveKReadyConfiguring -> FiveKReadyConfiguringScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.FiveKReadyDraftPreview -> FiveKReadyDraftPreviewScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.VerticalConfiguring -> VerticalWorkoutConfiguringScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.VerticalDraftPreview -> VerticalWorkoutDraftPreviewScreen(
            state = state,
            onAction = onAction,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
        )

        is ProgramSetupUiState.Unavailable -> ProgramSetupStatus(
            title = "PROGRAM UNAVAILABLE",
            message = "Program ${state.programId.value} is not available on this console.",
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
            modifier = modifier,
        )

        ProgramSetupUiState.DeviceUnavailable -> ProgramSetupStatus(
            title = "DEVICE UNAVAILABLE",
            message = "Connect the treadmill capabilities before starting a workout.",
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
            modifier = modifier,
        )

        is ProgramSetupUiState.Error -> ProgramSetupStatus(
            title = "WORKOUT SETUP ERROR",
            message = state.message,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
            modifier = modifier,
            error = true,
        )

        is ProgramSetupUiState.Started -> StartedWorkoutScreen(
            plan = state.plan,
            previewMode = state.previewMode,
            equipmentState = equipmentState,
            onBack = { onAction(ProgramSetupAction.Back) },
            onNavigate = onNavigate,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProgramSetupStatus(
    title: String,
    message: String,
    onBack: (() -> Unit)?,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    error: Boolean = false,
) {
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                color = if (error) SetupError else Cyan,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(text = message, color = MutedText, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            if (actionLabel != null && onAction != null) {
                SetupActionButton(
                    label = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun StartedWorkoutScreen(
    plan: ValidatedWorkoutPlan,
    previewMode: ProgramPreviewMode,
    equipmentState: EquipmentReadState,
    onBack: () -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier,
) {
    val settings = plan.plan.settings
    ConsoleScaffold(
        onNavigate = onNavigate,
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "WORKOUT READY", color = PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "The selected plan is validated and ready for the live-session boundary.",
                color = MutedText,
                fontSize = 16.sp,
            )
            ProgramPreviewNotice(previewMode = previewMode)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, RuleColor),
                colors = CardDefaults.cardColors(containerColor = CarbonLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "SELECTED PLAN", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    StartedPlanRow("PROGRAM", plan.plan.programId.value)
                    StartedPlanRow("DURATION", "${settings.duration.value} MIN")
                    StartedPlanRow("INTENSITY", settings.intensity.label())
                    StartedPlanRow("FOCUS", settings.focus.label())
                    StartedPlanRow("MAX SPEED", "${settings.maxSpeed.asDecimal()} MPH")
                    StartedPlanRow("MAX INCLINE", "${settings.maxIncline.asDecimal()}%")
                    StartedPlanRow("ADAPT TO YOU", if (settings.adaptToYou) "ON" else "OFF")
                }
            }
            EquipmentTelemetryPanel(state = equipmentState)
        }
    }
}

@Composable
private fun StartedPlanRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 12.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun SetupActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(CarbonHigh, RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = RuleColor, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

private fun SpeedTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)

private fun InclineTenths.asDecimal(): String = String.format(Locale.US, "%.1f", value / 10f)
