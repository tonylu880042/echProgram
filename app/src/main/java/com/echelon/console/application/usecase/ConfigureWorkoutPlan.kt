package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutPlanValidator

fun interface WorkoutSessionStarter {
    fun start(plan: ValidatedWorkoutPlan)
}

class ConfigureWorkoutPlan(
    private val sessionStarter: WorkoutSessionStarter,
) {
    operator fun invoke(
        plan: WorkoutPlan,
        capabilities: DeviceCapabilities,
    ): ConfigureWorkoutPlanResult {
        val errors = WorkoutPlanValidator.validate(plan, capabilities)
        if (errors.isNotEmpty()) {
            return ConfigureWorkoutPlanResult.Invalid(errors)
        }

        val validatedPlan = ValidatedWorkoutPlan(plan)
        sessionStarter.start(validatedPlan)
        return ConfigureWorkoutPlanResult.Valid(validatedPlan)
    }
}

sealed interface ConfigureWorkoutPlanResult {
    data class Valid(val plan: ValidatedWorkoutPlan) : ConfigureWorkoutPlanResult

    data class Invalid(val errors: List<PlanValidationError>) : ConfigureWorkoutPlanResult
}
