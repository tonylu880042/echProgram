package com.echelon.console.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceCapabilitiesTest {
    @Test
    fun `duration capabilities require a positive step`() {
        assertThrows(IllegalArgumentException::class.java) {
            DurationLimits(
                min = DurationMinutes(10),
                max = DurationMinutes(60),
                step = DurationMinutes(0),
            )
        }
    }

    @Test
    fun `duration capabilities require min at or below max`() {
        assertThrows(IllegalArgumentException::class.java) {
            DurationLimits(
                min = DurationMinutes(61),
                max = DurationMinutes(60),
                step = DurationMinutes(5),
            )
        }
    }

    @Test
    fun `speed capabilities require min at or below max`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeedRange(min = SpeedTenths(121), max = SpeedTenths(120))
        }
    }

    @Test
    fun `incline capabilities require min at or below max`() {
        assertThrows(IllegalArgumentException::class.java) {
            InclineRange(min = InclineTenths(121), max = InclineTenths(120))
        }
    }
}
