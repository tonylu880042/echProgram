package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.EvaluateCalorieTargetEquipmentSnapshot
import com.echelon.console.application.usecase.EvaluateZone2EquipmentHeartRate
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.WorkoutSessionController

class LiveWorkoutViewModelFactory(
    private val controller: WorkoutSessionController,
    private val tickSource: WorkoutSessionTickSource = DefaultWorkoutSessionTickSource,
    private val getProgramDetail: GetProgramDetail,
    private val evaluateZone2EquipmentHeartRate: EvaluateZone2EquipmentHeartRate =
        EvaluateZone2EquipmentHeartRate(),
    private val evaluateCalorieTargetEquipmentSnapshot: EvaluateCalorieTargetEquipmentSnapshot =
        EvaluateCalorieTargetEquipmentSnapshot(),
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveWorkoutViewModel::class.java)) {
            return LiveWorkoutViewModel(
                controller = controller,
                tickSource = tickSource,
                getProgramDetail = getProgramDetail,
                evaluateZone2EquipmentHeartRate = evaluateZone2EquipmentHeartRate,
                evaluateCalorieTargetEquipmentSnapshot = evaluateCalorieTargetEquipmentSnapshot,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
