package com.echelon.console.application.usecase

import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.WorkoutPlan
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

    private class RecordingStarter : WorkoutSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(plan: ValidatedWorkoutPlan) {
            received = plan
        }
    }
}
