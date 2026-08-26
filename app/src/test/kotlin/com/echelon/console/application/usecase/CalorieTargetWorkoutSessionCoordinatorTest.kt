package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.CalorieTargetSource
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalorieTargetWorkoutSessionCoordinatorTest {
    private val staticCatalog = StaticProgramCatalog()
    private val broadCapabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(90), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `all targets start the reviewed five segment forty minute timeline`() {
        mapOf(
            100 to 60,
            200 to 60,
            300 to 60,
            500 to 90,
        ).forEach { (estimatedKcal, proposedMaxTime) ->
            val coordinator = coordinator()
            val started = assertStarted(
                StartCalorieTargetPreview(
                    programCatalog = calorieCatalog(),
                    sessionStarter = coordinator,
                )(
                    StartCalorieTargetPreviewRequest(
                        target = target(estimatedKcal),
                        capabilities = broadCapabilities,
                    ),
                ),
            )
            val context = assertCalorieContext(started.state.timeline.context)

            assertEquals(2_400, started.state.timeline.totalDurationSeconds)
            assertEquals(
                listOf("Warm Up", "Base", "Build", "Push", "Cool Down"),
                started.state.timeline.segments.map { it.name },
            )
            assertEquals(2_400, started.state.timeline.segments.last().endSecond)
            assertEquals(DurationMinutes(40), started.plan.plan.settings.duration)
            assertEquals(DurationMinutes(40), context.representativeProfileDuration)
            assertEquals(estimatedKcal, context.target.estimatedKcal)
            assertEquals(proposedMaxTime, context.target.proposedMaxTime.minutes)
            assertEquals(CalorieTargetSource.USER_SELECTED, context.target.source)
            assertEquals(context, started.context)
            assertEquals(CalorieEstimateStatus.ESTIMATED, context.estimateStatus)
            assertEquals(
                CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
                context.source,
            )
            assertEquals(
                CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
                context.unitSemantics,
            )
            assertEquals(
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
                context.sessionResetSemantics,
            )
            assertEquals(
                CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
                context.completionAuthority,
            )
            assertEquals(
                CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
                context.progressSemantics,
            )
            assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, context.previewStatus)
            assertEquals(
                CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
                context.deviceCommandStatus,
            )
        }
    }

    @Test
    fun `direct calorie port rejects every constructible context and plan mismatch`() {
        val cases = listOf(
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.PROGRAM_ID,
                context = context(programId = ProgramId("OTHER")),
                plan = validPlan(),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.PROGRAM_ID,
                context = context(),
                plan = validPlan(programId = ProgramId("OTHER")),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.DURATION,
                context = context(duration = DurationMinutes(35)),
                plan = validPlan(),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.DURATION,
                context = context(),
                plan = validPlan(duration = DurationMinutes(35)),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.MAX_SPEED,
                context = context(maxSpeed = SpeedTenths(59)),
                plan = validPlan(),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.MAX_SPEED,
                context = context(),
                plan = validPlan(maxSpeed = SpeedTenths(59)),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.MAX_INCLINE,
                context = context(maxIncline = InclineTenths(99)),
                plan = validPlan(),
            ),
            MismatchCase(
                field = CalorieTargetPreviewContextPlanMismatchField.MAX_INCLINE,
                context = context(),
                plan = validPlan(maxIncline = InclineTenths(99)),
            ),
        )

        cases.forEach { (field, calorieContext, plan) ->
            val coordinator = coordinator()

            assertEquals(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
                ),
                coordinator.start(calorieContext, plan),
            )
            assertNull(coordinator.currentState())
        }
    }

    @Test
    fun `direct calorie port rejects reviewed settings and cap mismatches`() {
        val cases = listOf(
            CalorieTargetPreviewContextPlanMismatchField.INTENSITY to validPlan(
                intensity = PlanIntensity.HIGH,
            ),
            CalorieTargetPreviewContextPlanMismatchField.FOCUS to validPlan(
                focus = PlanFocus.MORE_INCLINE,
            ),
            CalorieTargetPreviewContextPlanMismatchField.ADAPT_TO_YOU to validPlan(
                adaptToYou = true,
            ),
            CalorieTargetPreviewContextPlanMismatchField.MAX_SPEED to validPlan(
                maxSpeed = SpeedTenths(61),
            ),
            CalorieTargetPreviewContextPlanMismatchField.MAX_INCLINE to validPlan(
                maxIncline = InclineTenths(101),
            ),
        )

        cases.forEach { (field, plan) ->
            val coordinator = coordinator()
            val calorieContext = context(
                maxSpeed = plan.plan.settings.maxSpeed,
                maxIncline = plan.plan.settings.maxIncline,
            )

            assertEquals(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.CalorieTargetPreviewContextPlanMismatch(field),
                ),
                coordinator.start(calorieContext, plan),
            )
            assertNull(coordinator.currentState())
        }
    }

    @Test
    fun `missing and wrong catalog details are typed and leave session empty`() {
        val missing = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { null },
        )
        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewProgramNotFound(
                    CALORIE_TARGET_PROGRAM_ID,
                ),
            ),
            missing.start(context(), validPlan()),
        )
        assertNull(missing.currentState())

        val actualId = ProgramId("OTHER")
        val wrongDetail = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { staticDetail().copy(programId = actualId) },
        )
        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewDetailMismatch(
                    expected = CALORIE_TARGET_PROGRAM_ID,
                    actual = actualId,
                ),
            ),
            wrongDetail.start(context(), validPlan()),
        )
        assertNull(wrongDetail.currentState())
    }

    @Test
    fun `default and supported duration policy mismatches are rejected`() {
        val defaultMismatch = staticDetail().copy(
            defaultSettings = staticDetail().defaultSettings.copy(
                duration = DurationMinutes(30),
            ),
            supportedDurations = listOf(DurationMinutes(30), DurationMinutes(40)),
        )
        val missingSupport = staticDetail().copy(
            defaultSettings = staticDetail().defaultSettings.copy(
                duration = DurationMinutes(30),
            ),
            supportedDurations = listOf(DurationMinutes(30)),
        )

        listOf(defaultMismatch, missingSupport).forEach { detail ->
            val coordinator = InMemoryWorkoutSessionCoordinator(
                catalog = ProgramDetailCatalog { detail },
            )

            assertEquals(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.CalorieTargetPreviewUnsupportedDuration(
                        duration = DurationMinutes(40),
                        supportedDurations = detail.supportedDurations,
                    ),
                ),
                coordinator.start(context(), validPlan()),
            )
            assertNull(coordinator.currentState())
        }
    }

    @Test
    fun `malformed profile total and nonpositive segment are rejected`() {
        val truncated = staticDetail().copy(profile = staticDetail().profile.dropLast(1))
        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewProfileDurationMismatch(
                    expectedMinutes = 40,
                    actualMinutes = 35,
                ),
            ),
            coordinator(truncated).start(context(), validPlan()),
        )

        val nonPositive = staticDetail().copy(
            profile = staticDetail().profile.mapIndexed { index, segment ->
                when (index) {
                    0 -> segment.copy(duration = DurationMinutes(0))
                    1 -> segment.copy(duration = DurationMinutes(15))
                    else -> segment
                }
            },
        )
        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.CalorieTargetPreviewProfileDurationMismatch(
                    expectedMinutes = 40,
                    actualMinutes = 40,
                ),
            ),
            coordinator(nonPositive).start(context(), validPlan()),
        )
    }

    @Test
    fun `active session failure has precedence and preserves the running timeline`() {
        val coordinator = coordinator()
        val first = assertStarted(
            StartCalorieTargetPreview(calorieCatalog(), coordinator)(
                StartCalorieTargetPreviewRequest(target(100), broadCapabilities),
            ),
        )

        val result = coordinator.start(
            context(programId = ProgramId("OTHER")),
            validPlan(),
        )

        assertEquals(
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
            result,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    @Test
    fun `pause resume stop and completion retain exact calorie context`() {
        val coordinator = coordinator()
        val started = assertStarted(
            StartCalorieTargetPreview(calorieCatalog(), coordinator)(
                StartCalorieTargetPreviewRequest(target(300), broadCapabilities),
            ),
        )
        val expectedContext = started.context

        val advanced = assertRunning(coordinator.advance(120))
        assertEquals(120, advanced.progress.elapsedSeconds)

        val paused = assertPaused(coordinator.pause())
        assertEquals(120, paused.progress.elapsedSeconds)
        assertEquals(expectedContext, paused.timeline.context)

        val resumed = assertRunning(coordinator.resume())
        assertEquals(120, resumed.progress.elapsedSeconds)

        val stopped = assertStopped(coordinator.stop())
        assertEquals(120, stopped.elapsedSeconds)
        assertEquals(expectedContext, stopped.timeline.context)

        val completedCoordinator = coordinator()
        val completedStarted = assertStarted(
            StartCalorieTargetPreview(calorieCatalog(), completedCoordinator)(
                StartCalorieTargetPreviewRequest(target(500), broadCapabilities),
            ),
        )
        val completed = assertCompleted(completedCoordinator.advance(2_400))
        assertEquals(2_400, completed.elapsedSeconds)
        assertEquals(completedStarted.context, completed.timeline.context)
        assertEquals(completed, completedCoordinator.currentState())
    }

    private fun calorieCatalog(): ProgramDetailCatalog = ProgramDetailCatalog {
        staticCatalog.findProgramDetail(it)
    }

    private fun coordinator(
        detail: ProgramDetail = staticDetail(),
    ): InMemoryWorkoutSessionCoordinator = InMemoryWorkoutSessionCoordinator(
        catalog = ProgramDetailCatalog { detail },
    )

    private fun staticDetail(): ProgramDetail = requireNotNull(
        staticCatalog.findProgramDetail(CALORIE_TARGET_PROGRAM_ID),
    )

    private fun target(estimatedKcal: Int): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun context(
        programId: ProgramId = CALORIE_TARGET_PROGRAM_ID,
        target: CalorieTargetSelection = target(300),
        duration: DurationMinutes = DurationMinutes(40),
        maxSpeed: SpeedTenths = SpeedTenths(60),
        maxIncline: InclineTenths = InclineTenths(100),
    ): WorkoutTimelineContext.CalorieTargetPreview = WorkoutTimelineContext.CalorieTargetPreview(
        programId = programId,
        target = target,
        estimateStatus = CalorieEstimateStatus.ESTIMATED,
        source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
        unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
        sessionResetSemantics = CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
        completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
        progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
        previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
        deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
        representativeProfileDuration = duration,
        effectiveMaxSpeed = maxSpeed,
        effectiveMaxIncline = maxIncline,
    )

    private fun validPlan(
        programId: ProgramId = CALORIE_TARGET_PROGRAM_ID,
        duration: DurationMinutes = DurationMinutes(40),
        intensity: PlanIntensity = PlanIntensity.MEDIUM,
        focus: PlanFocus = PlanFocus.BALANCED,
        maxSpeed: SpeedTenths = SpeedTenths(60),
        maxIncline: InclineTenths = InclineTenths(100),
        adaptToYou: Boolean = false,
    ): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(
            plan = WorkoutPlan(
                programId = programId,
                settings = PlanSettings(
                    duration = duration,
                    intensity = intensity,
                    focus = focus,
                    maxSpeed = maxSpeed,
                    maxIncline = maxIncline,
                    adaptToYou = adaptToYou,
                ),
            ),
            capabilities = broadCapabilities,
        )
    ) {
        is ValidatedWorkoutPlanResult.Valid -> result.plan
        is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
    }

    private fun assertCalorieContext(
        context: WorkoutTimelineContext,
    ): WorkoutTimelineContext.CalorieTargetPreview = when (context) {
        is WorkoutTimelineContext.CalorieTargetPreview -> context
        else -> error("Expected calorie context, got $context")
    }

    private fun assertStarted(
        result: StartCalorieTargetPreviewResult,
    ): StartCalorieTargetPreviewResult.Started = when (result) {
        is StartCalorieTargetPreviewResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private fun assertRunning(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Running = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Running -> state
        else -> error("Expected running state, got $state")
    }

    private fun assertPaused(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Paused = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Paused -> state
        else -> error("Expected paused state, got $state")
    }

    private fun assertStopped(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Stopped = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Stopped -> state
        else -> error("Expected stopped state, got $state")
    }

    private fun assertCompleted(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionState.Completed = when (val state = assertUpdated(result).state) {
        is WorkoutSessionState.Completed -> state
        else -> error("Expected completed state, got $state")
    }

    private fun assertUpdated(
        result: WorkoutSessionCommandResult,
    ): WorkoutSessionCommandResult.Updated = when (result) {
        is WorkoutSessionCommandResult.Updated -> result
        is WorkoutSessionCommandResult.Failed -> error("Expected updated state, got $result")
    }

    private data class MismatchCase(
        val field: CalorieTargetPreviewContextPlanMismatchField,
        val context: WorkoutTimelineContext.CalorieTargetPreview,
        val plan: ValidatedWorkoutPlan,
    )

    private companion object {
        val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")
    }
}
