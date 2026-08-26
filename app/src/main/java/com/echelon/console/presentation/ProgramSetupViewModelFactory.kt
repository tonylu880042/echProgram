package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.domain.DeviceCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class ProgramSetupViewModelFactory(
    private val getProgramDetail: GetProgramDetail,
    private val startWorkout: StartWorkout,
    private val capabilities: DeviceCapabilities?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgramSetupViewModel::class.java)) {
            return ProgramSetupViewModel(
                getProgramDetail = getProgramDetail,
                startWorkout = startWorkout,
                capabilities = capabilities,
                dispatcher = dispatcher,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
