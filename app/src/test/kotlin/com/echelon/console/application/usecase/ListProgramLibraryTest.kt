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
            listOf(
                "SUMMIT",
                "HIIT_20",
                "SWEAT_30",
                "SPEED_LAB",
                "BOOTY_BURN_15",
                "5K_READY",
                "ECHELON_CHALLENGE",
                "TWELVE_3_30",
                "POWER_WALK",
                "ROLLING_HILLS",
                "ZONE_2",
                "WALK_RUN",
                "ENDURANCE",
                "PYRAMID",
                "RECOVERY_WALK",
                "QUICK_10",
                "CALORIE_TARGET",
                "ECHELON_HYBRID_RUN",
            ),
            library.allPrograms.map { it.id.value },
        )
    }
}
