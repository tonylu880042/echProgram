package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
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

internal val ConsoleCanvas = Color(0xFF071016)
internal val CarbonLow = Color(0xFF0C171E)
internal val CarbonHigh = Color(0xFF12232C)
internal val RuleColor = Color(0xFF253842)
internal val Cyan = Color(0xFF28A8FF)
internal val PrimaryText = Color(0xFFE5EDF2)
internal val MutedText = Color(0xFFA4B3BD)

enum class ProgramLibraryDestination {
    DASHBOARD,
    PROGRAMS,
    FREE_RUN,
    HISTORY,
    SETTINGS,
}

private data class RailItem(
    val destination: ProgramLibraryDestination,
    val label: String,
    val mark: String,
)

private val RailItems = listOf(
    RailItem(ProgramLibraryDestination.DASHBOARD, "Dashboard", "DB"),
    RailItem(ProgramLibraryDestination.PROGRAMS, "Programs", "PR"),
    RailItem(ProgramLibraryDestination.FREE_RUN, "Free Run", "FR"),
    RailItem(ProgramLibraryDestination.HISTORY, "History", "HI"),
    RailItem(ProgramLibraryDestination.SETTINGS, "Settings", "ST"),
)

@Composable
internal fun ConsoleScaffold(
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier = Modifier,
    activeDestination: ProgramLibraryDestination = ProgramLibraryDestination.PROGRAMS,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ConsoleCanvas,
        contentColor = PrimaryText,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            ConsoleNavigationRail(
                activeDestination = activeDestination,
                onNavigate = onNavigate,
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ConsoleTelemetryHeader(onBack = onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun ConsoleTelemetryHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CarbonLow)
            .border(width = 1.dp, color = RuleColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { back ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = back)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Back"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "<",
                        color = PrimaryText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = "TELEMETRY",
                modifier = Modifier.padding(
                    start = if (onBack == null) 16.dp else 8.dp,
                    end = 16.dp,
                ),
                color = MutedText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "BT", color = MutedText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(text = "WF", color = MutedText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(text = "BAT", color = MutedText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text(text = "PROFILE", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConsoleNavigationRail(
    activeDestination: ProgramLibraryDestination,
    onNavigate: (ProgramLibraryDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(CarbonLow)
            .border(width = 1.dp, color = RuleColor),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(40.dp)
                .background(CarbonHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "E", color = Cyan, fontWeight = FontWeight.Bold)
        }
        RailItems.forEach { item ->
            val isActive = item.destination == activeDestination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(if (isActive) CarbonHigh else Color.Transparent)
                    .clickable { onNavigate(item.destination) }
                    .semantics {
                        role = Role.Button
                        contentDescription = item.label
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(if (isActive) Cyan else Color.Transparent),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = item.mark,
                        color = if (isActive) Cyan else MutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.label,
                        color = if (isActive) Cyan else MutedText,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
