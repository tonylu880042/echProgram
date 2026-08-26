package com.echelon.console.application.usecase

import com.echelon.console.domain.WorkoutSessionState

interface WorkoutSessionController {
    fun currentState(): WorkoutSessionState?

    fun advance(elapsedSeconds: Int): WorkoutSessionCommandResult

    fun pause(): WorkoutSessionCommandResult

    fun resume(): WorkoutSessionCommandResult

    fun stop(): WorkoutSessionCommandResult
}
