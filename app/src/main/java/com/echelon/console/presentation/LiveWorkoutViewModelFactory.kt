package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.WorkoutSessionController

class LiveWorkoutViewModelFactory(
    private val controller: WorkoutSessionController,
    private val tickSource: WorkoutSessionTickSource = DefaultWorkoutSessionTickSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveWorkoutViewModel::class.java)) {
            return LiveWorkoutViewModel(
                controller = controller,
                tickSource = tickSource,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
