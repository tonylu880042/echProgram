package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CalorieTargetPreviewTest {
    @Test
    fun `every supported target carries its exact proposed max time`() {
        val expectedMaxTimes = mapOf(
            100 to 60,
            200 to 60,
            300 to 60,
            500 to 90,
        )

        expectedMaxTimes.forEach { (estimatedKcal, maxMinutes) ->
            val selection = assertAccepted(
                CalorieTargetSelection.createUserSelected(estimatedKcal),
            )

            assertEquals(estimatedKcal, selection.target.estimatedKcal)
            assertEquals(CalorieTargetSource.USER_SELECTED, selection.source)
            assertEquals(maxMinutes, selection.proposedMaxTime.minutes)
            assertEquals(
                CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED,
                selection.proposedMaxTime.status,
            )
        }
    }

    @Test
    fun `missing and unsupported target values are rejected without throwing`() {
        assertEquals(
            CalorieTargetSelectionResult.Rejected(CalorieTargetSelectionFailure.MissingTarget),
            CalorieTargetSelection.createUserSelected(null),
        )
        listOf(0, 99, 400, 501).forEach { unsupported ->
            assertEquals(
                CalorieTargetSelectionResult.Rejected(
                    CalorieTargetSelectionFailure.UnsupportedTarget(unsupported),
                ),
                CalorieTargetSelection.createUserSelected(unsupported),
            )
        }
    }

    @Test
    fun `FitOS calorie sample validates finite non negative display value and timestamp`() {
        val sample = assertAcceptedSample(
            FitOsCalorieSample.create(
                displayValue = 183.5,
                elapsedRealtimeMillis = 9_000L,
            ),
        )
        assertEquals(183.5, sample.displayValue, 0.0)
        assertEquals(9_000L, sample.elapsedRealtimeMillis)

        assertEquals(
            FitOsCalorieSampleResult.Rejected(FitOsCalorieSampleFailure.MissingDisplayValue),
            FitOsCalorieSample.create(displayValue = null, elapsedRealtimeMillis = 9_000L),
        )
        assertEquals(
            FitOsCalorieSampleResult.Rejected(
                FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.NaN),
            ),
            FitOsCalorieSample.create(displayValue = Double.NaN, elapsedRealtimeMillis = 9_000L),
        )
        assertEquals(
            FitOsCalorieSampleResult.Rejected(
                FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.POSITIVE_INFINITY),
            ),
            FitOsCalorieSample.create(
                displayValue = Double.POSITIVE_INFINITY,
                elapsedRealtimeMillis = 9_000L,
            ),
        )
        assertEquals(
            FitOsCalorieSampleResult.Rejected(
                FitOsCalorieSampleFailure.NegativeDisplayValue(-0.1),
            ),
            FitOsCalorieSample.create(displayValue = -0.1, elapsedRealtimeMillis = 9_000L),
        )
        assertEquals(
            FitOsCalorieSampleResult.Rejected(FitOsCalorieSampleFailure.MissingTimestamp),
            FitOsCalorieSample.create(displayValue = 183.5, elapsedRealtimeMillis = null),
        )
        assertEquals(
            FitOsCalorieSampleResult.Rejected(FitOsCalorieSampleFailure.NegativeTimestamp(-1L)),
            FitOsCalorieSample.create(displayValue = 183.5, elapsedRealtimeMillis = -1L),
        )
    }

    @Test
    fun `freshness is fresh below 3000 milliseconds and stale at exactly 3000`() {
        val target = acceptedTarget(300)
        val sample = acceptedSample(183.5, elapsedRealtimeMillis = 7_000L)
        val evaluator = CalorieTargetEvaluator()

        val fresh = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample,
                nowElapsedRealtimeMillis = 9_999L,
                staleAfterMillis = 3_000L,
            ),
        )
        assertEquals(2_999L, fresh.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.FRESH, fresh.freshness)
        assertEquals(183.5, fresh.displayValue, 0.0)

        val stale = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample,
                nowElapsedRealtimeMillis = 10_000L,
                staleAfterMillis = 3_000L,
            ),
        )
        assertEquals(3_000L, stale.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.STALE, stale.freshness)
        assertEquals(183.5, stale.displayValue, 0.0)
    }

    @Test
    fun `invalid evaluator inputs and future samples are typed unavailable`() {
        val target = acceptedTarget(100)
        val sample = acceptedSample(42.0, elapsedRealtimeMillis = 1_000L)
        val evaluator = CalorieTargetEvaluator()

        assertEquals(
            CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L),
            ),
            evaluator.evaluate(target, sample, nowElapsedRealtimeMillis = -1L, staleAfterMillis = 3_000L),
        )
        assertEquals(
            CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.InvalidStaleAfterMillis(0L),
            ),
            evaluator.evaluate(target, sample, nowElapsedRealtimeMillis = 1_000L, staleAfterMillis = 0L),
        )
        assertEquals(
            CalorieTargetEvaluationResult.Unavailable(CalorieTargetEvaluationFailure.MissingTarget),
            evaluator.evaluate(null, sample, nowElapsedRealtimeMillis = 1_000L, staleAfterMillis = 3_000L),
        )
        assertEquals(
            CalorieTargetEvaluationResult.Unavailable(CalorieTargetEvaluationFailure.MissingSample),
            evaluator.evaluate(target, null, nowElapsedRealtimeMillis = 1_000L, staleAfterMillis = 3_000L),
        )
        assertEquals(
            CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = 1_001L,
                    nowElapsedRealtimeMillis = 1_000L,
                ),
            ),
            evaluator.evaluate(
                target,
                acceptedSample(42.0, elapsedRealtimeMillis = 1_001L),
                nowElapsedRealtimeMillis = 1_000L,
                staleAfterMillis = 3_000L,
            ),
        )
    }

    @Test
    fun `evaluation snapshots values and carries every display only metadata`() {
        val target = acceptedTarget(500)
        val sample = acceptedSample(321.25, elapsedRealtimeMillis = 10_000L)
        val evaluator = CalorieTargetEvaluator()

        val first = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample,
                nowElapsedRealtimeMillis = 13_000L,
                staleAfterMillis = 3_000L,
            ),
        )
        val repeated = assertEvaluated(
            evaluator.evaluate(
                target = target,
                sample = sample,
                nowElapsedRealtimeMillis = 13_000L,
                staleAfterMillis = 3_000L,
            ),
        )

        assertEquals(first, repeated)
        assertEquals(500, first.target.target.estimatedKcal)
        assertEquals(CalorieTargetSource.USER_SELECTED, first.target.source)
        assertEquals(90, first.target.proposedMaxTime.minutes)
        assertEquals(321.25, first.displayValue, 0.0)
        assertEquals(3_000L, first.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.STALE, first.freshness)
        assertEquals(CalorieEstimateStatus.ESTIMATED, first.estimateStatus)
        assertEquals(
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            first.source,
        )
        assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, first.unitSemantics)
        assertEquals(
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            first.sessionResetSemantics,
        )
        assertEquals(
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            first.completionAuthority,
        )
        assertEquals(
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            first.progressSemantics,
        )
        assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, first.previewStatus)
        assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, first.deviceCommandStatus)
    }

    private fun acceptedTarget(estimatedKcal: Int): CalorieTargetSelection =
        assertAccepted(CalorieTargetSelection.createUserSelected(estimatedKcal))

    private fun acceptedSample(
        displayValue: Double,
        elapsedRealtimeMillis: Long,
    ): FitOsCalorieSample = assertAcceptedSample(
        FitOsCalorieSample.create(displayValue, elapsedRealtimeMillis),
    )

    private fun assertAccepted(
        result: CalorieTargetSelectionResult,
    ): CalorieTargetSelection = when (result) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun assertAcceptedSample(
        result: FitOsCalorieSampleResult,
    ): FitOsCalorieSample = when (result) {
        is FitOsCalorieSampleResult.Accepted -> result.sample
        is FitOsCalorieSampleResult.Rejected -> error("Expected accepted sample, got $result")
    }

    private fun assertEvaluated(
        result: CalorieTargetEvaluationResult,
    ): CalorieTargetEvaluation = when (result) {
        is CalorieTargetEvaluationResult.Evaluated -> result.evaluation
        is CalorieTargetEvaluationResult.Unavailable -> error("Expected evaluated result, got $result")
    }
}
