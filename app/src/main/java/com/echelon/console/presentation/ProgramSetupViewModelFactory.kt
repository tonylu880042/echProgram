package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.SurpriseWorkoutGenerator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class ProgramSetupViewModelFactory(
    private val getProgramDetail: GetProgramDetail,
    private val startWorkout: StartWorkout,
    private val startSurpriseWorkoutDraft: StartSurpriseWorkoutDraft,
    private val surpriseWorkoutGenerator: SurpriseWorkoutGenerator,
    private val capabilities: DeviceCapabilities?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgramSetupViewModel::class.java)) {
            return ProgramSetupViewModel(
                getProgramDetail = getProgramDetail,
                startWorkout = startWorkout,
                startSurpriseWorkoutDraft = startSurpriseWorkoutDraft,
                surpriseWorkoutGenerator = surpriseWorkoutGenerator,
                capabilities = capabilities,
                dispatcher = dispatcher,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
