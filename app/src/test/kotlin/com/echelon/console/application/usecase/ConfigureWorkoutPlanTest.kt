package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanField
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigureWorkoutPlanTest {
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
    fun `invalid max speed is rejected at capability boundary and is not forwarded`() {
        val starter = RecordingStarter()
        val result = ConfigureWorkoutPlan(starter).invoke(
            plan = WorkoutPlan(
                programId = ProgramId("FAT_BURN"),
                settings = PlanSettings(
                    duration = DurationMinutes(45),
                    intensity = PlanIntensity.MEDIUM,
                    focus = PlanFocus.BALANCED,
                    maxSpeed = SpeedTenths(121),
                    maxIncline = InclineTenths(80),
                    adaptToYou = false,
                ),
            ),
            capabilities = capabilities,
        )

        assertTrue(result is ConfigureWorkoutPlanResult.Invalid)
        assertNull(starter.received)
    }

    @Test
    fun `exact speed and incline boundaries are valid and forward the exact plan`() {
        listOf(SpeedTenths(20), SpeedTenths(120)).forEach { speed ->
            listOf(InclineTenths(0), InclineTenths(120)).forEach { incline ->
                val plan = plan(maxSpeed = speed, maxIncline = incline)
                val starter = RecordingStarter()

                val result = ConfigureWorkoutPlan(starter).invoke(plan, capabilities)

                assertTrue(result is ConfigureWorkoutPlanResult.Valid)
                assertEquals(plan, starter.received?.plan)
            }
        }
    }

    @Test
    fun `speed and incline values outside either capability boundary are invalid and not forwarded`() {
        assertInvalid(plan(maxSpeed = SpeedTenths(19)), PlanField.MAX_SPEED)
        assertInvalid(plan(maxSpeed = SpeedTenths(121)), PlanField.MAX_SPEED)
        assertInvalid(plan(maxIncline = InclineTenths(-1)), PlanField.MAX_INCLINE)
        assertInvalid(plan(maxIncline = InclineTenths(121)), PlanField.MAX_INCLINE)
    }

    @Test
    fun `duration must be within capability range and aligned to its step`() {
        assertInvalid(plan(duration = DurationMinutes(9)), PlanField.DURATION)
        assertInvalid(plan(duration = DurationMinutes(61)), PlanField.DURATION)
        assertInvalid(plan(duration = DurationMinutes(12)), PlanField.DURATION)
        assertInvalid(plan(duration = DurationMinutes(47)), PlanField.DURATION)
    }

    @Test
    fun `focus and adapt setting are preserved when a valid plan is forwarded`() {
        val plan = plan(
            intensity = PlanIntensity.HIGH,
            focus = PlanFocus.MORE_INCLINE,
            adaptToYou = true,
        )
        val starter = RecordingStarter()

        val result = ConfigureWorkoutPlan(starter).invoke(plan, capabilities)

        assertTrue(result is ConfigureWorkoutPlanResult.Valid)
        assertEquals(PlanIntensity.HIGH, starter.received?.plan?.settings?.intensity)
        assertEquals(PlanFocus.MORE_INCLINE, starter.received?.plan?.settings?.focus)
        assertEquals(true, starter.received?.plan?.settings?.adaptToYou)
    }

    private fun assertInvalid(plan: WorkoutPlan, expectedField: PlanField) {
        val starter = RecordingStarter()
        val result = ConfigureWorkoutPlan(starter).invoke(plan, capabilities)

        assertTrue(result is ConfigureWorkoutPlanResult.Invalid)
        assertEquals(expectedField, (result as ConfigureWorkoutPlanResult.Invalid).errors.single().field)
        assertNull(starter.received)
    }

    private fun plan(
        duration: DurationMinutes = DurationMinutes(45),
        intensity: PlanIntensity = PlanIntensity.MEDIUM,
        focus: PlanFocus = PlanFocus.BALANCED,
        maxSpeed: SpeedTenths = SpeedTenths(85),
        maxIncline: InclineTenths = InclineTenths(80),
        adaptToYou: Boolean = false,
    ): WorkoutPlan = WorkoutPlan(
        programId = ProgramId("FAT_BURN"),
        settings = PlanSettings(
            duration = duration,
            intensity = intensity,
            focus = focus,
            maxSpeed = maxSpeed,
            maxIncline = maxIncline,
            adaptToYou = adaptToYou,
        ),
    )

    private class RecordingStarter : WorkoutSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(plan: ValidatedWorkoutPlan) {
            received = plan
        }
    }
}
