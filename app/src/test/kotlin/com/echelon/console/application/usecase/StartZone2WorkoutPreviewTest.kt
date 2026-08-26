package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutTimelineCompileError
import com.echelon.console.domain.WorkoutTimelineContext
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartZone2WorkoutPreviewTest {
    private val staticCatalog = StaticProgramCatalog()
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(90), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `all reviewed durations start exact five phase timeline with typed context`() {
        listOf(20, 30, 45, 60).forEach { durationMinutes ->
            val coordinator = InMemoryWorkoutSessionCoordinator(
                catalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            )
            val target = target()

            val started = assertStarted(
                StartZone2WorkoutPreview(
                    programCatalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
                    sessionStarter = coordinator,
                )(
                    StartZone2WorkoutPreviewRequest(
                        target = target,
                        duration = DurationMinutes(durationMinutes),
                        capabilities = capabilities,
                    ),
                ),
            )

            assertEquals(ProgramId("ZONE_2"), started.state.timeline.programId)
            assertEquals(durationMinutes * 60, started.state.timeline.totalDurationSeconds)
            assertEquals(durationMinutes * 60, started.state.timeline.segments.last().endSecond)
            assertEquals(
                listOf("Warm Up", "Settle", "Maintain", "Check", "Cool Down"),
                started.state.timeline.segments.map { it.name },
            )
            assertEquals(
                listOf(25, 30, 32, 32, 25),
                started.state.timeline.segments.map { it.targetSpeed.value },
            )
            assertEquals(
                listOf(10, 20, 30, 30, 10),
                started.state.timeline.segments.map { it.targetIncline.value },
            )
            assertTrue(
                started.state.timeline.segments.all {
                    it.targetSpeed.value <= started.plan.plan.settings.maxSpeed.value &&
                        it.targetIncline.value <= started.plan.plan.settings.maxIncline.value
                },
            )
            assertEquals(PlanIntensity.LOW, started.plan.plan.settings.intensity)
            assertEquals(PlanFocus.BALANCED, started.plan.plan.settings.focus)
            assertEquals(false, started.plan.plan.settings.adaptToYou)
            assertEquals(
                WorkoutTimelineContext.Zone2Preview(
                    programId = ProgramId("ZONE_2"),
                    target = target,
                    intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
                    previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                    adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                    thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                    hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
                    duration = DurationMinutes(durationMinutes),
                    effectiveMaxSpeed = SpeedTenths(50),
                    effectiveMaxIncline = InclineTenths(80),
                ),
                started.state.timeline.context,
            )
        }
    }

    @Test
    fun `effective plan caps are clamped to detail user cap and machine maximum`() {
        val machineLimited = capabilities.copy(
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(42)),
            incline = InclineRange(InclineTenths(0), InclineTenths(55)),
        )
        val starter = RecordingZone2Starter(testStartedWorkoutResult())

        val result = StartZone2WorkoutPreview(
            programCatalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            sessionStarter = starter,
        )(
            StartZone2WorkoutPreviewRequest(target(), DurationMinutes(30), machineLimited),
        )

        assertTrue(result is StartZone2WorkoutPreviewResult.Started)
        assertEquals(SpeedTenths(42), starter.receivedPlan?.plan?.settings?.maxSpeed)
        assertEquals(InclineTenths(55), starter.receivedPlan?.plan?.settings?.maxIncline)
        assertEquals(SpeedTenths(42), starter.receivedContext?.effectiveMaxSpeed)
        assertEquals(InclineTenths(55), starter.receivedContext?.effectiveMaxIncline)
    }

    @Test
    fun `reviewed zone 2 profile is used instead of another program profile`() {
        val customCatalog = ProgramDetailCatalog { requestedId ->
            staticCatalog.findProgramDetail(requestedId)?.let { detail ->
                if (requestedId == ProgramId("ZONE_2")) {
                    detail.copy(
                        profile = listOf(
                            ProgramSegmentSummary("CUSTOM WARM UP", DurationMinutes(5), SpeedTenths(25), InclineTenths(10)),
                            ProgramSegmentSummary("CUSTOM SETTLE", DurationMinutes(5), SpeedTenths(30), InclineTenths(20)),
                            ProgramSegmentSummary("CUSTOM MAINTAIN", DurationMinutes(10), SpeedTenths(32), InclineTenths(30)),
                            ProgramSegmentSummary("CUSTOM CHECK", DurationMinutes(5), SpeedTenths(32), InclineTenths(30)),
                            ProgramSegmentSummary("CUSTOM COOL DOWN", DurationMinutes(5), SpeedTenths(25), InclineTenths(10)),
                        ),
                    )
                } else {
                    detail
                }
            }
        }
        val coordinator = InMemoryWorkoutSessionCoordinator(customCatalog)

        val started = assertStarted(
            StartZone2WorkoutPreview(customCatalog, coordinator)(
                StartZone2WorkoutPreviewRequest(target(), DurationMinutes(30), capabilities),
            ),
        )

        assertEquals(
            listOf("CUSTOM WARM UP", "CUSTOM SETTLE", "CUSTOM MAINTAIN", "CUSTOM CHECK", "CUSTOM COOL DOWN"),
            started.state.timeline.segments.map { it.name },
        )
        assertTrue(started.state.timeline.segments.none { it.name == "Warm Up" })
    }

    @Test
    fun `missing detail and unsupported duration are rejected before starter`() {
        val missingStarter = RecordingZone2Starter()
        val missing = StartZone2WorkoutPreview(
            ProgramDetailCatalog { null },
            missingStarter,
        )(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(30), capabilities))
        assertEquals(
            StartZone2WorkoutPreviewResult.ProgramNotFound(ProgramId("ZONE_2")),
            missing,
        )
        assertNull(missingStarter.receivedPlan)

        val unsupportedStarter = RecordingZone2Starter()
        val unsupported = StartZone2WorkoutPreview(
            ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            unsupportedStarter,
        )(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(25), capabilities))
        assertEquals(
            StartZone2WorkoutPreviewResult.UnsupportedDuration(
                duration = DurationMinutes(25),
                supportedDurations = listOf(20, 30, 45, 60).map(::DurationMinutes),
            ),
            unsupported,
        )
        assertNull(unsupportedStarter.receivedPlan)
    }

    @Test
    fun `capability failure and starter failure are distinct and do not fake a start`() {
        val narrowCapabilities = capabilities.copy(
            duration = DurationLimits(DurationMinutes(30), DurationMinutes(60), DurationMinutes(1)),
        )
        val capabilityStarter = RecordingZone2Starter()
        val capabilityResult = StartZone2WorkoutPreview(
            ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            capabilityStarter,
        )(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(20), narrowCapabilities))
        assertTrue(capabilityResult is StartZone2WorkoutPreviewResult.CapabilityValidationFailed)
        assertNull(capabilityStarter.receivedPlan)

        val failure = WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile)
        val failedStarter = RecordingZone2Starter(WorkoutSessionStarterResult.Failed(failure))
        val starterResult = StartZone2WorkoutPreview(
            ProgramDetailCatalog { staticCatalog.findProgramDetail(it) },
            failedStarter,
        )(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(30), capabilities))
        assertEquals(StartZone2WorkoutPreviewResult.StarterFailed(failure), starterResult)
        assertTrue(failedStarter.receivedPlan != null)
    }

    @Test
    fun `active session rejection preserves first running state`() {
        val catalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) }
        val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
        val useCase = StartZone2WorkoutPreview(catalog, coordinator)
        val first = assertStarted(
            useCase(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(30), capabilities)),
        )

        val second = useCase(StartZone2WorkoutPreviewRequest(target(), DurationMinutes(45), capabilities))

        assertEquals(
            StartZone2WorkoutPreviewResult.StarterFailed(WorkoutSessionStartFailure.ActiveSessionExists),
            second,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    @Test
    fun `direct coordinator rejects context and plan identity duration and cap mismatches`() {
        val catalog = ProgramDetailCatalog { staticCatalog.findProgramDetail(it) }
        val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
        val target = target()
        val mismatches = listOf(
            Triple(
                Zone2PreviewContextPlanMismatchField.PROGRAM_ID,
                context(target).copy(programId = ProgramId("OTHER")),
                validPlan(programId = ProgramId("ZONE_2")),
            ),
            Triple(
                Zone2PreviewContextPlanMismatchField.PROGRAM_ID,
                context(target),
                validPlan(programId = ProgramId("FAT_BURN")),
            ),
            Triple(
                Zone2PreviewContextPlanMismatchField.DURATION,
                context(target),
                validPlan(duration = DurationMinutes(45)),
            ),
            Triple(
                Zone2PreviewContextPlanMismatchField.MAX_SPEED,
                context(target),
                validPlan(maxSpeed = SpeedTenths(49)),
            ),
            Triple(
                Zone2PreviewContextPlanMismatchField.MAX_INCLINE,
                context(target),
                validPlan(maxIncline = InclineTenths(79)),
            ),
        )

        mismatches.forEach { (field, zone2Context, plan) ->
            assertEquals(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.Zone2PreviewContextPlanMismatch(field),
                ),
                coordinator.start(zone2Context, plan),
            )
            assertNull(coordinator.currentState())
        }
    }

    private fun target(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }

    private fun context(
        target: HeartRateTargetRange,
        duration: DurationMinutes = DurationMinutes(30),
        maxSpeed: SpeedTenths = SpeedTenths(50),
        maxIncline: InclineTenths = InclineTenths(80),
    ): WorkoutTimelineContext.Zone2Preview = WorkoutTimelineContext.Zone2Preview(
        programId = ProgramId("ZONE_2"),
        target = target,
        intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
        previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
        adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
        thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
        hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
        duration = duration,
        effectiveMaxSpeed = maxSpeed,
        effectiveMaxIncline = maxIncline,
    )

    private fun validPlan(
        programId: ProgramId = ProgramId("ZONE_2"),
        duration: DurationMinutes = DurationMinutes(30),
        maxSpeed: SpeedTenths = SpeedTenths(50),
        maxIncline: InclineTenths = InclineTenths(80),
    ): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(
            WorkoutPlan(
                programId = programId,
                settings = PlanSettings(
                    duration = duration,
                    intensity = PlanIntensity.LOW,
                    focus = PlanFocus.BALANCED,
                    maxSpeed = maxSpeed,
                    maxIncline = maxIncline,
                    adaptToYou = false,
                ),
            ),
            capabilities,
        )
    ) {
        is ValidatedWorkoutPlanResult.Valid -> result.plan
        is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
    }

    private fun assertStarted(
        result: StartZone2WorkoutPreviewResult,
    ): StartZone2WorkoutPreviewResult.Started = when (result) {
        is StartZone2WorkoutPreviewResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private class RecordingZone2Starter(
        private val result: WorkoutSessionStarterResult =
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
    ) : Zone2WorkoutPreviewSessionStarter {
        var receivedContext: WorkoutTimelineContext.Zone2Preview? = null
        var receivedPlan: ValidatedWorkoutPlan? = null

        override fun start(
            context: WorkoutTimelineContext.Zone2Preview,
            plan: ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult {
            receivedContext = context
            receivedPlan = plan
            return result
        }
    }
}
