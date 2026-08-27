package com.echelon.console.presentation

import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartCalorieTargetPreviewRequest
import com.echelon.console.application.usecase.StartCalorieTargetPreviewResult
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramPreviewMode
import kotlinx.coroutines.CancellationException

internal class CalorieTargetSetupFlow(
    private val startPreview: StartCalorieTargetPreview,
    private val capabilities: DeviceCapabilities?,
) {
    fun enter(detail: ProgramDetail): ProgramSetupUiState {
        val deviceCapabilities = capabilities ?: return ProgramSetupUiState.DeviceUnavailable
        return ProgramSetupUiState.CalorieTargetConfiguring(
            detail = detail,
            representativeProfileDuration = calorieTargetRepresentativeDuration,
            selectedTarget = null,
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
        )
    }

    fun select(
        current: ProgramSetupUiState.CalorieTargetConfiguring,
        target: CalorieTargetOption,
    ): ProgramSetupUiState = when (
        val result = CalorieTargetSelection.createUserSelected(target.estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> current.copy(
            selectedTarget = result.selection,
            errorMessage = null,
        )

        is CalorieTargetSelectionResult.Rejected -> current.copy(
            errorMessage = CALORIE_TARGET_SELECTION_ERROR,
        )
    }

    fun start(current: ProgramSetupUiState.CalorieTargetConfiguring): ProgramSetupUiState {
        val target = current.selectedTarget
        if (target == null) {
            return current.copy(errorMessage = CALORIE_TARGET_SELECTION_ERROR)
        }
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            return ProgramSetupUiState.DeviceUnavailable
        }

        return try {
            when (
                val result = startPreview(
                    StartCalorieTargetPreviewRequest(
                        target = target,
                        capabilities = deviceCapabilities,
                    ),
                )
            ) {
                is StartCalorieTargetPreviewResult.Started -> ProgramSetupUiState.Started(
                    plan = result.plan,
                    previewMode = ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
                )

                is StartCalorieTargetPreviewResult.ProgramNotFound -> current.copy(
                    errorMessage = CALORIE_TARGET_PROGRAM_UNAVAILABLE_ERROR,
                )

                is StartCalorieTargetPreviewResult.ProgramDetailMismatch -> current.copy(
                    errorMessage = CALORIE_TARGET_DETAIL_ERROR,
                )

                is StartCalorieTargetPreviewResult.UnsupportedRepresentativeDuration -> current.copy(
                    errorMessage = CALORIE_TARGET_DURATION_ERROR,
                )

                is StartCalorieTargetPreviewResult.RepresentativeProfileDurationMismatch -> current.copy(
                    errorMessage = CALORIE_TARGET_PROFILE_ERROR,
                )

                is StartCalorieTargetPreviewResult.CapabilityValidationFailed -> current.copy(
                    errorMessage = CALORIE_TARGET_CAPABILITIES_ERROR,
                )

                is StartCalorieTargetPreviewResult.StarterFailed -> current.copy(
                    errorMessage = CALORIE_TARGET_START_ERROR,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            current.copy(errorMessage = CALORIE_TARGET_START_ERROR)
        }
    }
}

private const val CALORIE_TARGET_SELECTION_ERROR = "SELECT A CALORIE TARGET BEFORE STARTING"
private const val CALORIE_TARGET_PROGRAM_UNAVAILABLE_ERROR =
    "CALORIE TARGET PREVIEW IS UNAVAILABLE"
private const val CALORIE_TARGET_DETAIL_ERROR = "CALORIE TARGET PREVIEW DETAIL IS INVALID"
private const val CALORIE_TARGET_DURATION_ERROR =
    "CALORIE TARGET REQUIRES A 40-MINUTE REPRESENTATIVE PROFILE"
private const val CALORIE_TARGET_PROFILE_ERROR = "CALORIE TARGET PROFILE IS INVALID"
private const val CALORIE_TARGET_CAPABILITIES_ERROR =
    "CAPABILITIES CANNOT SUPPORT CALORIE TARGET PREVIEW"
private const val CALORIE_TARGET_START_ERROR = "UNABLE TO START CALORIE TARGET PREVIEW"

private val calorieTargetRepresentativeDuration = DurationMinutes(40)
