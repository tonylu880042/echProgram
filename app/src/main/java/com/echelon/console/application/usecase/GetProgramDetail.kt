package com.echelon.console.application.usecase

import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId

fun interface ProgramDetailCatalog {
    fun findProgramDetail(programId: ProgramId): ProgramDetail?
}

class GetProgramDetail(
    private val catalog: ProgramDetailCatalog,
) {
    operator fun invoke(programId: ProgramId): ProgramDetailResult =
        catalog.findProgramDetail(programId)?.let { detail ->
            ProgramDetailResult.Ready(detail)
        } ?: ProgramDetailResult.NotFound(programId)
}

sealed interface ProgramDetailResult {
    data class Ready(val detail: ProgramDetail) : ProgramDetailResult

    data class NotFound(val programId: ProgramId) : ProgramDetailResult
}
