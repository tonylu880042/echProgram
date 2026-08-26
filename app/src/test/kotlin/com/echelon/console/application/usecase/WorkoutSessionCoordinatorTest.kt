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
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineCompileError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSessionCoordinatorTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(1), DurationMinutes(60), DurationMinutes(1)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `fixed profile starts at zero with selected duration and clamped targets`() {
        val coordinator = coordinator()

        val result = coordinator.start(validatedPlan())

        val started = assertStarted(result)
        val running = started.state as WorkoutSessionState.Running
        assertEquals(0, running.progress.elapsedSeconds)
        assertEquals(360, running.progress.remainingSeconds)
        assertEquals("Warm Up", running.progress.currentSegment.name)
        assertEquals(55, running.progress.target.speed.value)
        assertEquals(50, running.progress.target.incline.value)
        assertEquals(running, coordinator.currentState())
    }

    @Test
    fun `missing detail is an explicit start failure`() {
        val coordinator = coordinator { null }

        val result = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.ProgramNotFound(ProgramId("FAT_BURN")),
            ),
            result,
        )
        assertNull(coordinator.currentState())
    }

    @Test
    fun `every non fixed preview mode is rejected`() {
        ProgramPreviewMode.values()
            .filterNot { it == ProgramPreviewMode.FIXED_PROFILE_PREVIEW }
            .forEach { mode ->
                val coordinator = coordinator { detail().copy(previewMode = mode) }

                val result = coordinator.start(validatedPlan())

                assertEquals(
                    WorkoutSessionStarterResult.Failed(
                        WorkoutSessionStartFailure.UnsupportedPreviewMode(mode),
                    ),
                    result,
                )
                assertNull(coordinator.currentState())
            }
    }

    @Test
    fun `empty profile compiler failure is propagated`() {
        val coordinator = coordinator { detail().copy(profile = emptyList()) }

        val result = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(
                WorkoutSessionStartFailure.TimelineCompileFailed(
                    WorkoutTimelineCompileError.EmptyProfile,
                ),
            ),
            result,
        )
        assertNull(coordinator.currentState())
    }

    @Test
    fun `second start is rejected without replacing the active session`() {
        val coordinator = coordinator()
        val first = assertStarted(coordinator.start(validatedPlan()))

        val second = coordinator.start(validatedPlan())

        assertEquals(
            WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists),
            second,
        )
        assertEquals(first.state, coordinator.currentState())
    }

    private fun coordinator(
        catalog: (ProgramId) -> ProgramDetail? = { requestedId ->
            detail().takeIf { it.programId == requestedId }
        },
    ): InMemoryWorkoutSessionCoordinator = InMemoryWorkoutSessionCoordinator(
        catalog = ProgramDetailCatalog(catalog),
    )

    private fun validatedPlan(): ValidatedWorkoutPlan = when (
        val result = ValidatedWorkoutPlan.create(plan(), capabilities)
    ) {
        is ValidatedWorkoutPlanResult.Valid -> result.plan
        is ValidatedWorkoutPlanResult.Invalid -> error("Expected valid plan, got $result")
    }

    private fun plan(): WorkoutPlan = WorkoutPlan(
        programId = ProgramId("FAT_BURN"),
        settings = detail().defaultSettings,
    )

    private fun detail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("FAT_BURN"),
        title = "FAT BURN",
        promise = "A fixed profile for a controlled burn.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(6),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(50),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(100)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(2), SpeedTenths(70), InclineTenths(80)),
            ProgramSegmentSummary("Build", DurationMinutes(2), SpeedTenths(60), InclineTenths(70)),
            ProgramSegmentSummary("Cool Down", DurationMinutes(2), SpeedTenths(25), InclineTenths(0)),
        ),
    )

    private fun assertStarted(
        result: WorkoutSessionStarterResult,
    ): WorkoutSessionStarterResult.Started = when (result) {
        is WorkoutSessionStarterResult.Started -> result
        is WorkoutSessionStarterResult.Accepted -> error("Expected stateful start result, got $result")
        is WorkoutSessionStarterResult.Failed -> error("Expected successful start, got $result")
    }
}
