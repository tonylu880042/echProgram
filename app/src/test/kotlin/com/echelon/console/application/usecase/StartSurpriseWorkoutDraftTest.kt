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
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.SurpriseWorkoutGenerator
import com.echelon.console.domain.SurpriseWorkoutGeneratorInput
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineCompileError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartSurpriseWorkoutDraftTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(60), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `accepted draft reaches running timeline with its exact profile`() {
        val draft = draft(durationMinutes = 20, effort = SurpriseWorkoutEffort.BURN)
        val coordinator = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { staticDetail() },
        )

        val result = StartSurpriseWorkoutDraft(coordinator)(draft, capabilities)

        val started = assertStarted(result)
        assertEquals(draftPlan(draft), started.plan)
        val running = started.state
        assertEquals(ProgramId("SURPRISE_ME"), running.timeline.programId)
        assertEquals(1_200, running.timeline.totalDurationSeconds)
        assertEquals(draft.profile.map { it.name }, running.timeline.segments.map { it.name })
        assertEquals(
            draft.profile.map { it.duration.value * 60 },
            running.timeline.segments.map { it.durationSeconds },
        )
        assertEquals(
            draft.profile.map { it.speed.value },
            running.timeline.segments.map { it.targetSpeed.value },
        )
        assertEquals(
            draft.profile.map { it.incline.value },
            running.timeline.segments.map { it.targetIncline.value },
        )
    }

    @Test
    fun `draft profile wins over a different static catalog profile`() {
        val draft = draft(durationMinutes = 10)
        val coordinator = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { staticDetail() },
        )

        val running = assertStarted(
            StartSurpriseWorkoutDraft(coordinator)(draft, capabilities),
        ).state

        assertTrue(running.timeline.segments.none { it.name == "STATIC CATALOG" })
        assertEquals(draft.profile.first().name, running.timeline.segments.first().name)
    }

    @Test
    fun `all supported draft durations start with exact timeline seconds`() {
        listOf(10, 20, 30, 45).forEach { durationMinutes ->
            val coordinator = InMemoryWorkoutSessionCoordinator(
                catalog = ProgramDetailCatalog { null },
            )
            val running = assertStarted(
                StartSurpriseWorkoutDraft(coordinator)(draft(durationMinutes), capabilities),
            ).state

            assertEquals(durationMinutes * 60, running.timeline.totalDurationSeconds)
            assertEquals(durationMinutes * 60, running.timeline.segments.last().endSecond)
        }
    }

    @Test
    fun `accepted effort maps to explicit baseline plan intensity before starter`() {
        val expected = mapOf(
            SurpriseWorkoutEffort.EASY to PlanIntensity.LOW,
            SurpriseWorkoutEffort.SWEAT to PlanIntensity.MEDIUM,
            SurpriseWorkoutEffort.BURN to PlanIntensity.HIGH,
            SurpriseWorkoutEffort.HARD to PlanIntensity.HIGH,
        )

        expected.forEach { (effort, intensity) ->
            val starter = RecordingDraftSessionStarter(
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile),
                ),
            )
            val result = StartSurpriseWorkoutDraft(starter)(draft(effort = effort), capabilities)

            assertEquals(
                StartSurpriseWorkoutDraftResult.StarterFailed(
                    WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile),
                ),
                result,
            )
            assertEquals(intensity, starter.received?.plan?.settings?.intensity)
            assertEquals(PlanFocus.BALANCED, starter.received?.plan?.settings?.focus)
            assertEquals(false, starter.received?.plan?.settings?.adaptToYou)
        }
    }

    @Test
    fun `tampered draft metadata and target are rejected before starter`() {
        val original = draft(durationMinutes = 20)
        val tampered = listOf(
            original.copy(
                metadata = original.metadata.copy(durationMinutes = 10),
            ),
            original.copy(
                metadata = original.metadata.copy(programId = ProgramId("FAT_BURN")),
            ),
            original.copy(
                profile = original.profile.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(speed = SpeedTenths(original.effectiveSpeedCap.value + 1)) else segment
                },
            ),
        )

        tampered.forEach { invalidDraft ->
            val starter = RecordingDraftSessionStarter(
                WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
            )

            val result = StartSurpriseWorkoutDraft(starter)(invalidDraft, capabilities)

            assertTrue(result is StartSurpriseWorkoutDraftResult.InvalidDraft)
            assertNull(starter.received)
        }
    }

    @Test
    fun `tampered replay metadata caps and profile are rejected before starter`() {
        val original = draft(durationMinutes = 20)
        val tampered = listOf(
            original.copy(
                profile = original.profile.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(speed = SpeedTenths(-1)) else segment
                },
            ),
            original.copy(
                profile = original.profile.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(incline = InclineTenths(-1)) else segment
                },
            ),
            original.copy(effectiveSpeedCap = SpeedTenths(81)),
            original.copy(effectiveInclineCap = InclineTenths(101)),
            original.copy(
                profile = original.profile.mapIndexed { index, segment ->
                    if (index == 0) segment.copy(speed = SpeedTenths(segment.speed.value + 1)) else segment
                },
            ),
            original.copy(
                metadata = original.metadata.copy(stableSeed = original.metadata.stableSeed + 1),
            ),
            original.copy(
                metadata = original.metadata.copy(userProfileRevision = " "),
            ),
            original.copy(
                metadata = original.metadata.copy(generatorVersion = "v2"),
            ),
        )

        tampered.forEach { invalidDraft ->
            val starter = RecordingDraftSessionStarter(
                WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
            )

            val result = StartSurpriseWorkoutDraft(starter)(invalidDraft, capabilities)

            assertTrue(result is StartSurpriseWorkoutDraftResult.InvalidDraft)
            assertNull(starter.received)
        }
    }

    @Test
    fun `device capability validation is distinct and starter remains untouched`() {
        val starter = RecordingDraftSessionStarter(
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
        )
        val narrowCapabilities = capabilities.copy(
            duration = DurationLimits(DurationMinutes(20), DurationMinutes(60), DurationMinutes(5)),
        )

        val result = StartSurpriseWorkoutDraft(starter)(draft(durationMinutes = 10), narrowCapabilities)

        assertTrue(result is StartSurpriseWorkoutDraftResult.CapabilityValidationFailed)
        assertNull(starter.received)
    }

    @Test
    fun `active session failure preserves the first draft state`() {
        val coordinator = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { null },
        )
        val useCase = StartSurpriseWorkoutDraft(coordinator)
        val first = assertStarted(useCase(draft(durationMinutes = 10), capabilities))

        val second = useCase(draft(durationMinutes = 20), capabilities)

        assertEquals(
            StartSurpriseWorkoutDraftResult.StarterFailed(
                WorkoutSessionStartFailure.ActiveSessionExists,
            ),
            second,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    @Test
    fun `timeline compile failure remains explicit after draft validation`() {
        val failure = WorkoutSessionStartFailure.TimelineCompileFailed(WorkoutTimelineCompileError.EmptyProfile)
        val starter = RecordingDraftSessionStarter(WorkoutSessionStarterResult.Failed(failure))

        val result = StartSurpriseWorkoutDraft(starter)(draft(), capabilities)

        assertEquals(StartSurpriseWorkoutDraftResult.StarterFailed(failure), result)
        assertNotNull(starter.received)
    }

    @Test
    fun `direct draft starter rejects a plan whose identity or caps differ`() {
        val draft = draft(durationMinutes = 20)
        val coordinator = InMemoryWorkoutSessionCoordinator(
            catalog = ProgramDetailCatalog { null },
        )
        val mismatchedPlans = listOf(
            DraftPlanMismatchField.PROGRAM_ID to draftPlan(draft, programId = ProgramId("FAT_BURN")),
            DraftPlanMismatchField.DURATION to draftPlan(draft, durationMinutes = 30),
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

            assertTrue(result is WorkoutSessionStarterResult.Failed)
            val failure = (result as WorkoutSessionStarterResult.Failed).failure
            assertEquals(WorkoutSessionStartFailure.DraftPlanMismatch(field), failure)
            assertNull(coordinator.currentState())
        }
    }

    private fun draft(
        durationMinutes: Int = 20,
        effort: SurpriseWorkoutEffort = SurpriseWorkoutEffort.SWEAT,
    ): SurpriseWorkoutDraft = when (
        val result = SurpriseWorkoutGenerator().generate(
            SurpriseWorkoutGeneratorInput(
                durationMinutes = durationMinutes,
                effort = effort,
                userProfileRevision = "profile-r1",
                regenerationIndex = 0,
                generatorVersion = "v1",
                userMaxSpeed = SpeedTenths(80),
                machineMaxSpeed = SpeedTenths(80),
                userMaxIncline = InclineTenths(100),
                machineMaxIncline = InclineTenths(100),
            ),
        )
    ) {
        is SurpriseWorkoutGenerationResult.Generated -> result.draft
        is SurpriseWorkoutGenerationResult.Rejected -> error("Expected draft, got ${result.failure}")
    }

    private fun staticDetail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("SURPRISE_ME"),
        title = "SURPRISE ME",
        promise = "Static profile must not replace an accepted draft.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(1),
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
        previewMode = ProgramPreviewMode.GENERATED_PREVIEW,
    )

    private fun draftPlan(
        draft: SurpriseWorkoutDraft,
        programId: ProgramId = draft.metadata.programId,
        durationMinutes: Int = draft.metadata.durationMinutes,
        intensity: PlanIntensity = when (draft.metadata.effort) {
            SurpriseWorkoutEffort.EASY -> PlanIntensity.LOW
            SurpriseWorkoutEffort.SWEAT -> PlanIntensity.MEDIUM
            SurpriseWorkoutEffort.BURN,
            SurpriseWorkoutEffort.HARD,
            -> PlanIntensity.HIGH
        },
        maxSpeed: SpeedTenths = draft.effectiveSpeedCap,
        maxIncline: InclineTenths = draft.effectiveInclineCap,
    ): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(
            WorkoutPlan(
                programId = programId,
                settings = PlanSettings(
                    duration = DurationMinutes(durationMinutes),
                    intensity = intensity,
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
        is ValidatedWorkoutPlanResult.Invalid ->
            error("Expected valid draft plan, got $result")
    }

    private fun assertStarted(
        result: StartSurpriseWorkoutDraftResult,
    ): StartSurpriseWorkoutDraftResult.Started = when (result) {
        is StartSurpriseWorkoutDraftResult.Started -> result
        else -> error("Expected started result, got $result")
    }

    private class RecordingDraftSessionStarter(
        private val result: WorkoutSessionStarterResult,
    ) : SurpriseWorkoutDraftSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(
            draft: SurpriseWorkoutDraft,
            plan: ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult {
            received = plan
            return result
        }
    }
}
