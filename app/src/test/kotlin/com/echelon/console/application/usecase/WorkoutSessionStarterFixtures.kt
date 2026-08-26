package com.echelon.console.application.usecase

import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.WorkoutSessionProgress
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionTarget
import com.echelon.console.domain.WorkoutSessionTargetMode
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineSegment

internal fun testStartedWorkoutResult(): WorkoutSessionStarterResult.Started {
    val segment = WorkoutTimelineSegment(
        name = "Test Segment",
        startSecond = 0,
        endSecond = 60,
        targetSpeed = SpeedTenths(20),
        targetIncline = InclineTenths(0),
    )
    val timeline = WorkoutTimeline(
        programId = ProgramId("TEST"),
        totalDurationSeconds = 60,
        segments = listOf(segment),
    )
    return WorkoutSessionStarterResult.Started(
        state = WorkoutSessionState.Running(
            timeline = timeline,
            progress = WorkoutSessionProgress(
                elapsedSeconds = 0,
                remainingSeconds = 60,
                currentSegmentIndex = 0,
                currentSegment = segment,
                nextSegment = null,
                secondsUntilNextSegment = null,
                target = WorkoutSessionTarget(
                    speed = SpeedTenths(20),
                    incline = InclineTenths(0),
                    mode = WorkoutSessionTargetMode.PROFILE,
                ),
            ),
        ),
    )
}
