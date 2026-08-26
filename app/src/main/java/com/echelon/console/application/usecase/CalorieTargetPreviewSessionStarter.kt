package com.echelon.console.application.usecase

import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutTimelineContext

/** Starts an accepted CALORIE TARGET preview without telemetry or device commands. */
fun interface CalorieTargetPreviewSessionStarter {
    fun start(
        context: WorkoutTimelineContext.CalorieTargetPreview,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult
}
