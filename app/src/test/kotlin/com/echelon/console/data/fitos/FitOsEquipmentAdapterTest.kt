package com.echelon.console.data.fitos

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentSpeedUnit
import com.echelon.console.domain.EquipmentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FitOsEquipmentAdapterTest {
    @Test
    fun `connected host is queried and publishes ready telemetry`() = runTest {
        val fake = FakeFitOsClient(
            apiVersion = 1,
            state = connectedState(),
            limits = FitOsLimitsPayload(
                runInclineMin = 0,
                runInclineMax = 15,
                runSpeedMinKmh = 1.0,
                runSpeedMaxKmh = 20.0,
            ),
            snapshot = FitOsSnapshotPayload(
                timeElapsed = "00:01:00",
                speed = "8.0",
                incline = "3",
                elapsedRealtimeMillis = 1_000L,
            ),
        )
        val factory = RecordingFactory(fake)
        val queryDispatcher = StandardTestDispatcher(testScheduler)
        val adapter = adapter(factory, queryDispatcher, backgroundScope)

        adapter.connect()
        assertEquals(EquipmentConnection.Connecting, adapter.state.value.connection)
        factory.callback?.onServiceConnected()
        runCurrent()

        val state = adapter.state.value
        assertEquals(EquipmentConnection.Ready, state.connection)
        assertEquals(1, state.apiVersion)
        assertEquals(EquipmentType.RUN, state.equipment?.equipmentType)
        assertEquals(EquipmentControlState.STARTED, state.equipment?.controlState)
        assertEquals(EquipmentSpeedUnit.MILES_PER_HOUR, state.telemetry?.speed?.unit)
        assertNotNull(state.limits?.runSpeedKmh)
        assertEquals(1, fake.apiVersionCalls)
        assertEquals(1, fake.stateCalls)
        assertEquals(1, fake.limitsCalls)
        assertEquals(1, fake.snapshotCalls)
    }

    @Test
    fun `missing service leaves connecting state only until timeout`() = runTest {
        val factory = RecordingFactory(FakeFitOsClient())
        val adapter = adapter(
            factory,
            StandardTestDispatcher(testScheduler),
            backgroundScope,
            timeoutMillis = 500L,
        )

        adapter.connect()
        assertEquals(EquipmentConnection.Connecting, adapter.state.value.connection)

        advanceTimeBy(499L)
        runCurrent()
        assertEquals(EquipmentConnection.Connecting, adapter.state.value.connection)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(
            EquipmentConnection.ServiceUnavailable("FitOS service did not connect before timeout"),
            adapter.state.value.connection,
        )
    }

    @Test
    fun `host timestamp determines stale telemetry state`() = runTest {
        var now = 1_100L
        val fake = FakeFitOsClient(apiVersion = 1, state = connectedState())
        val factory = RecordingFactory(fake)
        val adapter = adapter(
            factory = factory,
            queryDispatcher = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
            now = { now },
            staleAfterMillis = 200L,
            staleCheckMillis = 50L,
        )

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()
        factory.callback?.onEquipmentDataChanged(
            FitOsSnapshotPayload(speed = "5", elapsedRealtimeMillis = 1_000L),
        )
        runCurrent()
        assertEquals(EquipmentConnection.Ready, adapter.state.value.connection)

        now = 1_300L
        advanceTimeBy(50L)
        runCurrent()

        assertEquals(EquipmentConnection.Stale(300L), adapter.state.value.connection)
    }

    @Test
    fun `unsupported api is visible and control callback updates read state`() = runTest {
        val fake = FakeFitOsClient(apiVersion = 0, state = connectedState())
        val factory = RecordingFactory(fake)
        val adapter = adapter(factory, StandardTestDispatcher(testScheduler), backgroundScope)

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()
        assertEquals(EquipmentConnection.UnsupportedApi(0), adapter.state.value.connection)

        factory.callback?.onConnectionStateChanged(connectedState(controlState = 2))
        assertEquals(EquipmentControlState.PAUSED, adapter.state.value.equipment?.controlState)
        assertTrue(adapter.state.value.connection is EquipmentConnection.UnsupportedApi)
    }

    @Test
    fun `newer api keeps the v1 read path and accepts later telemetry`() = runTest {
        val fake = FakeFitOsClient(
            apiVersion = 2,
            state = connectedState(),
            snapshot = FitOsSnapshotPayload(speed = "6", elapsedRealtimeMillis = 1_000L),
        )
        val factory = RecordingFactory(fake)
        val adapter = adapter(factory, StandardTestDispatcher(testScheduler), backgroundScope)

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()

        assertEquals(2, adapter.state.value.apiVersion)
        assertEquals(EquipmentConnection.Ready, adapter.state.value.connection)
        assertEquals(6.0, adapter.state.value.telemetry?.speed?.displayValue ?: 0.0, 0.0001)

        factory.callback?.onEquipmentDataChanged(
            FitOsSnapshotPayload(speed = "7", elapsedRealtimeMillis = 2_000L),
        )
        assertEquals(EquipmentConnection.Ready, adapter.state.value.connection)
        assertEquals(7.0, adapter.state.value.telemetry?.speed?.displayValue ?: 0.0, 0.0001)
    }

    @Test
    fun `api query failure remains service unavailable`() = runTest {
        val fake = FakeFitOsClient(apiVersion = -1, state = connectedState())
        val factory = RecordingFactory(fake)
        val adapter = adapter(factory, StandardTestDispatcher(testScheduler), backgroundScope)

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()

        assertEquals(
            EquipmentConnection.ServiceUnavailable("FitOS API query failed"),
            adapter.state.value.connection,
        )
    }

    @Test
    fun `service disconnect invalidates an initial query that has not published`() = runTest {
        val queryStarted = CountDownLatch(1)
        val queryRelease = CountDownLatch(1)
        val queryFinished = CountDownLatch(1)
        val fake = FakeFitOsClient(
            apiVersion = 1,
            state = connectedState(),
            queryStarted = queryStarted,
            queryRelease = queryRelease,
            queryFinished = queryFinished,
        )
        val factory = RecordingFactory(fake)
        val adapter = adapter(
            factory = factory,
            queryDispatcher = Dispatchers.Default,
            scope = backgroundScope,
        )

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()
        assertTrue(queryStarted.await(1, TimeUnit.SECONDS))
        factory.callback?.onServiceDisconnected()
        queryRelease.countDown()
        assertTrue(queryFinished.await(1, TimeUnit.SECONDS))
        runCurrent()

        assertEquals(
            EquipmentConnection.ServiceUnavailable("FitOS service disconnected"),
            adapter.state.value.connection,
        )
    }

    @Test
    fun `late telemetry cannot restore ready after disconnect and reconnect starts a new query`() = runTest {
        val fake = FakeFitOsClient(
            apiVersion = 1,
            state = connectedState(),
            snapshot = FitOsSnapshotPayload(speed = "6", elapsedRealtimeMillis = 1_000L),
        )
        val factory = RecordingFactory(fake)
        val adapter = adapter(factory, StandardTestDispatcher(testScheduler), backgroundScope)

        adapter.connect()
        factory.callback?.onServiceConnected()
        runCurrent()
        assertEquals(EquipmentConnection.Ready, adapter.state.value.connection)

        factory.callback?.onServiceDisconnected()
        factory.callback?.onEquipmentDataChanged(
            FitOsSnapshotPayload(speed = "99", elapsedRealtimeMillis = 2_000L),
        )
        assertEquals(
            EquipmentConnection.ServiceUnavailable("FitOS service disconnected"),
            adapter.state.value.connection,
        )

        factory.callback?.onServiceConnected()
        runCurrent()
        assertEquals(EquipmentConnection.Ready, adapter.state.value.connection)
        assertEquals(2, fake.apiVersionCalls)
    }

    private fun adapter(
        factory: RecordingFactory,
        queryDispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        now: () -> Long = { 10_000L },
        timeoutMillis: Long = 5_000L,
        staleAfterMillis: Long = 3_000L,
        staleCheckMillis: Long = 1_000L,
    ): FitOsEquipmentAdapter = FitOsEquipmentAdapter(
        clientFactory = factory,
        queryDispatcher = queryDispatcher,
        scope = scope,
        nowElapsedRealtimeMillis = now,
        connectTimeoutMillis = timeoutMillis,
        staleAfterMillis = staleAfterMillis,
        staleCheckIntervalMillis = staleCheckMillis,
    )

    private fun connectedState(controlState: Int = 1): FitOsStatePayload = FitOsStatePayload(
        connectionStatus = "CONNECTED",
        equipmentType = "RUN",
        controlState = controlState,
        isMetric = false,
        isBindDevice = true,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private class RecordingFactory(
    private val client: FakeFitOsClient,
) : FitOsClientFactory {
    var callback: FitOsClientCallback? = null

    override fun create(callback: FitOsClientCallback): FitOsClient {
        this.callback = callback
        return client
    }
}

private class FakeFitOsClient(
    private val apiVersion: Int = -1,
    private val state: FitOsStatePayload? = null,
    private val limits: FitOsLimitsPayload? = null,
    private val snapshot: FitOsSnapshotPayload? = null,
    private val queryStarted: CountDownLatch? = null,
    private val queryRelease: CountDownLatch? = null,
    private val queryFinished: CountDownLatch? = null,
) : FitOsClient {
    var apiVersionCalls: Int = 0
        private set
    var stateCalls: Int = 0
        private set
    var limitsCalls: Int = 0
        private set
    var snapshotCalls: Int = 0
        private set

    override fun connect() = Unit

    override fun disconnect() = Unit

    override fun getApiVersion(): Int {
        apiVersionCalls += 1
        queryStarted?.countDown()
        queryRelease?.await()
        return apiVersion
    }

    override fun getConnectionState(): FitOsStatePayload? {
        stateCalls += 1
        return state
    }

    override fun getSnapshot(): FitOsSnapshotPayload? {
        snapshotCalls += 1
        return snapshot.also { queryFinished?.countDown() }
    }

    override fun getLimits(): FitOsLimitsPayload? {
        limitsCalls += 1
        return limits
    }
}
