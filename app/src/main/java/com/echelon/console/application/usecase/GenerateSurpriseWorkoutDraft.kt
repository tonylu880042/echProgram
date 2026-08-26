package com.echelon.console.application.usecase

import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.SurpriseWorkoutGenerator
import com.echelon.console.domain.SurpriseWorkoutGeneratorInput
import com.echelon.console.domain.SpeedTenths

data class GenerateSurpriseWorkoutDraftRequest(
    val durationMinutes: Int,
    val effort: SurpriseWorkoutEffort,
    val userProfileRevision: String,
    val regenerationIndex: Int,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

/** Generates a deterministic preview through the application boundary. */
class GenerateSurpriseWorkoutDraft(
    private val generator: SurpriseWorkoutGenerator = SurpriseWorkoutGenerator(),
) {
    operator fun invoke(
        request: GenerateSurpriseWorkoutDraftRequest,
    ): SurpriseWorkoutGenerationResult = generator.generate(
        SurpriseWorkoutGeneratorInput(
            durationMinutes = request.durationMinutes,
            effort = request.effort,
            userProfileRevision = request.userProfileRevision,
            regenerationIndex = request.regenerationIndex,
            generatorVersion = GENERATOR_VERSION,
            userMaxSpeed = request.userMaxSpeed,
            machineMaxSpeed = request.machineMaxSpeed,
            userMaxIncline = request.userMaxIncline,
            machineMaxIncline = request.machineMaxIncline,
        ),
    )

    private companion object {
        const val GENERATOR_VERSION = "v1"
    }
}
