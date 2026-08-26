package com.echelon.console.application.usecase

import com.echelon.console.domain.EquipmentReadState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EquipmentTelemetryUseCaseTest {
    @Test
    fun `telemetry use cases expose state and delegate lifecycle`() {
        val source = FakeEquipmentTelemetrySource()
        val observe = ObserveEquipmentTelemetry(source)
        val connect = ConnectEquipment(source)
        val disconnect = DisconnectEquipment(source)

        assertSame(source.state, observe())

        connect()
        disconnect()

        assertEquals(1, source.connectCount)
        assertEquals(1, source.disconnectCount)
    }
}

private class FakeEquipmentTelemetrySource : EquipmentTelemetrySource {
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
