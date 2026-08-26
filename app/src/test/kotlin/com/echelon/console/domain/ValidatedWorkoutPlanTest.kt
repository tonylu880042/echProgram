package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedWorkoutPlanTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(
            min = DurationMinutes(10),
            max = DurationMinutes(60),
            step = DurationMinutes(5),
        ),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(120)),
    )

    @Test
    fun `factory returns validated wrapper for a valid plan`() {
        val plan = plan()

        val result = ValidatedWorkoutPlan.create(plan, capabilities)

        assertTrue(result is ValidatedWorkoutPlanResult.Valid)
        assertEquals(plan, (result as ValidatedWorkoutPlanResult.Valid).plan.plan)
    }

    @Test
    fun `factory returns field error and no wrapper for an invalid plan`() {
        val plan = plan(maxSpeed = SpeedTenths(121))

        val result = ValidatedWorkoutPlan.create(plan, capabilities)

        assertTrue(result is ValidatedWorkoutPlanResult.Invalid)
        assertEquals(
            PlanField.MAX_SPEED,
            (result as ValidatedWorkoutPlanResult.Invalid).errors.single().field,
        )
    }

    private fun plan(maxSpeed: SpeedTenths = SpeedTenths(85)): WorkoutPlan = WorkoutPlan(
        programId = ProgramId("FAT_BURN"),
        settings = PlanSettings(
            duration = DurationMinutes(45),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = maxSpeed,
            maxIncline = InclineTenths(80),
            adaptToYou = false,
        ),
    )
}
