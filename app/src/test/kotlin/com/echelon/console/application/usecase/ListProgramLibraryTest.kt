package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class ListProgramLibraryTest {
    @Test
    fun `lists heroes and supporting programs in catalog order`() {
        val library = ListProgramLibrary(StaticProgramCatalog())()

        assertEquals(
            listOf("FAT_BURN", "GLUTE_BLAST", "VERTICAL", "SURPRISE_ME"),
            library.heroPrograms.map { it.id.value },
        )
        assertEquals(
            listOf("EASY_STROLL", "SPEED_DEMON", "ROLLING_HILLS", "HEART_HEALTH"),
            library.allPrograms.map { it.id.value },
        )
    }
}
