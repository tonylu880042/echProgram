package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramDetailCatalog
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

class StaticProgramDetailCatalog : ProgramDetailCatalog {
    override fun findProgramDetail(programId: ProgramId): ProgramDetail? =
        if (programId == FAT_BURN_ID) fatBurnDetail else null

    private companion object {
        val FAT_BURN_ID = ProgramId("FAT_BURN")

        val fatBurnDetail = ProgramDetail(
            programId = FAT_BURN_ID,
            title = "FAT BURN",
            promise = "Sustained calorie-burning work without requiring hard running.",
            defaultSettings = PlanSettings(
                duration = DurationMinutes(45),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = SpeedTenths(85),
                maxIncline = InclineTenths(80),
                adaptToYou = false,
            ),
            speedRange = SpeedRange(
                min = SpeedTenths(20),
                max = SpeedTenths(120),
            ),
            inclineRange = InclineRange(
                min = InclineTenths(0),
                max = InclineTenths(80),
            ),
            profile = listOf(
                ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(35), InclineTenths(0)),
                ProgramSegmentSummary("Steady Burn", DurationMinutes(10), SpeedTenths(50), InclineTenths(20)),
                ProgramSegmentSummary("Climb", DurationMinutes(10), SpeedTenths(45), InclineTenths(45)),
                ProgramSegmentSummary("Push", DurationMinutes(10), SpeedTenths(65), InclineTenths(30)),
                ProgramSegmentSummary("Recovery", DurationMinutes(5), SpeedTenths(35), InclineTenths(0)),
                ProgramSegmentSummary("Cool Down", DurationMinutes(5), SpeedTenths(30), InclineTenths(0)),
            ),
        )
    }
}
