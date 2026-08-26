package com.echelon.console.application.usecase

import com.echelon.console.domain.FiveKReadySessionDraft
import com.echelon.console.domain.ValidatedWorkoutPlan

/** Starts an accepted 5K READY draft without issuing device commands. */
fun interface FiveKReadySessionDraftSessionStarter {
    fun start(
        draft: FiveKReadySessionDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult
}
