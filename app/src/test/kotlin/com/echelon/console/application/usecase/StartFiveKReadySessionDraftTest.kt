package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadyRunWalkSummary
import com.echelon.console.domain.FiveKReadySegmentRole
import com.echelon.console.domain.FiveKReadySessionDraft
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.FiveKReadySessionGenerator
import com.echelon.console.domain.FiveKReadySessionGeneratorInput
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineCompileError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartFiveKReadySessionDraftTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(60), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `accepted draft reaches running timeline with its exact profile`() {
        val draft = draft(durationMinutes = 30)
        val coordinator = coordinator()

        val result = StartFiveKReadySessionDraft(coordinator)(draft, capabilities)

        val started = assertStarted(result)
        assertEquals(PlanIntensity.MEDIUM, started.plan.plan.settings.intensity)
        assertEquals(PlanFocus.BALANCED, started.plan.plan.settings.focus)
        assertEquals(false, started.plan.plan.settings.adaptToYou)
        assertEquals(draft.metadata.programId, started.state.timeline.programId)
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

    @Test
    fun `draft profile wins over a different static catalog profile`() {
        val draft = draft(durationMinutes = 20)
        val running = assertStarted(
            StartFiveKReadySessionDraft(coordinator())(draft, capabilities),
        ).state

        assertTrue(running.timeline.segments.none { it.name == "STATIC CATALOG" })
        assertEquals(draft.profile.first().name, running.timeline.segments.first().name)
    }

    @Test
    fun `all supported draft durations start with exact timeline seconds`() {
        listOf(20, 30, 40, 60).forEach { durationMinutes ->
            val generated = draft(durationMinutes = durationMinutes)
            val running = assertStarted(
                StartFiveKReadySessionDraft(coordinator())(generated, capabilities),
            ).state

            assertEquals(durationMinutes * 60, running.timeline.totalDurationSeconds)
            assertEquals(durationMinutes * 60, running.timeline.segments.last().endSecond)
        }
    }

    @Test
    fun `tampered draft metadata profile roles ordinals and summary are rejected before starter`() {
        val original = draft(durationMinutes = 30)
        val tampered = listOf(
            original.copy(
                metadata = original.metadata.copy(programId = ProgramId("FAT_BURN")),
            ),
            original.copy(
                metadata = original.metadata.copy(durationMinutes = 20),
            ),
            original.copy(effectiveSpeedCap = SpeedTenths(59)),
            original.copy(effectiveInclineCap = InclineTenths(59)),
            original.copy(
                metadata = original.metadata.copy(userMaxSpeed = SpeedTenths(59)),
            ),
            original.copy(
                metadata = original.metadata.copy(machineMaxIncline = InclineTenths(59)),
            ),
            original.copy(
                metadata = original.metadata.copy(userMaxSpeed = SpeedTenths(81)),
            ),
            original.copy(
                metadata = original.metadata.copy(replayFingerprint = "tampered"),
            ),
            original.copy(
                metadata = original.metadata.copy(
                    baselinePace = FiveKReadyBaselinePace(
                        speed = SpeedTenths(41),
                        source = FiveKReadyBaselineSource.USER_ENTERED,
                    ),
                ),
            ),
            original.copy(
                metadata = original.metadata.copy(wasClamped = true),
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(role = FiveKReadySegmentRole.EASY_WALK)
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 1) {
                        segment.copy(runOrdinal = 2)
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 1) {
                        segment.copy(totalRuns = 2)
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 1) {
                        segment.copy(
                            summary = segment.summary.copy(
                                speed = SpeedTenths(segment.summary.speed.value + 1),
                            ),
                        )
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                runWalkSummary = FiveKReadyRunWalkSummary(
                    runMinutes = original.runWalkSummary.runMinutes + 1,
                    walkMinutes = original.runWalkSummary.walkMinutes - 1,
                ),
            ),
        )

        tampered.forEach { invalidDraft ->
            val starter = RecordingDraftSessionStarter()

            val result = StartFiveKReadySessionDraft(starter)(invalidDraft, capabilities)

            assertTrue("Expected invalid draft: $invalidDraft", result is StartFiveKReadySessionDraftResult.InvalidDraft)
            assertNull(starter.received)
        }
    }

    @Test
    fun `unsafe profile values and non positive durations are rejected before starter`() {
        val original = draft()
        val tampered = listOf(
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(summary = segment.summary.copy(speed = SpeedTenths(-1)))
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(summary = segment.summary.copy(incline = InclineTenths(-1)))
                    } else {
                        segment
                    }
                },
            ),
            original.copy(
                segments = original.segments.mapIndexed { index, segment ->
                    if (index == 0) {
                        segment.copy(
                            summary = segment.summary.copy(duration = DurationMinutes(0)),
                        )
                    } else {
                        segment
                    }
                },
            ),
        )

        tampered.forEach { invalidDraft ->
            val starter = RecordingDraftSessionStarter()

            val result = StartFiveKReadySessionDraft(starter)(invalidDraft, capabilities)

            assertTrue(result is StartFiveKReadySessionDraftResult.InvalidDraft)
            assertNull(starter.received)
        }
    }

    @Test
    fun `device capability validation is distinct and starter remains untouched`() {
        val starter = RecordingDraftSessionStarter()
        val narrowCapabilities = capabilities.copy(
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(59)),
        )

        val result = StartFiveKReadySessionDraft(starter)(draft(), narrowCapabilities)

        assertTrue(result is StartFiveKReadySessionDraftResult.CapabilityValidationFailed)
        assertNull(starter.received)
    }

    @Test
    fun `active session failure preserves the first draft state`() {
        val coordinator = coordinator()
        val useCase = StartFiveKReadySessionDraft(coordinator)
        val first = assertStarted(useCase(draft(durationMinutes = 20), capabilities))

        val second = useCase(draft(durationMinutes = 30), capabilities)

        assertEquals(
            StartFiveKReadySessionDraftResult.StarterFailed(
                WorkoutSessionStartFailure.ActiveSessionExists,
            ),
            second,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    @Test
    fun `direct draft starter rejects plan identity duration and cap mismatches`() {
        val draft = draft()
        val coordinator = coordinator()
        val mismatchedPlans = listOf(
            DraftPlanMismatchField.PROGRAM_ID to draftPlan(draft, programId = ProgramId("FAT_BURN")),
            DraftPlanMismatchField.DURATION to draftPlan(draft, durationMinutes = 20),
            DraftPlanMismatchField.MAX_SPEED to draftPlan(
                draft,
                maxSpeed = SpeedTenths(draft.effectiveSpeedCap.value - 1),
            ),
            DraftPlanMismatchField.MAX_INCLINE to draftPlan(
                draft,
                maxIncline = InclineTenths(draft.effectiveInclineCap.value - 1),
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

    @Test
    fun `direct draft starter keeps timeline compile failures explicit`() {
        val draft = draft()
        val invalidDraft = draft.copy(
            segments = emptyList(),
            runWalkSummary = FiveKReadyRunWalkSummary(runMinutes = 0, walkMinutes = 0),
        )
        val coordinator = coordinator()

        val result = coordinator.start(invalidDraft, draftPlan(draft))

        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile),
            ),
            result,
        )
        assertNull(coordinator.currentState())
    }

    private fun coordinator(): InMemoryWorkoutSessionCoordinator = InMemoryWorkoutSessionCoordinator(
        catalog = ProgramDetailCatalog { staticDetail() },
    )

    private fun draft(durationMinutes: Int = 30): FiveKReadySessionDraft = assertGenerated(
        FiveKReadySessionGenerator().generate(
            FiveKReadySessionGeneratorInput(
                durationMinutes = durationMinutes,
                baselinePace = FiveKReadyBaselinePace(
                    speed = SpeedTenths(40),
                    source = FiveKReadyBaselineSource.USER_ENTERED,
                ),
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(60),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(60),
            ),
        ),
    )

    private fun assertGenerated(
        result: FiveKReadySessionGenerationResult,
    ): FiveKReadySessionDraft = when (result) {
        is FiveKReadySessionGenerationResult.Generated -> result.draft
        is FiveKReadySessionGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
    }

    private fun draftPlan(
        draft: FiveKReadySessionDraft,
        programId: ProgramId = draft.metadata.programId,
        durationMinutes: Int = draft.metadata.durationMinutes,
        maxSpeed: SpeedTenths = draft.effectiveSpeedCap,
        maxIncline: InclineTenths = draft.effectiveInclineCap,
    ): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(
            WorkoutPlan(
                programId = programId,
                settings = PlanSettings(
                    duration = DurationMinutes(durationMinutes),
                    intensity = PlanIntensity.MEDIUM,
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

    private fun staticDetail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("5K_READY"),
        title = "5K READY",
        promise = "The accepted draft must provide the live profile.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(30),
            intensity = PlanIntensity.LOW,
            focus = PlanFocus.MORE_SPEED,
            maxSpeed = SpeedTenths(30),
            maxIncline = InclineTenths(0),
            adaptToYou = true,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(150)),
        profile = listOf(
            ProgramSegmentSummary(
                name = "STATIC CATALOG",
                duration = DurationMinutes(1),
                speed = SpeedTenths(20),
                incline = InclineTenths(0),
            ),
        ),
        previewMode = ProgramPreviewMode.BASELINE_PREVIEW,
    )

    private fun assertStarted(
        result: StartFiveKReadySessionDraftResult,
    ): StartFiveKReadySessionDraftResult.Started = when (result) {
        is StartFiveKReadySessionDraftResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private class RecordingDraftSessionStarter : FiveKReadySessionDraftSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(
            draft: FiveKReadySessionDraft,
            plan: ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult {
            received = plan
            return WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists)
        }
    }
}
