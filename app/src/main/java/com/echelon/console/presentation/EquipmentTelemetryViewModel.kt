package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import com.echelon.console.application.usecase.ConnectEquipment
import com.echelon.console.application.usecase.DisconnectEquipment
import com.echelon.console.application.usecase.EquipmentTelemetrySource
import com.echelon.console.application.usecase.ObserveEquipmentTelemetry
import com.echelon.console.domain.EquipmentReadState
import kotlinx.coroutines.flow.StateFlow

class EquipmentTelemetryViewModel(
    observeEquipmentTelemetry: ObserveEquipmentTelemetry,
    private val connectEquipment: ConnectEquipment,
    private val disconnectEquipment: DisconnectEquipment,
) : ViewModel() {
    val state: StateFlow<EquipmentReadState> = observeEquipmentTelemetry()

    fun onStart() {
        connectEquipment()
    }

    fun onStop() {
        disconnectEquipment()
    }

    override fun onCleared() {
        disconnectEquipment()
        super.onCleared()
    }
}
