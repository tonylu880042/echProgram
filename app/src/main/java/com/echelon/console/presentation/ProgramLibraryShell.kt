package com.echelon.console.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echelon.console.domain.HeroProgram

private val ConsoleVoid = Color(0xFF071016)
private val RaisedCarbon = Color(0xFF12232C)
private val EchelonCyan = Color(0xFF28A8FF)
private val SteelText = Color(0xFFE5EDF2)
private val InstrumentText = Color(0xFFA4B3BD)

@Composable
fun ProgramLibraryShell(
    programs: List<HeroProgram>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = ConsoleVoid,
        contentColor = SteelText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ECHELON",
                color = EchelonCyan,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "WHAT DO YOU WANT TODAY?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Goal-first foundation shell — choose a workout promise.",
                color = InstrumentText,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .semantics {
                        contentDescription = "Goal-first hero programs"
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                programs.forEach { program ->
                    Card(
                        modifier = Modifier.width(220.dp),
                        colors = CardDefaults.cardColors(containerColor = RaisedCarbon),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = program.title,
                                color = EchelonCyan,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = program.promise,
                                color = InstrumentText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
