package com.echelon.console.data.fitos

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDistanceUnit
import com.echelon.console.domain.EquipmentSpeedUnit
import com.echelon.console.domain.EquipmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitOsPayloadMapperTest {
    @Test
    fun `metric telemetry keeps display speed and canonical kilometer speed`() {
        val state = FitOsStatePayload(
            connectionStatus = "CONNECTED",
            equipmentType = "RUN",
            controlState = 1,
            isMetric = true,
            isBindDevice = true,
        )
        val snapshot = FitOsSnapshotPayload(
            timeElapsed = "00:12:34",
            speed = "10.5",
            incline = "3",
            hr = "142",
            distance = "2.1",
            calories = "88.5",
            elapsedRealtimeMillis = 1000L,
        )

        val mapped = requireNotNull(
            FitOsPayloadMapper.mapTelemetry(snapshot, FitOsPayloadMapper.mapState(state)),
        )

        assertEquals(10.5, mapped.speed?.canonicalKmh?.value ?: 0.0, 0.0001)
        assertEquals(10.5, mapped.speed?.displayValue ?: 0.0, 0.0001)
        assertEquals(EquipmentSpeedUnit.KILOMETERS_PER_HOUR, mapped.speed?.unit)
        assertEquals(3, mapped.incline?.value)
        assertEquals(142, mapped.heartRateBpm)
        assertEquals(EquipmentDistanceUnit.KILOMETERS, mapped.distance?.unit)
        assertEquals(2.1, mapped.distance?.displayValue ?: 0.0, 0.0001)
        assertEquals(88.5, mapped.calories ?: 0.0, 0.0001)
    }

    @Test
    fun `imperial speed is normalized to kilometers without confusing incline levels`() {
        val state = FitOsStatePayload(
            connectionStatus = "CONNECTED",
            equipmentType = "RUN",
            controlState = 0,
            isMetric = false,
            isBindDevice = true,
        )
        val snapshot = FitOsSnapshotPayload(
            speed = "10",
            incline = "12",
            distance = "2.5",
            elapsedRealtimeMillis = 2000L,
        )

        val mappedState = FitOsPayloadMapper.mapState(state)
        val mapped = requireNotNull(FitOsPayloadMapper.mapTelemetry(snapshot, mappedState))

        assertEquals(16.0934, mapped.speed?.canonicalKmh?.value ?: 0.0, 0.0001)
        assertEquals(EquipmentSpeedUnit.MILES_PER_HOUR, mapped.speed?.unit)
        assertEquals(12, mapped.incline?.value)
        assertEquals(EquipmentDistanceUnit.MILES, mapped.distance?.unit)
        assertEquals(2.5, mapped.distance?.displayValue ?: 0.0, 0.0001)
        assertTrue(mapped.incline?.value != 120)
        assertEquals(EquipmentType.RUN, mappedState.equipmentType)
        assertEquals(EquipmentControlState.STOPPED, mappedState.controlState)
    }

    @Test
    fun `malformed and non applicable values remain absent`() {
        val state = FitOsPayloadMapper.mapState(
            FitOsStatePayload(
                connectionStatus = "DISCONNECTED",
                equipmentType = "RUN",
                controlState = -1,
                isMetric = true,
                isBindDevice = false,
            ),
        )
        val snapshot = FitOsSnapshotPayload(
            timeElapsed = " ",
            speed = "not-a-number",
            incline = "12.0",
            hr = "--",
            distance = "",
            calories = null,
            elapsedRealtimeMillis = 3000L,
        )

        val mapped = requireNotNull(FitOsPayloadMapper.mapTelemetry(snapshot, state))

        assertNull(mapped.elapsedTime)
        assertNull(mapped.speed)
        assertNull(mapped.incline)
        assertNull(mapped.heartRateBpm)
        assertNull(mapped.distance)
        assertNull(mapped.calories)
        assertEquals(EquipmentConnection.EquipmentDisconnected("DISCONNECTED"), FitOsPayloadMapper.mapConnection(state))
    }

    @Test
    fun `overflow during imperial normalization remains absent`() {
        val state = FitOsPayloadMapper.mapState(
            FitOsStatePayload(
                connectionStatus = "CONNECTED",
                equipmentType = "RUN",
                isMetric = false,
                isBindDevice = true,
            ),
        )

        val mapped = requireNotNull(
            FitOsPayloadMapper.mapTelemetry(
                FitOsSnapshotPayload(speed = "1.5E308", elapsedRealtimeMillis = 4_000L),
                state,
            ),
        )

        assertNull(mapped.speed)
    }

    @Test
    fun `limits preserve run speed kilometer units and incline levels`() {
        val mapped = FitOsPayloadMapper.mapLimits(
            FitOsLimitsPayload(
                runInclineMin = 0,
                runInclineMax = 15,
                runSpeedMinKmh = 1.0,
                runSpeedMaxKmh = 20.0,
            ),
        )

        assertEquals(1.0, mapped?.runSpeedKmh?.min ?: 0.0, 0.0001)
        assertEquals(20.0, mapped?.runSpeedKmh?.max ?: 0.0, 0.0001)
        assertEquals(0, mapped?.runIncline?.min?.value)
        assertEquals(15, mapped?.runIncline?.max?.value)
    }
}
