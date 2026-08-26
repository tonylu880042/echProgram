package com.echelon.console.application.usecase

import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutTimelineContext

/** Starts an accepted ZONE 2 preview without issuing telemetry or device commands. */
fun interface Zone2WorkoutPreviewSessionStarter {
    fun start(
        context: WorkoutTimelineContext.Zone2Preview,
        plan: ValidatedWorkoutPlan,
    ): WorkoutSessionStarterResult
}
