package com.echelon.console.application.usecase

import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateSampleFailure
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateEvaluation
import com.echelon.console.domain.Zone2HeartRateEvaluationFailure
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import com.echelon.console.domain.WorkoutTimelineContext
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateZone2EquipmentHeartRateTest {
    private val evaluator = EvaluateZone2EquipmentHeartRate()
    private val target = acceptedTarget()

    @Test
    fun `ready run telemetry delegates threshold states and preserves preview snapshot`() {
        val cases = listOf(
            110 to (Zone2HeartRateStatus.TOO_LOW to Zone2HeartRateAdvice.SUGGEST_INCLINE),
            130 to (Zone2HeartRateStatus.IN_ZONE to Zone2HeartRateAdvice.HOLD),
            150 to (
                Zone2HeartRateStatus.TOO_HIGH to
                    Zone2HeartRateAdvice.SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE
                ),
        )

        cases.forEach { (bpm, expected) ->
            val result = evaluator(
                request(
                    telemetry = telemetry(heartRateBpm = bpm, elapsedRealtimeMillis = 9_500L),
                    nowElapsedRealtimeMillis = 10_000L,
                    staleAfterMillis = 1_000L,
                ),
            )

            val evaluation = assertEvaluated(result)
            assertEquals(expected.first, evaluation.status)
            assertEquals(expected.second, evaluation.advice)
            assertEquals(bpm, evaluation.currentBpm)
            assertEquals(500L, evaluation.sampleAgeMillis)
            assertEquals(target, evaluation.target)
            assertEquals(120, evaluation.target.lowerBpm)
            assertEquals(140, evaluation.target.upperBpm)
            assertEquals(Zone2HeartRatePreviewStatus.PREVIEW_ONLY, evaluation.previewStatus)
            assertEquals(Zone2HeartRateAdviceMode.ADVISORY_ONLY, evaluation.adviceMode)
            assertEquals(
                Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                evaluation.thresholdMode,
            )
            assertEquals(
                Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
                evaluation.hysteresisStatus,
            )
        }
    }

    @Test
    fun `stale connection delegates exact host timestamp threshold`() {
        val freshFromHostTimestamp = evaluator(
            request(
                connection = EquipmentConnection.Stale(ageMillis = 5_000L),
                telemetry = telemetry(heartRateBpm = 130, elapsedRealtimeMillis = 9_500L),
                nowElapsedRealtimeMillis = 10_000L,
                staleAfterMillis = 1_000L,
            ),
        )
        val freshEvaluation = assertEvaluated(freshFromHostTimestamp)
        assertEquals(Zone2HeartRateStatus.IN_ZONE, freshEvaluation.status)
        assertEquals(500L, freshEvaluation.sampleAgeMillis)

        val result = evaluator(
            request(
                connection = EquipmentConnection.Stale(ageMillis = 1L),
                telemetry = telemetry(heartRateBpm = 130, elapsedRealtimeMillis = 9_000L),
                nowElapsedRealtimeMillis = 10_000L,
                staleAfterMillis = 1_000L,
            ),
        )

        val evaluation = assertEvaluated(result)
        assertEquals(Zone2HeartRateStatus.HR_SIGNAL_LOST, evaluation.status)
        assertEquals(Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE, evaluation.advice)
        assertEquals(130, evaluation.currentBpm)
        assertEquals(1_000L, evaluation.sampleAgeMillis)
        assertEquals(target, evaluation.target)
    }

    @Test
    fun `unavailable connections do not evaluate retained telemetry`() {
        val connections = listOf<EquipmentConnection>(
            EquipmentConnection.Connecting,
            EquipmentConnection.Disconnected,
            EquipmentConnection.ServiceUnavailable("service unavailable"),
            EquipmentConnection.UnsupportedApi(0),
            EquipmentConnection.EquipmentDisconnected("unbound"),
        )

        connections.forEach { connection ->
            val result = evaluator(
                request(
                    connection = connection,
                    telemetry = telemetry(heartRateBpm = 130, elapsedRealtimeMillis = 9_999L),
                ),
            )

            assertEquals(
                Zone2EquipmentHeartRateResult.Unavailable(
                    Zone2EquipmentHeartRateFailure.SourceUnavailable(connection),
                ),
                result,
            )
        }
    }

    @Test
    fun `ready and stale require a run descriptor`() {
        listOf(EquipmentConnection.Ready, EquipmentConnection.Stale(ageMillis = 1L)).forEach {
            val missingDescriptor = evaluator(
                request(
                    connection = it,
                    equipment = null,
                ),
            )
            assertEquals(
                Zone2EquipmentHeartRateResult.Unavailable(
                    Zone2EquipmentHeartRateFailure.MissingEquipmentDescriptor,
                ),
                missingDescriptor,
            )
        }

        val unsupportedEquipment = evaluator(
            request(
                connection = EquipmentConnection.Stale(ageMillis = 5_000L),
                equipment = descriptor(EquipmentType.BIKE),
            ),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.UnsupportedEquipment(EquipmentType.BIKE),
            ),
            unsupportedEquipment,
        )
    }

    @Test
    fun `missing telemetry and invalid heart rate retain typed sample failures`() {
        val missingTelemetry = evaluator(
            request(telemetry = null),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.MissingTelemetry,
            ),
            missingTelemetry,
        )

        val invalidSamples = listOf(
            null to HeartRateSampleFailure.MissingBpm,
            0 to HeartRateSampleFailure.NonPositiveBpm(0),
            -1 to HeartRateSampleFailure.NonPositiveBpm(-1),
        )
        invalidSamples.forEach { (bpm, failure) ->
            val result = evaluator(
                request(telemetry = telemetry(heartRateBpm = bpm)),
            )

            assertEquals(
                Zone2EquipmentHeartRateResult.Unavailable(
                    Zone2EquipmentHeartRateFailure.InvalidHeartRateSample(failure),
                ),
                result,
            )
        }

        val invalidTimestamp = evaluator(
            request(telemetry = telemetry(elapsedRealtimeMillis = -1L)),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.InvalidHeartRateSample(
                    HeartRateSampleFailure.NegativeTimestamp(-1L),
                ),
            ),
            invalidTimestamp,
        )
    }

    @Test
    fun `evaluator failures are propagated without changing their typed causes`() {
        val futureSample = evaluator(
            request(
                telemetry = telemetry(elapsedRealtimeMillis = 10_001L),
                nowElapsedRealtimeMillis = 10_000L,
            ),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.EvaluatorFailure(
                    Zone2HeartRateEvaluationFailure.FutureSampleTimestamp(
                        sampleElapsedRealtimeMillis = 10_001L,
                        nowElapsedRealtimeMillis = 10_000L,
                    ),
                ),
            ),
            futureSample,
        )

        val invalidNow = evaluator(
            request(nowElapsedRealtimeMillis = -1L),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.EvaluatorFailure(
                    Zone2HeartRateEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L),
                ),
            ),
            invalidNow,
        )

        val invalidTimeout = evaluator(
            request(staleAfterMillis = 0L),
        )
        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.EvaluatorFailure(
                    Zone2HeartRateEvaluationFailure.InvalidStaleAfterMillis(0L),
                ),
            ),
            invalidTimeout,
        )
    }

    @Test
    fun `context contract mismatch is typed and not reinterpreted`() {
        val result = evaluator(
            request(
                context = context().copy(programId = ProgramId("FAT_BURN")),
            ),
        )

        assertEquals(
            Zone2EquipmentHeartRateResult.Unavailable(
                Zone2EquipmentHeartRateFailure.ContextContractMismatch(
                    Zone2EquipmentHeartRateContextField.PROGRAM_ID,
                ),
            ),
            result,
        )
    }

    private fun request(
        context: WorkoutTimelineContext.Zone2Preview = context(),
        connection: EquipmentConnection = EquipmentConnection.Ready,
        equipment: EquipmentDescriptor? = descriptor(EquipmentType.RUN),
        telemetry: EquipmentTelemetry? = telemetry(),
        nowElapsedRealtimeMillis: Long = 10_000L,
        staleAfterMillis: Long = 1_000L,
    ): EvaluateZone2EquipmentHeartRateRequest = EvaluateZone2EquipmentHeartRateRequest(
        context = context,
        equipmentState = EquipmentReadState(
            connection = connection,
            equipment = equipment,
            telemetry = telemetry,
        ),
        nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
        staleAfterMillis = staleAfterMillis,
    )

    private fun context(): WorkoutTimelineContext.Zone2Preview = WorkoutTimelineContext.Zone2Preview(
        programId = ProgramId("ZONE_2"),
        target = target,
        intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
        previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
        adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
        thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
        hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
        duration = DurationMinutes(30),
        effectiveMaxSpeed = SpeedTenths(50),
        effectiveMaxIncline = InclineTenths(80),
    )

    private fun descriptor(type: EquipmentType): EquipmentDescriptor = EquipmentDescriptor(
        connectionStatus = "CONNECTED",
        equipmentType = type,
        runType = null,
        deviceName = "test-equipment",
        isMetric = false,
        isBindDevice = true,
        controlState = EquipmentControlState.STARTED,
    )

    private fun telemetry(
        heartRateBpm: Int? = 130,
        elapsedRealtimeMillis: Long = 9_500L,
    ): EquipmentTelemetry = EquipmentTelemetry(
        elapsedRealtimeMillis = elapsedRealtimeMillis,
        elapsedTime = null,
        speed = null,
        incline = null,
        heartRateBpm = heartRateBpm,
        distance = null,
        calories = null,
    )

    private fun acceptedTarget(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected ->
            error("Expected target, got $result")
    }

    private fun assertEvaluated(
        result: Zone2EquipmentHeartRateResult,
    ): Zone2HeartRateEvaluation = when (result) {
        is Zone2EquipmentHeartRateResult.Evaluated -> result.evaluation
        is Zone2EquipmentHeartRateResult.Unavailable -> error("Expected evaluation, got $result")
    }
}
