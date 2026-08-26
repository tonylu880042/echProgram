package com.echelon.console.presentation

import com.echelon.console.application.usecase.Zone2EquipmentHeartRateFailure
import com.echelon.console.application.usecase.Zone2EquipmentHeartRateResult
import com.echelon.console.application.usecase.Zone2EquipmentHeartRateContextField as ApplicationContextField
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.HeartRateSampleFailure
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.WorkoutTimelineSegment
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateEvaluation
import com.echelon.console.domain.Zone2HeartRateEvaluationFailure
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LiveZone2HeartRateMapperTest {
    @Test
    fun `accepted result preserves target reading and preview metadata`() {
        val timeline = zone2Timeline()
        val result = Zone2EquipmentHeartRateResult.Evaluated(
            Zone2HeartRateEvaluation(
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
                sampleAgeMillis = 250L,
                currentBpm = 130,
                target = target(),
                previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            ),
        )

        val mapped = requireNotNull(LiveZone2HeartRateMapper.map(timeline, result))
        assertEquals(target(), mapped.target)
        assertEquals(Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT, mapped.intendedSource)
        assertEquals(Zone2HeartRatePreviewStatus.PREVIEW_ONLY, mapped.previewStatus)
        assertEquals(Zone2HeartRateAdviceMode.ADVISORY_ONLY, mapped.adviceMode)
        assertEquals(Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW, mapped.thresholdMode)
        assertEquals(Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED, mapped.hysteresisStatus)
        assertEquals(
            LiveZone2HeartRateReading.Evaluated(
                currentBpm = 130,
                sampleAgeMillis = 250L,
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
            ),
            mapped.reading,
        )
    }

    @Test
    fun `different evaluation target yields typed unavailable without an evaluated payload`() {
        // The current safety metadata enums each have one value; target is the constructible mismatch.
        val timeline = zone2Timeline()
        val result = Zone2EquipmentHeartRateResult.Evaluated(
            Zone2HeartRateEvaluation(
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
                sampleAgeMillis = 250L,
                currentBpm = 130,
                target = target(lowerBpm = 125, upperBpm = 145),
                previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            ),
        )

        val mapped = requireNotNull(LiveZone2HeartRateMapper.map(timeline, result))
        val timelineContext = requireNotNull(timeline.context as? WorkoutTimelineContext.Zone2Preview)
        assertEquals(timelineContext.target, mapped.target)
        assertEquals(timelineContext.intendedSource, mapped.intendedSource)
        assertEquals(timelineContext.previewStatus, mapped.previewStatus)
        assertEquals(timelineContext.adviceMode, mapped.adviceMode)
        assertEquals(timelineContext.thresholdMode, mapped.thresholdMode)
        assertEquals(timelineContext.hysteresisStatus, mapped.hysteresisStatus)
        assertEquals(
            LiveZone2HeartRateReading.Unavailable(
                LiveZone2HeartRateUnavailableReason.EvaluationSnapshotMismatch(
                    LiveZone2HeartRateSnapshotField.TARGET,
                ),
            ),
            mapped.reading,
        )
        assertFalse(mapped.reading is LiveZone2HeartRateReading.Evaluated)
    }

    @Test
    fun `all source connection variants map to typed source reasons`() {
        val cases = listOf(
            EquipmentConnection.Connecting to LiveZone2HeartRateSourceReason.Connecting,
            EquipmentConnection.Disconnected to LiveZone2HeartRateSourceReason.Disconnected,
            EquipmentConnection.ServiceUnavailable("offline") to
                LiveZone2HeartRateSourceReason.ServiceUnavailable("offline"),
            EquipmentConnection.UnsupportedApi(2) to LiveZone2HeartRateSourceReason.UnsupportedApi(2),
            EquipmentConnection.EquipmentDisconnected("unbound") to
                LiveZone2HeartRateSourceReason.EquipmentDisconnected("unbound"),
            EquipmentConnection.Ready to LiveZone2HeartRateSourceReason.Ready,
            EquipmentConnection.Stale(7L) to LiveZone2HeartRateSourceReason.Stale(7L),
        )

        cases.forEach { (connection, expectedReason) ->
            assertUnavailable(
                Zone2EquipmentHeartRateFailure.SourceUnavailable(connection),
                LiveZone2HeartRateUnavailableReason.SourceUnavailable(expectedReason),
            )
        }
    }

    @Test
    fun `equipment and telemetry failures map to precise typed reasons`() {
        listOf(
            Zone2EquipmentHeartRateFailure.MissingEquipmentDescriptor to
                LiveZone2HeartRateUnavailableReason.MissingEquipmentDescriptor,
            Zone2EquipmentHeartRateFailure.UnsupportedEquipment(EquipmentType.BIKE) to
                LiveZone2HeartRateUnavailableReason.UnsupportedEquipment(EquipmentType.BIKE),
            Zone2EquipmentHeartRateFailure.MissingTelemetry to
                LiveZone2HeartRateUnavailableReason.MissingTelemetry,
        ).forEach { (failure, expectedReason) -> assertUnavailable(failure, expectedReason) }

        listOf(
            HeartRateSampleFailure.MissingBpm to LiveZone2HeartRateSampleReason.MissingBpm,
            HeartRateSampleFailure.MissingTimestamp to LiveZone2HeartRateSampleReason.MissingTimestamp,
            HeartRateSampleFailure.NonPositiveBpm(0) to
                LiveZone2HeartRateSampleReason.NonPositiveBpm(0),
            HeartRateSampleFailure.NegativeTimestamp(-1L) to
                LiveZone2HeartRateSampleReason.NegativeTimestamp(-1L),
        ).forEach { (failure, expectedReason) ->
            assertUnavailable(
                Zone2EquipmentHeartRateFailure.InvalidHeartRateSample(failure),
                LiveZone2HeartRateUnavailableReason.InvalidHeartRateSample(expectedReason),
            )
        }
    }

    @Test
    fun `evaluator failures map to precise typed reasons`() {
        listOf(
            Zone2HeartRateEvaluationFailure.MissingTarget to
                LiveZone2HeartRateEvaluatorReason.MissingTarget,
            Zone2HeartRateEvaluationFailure.MissingHeartRate to
                LiveZone2HeartRateEvaluatorReason.MissingHeartRate,
            Zone2HeartRateEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L) to
                LiveZone2HeartRateEvaluatorReason.InvalidNowElapsedRealtimeMillis(-1L),
            Zone2HeartRateEvaluationFailure.InvalidStaleAfterMillis(0L) to
                LiveZone2HeartRateEvaluatorReason.InvalidStaleAfterMillis(0L),
            Zone2HeartRateEvaluationFailure.FutureSampleTimestamp(2_000L, 1_000L) to
                LiveZone2HeartRateEvaluatorReason.FutureSampleTimestamp(2_000L, 1_000L),
        ).forEach { (failure, expectedReason) ->
            assertUnavailable(
                Zone2EquipmentHeartRateFailure.EvaluatorFailure(failure),
                LiveZone2HeartRateUnavailableReason.EvaluatorFailure(expectedReason),
            )
        }
    }

    @Test
    fun `all context contract fields map to typed mismatch reasons`() {
        ApplicationContextField.values().forEach { field ->
            val presentationField = when (field) {
                ApplicationContextField.PROGRAM_ID ->
                    LiveZone2HeartRateContextField.PROGRAM_ID
                ApplicationContextField.INTENDED_SOURCE ->
                    LiveZone2HeartRateContextField.INTENDED_SOURCE
                ApplicationContextField.PREVIEW_STATUS ->
                    LiveZone2HeartRateContextField.PREVIEW_STATUS
                ApplicationContextField.ADVICE_MODE ->
                    LiveZone2HeartRateContextField.ADVICE_MODE
                ApplicationContextField.THRESHOLD_MODE ->
                    LiveZone2HeartRateContextField.THRESHOLD_MODE
                ApplicationContextField.HYSTERESIS_STATUS ->
                    LiveZone2HeartRateContextField.HYSTERESIS_STATUS
            }
            assertUnavailable(
                Zone2EquipmentHeartRateFailure.ContextContractMismatch(field),
                LiveZone2HeartRateUnavailableReason.ContextContractMismatch(presentationField),
            )
        }
    }

    @Test
    fun `mapper returns null for generic vertical and mismatched timeline identity`() {
        val result = Zone2EquipmentHeartRateResult.Evaluated(
            Zone2HeartRateEvaluation(
                status = Zone2HeartRateStatus.IN_ZONE,
                advice = Zone2HeartRateAdvice.HOLD,
                sampleAgeMillis = 1L,
                currentBpm = 130,
                target = target(),
                previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            ),
        )

        assertNull(
            LiveZone2HeartRateMapper.map(
                zone2Timeline().copy(context = WorkoutTimelineContext.None),
                result,
            ),
        )
        assertNull(
            LiveZone2HeartRateMapper.map(
                zone2Timeline().copy(programId = ProgramId("FAT_BURN")),
                result,
            ),
        )
        assertNull(
            LiveZone2HeartRateMapper.map(
                zone2Timeline().copy(
                    context = zone2Context(programId = ProgramId("FAT_BURN")),
                ),
                result,
            ),
        )
        assertNull(LiveZone2HeartRateMapper.map(genericTimeline(), result))
        assertNull(LiveZone2HeartRateMapper.map(verticalTimeline(), result))
    }

    private fun assertUnavailable(
        failure: Zone2EquipmentHeartRateFailure,
        expectedReason: LiveZone2HeartRateUnavailableReason,
    ) {
        val result = Zone2EquipmentHeartRateResult.Unavailable(failure)
        val mapped = requireNotNull(LiveZone2HeartRateMapper.map(zone2Timeline(), result))
        assertEquals(LiveZone2HeartRateReading.Unavailable(expectedReason), mapped.reading)
    }

    private fun zone2Timeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("ZONE_2"),
        totalDurationSeconds = 1_800,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "ZONE 2",
                startSecond = 0,
                endSecond = 1_800,
                targetSpeed = SpeedTenths(50),
                targetIncline = InclineTenths(80),
            ),
        ),
        context = zone2Context(),
    )

    private fun zone2Context(programId: ProgramId = ProgramId("ZONE_2")): WorkoutTimelineContext.Zone2Preview =
        WorkoutTimelineContext.Zone2Preview(
            programId = programId,
            target = target(),
            intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
            previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
            adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
            thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
            hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            duration = DurationMinutes(30),
            effectiveMaxSpeed = SpeedTenths(50),
            effectiveMaxIncline = InclineTenths(80),
        )

    private fun genericTimeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("FAT_BURN"),
        totalDurationSeconds = 60,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "EASY",
                startSecond = 0,
                endSecond = 60,
                targetSpeed = SpeedTenths(30),
                targetIncline = InclineTenths(0),
            ),
        ),
    )

    private fun verticalTimeline(): WorkoutTimeline = WorkoutTimeline(
        programId = ProgramId("VERTICAL"),
        totalDurationSeconds = 60,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "CLIMB",
                startSecond = 0,
                endSecond = 60,
                targetSpeed = SpeedTenths(30),
                targetIncline = InclineTenths(50),
            ),
        ),
        context = WorkoutTimelineContext.VerticalPreview(
            programId = ProgramId("VERTICAL"),
            target = VerticalTarget.FIVE_HUNDRED_FEET,
            proposedTimeLimit = VerticalTimeLimitProposal(45, VerticalTimeLimitStatus.PROPOSED),
            elevationSource = VerticalElevationSource.UNAVAILABLE,
            progressStatus = VerticalProgressStatus.NOT_CALCULATED,
            controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
        ),
    )

    private fun target(
        lowerBpm: Int = 120,
        upperBpm: Int = 140,
    ): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(lowerBpm, upperBpm)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }
}
