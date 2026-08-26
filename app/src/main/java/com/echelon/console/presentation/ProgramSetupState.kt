package com.echelon.console.presentation

import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan

internal val SurpriseWorkoutDurationOptions = listOf(
    DurationMinutes(10),
    DurationMinutes(20),
    DurationMinutes(30),
    DurationMinutes(45),
)

sealed interface ProgramSetupUiState {
    data object Library : ProgramSetupUiState

    data class Loading(val programId: ProgramId) : ProgramSetupUiState

    data class Ready(val detail: ProgramDetail) : ProgramSetupUiState

    data class Unavailable(val programId: ProgramId) : ProgramSetupUiState

    data object DeviceUnavailable : ProgramSetupUiState

    data class Personalizing(
        val detail: ProgramDetail,
        val settings: PlanSettings,
        val fieldErrors: List<PlanValidationError> = emptyList(),
    ) : ProgramSetupUiState

    data class Configuring(
        val detail: ProgramDetail,
        val duration: DurationMinutes,
        val effort: SurpriseWorkoutEffort,
        val regenerationIndex: Int,
        val userMaxSpeed: SpeedTenths,
        val machineMaxSpeed: SpeedTenths,
        val userMaxIncline: InclineTenths,
        val machineMaxIncline: InclineTenths,
        val errorMessage: String? = null,
    ) : ProgramSetupUiState

    data class DraftPreview(
        val detail: ProgramDetail,
        val draft: SurpriseWorkoutDraft,
        val userMaxSpeed: SpeedTenths,
        val machineMaxSpeed: SpeedTenths,
        val userMaxIncline: InclineTenths,
        val machineMaxIncline: InclineTenths,
        val errorMessage: String? = null,
    ) : ProgramSetupUiState

    data class Started(
        val plan: ValidatedWorkoutPlan,
        val previewMode: ProgramPreviewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
    ) : ProgramSetupUiState

    data class Error(val message: String) : ProgramSetupUiState
}
