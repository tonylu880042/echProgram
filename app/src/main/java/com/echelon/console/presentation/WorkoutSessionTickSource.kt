package com.echelon.console.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun interface WorkoutSessionTickSource {
    fun ticks(): Flow<Int>
}

object DefaultWorkoutSessionTickSource : WorkoutSessionTickSource {
    override fun ticks(): Flow<Int> = flow {
        while (currentCoroutineContext().isActive) {
            delay(ONE_SECOND_MILLIS)
            emit(ONE_SECOND)
        }
    }

    private const val ONE_SECOND = 1
    private const val ONE_SECOND_MILLIS = 1_000L
}
