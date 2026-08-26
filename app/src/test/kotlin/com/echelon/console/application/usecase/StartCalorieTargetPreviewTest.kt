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
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutSessionProgress
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionTarget
import com.echelon.console.domain.WorkoutSessionTargetMode
import com.echelon.console.domain.WorkoutTimeline
import com.echelon.console.domain.WorkoutTimelineCompileError
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.WorkoutTimelineSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StartCalorieTargetPreviewTest {
    private val staticCatalog = StaticProgramCatalog()
    private val broadCapabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(90), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `calorie target preview forwards the reviewed target to the starter`() {
        val failure = WorkoutSessionStartFailure.ActiveSessionExists
        val result = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            sessionStarter = CalorieTargetPreviewSessionStarter { _, _ ->
                WorkoutSessionStarterResult.Failed(failure)
            },
        )(
            StartCalorieTargetPreviewRequest(
                target = target(300),
                capabilities = broadCapabilities,
            ),
        )

        assertEquals(StartCalorieTargetPreviewResult.StarterFailed(failure), result)
    }

    @Test
    fun `calorie target preview rejects a malformed representative profile total`() {
        val catalogDetail = requireNotNull(
            StaticProgramCatalog().findProgramDetail(ProgramId("CALORIE_TARGET")),
        )
        val malformedDetail = catalogDetail.copy(profile = catalogDetail.profile.dropLast(1))
        val starter = RecordingStarter()
        val result = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { malformedDetail },
            sessionStarter = starter,
        )(
            StartCalorieTargetPreviewRequest(
                target = target(300),
                capabilities = broadCapabilities,
            ),
        )

        assertEquals(
            StartCalorieTargetPreviewResult.RepresentativeProfileDurationMismatch(
                expectedMinutes = 40,
                actualMinutes = 35,
            ),
            result,
        )
        assertNull(starter.receivedContext)
    }

    @Test
    fun `all targets retain proposal while representative plan remains forty minutes`() {
        val requestedIds = mutableListOf<ProgramId>()
        val starter = RecordingStarter(WorkoutSessionStarterResult.Started(runningState()))
        val useCase = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { id ->
                requestedIds += id
                staticCatalog.findProgramDetail(id)
            },
            sessionStarter = starter,
        )

        mapOf(
            100 to 60,
            200 to 60,
            300 to 60,
            500 to 90,
        ).forEach { (estimatedKcal, proposedMaxTime) ->
            val selectedTarget = target(estimatedKcal)
            val started = assertStarted(
                useCase(
                    StartCalorieTargetPreviewRequest(
                        target = selectedTarget,
                        capabilities = broadCapabilities,
                    ),
                ),
            )

            assertEquals(ProgramId("CALORIE_TARGET"), started.plan.plan.programId)
            assertEquals(DurationMinutes(40), started.plan.plan.settings.duration)
            assertEquals(DurationMinutes(40), started.context.representativeProfileDuration)
            assertEquals(selectedTarget, started.context.target)
            assertEquals(estimatedKcal, started.context.target.estimatedKcal)
            assertEquals(proposedMaxTime, started.context.target.proposedMaxTime.minutes)
            assertEquals(CalorieTargetSource.USER_SELECTED, started.context.target.source)
        }

        assertEquals(
            listOf(
                ProgramId("CALORIE_TARGET"),
                ProgramId("CALORIE_TARGET"),
                ProgramId("CALORIE_TARGET"),
                ProgramId("CALORIE_TARGET"),
            ),
            requestedIds,
        )
    }

    @Test
    fun `started context carries fixed preview metadata and default plan settings`() {
        val started = assertStarted(
            StartCalorieTargetPreview(
                programCatalog = ProgramDetailCatalog { staticDetail() },
                sessionStarter = RecordingStarter(WorkoutSessionStarterResult.Started(runningState())),
            )(
                StartCalorieTargetPreviewRequest(
                    target = target(300),
                    capabilities = broadCapabilities,
                ),
            ),
        )

        assertEquals(PlanIntensity.MEDIUM, started.plan.plan.settings.intensity)
        assertEquals(PlanFocus.BALANCED, started.plan.plan.settings.focus)
        assertEquals(false, started.plan.plan.settings.adaptToYou)
        assertEquals(CalorieEstimateStatus.ESTIMATED, started.context.estimateStatus)
        assertEquals(
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            started.context.source,
        )
        assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, started.context.unitSemantics)
        assertEquals(
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            started.context.sessionResetSemantics,
        )
        assertEquals(
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            started.context.completionAuthority,
        )
        assertEquals(
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            started.context.progressSemantics,
        )
        assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, started.context.previewStatus)
        assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, started.context.deviceCommandStatus)
    }

    @Test
    fun `effective caps clamp to detail and device maxima`() {
        val starter = RecordingStarter(WorkoutSessionStarterResult.Started(runningState()))
        val capabilities = broadCapabilities.copy(
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(42)),
            incline = InclineRange(InclineTenths(0), InclineTenths(55)),
        )

        val started = assertStarted(
            StartCalorieTargetPreview(
                programCatalog = ProgramDetailCatalog { staticDetail() },
                sessionStarter = starter,
            )(
                StartCalorieTargetPreviewRequest(target(300), capabilities),
            ),
        )

        assertEquals(SpeedTenths(42), started.plan.plan.settings.maxSpeed)
        assertEquals(InclineTenths(55), started.plan.plan.settings.maxIncline)
        assertEquals(SpeedTenths(42), started.context.effectiveMaxSpeed)
        assertEquals(InclineTenths(55), started.context.effectiveMaxIncline)
    }

    @Test
    fun `starter receives the exact immutable context and validated plan`() {
        val starter = RecordingStarter(WorkoutSessionStarterResult.Started(runningState()))
        val started = assertStarted(
            StartCalorieTargetPreview(
                programCatalog = ProgramDetailCatalog { staticDetail() },
                sessionStarter = starter,
            )(
                StartCalorieTargetPreviewRequest(target(200), broadCapabilities),
            ),
        )

        assertSame(starter.receivedContext, started.context)
        assertEquals(starter.receivedPlan, started.plan)
        assertNotNull(starter.receivedPlan)
        assertEquals(ProgramId("CALORIE_TARGET"), starter.receivedPlan?.plan?.programId)
        assertEquals(DurationMinutes(40), starter.receivedContext?.representativeProfileDuration)
    }

    @Test
    fun `missing and mismatched detail are rejected before starter`() {
        val missingStarter = RecordingStarter()
        val missing = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { null },
            sessionStarter = missingStarter,
        )(StartCalorieTargetPreviewRequest(target(100), broadCapabilities))

        assertEquals(
            StartCalorieTargetPreviewResult.ProgramNotFound(ProgramId("CALORIE_TARGET")),
            missing,
        )
        assertNull(missingStarter.receivedContext)

        val wrongId = ProgramId("OTHER")
        val wrongDetailStarter = RecordingStarter()
        val wrongDetail = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { staticDetail().copy(programId = wrongId) },
            sessionStarter = wrongDetailStarter,
        )(StartCalorieTargetPreviewRequest(target(100), broadCapabilities))

        assertEquals(
            StartCalorieTargetPreviewResult.ProgramDetailMismatch(
                expected = ProgramId("CALORIE_TARGET"),
                actual = wrongId,
            ),
            wrongDetail,
        )
        assertNull(wrongDetailStarter.receivedContext)
    }

    @Test
    fun `representative duration requires fixed default and supported forty minute option`() {
        val defaultMismatch = staticDetail().copy(
            defaultSettings = staticDetail().defaultSettings.copy(duration = DurationMinutes(30)),
            supportedDurations = listOf(DurationMinutes(30), DurationMinutes(40)),
        )
        val missingSupport = staticDetail().copy(
            defaultSettings = staticDetail().defaultSettings.copy(duration = DurationMinutes(30)),
            supportedDurations = listOf(DurationMinutes(30)),
        )

        listOf(
            defaultMismatch to listOf(DurationMinutes(30), DurationMinutes(40)),
            missingSupport to listOf(DurationMinutes(30)),
        ).forEach { (detail, supportedDurations) ->
            val starter = RecordingStarter()
            val result = StartCalorieTargetPreview(
                programCatalog = ProgramDetailCatalog { detail },
                sessionStarter = starter,
            )(StartCalorieTargetPreviewRequest(target(100), broadCapabilities))

            assertEquals(
                StartCalorieTargetPreviewResult.UnsupportedRepresentativeDuration(
                    duration = DurationMinutes(40),
                    supportedDurations = supportedDurations,
                ),
                result,
            )
            assertNull(starter.receivedContext)
        }

        val additionalSupportedDuration = staticDetail().copy(
            supportedDurations = listOf(DurationMinutes(30), DurationMinutes(40), DurationMinutes(60)),
        )
        val started = assertStarted(
            StartCalorieTargetPreview(
                programCatalog = ProgramDetailCatalog { additionalSupportedDuration },
                sessionStarter = RecordingStarter(WorkoutSessionStarterResult.Started(runningState())),
            )(StartCalorieTargetPreviewRequest(target(100), broadCapabilities)),
        )
        assertEquals(DurationMinutes(40), started.plan.plan.settings.duration)
    }

    @Test
    fun `device capability validation fails before starter`() {
        val capabilities = broadCapabilities.copy(
            duration = DurationLimits(DurationMinutes(41), DurationMinutes(90), DurationMinutes(1)),
            speed = SpeedRange(SpeedTenths(70), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(110), InclineTenths(150)),
        )
        val starter = RecordingStarter()

        val result = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { staticDetail() },
            sessionStarter = starter,
        )(StartCalorieTargetPreviewRequest(target(300), capabilities))

        assertEquals(
            StartCalorieTargetPreviewResult.CapabilityValidationFailed(
                errors = listOf(
                    PlanValidationError.DurationOutOfRange(DurationMinutes(40), capabilities.duration),
                    PlanValidationError.MaxSpeedOutOfRange(SpeedTenths(60), capabilities.speed),
                    PlanValidationError.MaxInclineOutOfRange(InclineTenths(100), capabilities.incline),
                ),
            ),
            result,
        )
        assertNull(starter.receivedContext)
    }

    @Test
    fun `starter failure is propagated without faking a started state`() {
        val failure = WorkoutSessionStartFailure.TimelineCompileFailed(
            WorkoutTimelineCompileError.EmptyProfile,
        )
        val starter = RecordingStarter(WorkoutSessionStarterResult.Failed(failure))

        val result = StartCalorieTargetPreview(
            programCatalog = ProgramDetailCatalog { staticDetail() },
            sessionStarter = starter,
        )(StartCalorieTargetPreviewRequest(target(500), broadCapabilities))

        assertEquals(StartCalorieTargetPreviewResult.StarterFailed(failure), result)
        assertNotNull(starter.receivedContext)
        assertNotNull(starter.receivedPlan)
    }

    private fun target(estimatedKcal: Int): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun staticDetail(): ProgramDetail = requireNotNull(
        staticCatalog.findProgramDetail(ProgramId("CALORIE_TARGET")),
    )

    private fun runningState(): WorkoutSessionState.Running {
        val segment = WorkoutTimelineSegment(
            name = "Preview",
            startSecond = 0,
            endSecond = 60,
            targetSpeed = SpeedTenths(30),
            targetIncline = InclineTenths(0),
        )
        val timeline = WorkoutTimeline(
            programId = ProgramId("CALORIE_TARGET"),
            totalDurationSeconds = 60,
            segments = listOf(segment),
            context = WorkoutTimelineContext.None,
        )
        return WorkoutSessionState.Running(
            timeline = timeline,
            progress = WorkoutSessionProgress(
                elapsedSeconds = 0,
                remainingSeconds = 60,
                currentSegmentIndex = 0,
                currentSegment = segment,
                nextSegment = null,
                secondsUntilNextSegment = null,
                target = WorkoutSessionTarget(
                    speed = segment.targetSpeed,
                    incline = segment.targetIncline,
                    mode = WorkoutSessionTargetMode.PROFILE,
                ),
            ),
        )
    }

    private fun assertStarted(
        result: StartCalorieTargetPreviewResult,
    ): StartCalorieTargetPreviewResult.Started = when (result) {
        is StartCalorieTargetPreviewResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private class RecordingStarter(
        private val result: WorkoutSessionStarterResult =
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
    ) : CalorieTargetPreviewSessionStarter {
        var receivedContext: WorkoutTimelineContext.CalorieTargetPreview? = null
        var receivedPlan: ValidatedWorkoutPlan? = null

        override fun start(
            context: WorkoutTimelineContext.CalorieTargetPreview,
            plan: ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult {
            receivedContext = context
            receivedPlan = plan
            return result
        }
    }
}
