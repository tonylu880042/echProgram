package com.echelon.console.application.usecase

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
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.VerticalClampDimension
import com.echelon.console.domain.VerticalClampDisclosure
import com.echelon.console.domain.VerticalProfileSegmentRole
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalWorkoutDraft
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import com.echelon.console.domain.VerticalWorkoutGenerator
import com.echelon.console.domain.VerticalWorkoutGeneratorInput
import com.echelon.console.domain.VerticalWorkoutProfileSegment
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineCompileError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartVerticalWorkoutDraftTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(90), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `each target starts the exact six segment profile for a 50 minute session`() {
        VerticalTarget.values().forEach { target ->
            val draft = draft(target)
            val result = StartVerticalWorkoutDraft(coordinator())(draft, capabilities)

            val started = assertStarted(result)
            assertEquals(ProgramId("VERTICAL"), started.state.timeline.programId)
            assertEquals(3_000, started.state.timeline.totalDurationSeconds)
            assertEquals(3_000, started.state.timeline.segments.last().endSecond)
            assertEquals(draft.profile.map { it.name }, started.state.timeline.segments.map { it.name })
            assertEquals(
                draft.profile.map { it.duration.value * 60 },
                started.state.timeline.segments.map { it.durationSeconds },
            )
            assertEquals(
                draft.profile.map { it.speed.value },
                started.state.timeline.segments.map { it.targetSpeed.value },
            )
            assertEquals(
                draft.profile.map { it.incline.value },
                started.state.timeline.segments.map { it.targetIncline.value },
            )
        }
    }

    @Test
    fun `accepted draft uses fixed representative plan settings and does not use proposed target limit`() {
        val draft = draft(VerticalTarget.VERTICAL_MILE)

        val started = assertStarted(
            StartVerticalWorkoutDraft(coordinator())(draft, capabilities),
        )

        assertEquals(DurationMinutes(50), started.plan.plan.settings.duration)
        assertEquals(PlanIntensity.HIGH, started.plan.plan.settings.intensity)
        assertEquals(PlanFocus.MORE_INCLINE, started.plan.plan.settings.focus)
        assertEquals(false, started.plan.plan.settings.adaptToYou)
        assertEquals(240, draft.metadata.proposedTimeLimit.minutes)
        assertEquals(3_000, started.state.timeline.totalDurationSeconds)
    }

    @Test
    fun `accepted draft profile wins over a different static catalog profile`() {
        val draft = draft(VerticalTarget.FIVE_HUNDRED_FEET)
        val running = assertStarted(
            StartVerticalWorkoutDraft(
                coordinator(
                    ProgramDetailCatalog {
                        ProgramDetail(
                            programId = ProgramId("VERTICAL"),
                            title = "VERTICAL",
                            promise = "Static catalog profile must not replace the accepted draft.",
                            defaultSettings = PlanSettings(
                                duration = DurationMinutes(50),
                                intensity = PlanIntensity.LOW,
                                focus = PlanFocus.BALANCED,
                                maxSpeed = SpeedTenths(20),
                                maxIncline = InclineTenths(0),
                                adaptToYou = true,
                            ),
                            speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
                            inclineRange = InclineRange(InclineTenths(0), InclineTenths(150)),
                            profile = listOf(
                                ProgramSegmentSummary(
                                    name = "STATIC CATALOG",
                                    duration = DurationMinutes(50),
                                    speed = SpeedTenths(20),
                                    incline = InclineTenths(0),
                                ),
                            ),
                        )
                    },
                ),
            )(draft, capabilities),
        ).state

        assertTrue(running.timeline.segments.none { it.name == "STATIC CATALOG" })
        assertEquals(draft.profile.first().name, running.timeline.segments.first().name)
    }

    @Test
    fun `tampered metadata typed profile disclosure and caps are rejected before starter`() {
        val original = draft(VerticalTarget.FIVE_HUNDRED_FEET)
        val tampered = listOf(
            original.copy(
                metadata = original.metadata.copy(programId = ProgramId("FAT_BURN")),
            ),
            original.copy(
                metadata = original.metadata.copy(target = VerticalTarget.VERTICAL_MILE),
            ),
            original.copy(
                metadata = original.metadata.copy(
                    proposedTimeLimit = VerticalTimeLimitProposal(46, VerticalTimeLimitStatus.PROPOSED),
                ),
            ),
            original.copy(
                metadata = original.metadata.copy(userMaxSpeed = SpeedTenths(39)),
            ),
            original.copy(
                metadata = original.metadata.copy(effectiveInclineCap = InclineTenths(149)),
            ),
            original.copy(
                metadata = original.metadata.copy(wasClamped = true),
            ),
            original.copy(
                metadata = original.metadata.copy(
                    clampDisclosure = listOf(
                        VerticalClampDisclosure(
                            segmentIndex = 0,
                            role = VerticalProfileSegmentRole.WARM_UP,
                            dimensions = listOf(VerticalClampDimension.SPEED),
                            proposedSpeed = SpeedTenths(25),
                            proposedIncline = InclineTenths(40),
                            effectiveSpeed = SpeedTenths(24),
                            effectiveIncline = InclineTenths(40),
                        ),
                    ),
                ),
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(index = 1) else segment
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(role = VerticalProfileSegmentRole.COOL_DOWN)
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(summary = segment.summary.copy(speed = SpeedTenths(26)))
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(summary = segment.summary.copy(duration = DurationMinutes(0)))
                    } else {
                        segment
                    }
                },
            ),
        )

        tampered.forEach { invalidDraft ->
            val starter = RecordingDraftSessionStarter()

            val result = StartVerticalWorkoutDraft(starter)(invalidDraft, capabilities)

            assertTrue("Expected invalid draft: $invalidDraft", result is StartVerticalWorkoutDraftResult.InvalidDraft)
            assertNull(starter.received)
        }
    }

    @Test
    fun `device capability validation rejects unsupported representative caps before starter`() {
        val starter = RecordingDraftSessionStarter()
        val narrowCapabilities = capabilities.copy(
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(39)),
        )

        val result = StartVerticalWorkoutDraft(starter)(draft(), narrowCapabilities)

        assertTrue(result is StartVerticalWorkoutDraftResult.CapabilityValidationFailed)
        assertNull(starter.received)
    }

    @Test
    fun `starter failure and active session rejection preserve session safety`() {
        val coordinator = coordinator()
        val useCase = StartVerticalWorkoutDraft(coordinator)
        val first = assertStarted(useCase(draft(VerticalTarget.FIVE_HUNDRED_FEET), capabilities))

        val second = useCase(draft(VerticalTarget.VERTICAL_MILE), capabilities)

        assertEquals(
            StartVerticalWorkoutDraftResult.StarterFailed(
                WorkoutSessionStartFailure.ActiveSessionExists,
            ),
            second,
        )
        assertEquals(first.state, coordinator.currentState())

        val failure = WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile)
        val recording = RecordingDraftSessionStarter(
            result = WorkoutSessionStarterResult.Failed(failure),
        )
        val failed = StartVerticalWorkoutDraft(recording)(draft(), capabilities)

        assertEquals(StartVerticalWorkoutDraftResult.StarterFailed(failure), failed)
        assertTrue(recording.received != null)
    }

    @Test
    fun `direct coordinator adapter rejects draft plan identity duration and cap mismatches`() {
        val draft = draft()
        val coordinator = coordinator()
        val mismatchedPlans = listOf(
            DraftPlanMismatchField.PROGRAM_ID to draftPlan(draft, programId = ProgramId("FAT_BURN")),
            DraftPlanMismatchField.DURATION to draftPlan(draft, durationMinutes = 49),
            DraftPlanMismatchField.MAX_SPEED to draftPlan(
                draft,
                maxSpeed = SpeedTenths(draft.metadata.effectiveSpeedCap.value - 1),
            ),
            DraftPlanMismatchField.MAX_INCLINE to draftPlan(
                draft,
                maxIncline = InclineTenths(draft.metadata.effectiveInclineCap.value - 1),
            ),
        )

        mismatchedPlans.forEach { (field, plan) ->
            val result = coordinator.start(draft, plan)

            assertEquals(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.DraftPlanMismatch(field),
                ),
                result,
            )
            assertNull(coordinator.currentState())
        }
    }

    private fun coordinator(
        catalog: ProgramDetailCatalog = ProgramDetailCatalog { null },
    ): InMemoryWorkoutSessionCoordinator = InMemoryWorkoutSessionCoordinator(catalog)

    private fun draft(
        target: VerticalTarget = VerticalTarget.FIVE_HUNDRED_FEET,
    ): VerticalWorkoutDraft = when (
        val result = VerticalWorkoutGenerator().generate(
            VerticalWorkoutGeneratorInput(
                target = target,
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is VerticalWorkoutGenerationResult.Generated -> result.draft
        is VerticalWorkoutGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
    }

    private fun draftPlan(
        draft: VerticalWorkoutDraft,
        programId: ProgramId = draft.metadata.programId,
        durationMinutes: Int = 50,
        maxSpeed: SpeedTenths = draft.metadata.effectiveSpeedCap,
        maxIncline: InclineTenths = draft.metadata.effectiveInclineCap,
    ): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(
            WorkoutPlan(
                programId = programId,
                settings = PlanSettings(
                    duration = DurationMinutes(durationMinutes),
                    intensity = PlanIntensity.HIGH,
                    focus = PlanFocus.MORE_INCLINE,
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
        result: StartVerticalWorkoutDraftResult,
    ): StartVerticalWorkoutDraftResult.Started = when (result) {
        is StartVerticalWorkoutDraftResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private class RecordingDraftSessionStarter(
        private val result: WorkoutSessionStarterResult =
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
    ) : VerticalWorkoutDraftSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(
            draft: VerticalWorkoutDraft,
            plan: ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult {
            received = plan
            return result
        }
    }
}
