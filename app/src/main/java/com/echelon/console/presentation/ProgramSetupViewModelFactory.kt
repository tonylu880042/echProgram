package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.domain.DeviceCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class ProgramSetupViewModelFactory(
    private val getProgramDetail: GetProgramDetail,
    private val startWorkout: StartWorkout,
    private val startSurpriseWorkoutDraft: StartSurpriseWorkoutDraft,
    private val generateSurpriseWorkoutDraft: GenerateSurpriseWorkoutDraft,
    private val capabilities: DeviceCapabilities?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val startFiveKReadySessionDraft: StartFiveKReadySessionDraft,
    private val generateFiveKReadySessionDraft: GenerateFiveKReadySessionDraft,
    private val startVerticalWorkoutDraft: StartVerticalWorkoutDraft,
    private val generateVerticalWorkoutDraft: GenerateVerticalWorkoutDraft,
    private val startZone2WorkoutPreview: StartZone2WorkoutPreview,
    private val startCalorieTargetPreview: StartCalorieTargetPreview,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgramSetupViewModel::class.java)) {
            return ProgramSetupViewModel(
                getProgramDetail = getProgramDetail,
                startWorkout = startWorkout,
                startSurpriseWorkoutDraft = startSurpriseWorkoutDraft,
                generateSurpriseWorkoutDraft = generateSurpriseWorkoutDraft,
                capabilities = capabilities,
                dispatcher = dispatcher,
                startFiveKReadySessionDraft = startFiveKReadySessionDraft,
                generateFiveKReadySessionDraft = generateFiveKReadySessionDraft,
                startVerticalWorkoutDraft = startVerticalWorkoutDraft,
                generateVerticalWorkoutDraft = generateVerticalWorkoutDraft,
                startZone2WorkoutPreview = startZone2WorkoutPreview,
                startCalorieTargetPreview = startCalorieTargetPreview,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
