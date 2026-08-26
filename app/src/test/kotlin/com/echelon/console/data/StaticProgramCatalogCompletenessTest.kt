package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutPlanValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticProgramCatalogCompletenessTest {
    private val compositionRootCapabilities = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(10), DurationMinutes(60), DurationMinutes(5)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    private val expectedHeroIds = listOf(
        "FAT_BURN",
        "GLUTE_BLAST",
        "VERTICAL",
        "SURPRISE_ME",
    )

    private val expectedSupportingIds = listOf(
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
    )

    @Test
    fun `catalog exposes the reviewed 22 programs in hero and supporting order`() {
        val catalog = StaticProgramCatalog()

        assertEquals(expectedHeroIds, catalog.listHeroPrograms().map { it.id.value })
        assertEquals(expectedSupportingIds, catalog.listPrograms().map { it.id.value })
        assertEquals(
            expectedHeroIds + expectedSupportingIds,
            (catalog.listHeroPrograms().map { it.id.value } + catalog.listPrograms().map { it.id.value }),
        )
        assertEquals(22, (expectedHeroIds + expectedSupportingIds).toSet().size)
    }

    @Test
    fun `every visible program has a valid representative detail`() {
        val catalog = StaticProgramCatalog()
        val detailCatalog = catalog as? ProgramDetailCatalog
        assertNotNull("Static catalog must provide the detail boundary", detailCatalog)
        val allIds = expectedHeroIds + expectedSupportingIds

        allIds.forEach { id ->
            val detail = requireNotNull(detailCatalog?.findProgramDetail(ProgramId(id)))
            assertEquals(ProgramId(id), detail.programId)
            assertTrue(detail.profile.isNotEmpty())
            assertEquals(
                detail.defaultDuration,
                detail.profile.fold(0) { total, segment -> total + segment.duration.value }
                    .let(::DurationMinutes),
            )
            assertTrue(detail.profile.all { it.speed.value in detail.speedRange.min.value..detail.speedRange.max.value })
            assertTrue(detail.profile.all { it.incline.value in detail.inclineRange.min.value..detail.inclineRange.max.value })
            assertTrue(detail.defaultSettings.maxSpeed.value in detail.speedRange.min.value..detail.speedRange.max.value)
            assertTrue(detail.defaultSettings.maxIncline.value in detail.inclineRange.min.value..detail.inclineRange.max.value)
            assertTrue(
                "${detail.programId.value} default must fit composition-root capabilities",
                WorkoutPlanValidator.validate(
                    WorkoutPlan(detail.programId, detail.defaultSettings),
                    compositionRootCapabilities,
                ).isEmpty(),
            )
        }
    }

    @Test
    fun `target and proposal programs retain explicit preview metadata`() {
        val catalog = StaticProgramCatalog()
        val detailCatalog = requireNotNull(catalog as? ProgramDetailCatalog)

        assertEquals(
            ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("VERTICAL"))?.previewMode,
        )
        assertEquals(
            ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("ECHELON_CHALLENGE"))?.previewMode,
        )
        assertEquals(
            ProgramPreviewMode.BASELINE_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("5K_READY"))?.previewMode,
        )
        assertEquals(
            ProgramPreviewMode.HEART_RATE_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("ZONE_2"))?.previewMode,
        )
        assertEquals(
            ProgramPreviewMode.GENERATED_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("SURPRISE_ME"))?.previewMode,
        )
        assertEquals(
            ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
            detailCatalog.findProgramDetail(ProgramId("CALORIE_TARGET"))?.previewMode,
        )
    }
}
