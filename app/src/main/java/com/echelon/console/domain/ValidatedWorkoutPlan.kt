package com.echelon.console.domain

@JvmInline
value class ValidatedWorkoutPlan private constructor(val plan: WorkoutPlan) {
    companion object {
        fun create(
            plan: WorkoutPlan,
            capabilities: DeviceCapabilities,
        ): ValidatedWorkoutPlanResult {
            val errors = WorkoutPlanValidator.validate(plan, capabilities)
            return if (errors.isEmpty()) {
                ValidatedWorkoutPlanResult.Valid(ValidatedWorkoutPlan(plan))
            } else {
                ValidatedWorkoutPlanResult.Invalid(errors)
            }
        }
    }
}

sealed interface ValidatedWorkoutPlanResult {
    data class Valid(val plan: ValidatedWorkoutPlan) : ValidatedWorkoutPlanResult

    data class Invalid(val errors: List<PlanValidationError>) : ValidatedWorkoutPlanResult
}
