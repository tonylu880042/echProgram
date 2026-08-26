package com.echelon.console.application.usecase

import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.ValidatedWorkoutPlan

/**
 * Application capability for starting an already accepted SURPRISE ME draft.
 * The implementation may create only an in-memory profile session; device
 * control is deliberately outside this port.
 */
fun interface SurpriseWorkoutDraftSessionStarter {
    fun start(
        draft: SurpriseWorkoutDraft,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult
}
