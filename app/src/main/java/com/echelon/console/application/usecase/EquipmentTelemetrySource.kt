package com.echelon.console.application.usecase

import com.echelon.console.domain.EquipmentReadState
import kotlinx.coroutines.flow.StateFlow

interface EquipmentTelemetrySource {
    val state: StateFlow<EquipmentReadState>

    fun connect()

    fun disconnect()
}

class ObserveEquipmentTelemetry(
    private val source: EquipmentTelemetrySource,
) {
    operator fun invoke(): StateFlow<EquipmentReadState> = source.state
}

class ConnectEquipment(
    private val source: EquipmentTelemetrySource,
) {
    operator fun invoke() = source.connect()
}

class DisconnectEquipment(
    private val source: EquipmentTelemetrySource,
) {
    operator fun invoke() = source.disconnect()
}
