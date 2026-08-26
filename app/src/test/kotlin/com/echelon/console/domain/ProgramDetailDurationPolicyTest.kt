package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProgramDetailDurationPolicyTest {
    @Test
    fun `empty supported durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(supportedDurations = emptyList())
        }
    }

    @Test
    fun `zero duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(supportedDurations = listOf(DurationMinutes(0), DurationMinutes(30)))
        }
    }

    @Test
    fun `negative duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(supportedDurations = listOf(DurationMinutes(-5), DurationMinutes(30)))
        }
    }

    @Test
    fun `duplicate durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(
                supportedDurations = listOf(
                    DurationMinutes(20),
                    DurationMinutes(30),
                    DurationMinutes(30),
                ),
            )
        }
    }

    @Test
    fun `unsorted durations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(
                supportedDurations = listOf(DurationMinutes(30), DurationMinutes(20)),
            )
        }
    }

    @Test
    fun `missing default duration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detail(
                defaultDuration = DurationMinutes(30),
                supportedDurations = listOf(DurationMinutes(20), DurationMinutes(45)),
            )
        }
    }

    @Test
    fun `valid policy keeps sorted read-only duration values and default`() {
        val supportedDurations = listOf(
            DurationMinutes(20),
            DurationMinutes(30),
            DurationMinutes(45),
        )

        val detail = detail(
            defaultDuration = DurationMinutes(30),
            supportedDurations = supportedDurations,
        )

        assertEquals(supportedDurations, detail.supportedDurations)
        assertEquals(DurationMinutes(30), detail.defaultDuration)
    }

    private fun detail(
        defaultDuration: DurationMinutes = DurationMinutes(30),
        supportedDurations: List<DurationMinutes> = listOf(defaultDuration),
    ): ProgramDetail = ProgramDetail(
        programId = ProgramId("TEST_PROGRAM"),
        title = "TEST PROGRAM",
        promise = "A test profile.",
        defaultSettings = PlanSettings(
            duration = defaultDuration,
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(40),
            maxIncline = InclineTenths(20),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(60)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(100)),
        profile = listOf(
            ProgramSegmentSummary(
                name = "Warm Up",
                duration = DurationMinutes(30),
                speed = SpeedTenths(30),
                incline = InclineTenths(0),
            ),
        ),
        supportedDurations = supportedDurations,
    )
}
