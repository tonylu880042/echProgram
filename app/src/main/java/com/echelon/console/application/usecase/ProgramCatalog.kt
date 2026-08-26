package com.echelon.console.application.usecase

import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.Program

fun interface ProgramCatalog {
    fun listHeroPrograms(): List<HeroProgram>

    fun listPrograms(): List<Program> = listHeroPrograms().map { hero ->
        Program(
            id = hero.id,
            title = hero.title,
            category = com.echelon.console.domain.ProgramCategory.ALL,
            durationLabel = hero.durationLabel,
            promise = hero.promise,
        )
    }
}
