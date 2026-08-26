package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentSpeedUnit
import com.echelon.console.domain.EquipmentTelemetry
import java.util.Locale

@Composable
fun EquipmentTelemetryPanel(
    state: EquipmentReadState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        when (val connection = state.connection) {
            EquipmentConnection.Connecting -> StatusBody(
                title = "CONNECTING TO FITOS",
                message = "Waiting for the equipment service.",
            )

            is EquipmentConnection.ServiceUnavailable -> StatusBody(
                title = "EQUIPMENT UNAVAILABLE",
                message = "FitOS equipment service is not available.",
            )

            is EquipmentConnection.UnsupportedApi -> StatusBody(
                title = "FITOS API UNSUPPORTED",
                message = "This console requires FitOS equipment API v1.",
            )

            EquipmentConnection.Disconnected -> StatusBody(
                title = "EQUIPMENT NOT CONNECTED",
                message = "Connect the treadmill to read live telemetry.",
            )

            is EquipmentConnection.EquipmentDisconnected -> StatusBody(
                title = "EQUIPMENT DISCONNECTED",
                message = "FitOS is available, but no equipment is bound.",
            )

            is EquipmentConnection.Stale -> StatusBody(
                title = "TELEMETRY STALE",
                message = "Waiting for a fresh FitOS reading.",
            )

            EquipmentConnection.Ready -> ReadyBody(state.telemetry)
        }
    }
}

@Composable
private fun StatusBody(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = message, color = MutedText, fontSize = 13.sp)
    }
}

@Composable
private fun ReadyBody(telemetry: EquipmentTelemetry?) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "LIVE TELEMETRY", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (telemetry == null) {
            Text(text = "Waiting for a first FitOS reading.", color = MutedText, fontSize = 13.sp)
            return
        }
        TelemetryRow("TIME", telemetry.elapsedTime ?: "--")
        telemetry.speed?.let { speed ->
            val unit = when (speed.unit) {
                EquipmentSpeedUnit.KILOMETERS_PER_HOUR -> "KM/H"
                EquipmentSpeedUnit.MILES_PER_HOUR -> "MPH"
            }
            TelemetryRow("SPEED", "${speed.displayValue.formatOneDecimal()} $unit")
        }
        telemetry.incline?.let { TelemetryRow("INCLINE", "${it.value} LEVEL") }
        telemetry.heartRateBpm?.let { TelemetryRow("HEART RATE", "$it BPM") }
        telemetry.distance?.let { TelemetryRow("DISTANCE", it.formatOneDecimal()) }
        telemetry.calories?.let { TelemetryRow("CALORIES", it.formatOneDecimal()) }
    }
}

@Composable
private fun TelemetryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MutedText, fontSize = 11.sp)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

private fun Double.formatOneDecimal(): String = String.format(Locale.US, "%.1f", this)
