package com.echelon.console.presentation

import com.echelon.console.application.usecase.ConnectEquipment
import com.echelon.console.application.usecase.DisconnectEquipment
import com.echelon.console.application.usecase.EquipmentTelemetrySource
import com.echelon.console.application.usecase.ObserveEquipmentTelemetry
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EquipmentTelemetryViewModelTest {
    @Test
    fun `lifecycle delegates connection and exposes source state`() {
        val source = FakeTelemetrySource()
        val viewModel = EquipmentTelemetryViewModel(
            observeEquipmentTelemetry = ObserveEquipmentTelemetry(source),
            connectEquipment = ConnectEquipment(source),
            disconnectEquipment = DisconnectEquipment(source),
        )

        viewModel.onStart()
        assertEquals(1, source.connectCount)
        assertEquals(EquipmentConnection.Disconnected, viewModel.state.value.connection)

        source.state.value = EquipmentReadState(connection = EquipmentConnection.Ready)
        assertEquals(EquipmentConnection.Ready, viewModel.state.value.connection)

        viewModel.onStop()
        assertEquals(1, source.disconnectCount)
    }
}

private class FakeTelemetrySource : EquipmentTelemetrySource {
    override val state = MutableStateFlow(EquipmentReadState())
    var connectCount = 0
    var disconnectCount = 0

    override fun connect() {
        connectCount += 1
    }

    override fun disconnect() {
        disconnectCount += 1
    }
}
