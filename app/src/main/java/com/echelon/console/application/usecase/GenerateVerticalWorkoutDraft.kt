package com.echelon.console.application.usecase

import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import com.echelon.console.domain.VerticalWorkoutGenerator
import com.echelon.console.domain.VerticalWorkoutGeneratorInput

data class GenerateVerticalWorkoutDraftRequest(
    val target: VerticalTarget,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

/** Generates a deterministic, representative-preview-only VERTICAL draft. */
class GenerateVerticalWorkoutDraft(
    private val generator: VerticalWorkoutGenerator = VerticalWorkoutGenerator(),
) {
    operator fun invoke(
        request: GenerateVerticalWorkoutDraftRequest,
    ): VerticalWorkoutGenerationResult = generator.generate(
        VerticalWorkoutGeneratorInput(
            target = request.target,
            userMaxSpeed = request.userMaxSpeed,
            machineMaxSpeed = request.machineMaxSpeed,
            userMaxIncline = request.userMaxIncline,
            machineMaxIncline = request.machineMaxIncline,
        ),
    )
}
