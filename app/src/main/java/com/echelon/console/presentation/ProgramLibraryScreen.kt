package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.Program
import com.echelon.console.domain.ProgramCategory

private val ConsoleCanvas = Color(0xFF071016)
private val CarbonLow = Color(0xFF0C171E)
private val CarbonHigh = Color(0xFF12232C)
private val RuleColor = Color(0xFF253842)
private val Cyan = Color(0xFF28A8FF)
private val PrimaryText = Color(0xFFE5EDF2)
private val MutedText = Color(0xFFA4B3BD)

enum class ProgramLibraryDestination {
    DASHBOARD,
    PROGRAMS,
    FREE_RUN,
    HISTORY,
    SETTINGS,
}

@Composable
fun ProgramLibraryRoute(
    viewModel: ProgramLibraryViewModel,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgramLibraryScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigate = onNavigate,
        modifier = modifier,
    )
}

@Composable
fun ProgramLibraryScreen(
    state: ProgramLibraryUiState,
    onAction: (ProgramLibraryAction) -> Unit,
    onNavigate: (ProgramLibraryDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ConsoleCanvas,
        contentColor = PrimaryText,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            ProgramLibraryNavigationRail(onNavigate = onNavigate)
            Column(modifier = Modifier.fillMaxSize()) {
                ProgramTelemetryHeader()
                ProgramLibraryContent(
                    state = state,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ProgramTelemetryHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CarbonLow)
            .border(width = 1.dp, color = RuleColor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "TELEMETRY",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
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
private fun ProgramLibraryNavigationRail(
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
        Spacer(modifier = Modifier.height(64.dp))
        RailItems.forEach { item ->
            val isActive = item.destination == ProgramLibraryDestination.PROGRAMS
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
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .size(36.dp)
                .background(Cyan, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "E", color = ConsoleCanvas, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProgramLibraryContent(
    state: ProgramLibraryUiState,
    onAction: (ProgramLibraryAction) -> Unit,
) {
    when (state) {
        ProgramLibraryUiState.Loading -> ProgramLibraryStatus(
            title = "LOADING PROGRAM LIBRARY",
            message = "Reading program telemetry profiles.",
        )

        is ProgramLibraryUiState.Error -> ProgramLibraryStatus(
            title = "PROGRAM LIBRARY ERROR",
            message = state.message,
        )

        is ProgramLibraryUiState.Ready -> ProgramLibraryReadyContent(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun ProgramLibraryStatus(
    title: String,
    message: String,
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
            color = Cyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = message, color = MutedText, fontSize = 16.sp)
    }
}

@Composable
private fun ProgramLibraryReadyContent(
    state: ProgramLibraryUiState.Ready,
    onAction: (ProgramLibraryAction) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        val isWideLandscape = maxWidth >= 1000.dp &&
            (maxHeight.value.isInfinite() || maxWidth / maxHeight >= 1.45f)
        val heroHeight = (maxHeight * 0.58f).coerceIn(280.dp, 420.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "WHAT DO YOU WANT TODAY?",
                color = PrimaryText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Select a program telemetry profile to initiate sequence.",
                color = MutedText,
                fontSize = 14.sp,
            )
            HeroGrid(
                programs = state.heroPrograms,
                selectedHeroId = state.selectedHeroId,
                heroHeight = heroHeight,
                onSelect = { onAction(ProgramLibraryAction.SelectHero(it)) },
            )
            AllProgramsSection(
                programs = state.visiblePrograms,
                activeCategory = state.activeCategory,
                columns = if (isWideLandscape) 4 else 2,
                onFilter = { onAction(ProgramLibraryAction.FilterPrograms(it)) },
            )
        }
    }
}

@Composable
private fun HeroGrid(
    programs: List<HeroProgram>,
    selectedHeroId: com.echelon.console.domain.ProgramId?,
    heroHeight: Dp,
    onSelect: (com.echelon.console.domain.ProgramId) -> Unit,
) {
    val fatBurn = programs.getOrNull(0)
    val gluteBlast = programs.getOrNull(1)
    val vertical = programs.getOrNull(2)
    val surpriseMe = programs.getOrNull(3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .height(heroHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fatBurn?.let { program ->
            HeroCard(
                program = program,
                selected = program.id == selectedHeroId,
                size = HeroCardSize.Large,
                modifier = Modifier.weight(2f),
                onClick = { onSelect(program.id) },
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gluteBlast?.let { program ->
                HeroCard(
                    program = program,
                    selected = program.id == selectedHeroId,
                    size = HeroCardSize.Medium,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(program.id) },
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vertical?.let { program ->
                    HeroCard(
                        program = program,
                        selected = program.id == selectedHeroId,
                        size = HeroCardSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(program.id) },
                    )
                }
                surpriseMe?.let { program ->
                    HeroCard(
                        program = program,
                        selected = program.id == selectedHeroId,
                        size = HeroCardSize.Compact,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(program.id) },
                    )
                }
            }
        }
    }
}

private enum class HeroCardSize {
    Large,
    Medium,
    Compact,
}

@Composable
private fun HeroCard(
    program: HeroProgram,
    selected: Boolean,
    size: HeroCardSize,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Cyan else RuleColor
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = program.title },
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (size == HeroCardSize.Large) 16.dp else 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = when (size) {
                        HeroCardSize.Large -> "INTENSITY: HIGH"
                        HeroCardSize.Medium -> "STRENGTH"
                        HeroCardSize.Compact -> if (program.title == "VERTICAL") "ENDURANCE" else "RANDOMIZED"
                    },
                    color = MutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = program.durationLabel,
                    color = Cyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (size == HeroCardSize.Large) 28.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = program.title,
                    color = PrimaryText,
                    fontSize = if (size == HeroCardSize.Large) 24.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (size != HeroCardSize.Compact) {
                    Text(
                        text = program.promise,
                        color = MutedText,
                        fontSize = 14.sp,
                        maxLines = if (size == HeroCardSize.Large) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (size == HeroCardSize.Large) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = RuleColor)
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                    ) {
                        Metric(label = "TARGET HR", value = "135–155")
                        Metric(label = "AVG INCLINE", value = "3.5%")
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(text = label, color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = PrimaryText, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
    }
}

@Composable
private fun AllProgramsSection(
    programs: List<Program>,
    activeCategory: ProgramCategory,
    columns: Int,
    onFilter: (ProgramCategory) -> Unit,
) {
    var filterOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "ALL PROGRAMS", color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = { filterOpen = !filterOpen },
                    colors = ButtonDefaults.textButtonColors(contentColor = Cyan),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(text = "FILTER", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (filterOpen) {
                    Surface(
                        modifier = Modifier.width(180.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, RuleColor),
                        color = CarbonHigh,
                    ) {
                        Column {
                            ProgramCategory.values().forEach { category ->
                                Text(
                                    text = category.label,
                                    color = PrimaryText,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable {
                                            filterOpen = false
                                            onFilter(category)
                                        }
                                        .semantics {
                                            role = Role.Button
                                            contentDescription = "Filter ${category.label}"
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .border(width = 1.dp, color = RuleColor),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "NO MATCHING PROGRAMS", color = PrimaryText, fontWeight = FontWeight.Bold)
                    Text(
                        text = "No ${activeCategory.label} profiles are available.",
                        color = MutedText,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            programs.chunked(columns).forEach { rowPrograms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowPrograms.forEach { program ->
                        ProgramCard(program = program, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - rowPrograms.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: Program,
    modifier: Modifier,
) {
    Card(
        modifier = modifier
            .heightIn(min = 120.dp)
            .widthIn(min = 120.dp),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, RuleColor),
        colors = CardDefaults.cardColors(containerColor = CarbonLow),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "•",
                    color = MutedText,
                    fontSize = 18.sp,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Program marker"
                    },
                )
                Text(
                    text = program.durationLabel,
                    color = PrimaryText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = program.category.label,
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "${program.category.label} category"
                },
            )
            Text(
                text = program.title,
                color = PrimaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
