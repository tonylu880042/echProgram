package com.echelon.console.application.usecase

import com.echelon.console.domain.ValidatedWorkoutPlan

fun interface WorkoutSessionStarter {
    fun start(plan: ValidatedWorkoutPlan): WorkoutSessionStarterResult
}
