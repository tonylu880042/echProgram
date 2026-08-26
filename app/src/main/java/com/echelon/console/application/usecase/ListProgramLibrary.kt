package com.echelon.console.application.usecase

import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.Program

data class ProgramLibrary(
    val heroPrograms: List<HeroProgram>,
    val allPrograms: List<Program>,
)

class ListProgramLibrary(
    private val catalog: ProgramCatalog,
) {
    operator fun invoke(): ProgramLibrary = ProgramLibrary(
        heroPrograms = catalog.listHeroPrograms(),
        allPrograms = catalog.listPrograms(),
    )
}
