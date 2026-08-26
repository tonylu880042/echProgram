package com.echelon.console.presentation

import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ValidatedWorkoutPlan

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

    data class Started(val plan: ValidatedWorkoutPlan) : ProgramSetupUiState

    data class Error(val message: String) : ProgramSetupUiState
}
