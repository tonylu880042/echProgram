package com.echelon.console.data.fitos

import android.os.SystemClock
import com.echelon.console.application.usecase.EquipmentTelemetrySource
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FitOsEquipmentAdapter(
    private val clientFactory: FitOsClientFactory,
    private val queryDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val nowElapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    private val staleCheckIntervalMillis: Long = DEFAULT_STALE_CHECK_INTERVAL_MILLIS,
) : EquipmentTelemetrySource {
    init {
        require(connectTimeoutMillis > 0L) { "Connect timeout must be positive" }
        require(staleAfterMillis > 0L) { "Stale threshold must be positive" }
        require(staleCheckIntervalMillis > 0L) { "Stale check interval must be positive" }
    }

    private val stateLock = Any()
    private val _state = MutableStateFlow(EquipmentReadState())
    private val callback = AdapterCallback()
    private var client: FitOsClient? = null
    private var connectRequested = false
    private var serviceConnected = false
    private var sessionGeneration = 0L
    private var connectTimeoutJob: Job? = null
    private var staleMonitorJob: Job? = null
    private var queryJob: Job? = null

    override val state: StateFlow<EquipmentReadState> = _state.asStateFlow()

    override fun connect() {
        synchronized(stateLock) {
            if (connectRequested) return
            connectRequested = true
            serviceConnected = false
            sessionGeneration += 1L
        }
        updateState { current -> current.copy(connection = EquipmentConnection.Connecting) }

        val newClient = try {
            clientFactory.create(callback)
        } catch (_: RuntimeException) {
            updateState { current ->
                current.copy(connection = EquipmentConnection.ServiceUnavailable("FitOS client could not be created"))
            }
            return
        }
        synchronized(stateLock) {
            client = newClient
        }

        scheduleConnectTimeout()
        startStaleMonitor()
        try {
            newClient.connect()
        } catch (_: RuntimeException) {
            updateState { current ->
                current.copy(connection = EquipmentConnection.ServiceUnavailable("FitOS service rejected the connection"))
            }
        }
    }

    override fun disconnect() {
        val oldClient = synchronized(stateLock) {
            connectRequested = false
            serviceConnected = false
            sessionGeneration += 1L
            val value = client
            client = null
            value
        }
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        staleMonitorJob?.cancel()
        staleMonitorJob = null
        queryJob?.cancel()
        queryJob = null
        try {
            oldClient?.disconnect()
        } catch (_: RuntimeException) {
            // The host may have died while the app was leaving the foreground.
        }
        updateState { EquipmentReadState(connection = EquipmentConnection.Disconnected) }
    }

    private fun scheduleConnectTimeout() {
        connectTimeoutJob?.cancel()
        connectTimeoutJob = scope.launch {
            delay(connectTimeoutMillis)
            updateState { current ->
                if (current.connection is EquipmentConnection.Connecting) {
                    current.copy(
                        connection = EquipmentConnection.ServiceUnavailable(
                            "FitOS service did not connect before timeout",
                        ),
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun startStaleMonitor() {
        staleMonitorJob?.cancel()
        staleMonitorJob = scope.launch {
            while (isActive) {
                delay(staleCheckIntervalMillis)
                val now = nowElapsedRealtimeMillis()
                updateState { current ->
                    val telemetry = current.telemetry ?: return@updateState current
                    val age = (now - telemetry.elapsedRealtimeMillis).coerceAtLeast(0L)
                    when {
                        current.connection is EquipmentConnection.Ready && age >= staleAfterMillis -> {
                            current.copy(connection = EquipmentConnection.Stale(age))
                        }

                        current.connection is EquipmentConnection.Stale && age >= staleAfterMillis -> {
                            current.copy(connection = EquipmentConnection.Stale(age))
                        }

                        else -> current
                    }
                }
            }
        }
    }

    private fun onServiceConnected() {
        val generation = synchronized(stateLock) {
            if (!connectRequested) return
            serviceConnected = true
            sessionGeneration += 1L
            sessionGeneration
        }
        connectTimeoutJob?.cancel()
        queryJob?.cancel()
        queryJob = scope.launch {
            val result = withContext(queryDispatcher) {
                val host = currentClient() ?: return@withContext InitialQuery(apiVersion = -1)
                val apiVersion = runCatching { host.getApiVersion() }.getOrDefault(-1)
                val state = runCatching { host.getConnectionState() }.getOrNull()
                val limits = runCatching { host.getLimits() }.getOrNull()
                val snapshot = runCatching { host.getSnapshot() }.getOrNull()
                InitialQuery(apiVersion, state, limits, snapshot)
            }
            publishInitialQuery(result, generation)
        }
    }

    private fun publishInitialQuery(result: InitialQuery, generation: Long) {
        if (result.apiVersion < 0) {
            updateIfCurrentSession(generation) { current ->
                current.copy(connection = EquipmentConnection.ServiceUnavailable("FitOS API query failed"))
            }
            return
        }
        if (result.apiVersion < FITOS_API_VERSION) {
            updateIfCurrentSession(generation) { current ->
                current.copy(
                    apiVersion = result.apiVersion,
                    connection = EquipmentConnection.UnsupportedApi(result.apiVersion),
                )
            }
            return
        }

        val descriptor = result.state?.let(FitOsPayloadMapper::mapState)
        val telemetry = if (descriptor != null && result.snapshot != null) {
            FitOsPayloadMapper.mapTelemetry(result.snapshot, descriptor)
        } else {
            null
        }
        val connection = descriptor?.let(FitOsPayloadMapper::mapConnection)
            ?: EquipmentConnection.EquipmentDisconnected(null)
        updateIfCurrentSession(generation) { current ->
            current.copy(
                connection = connection,
                apiVersion = result.apiVersion,
                equipment = descriptor,
                limits = result.limits?.let(FitOsPayloadMapper::mapLimits),
                telemetry = telemetry,
            )
        }
    }

    private fun onConnectionStateChanged(payload: FitOsStatePayload?) {
        val descriptor = payload?.let(FitOsPayloadMapper::mapState)
        updateConnectedState { current ->
            val nextConnection = if (current.connection is EquipmentConnection.UnsupportedApi) {
                current.connection
            } else {
                descriptor?.let(FitOsPayloadMapper::mapConnection)
                    ?: EquipmentConnection.EquipmentDisconnected(null)
            }
            current.copy(
                connection = nextConnection,
                equipment = descriptor,
            )
        }
    }

    private fun onEquipmentDataChanged(payload: FitOsSnapshotPayload?) {
        if (payload == null) return
        updateConnectedState { current ->
            val descriptor = current.equipment ?: return@updateConnectedState current
            val telemetry = FitOsPayloadMapper.mapTelemetry(payload, descriptor)
                ?: return@updateConnectedState current
            if (current.apiVersion == null || current.apiVersion < FITOS_API_VERSION) {
                return@updateConnectedState current.copy(telemetry = telemetry)
            }
            current.copy(
                connection = if (FitOsPayloadMapper.mapConnection(descriptor) is EquipmentConnection.Ready) {
                    EquipmentConnection.Ready
                } else {
                    current.connection
                },
                telemetry = telemetry,
            )
        }
    }

    private fun onControlStateChanged(controlState: Int) {
        updateConnectedState { current ->
            val equipment = current.equipment ?: return@updateConnectedState current
            current.copy(equipment = equipment.copy(controlState = FitOsPayloadMapper.mapControlState(controlState)))
        }
    }

    private fun updateState(transform: (EquipmentReadState) -> EquipmentReadState) {
        synchronized(stateLock) {
            _state.value = transform(_state.value)
        }
    }

    private fun updateConnectedState(transform: (EquipmentReadState) -> EquipmentReadState) {
        synchronized(stateLock) {
            if (!connectRequested || !serviceConnected) return
            _state.value = transform(_state.value)
        }
    }

    private fun updateIfCurrentSession(
        generation: Long,
        transform: (EquipmentReadState) -> EquipmentReadState,
    ) {
        synchronized(stateLock) {
            if (!connectRequested || !serviceConnected || sessionGeneration != generation) return
            _state.value = transform(_state.value)
        }
    }

    private fun currentClient(): FitOsClient? = synchronized(stateLock) { client }

    private inner class AdapterCallback : FitOsClientCallback {
        override fun onServiceConnected() = this@FitOsEquipmentAdapter.onServiceConnected()

        override fun onServiceDisconnected() {
            val shouldPublish = synchronized(stateLock) {
                if (!connectRequested) {
                    false
                } else {
                    serviceConnected = false
                    sessionGeneration += 1L
                    true
                }
            }
            queryJob?.cancel()
            if (!shouldPublish) return
            updateState { current ->
                current.copy(connection = EquipmentConnection.ServiceUnavailable("FitOS service disconnected"))
            }
        }

        override fun onConnectionStateChanged(state: FitOsStatePayload?) =
            this@FitOsEquipmentAdapter.onConnectionStateChanged(state)

        override fun onEquipmentDataChanged(snapshot: FitOsSnapshotPayload?) =
            this@FitOsEquipmentAdapter.onEquipmentDataChanged(snapshot)

        override fun onControlStateChanged(controlState: Int) =
            this@FitOsEquipmentAdapter.onControlStateChanged(controlState)
    }

    private data class InitialQuery(
        val apiVersion: Int,
        val state: FitOsStatePayload? = null,
        val limits: FitOsLimitsPayload? = null,
        val snapshot: FitOsSnapshotPayload? = null,
    )

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_STALE_AFTER_MILLIS = 3_000L
        const val DEFAULT_STALE_CHECK_INTERVAL_MILLIS = 1_000L
    }
}
