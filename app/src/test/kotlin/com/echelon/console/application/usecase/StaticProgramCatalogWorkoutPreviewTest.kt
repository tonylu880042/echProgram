package com.echelon.console.application.usecase

import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import com.echelon.console.domain.ValidatedWorkoutPlanResult
import com.echelon.console.domain.WorkoutPlan
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutSessionTargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticProgramCatalogWorkoutPreviewTest {
    @Test
    fun `every catalog program starts a bounded representative preview`() {
        val catalog = StaticProgramCatalog()
        val programIds = catalog.listHeroPrograms().map { it.id } + catalog.listPrograms().map { it.id }

        assertEquals(22, programIds.size)
        assertEquals(programIds.size, programIds.toSet().size)

        programIds.forEach { programId ->
            val detail = checkNotNull(catalog.findProgramDetail(programId))
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val plan = when (
                val result = ValidatedWorkoutPlan.create(
                    plan = WorkoutPlan(programId, detail.defaultSettings),
                    capabilities = compositionCapabilities,
                )
            ) {
                is ValidatedWorkoutPlanResult.Valid -> result.plan
                is ValidatedWorkoutPlanResult.Invalid -> error(
                    "${detail.programId.value} default settings should validate: ${result.errors}",
                )
            }

            val started = when (val result = StartWorkout(coordinator)(plan.plan, compositionCapabilities)) {
                is StartWorkoutResult.Valid -> result.plan
                is StartWorkoutResult.Invalid -> error(
                    "${detail.programId.value} should start: ${result.errors}",
                )
                is StartWorkoutResult.StarterFailure -> error(
                    "${detail.programId.value} should start: ${result.failure}",
                )
            }
            val running = checkNotNull(coordinator.currentState()) as WorkoutSessionState.Running

            assertEquals(started.plan.programId, running.timeline.programId)
            assertEquals(detail.defaultDuration.value * SECONDS_PER_MINUTE, running.timeline.totalDurationSeconds)
            assertEquals(0, running.progress.elapsedSeconds)
            assertEquals(WorkoutSessionTargetMode.PROFILE, running.progress.target.mode)
            assertTrue(
                "${detail.programId.value} initial speed exceeds selected cap",
                running.progress.target.speed.value <= started.plan.settings.maxSpeed.value,
            )
            assertTrue(
                "${detail.programId.value} initial incline exceeds selected cap",
                running.progress.target.incline.value <= started.plan.settings.maxIncline.value,
            )
            running.timeline.segments.forEach { segment ->
                assertTrue(segment.targetSpeed.value <= started.plan.settings.maxSpeed.value)
                assertTrue(segment.targetIncline.value <= started.plan.settings.maxIncline.value)
                assertTrue(segment.targetSpeed.value <= compositionCapabilities.speed.max.value)
                assertTrue(segment.targetIncline.value <= compositionCapabilities.incline.max.value)
            }
        }
    }

    @Test
    fun `non fixed catalog modes retain preview metadata without device control`() {
        val catalog = StaticProgramCatalog()
        val expectedModes = mapOf(
            "VERTICAL" to ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
            "SURPRISE_ME" to ProgramPreviewMode.GENERATED_PREVIEW,
            "5K_READY" to ProgramPreviewMode.BASELINE_PREVIEW,
            "ECHELON_CHALLENGE" to ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW,
            "ZONE_2" to ProgramPreviewMode.HEART_RATE_PREVIEW,
            "CALORIE_TARGET" to ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
        )

        expectedModes.forEach { (programId, expectedMode) ->
            val detail = checkNotNull(catalog.findProgramDetail(ProgramId(programId)))

            assertEquals(expectedMode, detail.previewMode)

            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val result = StartWorkout(coordinator)(
                plan = WorkoutPlan(detail.programId, detail.defaultSettings),
                capabilities = compositionCapabilities,
            )
            assertTrue("$programId should be a valid preview start", result is StartWorkoutResult.Valid)
            val running = checkNotNull(coordinator.currentState()) as WorkoutSessionState.Running
            assertEquals(WorkoutSessionTargetMode.PROFILE, running.progress.target.mode)
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60

        val compositionCapabilities = DeviceCapabilities(
            duration = DurationLimits(
                min = DurationMinutes(10),
                max = DurationMinutes(60),
                step = DurationMinutes(5),
            ),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )
    }
}
