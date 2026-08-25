package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramCatalog
import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.ProgramId

class StaticProgramCatalog : ProgramCatalog {
    override fun listHeroPrograms(): List<HeroProgram> = listOf(
        HeroProgram(
            id = ProgramId("FAT_BURN"),
            title = "FAT BURN",
            promise = "Sustained calorie-burning work without requiring hard running.",
        ),
        HeroProgram(
            id = ProgramId("GLUTE_BLAST"),
            title = "GLUTE BLAST",
            promise = "Hill-focused work for glutes and legs.",
        ),
        HeroProgram(
            id = ProgramId("VERTICAL"),
            title = "VERTICAL",
            promise = "Choose an elevation target and keep climbing.",
        ),
        HeroProgram(
            id = ProgramId("SURPRISE_ME"),
            title = "SURPRISE ME",
            promise = "Set your time and effort; Echelon creates the session.",
        ),
    )
}
