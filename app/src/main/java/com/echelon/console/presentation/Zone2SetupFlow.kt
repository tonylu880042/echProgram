package com.echelon.console.presentation

import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.application.usecase.StartZone2WorkoutPreviewRequest
import com.echelon.console.application.usecase.StartZone2WorkoutPreviewResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateTargetBound
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeFailure
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramPreviewMode
import kotlinx.coroutines.CancellationException

internal class Zone2SetupFlow(
    private val startPreview: StartZone2WorkoutPreview,
    private val capabilities: DeviceCapabilities?,
) {
    fun enter(detail: ProgramDetail): ProgramSetupUiState {
        val deviceCapabilities = capabilities ?: return ProgramSetupUiState.DeviceUnavailable
        return ProgramSetupUiState.Zone2Configuring(
            detail = detail,
            duration = detail.defaultSettings.duration.takeIf {
                it in Zone2DurationOptions
            } ?: zone2DefaultDuration,
            lowerBpmText = "",
            upperBpmText = "",
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
        )
    }

    fun setDuration(
        current: ProgramSetupUiState.Zone2Configuring,
        duration: DurationMinutes,
    ): ProgramSetupUiState {
        if (duration !in Zone2DurationOptions) return current
        return current.copy(duration = duration, errorMessage = null)
    }

    fun setLowerBpm(
        current: ProgramSetupUiState.Zone2Configuring,
        text: String,
    ): ProgramSetupUiState = current.copy(lowerBpmText = text, errorMessage = null)

    fun setUpperBpm(
        current: ProgramSetupUiState.Zone2Configuring,
        text: String,
    ): ProgramSetupUiState = current.copy(upperBpmText = text, errorMessage = null)

    fun start(current: ProgramSetupUiState.Zone2Configuring): ProgramSetupUiState {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            return ProgramSetupUiState.DeviceUnavailable
        }

        val targetResult = HeartRateTargetRange.createUserConfirmed(
            lowerBpm = current.lowerBpmText.trim().toIntOrNull(),
            upperBpm = current.upperBpmText.trim().toIntOrNull(),
        )
        val target = when (targetResult) {
            is HeartRateTargetRangeResult.Accepted -> targetResult.target
            is HeartRateTargetRangeResult.Rejected -> {
                return current.copy(
                    errorMessage = zone2TargetError(
                        targetResult.failure,
                        current.lowerBpmText,
                        current.upperBpmText,
                    ),
                )
            }
        }

        return try {
            when (
                val result = startPreview(
                    StartZone2WorkoutPreviewRequest(
                        target = target,
                        duration = current.duration,
                        capabilities = deviceCapabilities,
                    ),
                )
            ) {
                is StartZone2WorkoutPreviewResult.Started -> ProgramSetupUiState.Started(
                    plan = result.plan,
                    previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
                )

                is StartZone2WorkoutPreviewResult.ProgramNotFound -> current.copy(
                    errorMessage = ZONE_2_PROGRAM_UNAVAILABLE_ERROR,
                )

                is StartZone2WorkoutPreviewResult.UnsupportedDuration -> current.copy(
                    errorMessage = ZONE_2_UNSUPPORTED_DURATION_ERROR,
                )

                is StartZone2WorkoutPreviewResult.CapabilityValidationFailed -> current.copy(
                    errorMessage = ZONE_2_CAPABILITIES_ERROR,
                )

                is StartZone2WorkoutPreviewResult.StarterFailed -> current.copy(
                    errorMessage = ZONE_2_START_ERROR,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            current.copy(errorMessage = ZONE_2_START_ERROR)
        }
    }
}

private fun zone2TargetError(
    failure: HeartRateTargetRangeFailure,
    lowerBpmText: String,
    upperBpmText: String,
): String = when (failure) {
    HeartRateTargetRangeFailure.MissingLowerBound ->
        if (lowerBpmText.isBlank()) "LOWER BPM IS REQUIRED" else "LOWER BPM MUST BE A WHOLE NUMBER"

    HeartRateTargetRangeFailure.MissingUpperBound ->
        if (upperBpmText.isBlank()) "UPPER BPM IS REQUIRED" else "UPPER BPM MUST BE A WHOLE NUMBER"

    is HeartRateTargetRangeFailure.NonPositiveBound -> when (failure.bound) {
        HeartRateTargetBound.LOWER -> "LOWER BPM MUST BE GREATER THAN 0"
        HeartRateTargetBound.UPPER -> "UPPER BPM MUST BE GREATER THAN 0"
    }

    is HeartRateTargetRangeFailure.LowerAboveUpper ->
        "LOWER BPM MUST NOT EXCEED UPPER BPM"
}

private const val ZONE_2_PROGRAM_UNAVAILABLE_ERROR = "ZONE 2 PREVIEW IS UNAVAILABLE"
private const val ZONE_2_UNSUPPORTED_DURATION_ERROR = "SELECT 20, 30, 45, OR 60 MINUTES"
private const val ZONE_2_CAPABILITIES_ERROR = "CAPABILITIES CANNOT SUPPORT ZONE 2 PREVIEW"
private const val ZONE_2_START_ERROR = "UNABLE TO START ZONE 2 PREVIEW"

private val zone2DefaultDuration = DurationMinutes(30)
