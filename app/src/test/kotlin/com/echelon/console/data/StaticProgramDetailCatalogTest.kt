package com.echelon.console.data

import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticProgramDetailCatalogTest {
    @Test
    fun `fat burn detail keeps safe promise default config and ordered profile`() {
        val detail = StaticProgramDetailCatalog().findProgramDetail(ProgramId("FAT_BURN"))

        assertNotNull(detail)
        requireNotNull(detail)
        assertTrue(detail.promise.contains("without requiring hard running"))
        assertEquals(DurationMinutes(45), detail.defaultSettings.duration)
        assertEquals(PlanIntensity.MEDIUM, detail.defaultSettings.intensity)
        assertEquals(PlanFocus.BALANCED, detail.defaultSettings.focus)
        assertEquals(SpeedTenths(55), detail.defaultSettings.maxSpeed)
        assertEquals(InclineTenths(120), detail.defaultSettings.maxIncline)
        assertEquals(SpeedRange(SpeedTenths(28), SpeedTenths(55)), detail.speedRange)
        assertEquals(InclineRange(InclineTenths(10), InclineTenths(120)), detail.inclineRange)
        assertEquals(
            listOf("Warm Up", "Steady Burn", "Climb", "Push", "Recovery", "Cool Down"),
            detail.profile.map { it.name },
        )
        assertEquals(
            detail.defaultDuration,
            DurationMinutes(detail.profile.sumOf { it.duration.value }),
        )
        assertTrue(
            detail.profile.all {
                it.speed.value in detail.speedRange.min.value..detail.speedRange.max.value
            },
        )
        assertTrue(
            detail.profile.all {
                it.incline.value in detail.inclineRange.min.value..detail.inclineRange.max.value
            },
        )
    }
}
