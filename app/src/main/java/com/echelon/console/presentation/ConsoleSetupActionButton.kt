package com.echelon.console.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class ConsoleSetupActionButtonVariant {
    PRIMARY,
    SECONDARY,
}

@Composable
internal fun ConsoleSetupActionButton(
    label: String,
    onClick: () -> Unit,
    variant: ConsoleSetupActionButtonVariant,
) {
    val primary = variant == ConsoleSetupActionButtonVariant.PRIMARY
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (primary) CarbonHigh else Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.dp, if (primary) Cyan else RuleColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = if (primary) Cyan else PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
