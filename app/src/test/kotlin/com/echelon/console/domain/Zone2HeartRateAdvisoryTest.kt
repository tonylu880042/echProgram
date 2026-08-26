package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Zone2HeartRateAdvisoryTest {
    @Test
    fun `user confirmed target requires positive ordered bounds and never throws`() {
        val accepted = HeartRateTargetRange.createUserConfirmed(120, 140)
        val target = assertAcceptedTarget(accepted)
        assertEquals(120, target.lowerBpm)
        assertEquals(140, target.upperBpm)
        assertEquals(HeartRateTargetSource.USER_CONFIRMED, target.source)

        assertEquals(
            HeartRateTargetRangeResult.Rejected(HeartRateTargetRangeFailure.MissingLowerBound),
            HeartRateTargetRange.createUserConfirmed(null, 140),
        )
        assertEquals(
            HeartRateTargetRangeResult.Rejected(HeartRateTargetRangeFailure.MissingUpperBound),
            HeartRateTargetRange.createUserConfirmed(120, null),
        )
        assertEquals(
            HeartRateTargetRangeResult.Rejected(
                HeartRateTargetRangeFailure.NonPositiveBound(
                    bound = HeartRateTargetBound.LOWER,
                    value = 0,
                ),
            ),
            HeartRateTargetRange.createUserConfirmed(0, 140),
        )
        assertEquals(
            HeartRateTargetRangeResult.Rejected(
                HeartRateTargetRangeFailure.NonPositiveBound(
                    bound = HeartRateTargetBound.UPPER,
                    value = -1,
                ),
            ),
            HeartRateTargetRange.createUserConfirmed(120, -1),
        )
        assertEquals(
            HeartRateTargetRangeResult.Rejected(
                HeartRateTargetRangeFailure.LowerAboveUpper(lowerBpm = 141, upperBpm = 140),
            ),
            HeartRateTargetRange.createUserConfirmed(141, 140),
        )
    }

    @Test
    fun `heart rate sample requires positive bpm and non negative host timestamp`() {
        val accepted = HeartRateSample.create(bpm = 130, elapsedRealtimeMillis = 10_000L)
        val sample = assertAcceptedSample(accepted)
        assertEquals(130, sample.bpm)
        assertEquals(10_000L, sample.elapsedRealtimeMillis)

        assertEquals(
            HeartRateSampleResult.Rejected(HeartRateSampleFailure.MissingBpm),
            HeartRateSample.create(bpm = null, elapsedRealtimeMillis = 10_000L),
        )
        assertEquals(
            HeartRateSampleResult.Rejected(HeartRateSampleFailure.MissingTimestamp),
            HeartRateSample.create(bpm = 130, elapsedRealtimeMillis = null),
        )
        assertEquals(
            HeartRateSampleResult.Rejected(HeartRateSampleFailure.NonPositiveBpm(0)),
            HeartRateSample.create(bpm = 0, elapsedRealtimeMillis = 10_000L),
        )
        assertEquals(
            HeartRateSampleResult.Rejected(HeartRateSampleFailure.NegativeTimestamp(-1L)),
            HeartRateSample.create(bpm = 130, elapsedRealtimeMillis = -1L),
        )
    }

    @Test
    fun `direct threshold evaluation keeps lower and upper bounds inclusive`() {
        val target = target(120, 140)
        val evaluator = Zone2HeartRateEvaluator()

        assertEquals(
            Zone2HeartRateStatus.TOO_LOW,
            assertEvaluated(evaluator.evaluate(target, sample(119, 1_000L), 1_000L, 1_000L)).status,
        )
        assertEquals(
            Zone2HeartRateStatus.IN_ZONE,
            assertEvaluated(evaluator.evaluate(target, sample(120, 1_000L), 1_000L, 1_000L)).status,
        )
        assertEquals(
            Zone2HeartRateStatus.IN_ZONE,
            assertEvaluated(evaluator.evaluate(target, sample(140, 1_000L), 1_000L, 1_000L)).status,
        )
        assertEquals(
            Zone2HeartRateStatus.TOO_HIGH,
            assertEvaluated(evaluator.evaluate(target, sample(141, 1_000L), 1_000L, 1_000L)).status,
        )
    }

    @Test
    fun `fresh sample at timeout minus one is evaluated and exact timeout loses signal`() {
        val target = target(120, 140)
        val evaluator = Zone2HeartRateEvaluator()

        val fresh = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample(130, 4_001L),
                nowElapsedRealtimeMillis = 5_000L,
                staleAfterMillis = 1_000L,
            ),
        )
        assertEquals(999L, fresh.sampleAgeMillis)
        assertEquals(Zone2HeartRateStatus.IN_ZONE, fresh.status)

        val stale = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample(130, 4_000L),
                nowElapsedRealtimeMillis = 5_000L,
                staleAfterMillis = 1_000L,
            ),
        )
        assertEquals(1_000L, stale.sampleAgeMillis)
        assertEquals(Zone2HeartRateStatus.HR_SIGNAL_LOST, stale.status)
        assertEquals(
            Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE,
            stale.advice,
        )
    }

    @Test
    fun `missing target sample invalid clock timeout and future sample are typed unavailable`() {
        val evaluator = Zone2HeartRateEvaluator()
        val target = target(120, 140)
        val sample = sample(130, 1_000L)

        assertEquals(
            Zone2HeartRateEvaluationResult.Unavailable(Zone2HeartRateEvaluationFailure.MissingTarget),
            evaluator.evaluate(null, sample, 1_000L, 1_000L),
        )
        assertEquals(
            Zone2HeartRateEvaluationResult.Unavailable(Zone2HeartRateEvaluationFailure.MissingHeartRate),
            evaluator.evaluate(target, null, 1_000L, 1_000L),
        )
        assertEquals(
            Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L),
            ),
            evaluator.evaluate(target, sample, -1L, 1_000L),
        )
        assertEquals(
            Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.InvalidStaleAfterMillis(0L),
            ),
            evaluator.evaluate(target, sample, 1_000L, 0L),
        )
        assertEquals(
            Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = 1_001L,
                    nowElapsedRealtimeMillis = 1_000L,
                ),
            ),
            evaluator.evaluate(target, sample(130, 1_001L), 1_000L, 1_000L),
        )
    }

    @Test
    fun `each evaluation carries advisory metadata and coaching never contains a motor command`() {
        val evaluator = Zone2HeartRateEvaluator()
        val target = target(120, 140)

        val tooLow = assertEvaluated(evaluator.evaluate(target, sample(119, 10_000L), 10_050L, 1_000L))
        val inZone = assertEvaluated(evaluator.evaluate(target, sample(130, 10_000L), 10_050L, 1_000L))
        val tooHigh = assertEvaluated(evaluator.evaluate(target, sample(141, 10_000L), 10_050L, 1_000L))
        val lost = assertEvaluated(evaluator.evaluate(target, sample(130, 9_000L), 10_000L, 1_000L))

        listOf(tooLow, inZone, tooHigh, lost).forEach { evaluation ->
            val expectedAge = if (evaluation.status == Zone2HeartRateStatus.HR_SIGNAL_LOST) {
                1_000L
            } else {
                50L
            }
            assertEquals(expectedAge, evaluation.sampleAgeMillis)
            assertEquals(HeartRateTargetSource.USER_CONFIRMED, evaluation.targetSource)
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
        assertEquals(Zone2HeartRateAdvice.SUGGEST_INCLINE, tooLow.advice)
        assertEquals(Zone2HeartRateAdvice.HOLD, inZone.advice)
        assertEquals(Zone2HeartRateAdvice.SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE, tooHigh.advice)
        assertEquals(Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE, lost.advice)
    }

    @Test
    fun `evaluation is deterministic for the same target sample and caller clock`() {
        val evaluator = Zone2HeartRateEvaluator()
        val target = target(120, 140)
        val first = evaluator.evaluate(target, sample(130, 20_000L), 20_250L, 1_000L)
        val second = evaluator.evaluate(target, sample(130, 20_000L), 20_250L, 1_000L)

        assertEquals(first, second)
        assertNotEquals(
            first,
            evaluator.evaluate(target, sample(141, 20_000L), 20_250L, 1_000L),
        )
        assertTrue(assertEvaluated(first).sampleAgeMillis >= 0L)
    }

    private fun target(lowerBpm: Int, upperBpm: Int): HeartRateTargetRange =
        assertAcceptedTarget(HeartRateTargetRange.createUserConfirmed(lowerBpm, upperBpm))

    private fun sample(bpm: Int, elapsedRealtimeMillis: Long): HeartRateSample =
        assertAcceptedSample(HeartRateSample.create(bpm, elapsedRealtimeMillis))

    private fun assertAcceptedTarget(result: HeartRateTargetRangeResult): HeartRateTargetRange = when (result) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun assertAcceptedSample(result: HeartRateSampleResult): HeartRateSample = when (result) {
        is HeartRateSampleResult.Accepted -> result.sample
        is HeartRateSampleResult.Rejected -> error("Expected accepted sample, got $result")
    }

    private fun assertEvaluated(
        result: Zone2HeartRateEvaluationResult,
    ): Zone2HeartRateEvaluation = when (result) {
        is Zone2HeartRateEvaluationResult.Evaluated -> result.evaluation
        is Zone2HeartRateEvaluationResult.Unavailable -> error("Expected evaluation, got $result")
    }
}
