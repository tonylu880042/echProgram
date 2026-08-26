package com.echelon.console.domain

enum class EquipmentType {
    RUN,
    BIKE,
    ROW,
    STAIR_MILL,
    UNKNOWN,
}

enum class EquipmentControlState {
    UNKNOWN,
    STOPPED,
    STARTED,
    PAUSED,
}

data class EquipmentDescriptor(
    val connectionStatus: String?,
    val equipmentType: EquipmentType,
    val runType: String?,
    val deviceName: String?,
    val isMetric: Boolean,
    val isBindDevice: Boolean,
    val controlState: EquipmentControlState,
)

sealed interface EquipmentConnection {
    data object Disconnected : EquipmentConnection

    data object Connecting : EquipmentConnection

    data class ServiceUnavailable(val reason: String) : EquipmentConnection

    data class UnsupportedApi(val apiVersion: Int) : EquipmentConnection

    data class EquipmentDisconnected(val status: String?) : EquipmentConnection

    data object Ready : EquipmentConnection

    data class Stale(val ageMillis: Long) : EquipmentConnection
}

data class EquipmentReadState(
    val connection: EquipmentConnection = EquipmentConnection.Disconnected,
    val apiVersion: Int? = null,
    val equipment: EquipmentDescriptor? = null,
    val limits: EquipmentLimits? = null,
    val telemetry: EquipmentTelemetry? = null,
)
