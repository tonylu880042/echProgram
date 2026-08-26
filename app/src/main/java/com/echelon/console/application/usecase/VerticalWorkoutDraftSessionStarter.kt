package com.echelon.console.application.usecase

import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.VerticalWorkoutDraft

/** Starts an accepted VERTICAL draft without issuing device commands. */
fun interface VerticalWorkoutDraftSessionStarter {
    fun start(
        draft: VerticalWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult
}
