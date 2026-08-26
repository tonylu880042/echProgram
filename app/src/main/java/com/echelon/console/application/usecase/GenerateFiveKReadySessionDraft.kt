package com.echelon.console.application.usecase

import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.FiveKReadySessionGenerator
import com.echelon.console.domain.FiveKReadySessionGeneratorInput
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths

data class GenerateFiveKReadySessionDraftRequest(
    val durationMinutes: Int,
    val baselinePace: FiveKReadyBaselinePace?,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

/** Generates a deterministic, preview-only 5K READY single-session draft. */
class GenerateFiveKReadySessionDraft(
    private val generator: FiveKReadySessionGenerator = FiveKReadySessionGenerator(),
) {
    operator fun invoke(
        request: GenerateFiveKReadySessionDraftRequest,
    ): FiveKReadySessionGenerationResult = generator.generate(
        FiveKReadySessionGeneratorInput(
            durationMinutes = request.durationMinutes,
            baselinePace = request.baselinePace,
            userMaxSpeed = request.userMaxSpeed,
            machineMaxSpeed = request.machineMaxSpeed,
            userMaxIncline = request.userMaxIncline,
            machineMaxIncline = request.machineMaxIncline,
        ),
    )
}
