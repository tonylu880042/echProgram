package com.echelon.console.application.usecase

import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetProgramDetailTest {
    @Test
    fun `returns ready detail from the detail catalog`() {
        val detail = sampleDetail(ProgramId("FAT_BURN"))
        val useCase = GetProgramDetail(ProgramDetailCatalog { detail })

        val result = useCase(detail.programId)

        assertEquals(ProgramDetailResult.Ready(detail), result)
    }

    @Test
    fun `returns explicit not found result when program is unavailable`() {
        val missingId = ProgramId("MISSING")
        val useCase = GetProgramDetail(ProgramDetailCatalog { null })

        val result = useCase(missingId)

        assertTrue(result is ProgramDetailResult.NotFound)
        assertEquals(missingId, (result as ProgramDetailResult.NotFound).programId)
    }

    private fun sampleDetail(id: ProgramId): ProgramDetail = ProgramDetail(
        programId = id,
        title = "FAT BURN",
        promise = "Sustained calorie-burning work without requiring hard running.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(45),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(120),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(28), SpeedTenths(55)),
        inclineRange = InclineRange(InclineTenths(10), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(35), InclineTenths(0)),
        ),
    )
}
