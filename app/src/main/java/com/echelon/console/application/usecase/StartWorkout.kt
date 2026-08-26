package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan

class StartWorkout(
    private val sessionStarter: WorkoutSessionStarter,
) {
    operator fun invoke(
        plan: WorkoutPlan,
        capabilities: DeviceCapabilities,
    ): StartWorkoutResult = when (
        val validation = ValidatedWorkoutPlan.create(plan, capabilities)
    ) {
        is ValidatedWorkoutPlanResult.Valid -> {
            when (val result = sessionStarter.start(validation.plan)) {
                is WorkoutSessionStarterResult.Started,
                WorkoutSessionStarterResult.Accepted,
                -> StartWorkoutResult.Valid(validation.plan)

                is WorkoutSessionStarterResult.Failed -> StartWorkoutResult.StarterFailure(
                    result.failure,
                )
            }
        }

        is ValidatedWorkoutPlanResult.Invalid -> StartWorkoutResult.Invalid(validation.errors)
    }
}

sealed interface StartWorkoutResult {
    data class Valid(val plan: ValidatedWorkoutPlan) : StartWorkoutResult

    data class Invalid(val errors: List<PlanValidationError>) : StartWorkoutResult

    data class StarterFailure(
        val failure: WorkoutSessionStartFailure,
    ) : StartWorkoutResult
}
