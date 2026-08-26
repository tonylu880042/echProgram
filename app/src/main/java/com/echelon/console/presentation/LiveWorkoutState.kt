package com.echelon.console.presentation

import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths

sealed interface LiveWorkoutUiState {
    data object NoSession : LiveWorkoutUiState

    data class Active(
        val workout: LiveWorkoutReadModel,
    ) : LiveWorkoutUiState

    data class Completed(
        val summary: LiveWorkoutSummary,
    ) : LiveWorkoutUiState

    data class Stopped(
        val summary: LiveWorkoutSummary,
    ) : LiveWorkoutUiState

    data class Error(
        val message: String,
    ) : LiveWorkoutUiState
}

data class LiveWorkoutReadModel(
    val programId: ProgramId,
    val elapsedSeconds: Int,
    val remainingSeconds: Int,
    val currentSegment: LiveWorkoutSegment,
    val nextSegment: LiveWorkoutSegment?,
    val secondsUntilNextSegment: Int?,
    val targetSpeed: SpeedTenths,
    val targetIncline: InclineTenths,
    val isPaused: Boolean,
    val programTitle: String,
    val previewMode: ProgramPreviewMode,
)

data class LiveWorkoutSegment(
    val index: Int,
    val name: String,
)

data class LiveWorkoutSummary(
    val programId: ProgramId,
    val elapsedSeconds: Int,
    val totalDurationSeconds: Int,
    val programTitle: String,
    val previewMode: ProgramPreviewMode,
)
