package com.echelon.console.presentation

import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotContextField
import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotFailure
import com.echelon.console.application.usecase.CalorieTargetEquipmentSnapshotResult
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSampleFreshness
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetEvaluation
import com.echelon.console.domain.CalorieTargetEvaluationFailure
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.FitOsCalorieSampleFailure
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
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LiveCalorieTargetMapperTest {
    @Test
    fun evaluatedSnapshotMapsTargetValueAgeFreshnessAndContextMetadata() {
        val timeline = calorieTimeline()
        val context = requireNotNull(timeline.context as? WorkoutTimelineContext.CalorieTargetPreview)
        val result = CalorieTargetEquipmentSnapshotResult.Evaluated(
            CalorieTargetEvaluation(
                target = context.target,
                displayValue = 183.5,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
                estimateStatus = context.estimateStatus,
                source = context.source,
                unitSemantics = context.unitSemantics,
                sessionResetSemantics = context.sessionResetSemantics,
                completionAuthority = context.completionAuthority,
                progressSemantics = context.progressSemantics,
                previewStatus = context.previewStatus,
                deviceCommandStatus = context.deviceCommandStatus,
            ),
        )

        val mapped = requireNotNull(LiveCalorieTargetMapper.map(timeline, result))

        assertEquals(context.target, mapped.target)
        assertEquals(context.representativeProfileDuration, mapped.representativeProfileDuration)
        assertEquals(context.effectiveMaxSpeed, mapped.effectiveMaxSpeed)
        assertEquals(context.effectiveMaxIncline, mapped.effectiveMaxIncline)
        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 183.5,
                sampleAgeMillis = 500L,
                freshness = CalorieSampleFreshness.FRESH,
            ),
            mapped.reading,
        )
        assertEquals(context.estimateStatus, mapped.estimateStatus)
        assertEquals(context.source, mapped.source)
        assertEquals(context.unitSemantics, mapped.unitSemantics)
        assertEquals(context.sessionResetSemantics, mapped.sessionResetSemantics)
        assertEquals(context.completionAuthority, mapped.completionAuthority)
        assertEquals(context.progressSemantics, mapped.progressSemantics)
        assertEquals(context.previewStatus, mapped.previewStatus)
        assertEquals(context.deviceCommandStatus, mapped.deviceCommandStatus)
    }

    @Test
    fun staleEvaluatedSnapshotPreservesStaleFreshnessAndExactTypedReading() {
        val result = evaluatedResult(
            displayValue = 201.25,
            sampleAgeMillis = 3_000L,
            freshness = CalorieSampleFreshness.STALE,
        )

        val mapped = requireNotNull(LiveCalorieTargetMapper.map(calorieTimeline(), result))

        assertEquals(
            LiveCalorieTargetReading.Evaluated(
                displayValue = 201.25,
                sampleAgeMillis = 3_000L,
                freshness = CalorieSampleFreshness.STALE,
            ),
            mapped.reading,
        )
    }

    @Test
    fun differentEvaluationTargetYieldsTimelineContextWithTypedUnavailableReading() {
        // Current safety metadata enums each have one value; target is the constructible mismatch.
        val result = evaluatedResult(
            target = acceptedTarget(CalorieTargetOption.FIVE_HUNDRED_KCAL),
        )

        val mapped = requireNotNull(LiveCalorieTargetMapper.map(calorieTimeline(), result))
        val context = requireNotNull(
            calorieTimeline().context as? WorkoutTimelineContext.CalorieTargetPreview,
        )
        assertEquals(context.target, mapped.target)
        assertEquals(context.representativeProfileDuration, mapped.representativeProfileDuration)
        assertEquals(context.effectiveMaxSpeed, mapped.effectiveMaxSpeed)
        assertEquals(context.effectiveMaxIncline, mapped.effectiveMaxIncline)
        assertEquals(
            LiveCalorieTargetReading.Unavailable(
                LiveCalorieTargetUnavailableReason.EvaluationSnapshotMismatch(
                    LiveCalorieTargetSnapshotField.TARGET,
                ),
            ),
            mapped.reading,
        )
        assertFalse(mapped.reading is LiveCalorieTargetReading.Evaluated)
    }

    @Test
    fun allApplicationContextFieldsMapToTypedPresentationMismatchReasons() {
        CalorieTargetEquipmentSnapshotContextField.values().forEach { field ->
            val presentationField = when (field) {
                CalorieTargetEquipmentSnapshotContextField.PROGRAM_ID ->
                    LiveCalorieTargetContextField.PROGRAM_ID
                CalorieTargetEquipmentSnapshotContextField.TARGET_SOURCE ->
                    LiveCalorieTargetContextField.TARGET_SOURCE
                CalorieTargetEquipmentSnapshotContextField.ESTIMATE_STATUS ->
                    LiveCalorieTargetContextField.ESTIMATE_STATUS
                CalorieTargetEquipmentSnapshotContextField.TELEMETRY_SOURCE ->
                    LiveCalorieTargetContextField.TELEMETRY_SOURCE
                CalorieTargetEquipmentSnapshotContextField.UNIT_SEMANTICS ->
                    LiveCalorieTargetContextField.UNIT_SEMANTICS
                CalorieTargetEquipmentSnapshotContextField.SESSION_RESET_SEMANTICS ->
                    LiveCalorieTargetContextField.SESSION_RESET_SEMANTICS
                CalorieTargetEquipmentSnapshotContextField.COMPLETION_AUTHORITY ->
                    LiveCalorieTargetContextField.COMPLETION_AUTHORITY
                CalorieTargetEquipmentSnapshotContextField.PROGRESS_SEMANTICS ->
                    LiveCalorieTargetContextField.PROGRESS_SEMANTICS
                CalorieTargetEquipmentSnapshotContextField.PREVIEW_STATUS ->
                    LiveCalorieTargetContextField.PREVIEW_STATUS
                CalorieTargetEquipmentSnapshotContextField.DEVICE_COMMAND_STATUS ->
                    LiveCalorieTargetContextField.DEVICE_COMMAND_STATUS
            }
            assertUnavailable(
                CalorieTargetEquipmentSnapshotFailure.ContextContractMismatch(field),
                LiveCalorieTargetUnavailableReason.ContextContractMismatch(presentationField),
            )
        }
    }

    @Test
    fun allSourceConnectionVariantsPreserveTypedReasons() {
        val cases = listOf(
            EquipmentConnection.Connecting to LiveCalorieTargetSourceReason.Connecting,
            EquipmentConnection.Disconnected to LiveCalorieTargetSourceReason.Disconnected,
            EquipmentConnection.ServiceUnavailable("offline") to
                LiveCalorieTargetSourceReason.ServiceUnavailable("offline"),
            EquipmentConnection.UnsupportedApi(2) to LiveCalorieTargetSourceReason.UnsupportedApi(2),
            EquipmentConnection.EquipmentDisconnected("unbound") to
                LiveCalorieTargetSourceReason.EquipmentDisconnected("unbound"),
            EquipmentConnection.Ready to LiveCalorieTargetSourceReason.Ready,
            EquipmentConnection.Stale(7L) to LiveCalorieTargetSourceReason.Stale(7L),
        )

        cases.forEach { (connection, expectedReason) ->
            assertUnavailable(
                CalorieTargetEquipmentSnapshotFailure.SourceUnavailable(connection),
                LiveCalorieTargetUnavailableReason.SourceUnavailable(expectedReason),
            )
        }
    }

    @Test
    fun equipmentAndTelemetryFailuresPreserveTypedReasonsAndTimelineContext() {
        listOf(
            CalorieTargetEquipmentSnapshotFailure.MissingEquipmentDescriptor to
                LiveCalorieTargetUnavailableReason.MissingEquipmentDescriptor,
            CalorieTargetEquipmentSnapshotFailure.UnsupportedEquipment(EquipmentType.BIKE) to
                LiveCalorieTargetUnavailableReason.UnsupportedEquipment(EquipmentType.BIKE),
            CalorieTargetEquipmentSnapshotFailure.MissingTelemetry to
                LiveCalorieTargetUnavailableReason.MissingTelemetry,
        ).forEach { (failure, expectedReason) -> assertUnavailable(failure, expectedReason) }

        val mapped = requireNotNull(
            LiveCalorieTargetMapper.map(
                calorieTimeline(),
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.MissingTelemetry,
                ),
            ),
        )
        val context = requireNotNull(
            calorieTimeline().context as? WorkoutTimelineContext.CalorieTargetPreview,
        )
        assertEquals(context.target, mapped.target)
        assertEquals(context.representativeProfileDuration, mapped.representativeProfileDuration)
        assertEquals(context.effectiveMaxSpeed, mapped.effectiveMaxSpeed)
        assertEquals(context.effectiveMaxIncline, mapped.effectiveMaxIncline)
        assertEquals(context.estimateStatus, mapped.estimateStatus)
        assertEquals(context.source, mapped.source)
        assertEquals(context.unitSemantics, mapped.unitSemantics)
        assertEquals(context.sessionResetSemantics, mapped.sessionResetSemantics)
        assertEquals(context.completionAuthority, mapped.completionAuthority)
        assertEquals(context.progressSemantics, mapped.progressSemantics)
        assertEquals(context.previewStatus, mapped.previewStatus)
        assertEquals(context.deviceCommandStatus, mapped.deviceCommandStatus)
    }

    @Test
    fun everyCalorieSampleFailureMapsWithoutLosingValues() {
        listOf(
            FitOsCalorieSampleFailure.MissingDisplayValue to
                LiveCalorieTargetSampleReason.MissingDisplayValue,
            FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.NaN) to
                LiveCalorieTargetSampleReason.NonFiniteDisplayValue(Double.NaN),
            FitOsCalorieSampleFailure.NegativeDisplayValue(-2.5) to
                LiveCalorieTargetSampleReason.NegativeDisplayValue(-2.5),
            FitOsCalorieSampleFailure.MissingTimestamp to
                LiveCalorieTargetSampleReason.MissingTimestamp,
            FitOsCalorieSampleFailure.NegativeTimestamp(-1L) to
                LiveCalorieTargetSampleReason.NegativeTimestamp(-1L),
        ).forEach { (failure, expectedReason) ->
            assertUnavailable(
                CalorieTargetEquipmentSnapshotFailure.InvalidCalorieSample(failure),
                LiveCalorieTargetUnavailableReason.InvalidCalorieSample(expectedReason),
            )
        }
    }

    @Test
    fun everyEvaluatorFailureMapsWithoutLosingValues() {
        listOf(
            CalorieTargetEvaluationFailure.MissingTarget to
                LiveCalorieTargetEvaluatorReason.MissingTarget,
            CalorieTargetEvaluationFailure.MissingSample to
                LiveCalorieTargetEvaluatorReason.MissingSample,
            CalorieTargetEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L) to
                LiveCalorieTargetEvaluatorReason.InvalidNowElapsedRealtimeMillis(-1L),
            CalorieTargetEvaluationFailure.InvalidStaleAfterMillis(0L) to
                LiveCalorieTargetEvaluatorReason.InvalidStaleAfterMillis(0L),
            CalorieTargetEvaluationFailure.FutureSampleTimestamp(2_000L, 1_000L) to
                LiveCalorieTargetEvaluatorReason.FutureSampleTimestamp(2_000L, 1_000L),
        ).forEach { (failure, expectedReason) ->
            assertUnavailable(
                CalorieTargetEquipmentSnapshotFailure.EvaluatorFailure(failure),
                LiveCalorieTargetUnavailableReason.EvaluatorFailure(expectedReason),
            )
        }
    }

    @Test
    fun mapperReturnsNullOutsideCalorieTargetIdentity() {
        val result = evaluatedResult()

        assertNull(LiveCalorieTargetMapper.map(genericTimeline(), result))
        assertNull(LiveCalorieTargetMapper.map(verticalTimeline(), result))
        assertNull(LiveCalorieTargetMapper.map(zone2Timeline(), result))
        assertNull(
            LiveCalorieTargetMapper.map(
                calorieTimeline(programId = ProgramId("FAT_BURN")),
                result,
            ),
        )
        assertNull(
            LiveCalorieTargetMapper.map(
                calorieTimeline(
                    context = calorieContext(programId = ProgramId("FAT_BURN")),
                ),
                result,
            ),
        )
        assertNull(
            LiveCalorieTargetMapper.map(
                calorieTimeline(context = WorkoutTimelineContext.None),
                result,
            ),
        )
    }

    @Test
    fun mappingIsDeterministicForIdenticalImmutableInput() {
        val timeline = calorieTimeline()
        val result = evaluatedResult(displayValue = 201.25, sampleAgeMillis = 750L)

        assertEquals(
            LiveCalorieTargetMapper.map(timeline, result),
            LiveCalorieTargetMapper.map(timeline, result),
        )
    }

    private fun assertUnavailable(
        failure: CalorieTargetEquipmentSnapshotFailure,
        expectedReason: LiveCalorieTargetUnavailableReason,
    ) {
        val mapped = requireNotNull(
            LiveCalorieTargetMapper.map(
                calorieTimeline(),
                CalorieTargetEquipmentSnapshotResult.Unavailable(failure),
            ),
        )
        assertEquals(LiveCalorieTargetReading.Unavailable(expectedReason), mapped.reading)
    }

    private fun evaluatedResult(
        context: WorkoutTimelineContext.CalorieTargetPreview = calorieContext(),
        target: CalorieTargetSelection = context.target,
        displayValue: Double = 183.5,
        sampleAgeMillis: Long = 500L,
        freshness: CalorieSampleFreshness = CalorieSampleFreshness.FRESH,
    ): CalorieTargetEquipmentSnapshotResult.Evaluated =
        CalorieTargetEquipmentSnapshotResult.Evaluated(
            CalorieTargetEvaluation(
                target = target,
                displayValue = displayValue,
                sampleAgeMillis = sampleAgeMillis,
                freshness = freshness,
                estimateStatus = context.estimateStatus,
                source = context.source,
                unitSemantics = context.unitSemantics,
                sessionResetSemantics = context.sessionResetSemantics,
                completionAuthority = context.completionAuthority,
                progressSemantics = context.progressSemantics,
                previewStatus = context.previewStatus,
                deviceCommandStatus = context.deviceCommandStatus,
            ),
        )

    private fun calorieTimeline(
        programId: ProgramId = ProgramId("CALORIE_TARGET"),
        context: WorkoutTimelineContext = calorieContext(),
    ): WorkoutTimeline = WorkoutTimeline(
        programId = programId,
        totalDurationSeconds = 2_400,
        segments = listOf(
            WorkoutTimelineSegment(
                name = "CALORIE TARGET",
                startSecond = 0,
                endSecond = 2_400,
                targetSpeed = SpeedTenths(60),
                targetIncline = InclineTenths(100),
            ),
        ),
        context = context,
    )

    private fun calorieContext(
        programId: ProgramId = ProgramId("CALORIE_TARGET"),
        target: CalorieTargetSelection = acceptedTarget(),
    ): WorkoutTimelineContext.CalorieTargetPreview =
        WorkoutTimelineContext.CalorieTargetPreview(
            programId = programId,
            target = target,
            estimateStatus = CalorieEstimateStatus.ESTIMATED,
            source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
            sessionResetSemantics =
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
            deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            representativeProfileDuration = DurationMinutes(40),
            effectiveMaxSpeed = SpeedTenths(60),
            effectiveMaxIncline = InclineTenths(100),
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
        context = WorkoutTimelineContext.Zone2Preview(
            programId = ProgramId("ZONE_2"),
            target = acceptedHeartRateTarget(),
            intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
            previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
            adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
            thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
            hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            duration = DurationMinutes(30),
            effectiveMaxSpeed = SpeedTenths(50),
            effectiveMaxIncline = InclineTenths(80),
        ),
    )

    private fun acceptedTarget(
        option: CalorieTargetOption = CalorieTargetOption.THREE_HUNDRED_KCAL,
    ): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(option.estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected target, got $result")
    }

    private fun acceptedHeartRateTarget(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }
}
