package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraftRequest
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartFiveKReadySessionDraftResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationFailure
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

internal class FiveKReadySetupFlow(
    private val generateDraft: GenerateFiveKReadySessionDraft,
    private val startDraft: StartFiveKReadySessionDraft,
    private val capabilities: DeviceCapabilities?,
) {
    fun enter(detail: ProgramDetail): ProgramSetupUiState {
        val deviceCapabilities = capabilities ?: return ProgramSetupUiState.DeviceUnavailable
        return ProgramSetupUiState.FiveKReadyConfiguring(
            detail = detail,
            duration = detail.defaultSettings.duration.takeIf {
                it in FiveKReadyDurationOptions
            } ?: fiveKReadyDefaultDuration,
            baselinePaceText = "",
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
        )
    }

    fun setDuration(
        current: ProgramSetupUiState.FiveKReadyConfiguring,
        duration: DurationMinutes,
    ): ProgramSetupUiState {
        if (duration !in FiveKReadyDurationOptions) return current
        return current.copy(duration = duration, errorMessage = null)
    }

    fun setBaselinePace(
        current: ProgramSetupUiState.FiveKReadyConfiguring,
        text: String,
    ): ProgramSetupUiState = current.copy(baselinePaceText = text, errorMessage = null)

    fun generatePreview(current: ProgramSetupUiState.FiveKReadyConfiguring): ProgramSetupUiState {
        val baseline = parseFiveKBaseline(current.baselinePaceText)
        if (baseline == null) {
            return current.copy(errorMessage = FIVE_K_READY_BASELINE_INPUT_ERROR)
        }

        return when (
            val result = generateDraft(
                GenerateFiveKReadySessionDraftRequest(
                    durationMinutes = current.duration.value,
                    baselinePace = baseline,
                    userMaxSpeed = current.userMaxSpeed,
                    machineMaxSpeed = current.machineMaxSpeed,
                    userMaxIncline = current.userMaxIncline,
                    machineMaxIncline = current.machineMaxIncline,
                ),
            )
        ) {
            is FiveKReadySessionGenerationResult.Generated -> ProgramSetupUiState.FiveKReadyDraftPreview(
                detail = current.detail,
                draft = result.draft,
                baselinePaceText = current.baselinePaceText,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )

            is FiveKReadySessionGenerationResult.Rejected -> current.copy(
                errorMessage = fiveKGenerationError(result.failure),
            )
        }
    }

    fun accept(current: ProgramSetupUiState.FiveKReadyDraftPreview): ProgramSetupUiState {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            return ProgramSetupUiState.DeviceUnavailable
        }
        return try {
            when (val result = startDraft(current.draft, deviceCapabilities)) {
                is StartFiveKReadySessionDraftResult.Started -> ProgramSetupUiState.Started(
                    plan = result.plan,
                    previewMode = ProgramPreviewMode.BASELINE_PREVIEW,
                )

                is StartFiveKReadySessionDraftResult.InvalidDraft,
                is StartFiveKReadySessionDraftResult.CapabilityValidationFailed,
                is StartFiveKReadySessionDraftResult.StarterFailed,
                -> ProgramSetupUiState.Error(FIVE_K_READY_ACCEPT_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ProgramSetupUiState.Error(FIVE_K_READY_ACCEPT_ERROR)
        }
    }
}

private fun parseFiveKBaseline(text: String): FiveKReadyBaselinePace? {
    val normalized = text.trim()
    if (!fiveKReadyPacePattern.matches(normalized)) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value > Int.MAX_VALUE / 10.0) return null
    return FiveKReadyBaselinePace(
        speed = SpeedTenths((value * 10.0).roundToInt()),
        source = FiveKReadyBaselineSource.USER_ENTERED,
    )
}

private fun fiveKGenerationError(
    failure: FiveKReadySessionGenerationFailure,
): String = when (failure) {
    FiveKReadySessionGenerationFailure.BaselineRequired -> FIVE_K_READY_BASELINE_INPUT_ERROR
    is FiveKReadySessionGenerationFailure.BaselineSourceNotUserEntered ->
        FIVE_K_READY_BASELINE_INPUT_ERROR
    is FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope ->
        "RUN PACE MUST BE BETWEEN 2.8 AND 6.0 MPH"
    is FiveKReadySessionGenerationFailure.BaselineExceedsEffectiveSpeedCap ->
        "RUN PACE EXCEEDS THE EFFECTIVE SPEED CAP"
    is FiveKReadySessionGenerationFailure.BaselineLeavesNoRecoveryMargin ->
        "RUN PACE 2.8 MPH LEAVES NO RECOVERY MARGIN; ENTER AT LEAST 2.9 MPH"
    is FiveKReadySessionGenerationFailure.InvalidSpeedCap,
    is FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect,
    -> FIVE_K_READY_CAPABILITIES_ERROR
    is FiveKReadySessionGenerationFailure.InvalidInclineCap,
    is FiveKReadySessionGenerationFailure.InclineCapsDoNotIntersect,
    -> FIVE_K_READY_CAPABILITIES_ERROR
    is FiveKReadySessionGenerationFailure.UnsupportedDuration ->
        "SELECT 20, 30, 40, OR 60 MINUTES"
}

private const val FIVE_K_READY_BASELINE_INPUT_ERROR =
    "SET YOUR RUN PACE BEFORE PREVIEW (MPH, FOR EXAMPLE 4.0)"
private const val FIVE_K_READY_CAPABILITIES_ERROR =
    "CAPABILITIES CANNOT SUPPORT THIS PREVIEW"
private const val FIVE_K_READY_ACCEPT_ERROR = "Unable to accept 5K READY preview"

private val fiveKReadyPacePattern = Regex("""^\d+(?:\.\d)?$""")
private val fiveKReadyDefaultDuration = DurationMinutes(30)
