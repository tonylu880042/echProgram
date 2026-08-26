package com.echelon.console.data.fitos

import android.content.Context
import com.ucare.fitosequipmentsdk.EquipmentLimits
import com.ucare.fitosequipmentsdk.EquipmentServiceClient
import com.ucare.fitosequipmentsdk.EquipmentSnapshot
import com.ucare.fitosequipmentsdk.EquipmentState

internal class AndroidFitOsClientFactory(
    private val context: Context,
) : FitOsClientFactory {
    override fun create(callback: FitOsClientCallback): FitOsClient =
        AndroidFitOsClient(context, callback)
}

private class AndroidFitOsClient(
    context: Context,
    callback: FitOsClientCallback,
) : FitOsClient {
    private val serviceClient = EquipmentServiceClient(
        context,
        object : EquipmentServiceClient.Callback() {
            override fun onServiceConnected() = callback.onServiceConnected()

            override fun onServiceDisconnected() = callback.onServiceDisconnected()

            override fun onConnectionStateChanged(state: EquipmentState) {
                callback.onConnectionStateChanged(state.toPayload())
            }

            override fun onEquipmentDataChanged(snapshot: EquipmentSnapshot) {
                callback.onEquipmentDataChanged(snapshot.toPayload())
            }

            override fun onControlStateChanged(controlState: Int) {
                callback.onControlStateChanged(controlState)
            }
        },
    )

    override fun connect() = serviceClient.connect()

    override fun disconnect() = serviceClient.disconnect()

    override fun getApiVersion(): Int = serviceClient.getApiVersion()

    override fun getConnectionState(): FitOsStatePayload? = serviceClient.getConnectionState()?.toPayload()

    override fun getSnapshot(): FitOsSnapshotPayload? = serviceClient.getSnapshot()?.toPayload()

    override fun getLimits(): FitOsLimitsPayload? = serviceClient.getLimits()?.toPayload()
}

private fun EquipmentState.toPayload(): FitOsStatePayload = FitOsStatePayload(
    connectionStatus = connectionStatus,
    equipmentType = equipmentType,
    runType = runType,
    deviceName = deviceName,
    controlState = controlState,
    isMetric = isMetric,
    isBindDevice = isBindDevice,
)

private fun EquipmentSnapshot.toPayload(): FitOsSnapshotPayload = FitOsSnapshotPayload(
    timeElapsed = timeElapsed,
    speed = speed,
    hr = hr,
    distance = distance,
    calories = calories,
    incline = incline,
    elapsedRealtimeMillis = elapsedRealtimeMillis,
)

private fun EquipmentLimits.toPayload(): FitOsLimitsPayload = FitOsLimitsPayload(
    runInclineMin = runInclineMin,
    runInclineMax = runInclineMax,
    runSpeedMinKmh = runSpeedMinKmh,
    runSpeedMaxKmh = runSpeedMaxKmh,
)
