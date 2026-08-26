package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanField
import com.echelon.console.domain.PlanValidationError
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.ProgramId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartWorkoutDurationPolicyTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(10), DurationMinutes(90), DurationMinutes(5)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `application rejects a capability-valid but program-unsupported duration`() {
        val starter = RecordingStarter()
        val catalog = StaticProgramCatalog()
        val programId = ProgramId("FAT_BURN")
        val detail = requireNotNull(catalog.findProgramDetail(programId))
        val result = StartWorkout(starter, catalog)(
            plan = WorkoutPlan(
                programId = programId,
                settings = detail.defaultSettings.copy(duration = DurationMinutes(35)),
            ),
            capabilities = capabilities,
        )

        assertEquals(
            StartWorkoutResult.Invalid(
                listOf(
                    PlanValidationError.DurationNotSupported(
                        value = DurationMinutes(35),
                        supportedDurations = listOf(20, 30, 45).map(::DurationMinutes),
                    ),
                ),
            ),
            result,
        )
        assertEquals(PlanField.DURATION, (result as StartWorkoutResult.Invalid).errors.single().field)
        assertNull(starter.received)
    }

    @Test
    fun `coordinator direct boundary rejects unsupported static duration`() {
        val catalog = StaticProgramCatalog()
        val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
        val programId = ProgramId("FAT_BURN")
        val detail = requireNotNull(catalog.findProgramDetail(programId))
        val validated = when (
            val result = ValidatedWorkoutPlan.create(
                plan = WorkoutPlan(
                    detail.programId,
                    detail.defaultSettings.copy(duration = DurationMinutes(35)),
                ),
                capabilities = capabilities,
            )
        ) {
            is ValidatedWorkoutPlanResult.Valid -> result.plan
            is ValidatedWorkoutPlanResult.Invalid -> error("Expected capability-valid plan: ${result.errors}")
        }

        val result = coordinator.start(validated)

        assertTrue(result is WorkoutSessionStarterResult.Failed)
        assertTrue(
            (result as WorkoutSessionStarterResult.Failed).failure
                is WorkoutSessionStartFailure.UnsupportedDuration,
        )
        assertNull(coordinator.currentState())
    }

    @Test
    fun `unknown program is rejected before the starter is called`() {
        val starter = RecordingStarter()
        val unknownProgram = ProgramId("UNKNOWN")
        val result = StartWorkout(
            sessionStarter = starter,
            programCatalog = ProgramDetailCatalog { null },
        )(
            plan = WorkoutPlan(
                programId = unknownProgram,
                settings = requireNotNull(
                    StaticProgramCatalog().findProgramDetail(ProgramId("FAT_BURN")),
                ).defaultSettings,
            ),
            capabilities = capabilities,
        )

        assertEquals(
            StartWorkoutResult.StarterFailure(
                WorkoutSessionStartFailure.ProgramNotFound(unknownProgram),
            ),
            result,
        )
        assertNull(starter.received)
    }

    private class RecordingStarter : WorkoutSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(plan: ValidatedWorkoutPlan): WorkoutSessionStarterResult {
            received = plan
            return WorkoutSessionStarterResult.Failed(WorkoutSessionStartFailure.ActiveSessionExists)
        }
    }
}
