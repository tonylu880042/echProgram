package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.ProgramId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticProgramCatalogDurationPolicyTest {
    @Test
    fun `every reviewed program exposes its exact selectable duration policy`() {
        val catalog = StaticProgramCatalog()
        val detailCatalog = catalog as ProgramDetailCatalog
        val expected = mapOf(
            "FAT_BURN" to listOf(20, 30, 45),
            "GLUTE_BLAST" to listOf(15, 20, 30, 45),
            "VERTICAL" to listOf(50),
            "SURPRISE_ME" to listOf(10, 20, 30, 45),
            "SUMMIT" to listOf(20, 30, 45, 60),
            "HIIT_20" to listOf(10, 20, 30),
            "SWEAT_30" to listOf(20, 30, 45),
            "SPEED_LAB" to listOf(20, 30, 40),
            "BOOTY_BURN_15" to listOf(10, 15, 20),
            "5K_READY" to listOf(20, 30, 40, 60),
            "ECHELON_CHALLENGE" to listOf(20, 30, 45, 60),
            "TWELVE_3_30" to listOf(30),
            "POWER_WALK" to listOf(15, 30, 45, 60),
            "ROLLING_HILLS" to listOf(30, 45, 60),
            "ZONE_2" to listOf(20, 30, 45, 60),
            "WALK_RUN" to listOf(20, 30, 45, 60),
            "ENDURANCE" to listOf(30, 45, 60, 90),
            "PYRAMID" to listOf(20, 30, 45),
            "RECOVERY_WALK" to listOf(10, 20, 30),
            "QUICK_10" to listOf(10, 15, 20),
            "CALORIE_TARGET" to listOf(40),
            "ECHELON_HYBRID_RUN" to listOf(20, 30, 45),
        )

        assertEquals(22, expected.size)
        expected.forEach { (id, durations) ->
            val detail = requireNotNull(detailCatalog.findProgramDetail(ProgramId(id)))
            assertEquals(
                durations.map(::DurationMinutes),
                detail.supportedDurations,
            )
            assertTrue(detail.defaultDuration in detail.supportedDurations)
        }
    }
}
