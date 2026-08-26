package com.echelon.console.application.usecase

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
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.FitOsCalorieSampleFailure
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutTimelineContext
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateCalorieTargetEquipmentSnapshotTest {
    private val evaluator = EvaluateCalorieTargetEquipmentSnapshot()

    @Test
    fun `ready run fresh snapshot preserves target value age and safety metadata`() {
        val evaluation = assertEvaluated(
            evaluator(
                request(
                    telemetry = telemetry(calories = 183.5, elapsedRealtimeMillis = 9_500L),
                    nowElapsedRealtimeMillis = 10_000L,
                    staleAfterMillis = 3_000L,
                ),
            ),
        )

        assertEquals(300, evaluation.target.estimatedKcal)
        assertEquals(CalorieTargetSource.USER_SELECTED, evaluation.target.source)
        assertEquals(60, evaluation.target.proposedMaxTime.minutes)
        assertEquals(183.5, evaluation.displayValue, 0.0)
        assertEquals(500L, evaluation.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.FRESH, evaluation.freshness)
        assertEquals(CalorieEstimateStatus.ESTIMATED, evaluation.estimateStatus)
        assertEquals(
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            evaluation.source,
        )
        assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, evaluation.unitSemantics)
        assertEquals(
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            evaluation.sessionResetSemantics,
        )
        assertEquals(
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            evaluation.completionAuthority,
        )
        assertEquals(
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            evaluation.progressSemantics,
        )
        assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, evaluation.previewStatus)
        assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, evaluation.deviceCommandStatus)
    }

    @Test
    fun `ready and stale use telemetry host timestamp at exact freshness boundary`() {
        val fresh = assertEvaluated(
            evaluator(
                request(
                    connection = EquipmentConnection.Stale(ageMillis = 99_999L),
                    telemetry = telemetry(calories = 183.5, elapsedRealtimeMillis = 7_001L),
                    nowElapsedRealtimeMillis = 10_000L,
                    staleAfterMillis = 3_000L,
                ),
            ),
        )
        assertEquals(2_999L, fresh.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.FRESH, fresh.freshness)

        val stale = assertEvaluated(
            evaluator(
                request(
                    connection = EquipmentConnection.Stale(ageMillis = 1L),
                    telemetry = telemetry(calories = 183.5, elapsedRealtimeMillis = 7_000L),
                    nowElapsedRealtimeMillis = 10_000L,
                    staleAfterMillis = 3_000L,
                ),
            ),
        )
        assertEquals(3_000L, stale.sampleAgeMillis)
        assertEquals(CalorieSampleFreshness.STALE, stale.freshness)
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
            assertEquals(
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.SourceUnavailable(connection),
                ),
                evaluator(
                    request(
                        connection = connection,
                        telemetry = telemetry(calories = 183.5, elapsedRealtimeMillis = 9_999L),
                    ),
                ),
            )
        }
    }

    @Test
    fun `ready and stale require run descriptor and telemetry`() {
        listOf(
            EquipmentConnection.Ready,
            EquipmentConnection.Stale(ageMillis = 1L),
        ).forEach { connection ->
            assertEquals(
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.MissingEquipmentDescriptor,
                ),
                evaluator(
                    request(
                        connection = connection,
                        equipment = null,
                    ),
                ),
            )

            assertEquals(
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.MissingTelemetry,
                ),
                evaluator(
                    request(
                        connection = connection,
                        telemetry = null,
                    ),
                ),
            )

            EquipmentType.entries.filter { it != EquipmentType.RUN }.forEach { type ->
                assertEquals(
                    CalorieTargetEquipmentSnapshotResult.Unavailable(
                        CalorieTargetEquipmentSnapshotFailure.UnsupportedEquipment(type),
                    ),
                    evaluator(
                        request(
                            connection = connection,
                            equipment = descriptor(type),
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `invalid calories and timestamp preserve exact sample failures`() {
        val invalidCalories = listOf(
            null to FitOsCalorieSampleFailure.MissingDisplayValue,
            Double.NaN to FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.NaN),
            Double.POSITIVE_INFINITY to
                FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.POSITIVE_INFINITY),
            Double.NEGATIVE_INFINITY to
                FitOsCalorieSampleFailure.NonFiniteDisplayValue(Double.NEGATIVE_INFINITY),
            -1.0 to FitOsCalorieSampleFailure.NegativeDisplayValue(-1.0),
        )

        invalidCalories.forEach { (calories, failure) ->
            assertEquals(
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.InvalidCalorieSample(failure),
                ),
                evaluator(
                    request(
                        telemetry = telemetry(calories = calories),
                    ),
                ),
            )
        }

        assertEquals(
            CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.InvalidCalorieSample(
                    FitOsCalorieSampleFailure.NegativeTimestamp(-1L),
                ),
            ),
            evaluator(
                request(
                    telemetry = telemetry(elapsedRealtimeMillis = -1L),
                ),
            ),
        )
    }

    @Test
    fun `evaluator failures are propagated without changing typed causes`() {
        val cases = listOf(
            request(nowElapsedRealtimeMillis = -1L) to
                CalorieTargetEvaluationFailure.InvalidNowElapsedRealtimeMillis(-1L),
            request(staleAfterMillis = 0L) to
                CalorieTargetEvaluationFailure.InvalidStaleAfterMillis(0L),
            request(telemetry = telemetry(elapsedRealtimeMillis = 10_001L)) to
                CalorieTargetEvaluationFailure.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = 10_001L,
                    nowElapsedRealtimeMillis = 10_000L,
                ),
        )

        cases.forEach { (request, failure) ->
            assertEquals(
                CalorieTargetEquipmentSnapshotResult.Unavailable(
                    CalorieTargetEquipmentSnapshotFailure.EvaluatorFailure(failure),
                ),
                evaluator(request),
            )
        }
    }

    @Test
    fun `context program identity mismatch is typed before equipment evaluation`() {
        assertEquals(
            CalorieTargetEquipmentSnapshotResult.Unavailable(
                CalorieTargetEquipmentSnapshotFailure.ContextContractMismatch(
                    CalorieTargetEquipmentSnapshotContextField.PROGRAM_ID,
                ),
            ),
            evaluator(
                request(
                    context = context().copy(programId = ProgramId("ZONE_2")),
                    connection = EquipmentConnection.Disconnected,
                ),
            ),
        )
    }

    @Test
    fun `repeated evaluation is deterministic for identical input`() {
        val request = request(
            telemetry = telemetry(calories = 201.25, elapsedRealtimeMillis = 9_250L),
            nowElapsedRealtimeMillis = 10_000L,
            staleAfterMillis = 3_000L,
        )

        assertEquals(evaluator(request), evaluator(request))
    }

    private fun request(
        context: WorkoutTimelineContext.CalorieTargetPreview = context(),
        connection: EquipmentConnection = EquipmentConnection.Ready,
        equipment: EquipmentDescriptor? = descriptor(EquipmentType.RUN),
        telemetry: EquipmentTelemetry? = telemetry(),
        nowElapsedRealtimeMillis: Long = 10_000L,
        staleAfterMillis: Long = 3_000L,
    ): EvaluateCalorieTargetEquipmentSnapshotRequest =
        EvaluateCalorieTargetEquipmentSnapshotRequest(
            context = context,
            equipmentState = EquipmentReadState(
                connection = connection,
                equipment = equipment,
                telemetry = telemetry,
            ),
            nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
            staleAfterMillis = staleAfterMillis,
        )

    private fun context(): WorkoutTimelineContext.CalorieTargetPreview =
        WorkoutTimelineContext.CalorieTargetPreview(
            programId = ProgramId("CALORIE_TARGET"),
            target = acceptedTarget(),
            estimateStatus = CalorieEstimateStatus.ESTIMATED,
            source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
            sessionResetSemantics =
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
            deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            representativeProfileDuration = com.echelon.console.domain.DurationMinutes(40),
            effectiveMaxSpeed = SpeedTenths(60),
            effectiveMaxIncline = InclineTenths(100),
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
        calories: Double? = 183.5,
        elapsedRealtimeMillis: Long = 9_500L,
    ): EquipmentTelemetry = EquipmentTelemetry(
        elapsedRealtimeMillis = elapsedRealtimeMillis,
        elapsedTime = null,
        speed = null,
        incline = null,
        heartRateBpm = null,
        distance = null,
        calories = calories,
    )

    private fun acceptedTarget(): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(
            CalorieTargetOption.THREE_HUNDRED_KCAL.estimatedKcal,
        )
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun assertEvaluated(
        result: CalorieTargetEquipmentSnapshotResult,
    ): CalorieTargetEvaluation = when (result) {
        is CalorieTargetEquipmentSnapshotResult.Evaluated -> result.evaluation
        is CalorieTargetEquipmentSnapshotResult.Unavailable ->
            error("Expected evaluation, got $result")
    }
}
