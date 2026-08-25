package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ListHeroProgramsTest {
    @Test
    fun `lists the four goal-first hero programs in customer priority order`() {
        val programs = ListHeroPrograms(StaticProgramCatalog())()

        assertEquals(
            listOf("FAT_BURN", "GLUTE_BLAST", "VERTICAL", "SURPRISE_ME"),
            programs.map { it.id.value },
        )
    }
}
