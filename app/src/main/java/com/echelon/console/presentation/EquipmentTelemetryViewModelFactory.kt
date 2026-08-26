package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.ConnectEquipment
import com.echelon.console.application.usecase.DisconnectEquipment
import com.echelon.console.application.usecase.EquipmentTelemetrySource
import com.echelon.console.application.usecase.ObserveEquipmentTelemetry

class EquipmentTelemetryViewModelFactory(
    private val source: EquipmentTelemetrySource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EquipmentTelemetryViewModel::class.java)) {
            return EquipmentTelemetryViewModel(
                observeEquipmentTelemetry = ObserveEquipmentTelemetry(source),
                connectEquipment = ConnectEquipment(source),
                disconnectEquipment = DisconnectEquipment(source),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
