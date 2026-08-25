package com.echelon.console.application.usecase

import com.echelon.console.domain.HeroProgram

class ListHeroPrograms(
    private val catalog: ProgramCatalog,
) {
    operator fun invoke(): List<HeroProgram> = catalog.listHeroPrograms()
}
