package com.echelon.console.data.fitos

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentInclineLevel
import com.echelon.console.domain.EquipmentInclineRange
import com.echelon.console.domain.EquipmentLimits
import com.echelon.console.domain.EquipmentSpeed
import com.echelon.console.domain.EquipmentSpeedRangeKmh
import com.echelon.console.domain.EquipmentSpeedUnit
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.SpeedKmh
import java.util.Locale

internal object FitOsPayloadMapper {
    private const val MPH_TO_KMH = 1.60934

    fun mapState(payload: FitOsStatePayload): EquipmentDescriptor = EquipmentDescriptor(
        connectionStatus = payload.connectionStatus.cleanText(),
        equipmentType = payload.equipmentType.toEquipmentType(),
        runType = payload.runType.cleanText(),
        deviceName = payload.deviceName.cleanText(),
        isMetric = payload.isMetric,
        isBindDevice = payload.isBindDevice,
        controlState = payload.controlState.toControlState(),
    )

    fun mapConnection(state: EquipmentDescriptor): EquipmentConnection {
        val status = state.connectionStatus
        if (!state.isBindDevice) {
            return EquipmentConnection.EquipmentDisconnected(status)
        }

        return when (status?.uppercase(Locale.ROOT)) {
            "CONNECTED", "READY", "ONLINE", "BOUND", "ACTIVE" -> EquipmentConnection.Ready
            else -> EquipmentConnection.EquipmentDisconnected(status)
        }
    }

    fun mapTelemetry(
        payload: FitOsSnapshotPayload,
        state: EquipmentDescriptor,
    ): EquipmentTelemetry? {
        val timestamp = payload.elapsedRealtimeMillis.takeIf { it >= 0L } ?: return null
        val displayUnit = if (state.isMetric) {
            EquipmentSpeedUnit.KILOMETERS_PER_HOUR
        } else {
            EquipmentSpeedUnit.MILES_PER_HOUR
        }
        val displaySpeed = payload.speed.parseNonNegativeDouble()

        return EquipmentTelemetry(
            elapsedRealtimeMillis = timestamp,
            elapsedTime = payload.timeElapsed.cleanText(),
            speed = displaySpeed?.let { value ->
                EquipmentSpeed(
                    canonicalKmh = SpeedKmh(if (state.isMetric) value else value * MPH_TO_KMH),
                    displayValue = value,
                    unit = displayUnit,
                )
            },
            incline = payload.incline.parseNonNegativeInt()?.let(::EquipmentInclineLevel),
            heartRateBpm = payload.hr.parseNonNegativeInt(),
            distance = payload.distance.parseNonNegativeDouble(),
            calories = payload.calories.parseNonNegativeDouble(),
        )
    }

    fun mapLimits(payload: FitOsLimitsPayload): EquipmentLimits? {
        val speed = validSpeedRange(payload.runSpeedMinKmh, payload.runSpeedMaxKmh)
        val incline = validInclineRange(payload.runInclineMin, payload.runInclineMax)
        return if (speed == null && incline == null) null else EquipmentLimits(speed, incline)
    }

    fun mapControlState(controlState: Int): EquipmentControlState = controlState.toControlState()

    private fun validSpeedRange(min: Double?, max: Double?): EquipmentSpeedRangeKmh? {
        if (min == null || max == null || !min.isFinite() || !max.isFinite() || min < 0.0 || min > max) {
            return null
        }
        return EquipmentSpeedRangeKmh(min, max)
    }

    private fun validInclineRange(min: Int?, max: Int?): EquipmentInclineRange? {
        if (min == null || max == null || min < 0 || min > max) return null
        return EquipmentInclineRange(EquipmentInclineLevel(min), EquipmentInclineLevel(max))
    }

    private fun String?.cleanText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String?.parseNonNegativeDouble(): Double? = cleanText()
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }

    private fun String?.parseNonNegativeInt(): Int? = cleanText()
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }

    private fun String?.toEquipmentType(): EquipmentType = when (cleanText()?.uppercase(Locale.ROOT)) {
        "RUN", "TREADMILL" -> EquipmentType.RUN
        "BIKE" -> EquipmentType.BIKE
        "ROW" -> EquipmentType.ROW
        "STAIRMILL", "STAIR_MILL", "STAIR-MILL" -> EquipmentType.STAIR_MILL
        else -> EquipmentType.UNKNOWN
    }

    private fun Int.toControlState(): EquipmentControlState = when (this) {
        0 -> EquipmentControlState.STOPPED
        1 -> EquipmentControlState.STARTED
        2 -> EquipmentControlState.PAUSED
        else -> EquipmentControlState.UNKNOWN
    }
}
