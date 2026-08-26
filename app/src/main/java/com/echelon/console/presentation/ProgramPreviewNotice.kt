package com.echelon.console.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echelon.console.domain.ProgramPreviewMode

private val PreviewNoticeColor = Color(0xFFFFC857)

@Composable
internal fun ProgramPreviewNotice(
    previewMode: ProgramPreviewMode,
    modifier: Modifier = Modifier,
) {
    if (previewMode == ProgramPreviewMode.FIXED_PROFILE_PREVIEW) {
        return
    }
    val message = previewMode.disclosureMessage()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CarbonLow, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, PreviewNoticeColor), RoundedCornerShape(4.dp))
            .semantics { contentDescription = "PREVIEW ONLY" }
            .padding(12.dp),
    ) {
        Text(
            text = "PREVIEW ONLY",
            color = PreviewNoticeColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = message,
            color = MutedText,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

internal fun ProgramPreviewMode.disclosureMessage(): String = when (this) {
    ProgramPreviewMode.FIXED_PROFILE_PREVIEW ->
        "Follow the displayed targets manually; FitOS control is not enabled."
    ProgramPreviewMode.BASELINE_PREVIEW ->
        "Baseline progression is not connected to approved workout history yet."
    ProgramPreviewMode.ELEVATION_TARGET_PREVIEW ->
        "Elevation target and completion rules require approved elevation data."
    ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW ->
        "History-based adaptation requires approved workout history."
    ProgramPreviewMode.HEART_RATE_PREVIEW ->
        "Heart-rate zone control requires an approved HR source."
    ProgramPreviewMode.GENERATED_PREVIEW ->
        "Generated plans require an approved deterministic generator."
    ProgramPreviewMode.CALORIE_TARGET_PREVIEW ->
        "Calorie target progress requires FitOS estimated calories; estimator semantics are pending."
}
