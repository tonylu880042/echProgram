package com.echelon.console.application.usecase

import com.echelon.console.domain.HeroProgram

fun interface ProgramCatalog {
    fun listHeroPrograms(): List<HeroProgram>
}
