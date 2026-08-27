package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraftRequest
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraftResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutGenerationFailure
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import kotlinx.coroutines.CancellationException

internal class VerticalSetupFlow(
    private val generateDraft: GenerateVerticalWorkoutDraft,
    private val startDraft: StartVerticalWorkoutDraft,
    private val capabilities: DeviceCapabilities?,
) {
    fun enter(detail: ProgramDetail): ProgramSetupUiState {
        val deviceCapabilities = capabilities ?: return ProgramSetupUiState.DeviceUnavailable
        return ProgramSetupUiState.VerticalConfiguring(
            detail = detail,
            target = VerticalTarget.ONE_THOUSAND_FEET,
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
        )
    }

    fun setTarget(
        current: ProgramSetupUiState.VerticalConfiguring,
        target: VerticalTarget,
    ): ProgramSetupUiState {
        if (target !in VerticalTargetOptions) return current
        return current.copy(target = target, errorMessage = null)
    }

    fun generatePreview(current: ProgramSetupUiState.VerticalConfiguring): ProgramSetupUiState = when (
        val result = generateDraft(
            GenerateVerticalWorkoutDraftRequest(
                target = current.target,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            ),
        )
    ) {
        is VerticalWorkoutGenerationResult.Generated -> ProgramSetupUiState.VerticalDraftPreview(
            detail = current.detail,
            draft = result.draft,
            userMaxSpeed = current.userMaxSpeed,
            machineMaxSpeed = current.machineMaxSpeed,
            userMaxIncline = current.userMaxIncline,
            machineMaxIncline = current.machineMaxIncline,
        )

        is VerticalWorkoutGenerationResult.Rejected -> current.copy(
            errorMessage = verticalGenerationError(result.failure),
        )
    }

    fun accept(current: ProgramSetupUiState.VerticalDraftPreview): ProgramSetupUiState {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            return ProgramSetupUiState.DeviceUnavailable
        }
        return try {
            when (val result = startDraft(current.draft, deviceCapabilities)) {
                is StartVerticalWorkoutDraftResult.Started -> ProgramSetupUiState.Started(
                    plan = result.plan,
                    previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                )

                is StartVerticalWorkoutDraftResult.InvalidDraft,
                is StartVerticalWorkoutDraftResult.CapabilityValidationFailed,
                is StartVerticalWorkoutDraftResult.StarterFailed,
                -> ProgramSetupUiState.Error(VERTICAL_ACCEPT_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ProgramSetupUiState.Error(VERTICAL_ACCEPT_ERROR)
        }
    }
}

private fun verticalGenerationError(
    failure: VerticalWorkoutGenerationFailure,
): String = when (failure) {
    is VerticalWorkoutGenerationFailure.InvalidSpeedCaps,
    is VerticalWorkoutGenerationFailure.InvalidInclineCaps,
    is VerticalWorkoutGenerationFailure.SpeedCapsDoNotIntersect,
    is VerticalWorkoutGenerationFailure.InclineCapsDoNotIntersect,
    -> VERTICAL_CAPABILITIES_ERROR
}

private const val VERTICAL_CAPABILITIES_ERROR = "CAPABILITIES CANNOT SUPPORT THIS VERTICAL PREVIEW"
private const val VERTICAL_ACCEPT_ERROR = "Unable to accept VERTICAL preview"
