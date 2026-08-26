package com.echelon.console.data.fitos

internal data class FitOsStatePayload(
    val connectionStatus: String? = null,
    val equipmentType: String? = null,
    val runType: String? = null,
    val deviceName: String? = null,
    val controlState: Int = CONTROL_STATE_INIT,
    val isMetric: Boolean = false,
    val isBindDevice: Boolean = false,
)

internal data class FitOsSnapshotPayload(
    val timeElapsed: String? = null,
    val speed: String? = null,
    val hr: String? = null,
    val distance: String? = null,
    val calories: String? = null,
    val incline: String? = null,
    val elapsedRealtimeMillis: Long = -1L,
)

internal data class FitOsLimitsPayload(
    val runInclineMin: Int? = null,
    val runInclineMax: Int? = null,
    val runSpeedMinKmh: Double? = null,
    val runSpeedMaxKmh: Double? = null,
)

internal interface FitOsClientCallback {
    fun onServiceConnected()

    fun onServiceDisconnected()

    fun onConnectionStateChanged(state: FitOsStatePayload?)

    fun onEquipmentDataChanged(snapshot: FitOsSnapshotPayload?)

    fun onControlStateChanged(controlState: Int)
}

internal interface FitOsClient {
    fun connect()

    fun disconnect()

    fun getApiVersion(): Int

    fun getConnectionState(): FitOsStatePayload?

    fun getSnapshot(): FitOsSnapshotPayload?

    fun getLimits(): FitOsLimitsPayload?
}

internal fun interface FitOsClientFactory {
    fun create(callback: FitOsClientCallback): FitOsClient
}

internal const val FITOS_API_VERSION = 1
internal const val CONTROL_STATE_INIT = -1
