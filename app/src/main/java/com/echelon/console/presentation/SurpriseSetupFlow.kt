package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraftRequest
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraftResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import kotlinx.coroutines.CancellationException

internal class SurpriseSetupFlow(
    private val generateDraft: GenerateSurpriseWorkoutDraft,
    private val startDraft: StartSurpriseWorkoutDraft,
    private val capabilities: DeviceCapabilities?,
) {
    fun enter(detail: ProgramDetail): ProgramSetupUiState {
        val deviceCapabilities = capabilities ?: return ProgramSetupUiState.DeviceUnavailable
        return ProgramSetupUiState.Configuring(
            detail = detail,
            duration = detail.defaultSettings.duration.takeIf {
                it in SurpriseWorkoutDurationOptions
            } ?: surpriseDefaultDuration,
            effort = surpriseDefaultEffort,
            regenerationIndex = 0,
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
            errorMessage = if (detail.defaultSettings.duration in SurpriseWorkoutDurationOptions) {
                null
            } else {
                SURPRISE_UNSUPPORTED_DURATION_ERROR
            },
        )
    }

    fun setDuration(
        current: ProgramSetupUiState.Configuring,
        duration: DurationMinutes,
    ): ProgramSetupUiState {
        if (duration !in SurpriseWorkoutDurationOptions) return current
        return current.copy(
            duration = duration,
            regenerationIndex = 0,
            errorMessage = null,
        )
    }

    fun setEffort(
        current: ProgramSetupUiState.Configuring,
        effort: SurpriseWorkoutEffort,
    ): ProgramSetupUiState = current.copy(
        effort = effort,
        regenerationIndex = 0,
        errorMessage = null,
    )

    fun generatePreview(current: ProgramSetupUiState.Configuring): ProgramSetupUiState {
        val draft = generateSurpriseDraft(current)
        return if (draft != null) {
            ProgramSetupUiState.DraftPreview(
                detail = current.detail,
                draft = draft,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
        } else {
            current.copy(errorMessage = SURPRISE_GENERATION_ERROR)
        }
    }

    fun regeneratePreview(current: ProgramSetupUiState.DraftPreview): ProgramSetupUiState {
        val nextConfiguring = ProgramSetupUiState.Configuring(
            detail = current.detail,
            duration = DurationMinutes(current.draft.metadata.durationMinutes),
            effort = current.draft.metadata.effort,
            regenerationIndex = current.draft.metadata.regenerationIndex + 1,
            userMaxSpeed = current.userMaxSpeed,
            machineMaxSpeed = current.machineMaxSpeed,
            userMaxIncline = current.userMaxIncline,
            machineMaxIncline = current.machineMaxIncline,
        )
        val draft = generateSurpriseDraft(nextConfiguring)
        return if (draft != null) {
            ProgramSetupUiState.DraftPreview(
                detail = current.detail,
                draft = draft,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
        } else {
            current.copy(errorMessage = SURPRISE_GENERATION_ERROR)
        }
    }

    fun accept(current: ProgramSetupUiState.DraftPreview): ProgramSetupUiState {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            return ProgramSetupUiState.DeviceUnavailable
        }
        return try {
            when (val result = startDraft(current.draft, deviceCapabilities)) {
                is StartSurpriseWorkoutDraftResult.Started ->
                    ProgramSetupUiState.Started(
                        result.plan,
                        ProgramPreviewMode.GENERATED_PREVIEW,
                    )

                is StartSurpriseWorkoutDraftResult.InvalidDraft,
                is StartSurpriseWorkoutDraftResult.CapabilityValidationFailed,
                is StartSurpriseWorkoutDraftResult.StarterFailed,
                -> ProgramSetupUiState.Error(SURPRISE_ACCEPT_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ProgramSetupUiState.Error(SURPRISE_ACCEPT_ERROR)
        }
    }

    private fun generateSurpriseDraft(
        configuring: ProgramSetupUiState.Configuring,
    ): SurpriseWorkoutDraft? {
        val result = generateDraft(
            GenerateSurpriseWorkoutDraftRequest(
                durationMinutes = configuring.duration.value,
                effort = configuring.effort,
                userProfileRevision = SURPRISE_PROFILE_REVISION,
                regenerationIndex = configuring.regenerationIndex,
                userMaxSpeed = configuring.userMaxSpeed,
                machineMaxSpeed = configuring.machineMaxSpeed,
                userMaxIncline = configuring.userMaxIncline,
                machineMaxIncline = configuring.machineMaxIncline,
            ),
        )
        return when (result) {
            is SurpriseWorkoutGenerationResult.Generated -> result.draft
            is SurpriseWorkoutGenerationResult.Rejected -> null
        }
    }
}

private const val SURPRISE_PROFILE_REVISION = "anonymous-baseline-r1"
private const val SURPRISE_GENERATION_ERROR = "Unable to generate workout preview"
private const val SURPRISE_ACCEPT_ERROR = "Unable to accept workout preview"
private const val SURPRISE_UNSUPPORTED_DURATION_ERROR =
    "Default duration unavailable; using 20 minutes"

private val surpriseDefaultDuration = DurationMinutes(20)
private val surpriseDefaultEffort = SurpriseWorkoutEffort.SWEAT
