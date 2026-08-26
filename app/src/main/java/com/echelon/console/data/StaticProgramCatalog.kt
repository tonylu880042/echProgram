package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramCatalog
import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.Program
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramCategory

class StaticProgramCatalog : ProgramCatalog {
    override fun listHeroPrograms(): List<HeroProgram> = listOf(
        HeroProgram(
            id = ProgramId("FAT_BURN"),
            title = "FAT BURN",
            promise = "Sustained calorie-burning work without requiring hard running.",
            durationLabel = "45 MIN",
        ),
        HeroProgram(
            id = ProgramId("GLUTE_BLAST"),
            title = "GLUTE BLAST",
            promise = "Hill-focused work for glutes and legs.",
            durationLabel = "20 MIN",
        ),
        HeroProgram(
            id = ProgramId("VERTICAL"),
            title = "VERTICAL",
            promise = "Choose an elevation target and keep climbing.",
            durationLabel = "60 M",
        ),
        HeroProgram(
            id = ProgramId("SURPRISE_ME"),
            title = "SURPRISE ME",
            promise = "Set your time and effort; Echelon creates the session.",
            durationLabel = "10–60 MIN",
        ),
    )

    override fun listPrograms(): List<Program> = listOf(
        Program(
            id = ProgramId("EASY_STROLL"),
            title = "Easy Stroll",
            category = ProgramCategory.RECOVERY,
            durationLabel = "15 MIN",
            promise = "A gentle walk to reset your pace.",
        ),
        Program(
            id = ProgramId("SPEED_DEMON"),
            title = "Speed Demon",
            category = ProgramCategory.HIIT,
            durationLabel = "30 MIN",
            promise = "Short pushes with clear recovery windows.",
        ),
        Program(
            id = ProgramId("ROLLING_HILLS"),
            title = "Rolling Hills",
            category = ProgramCategory.STAMINA,
            durationLabel = "45 MIN",
            promise = "A steady climb through changing grades.",
        ),
        Program(
            id = ProgramId("HEART_HEALTH"),
            title = "Heart Health",
            category = ProgramCategory.AEROBIC,
            durationLabel = "20 MIN",
            promise = "A measured effort for consistent movement.",
        ),
    )
}
