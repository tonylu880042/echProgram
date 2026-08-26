package com.echelon.console.application.usecase

import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationFailure
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateSurpriseWorkoutDraftTest {
    @Test
    fun `application owns generator version and returns generated draft metadata`() {
        val result = GenerateSurpriseWorkoutDraft()(
            GenerateSurpriseWorkoutDraftRequest(
                durationMinutes = 30,
                effort = SurpriseWorkoutEffort.SWEAT,
                userProfileRevision = "anonymous-baseline-r1",
                regenerationIndex = 0,
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(70),
                machineMaxIncline = InclineTenths(150),
            ),
        )

        val generated = result as SurpriseWorkoutGenerationResult.Generated
        assertEquals("v1", generated.draft.metadata.generatorVersion)
        assertEquals(30, generated.draft.metadata.durationMinutes)
        assertEquals(SpeedTenths(60), generated.draft.effectiveSpeedCap)
        assertEquals(InclineTenths(70), generated.draft.effectiveInclineCap)
    }

    @Test
    fun `application returns generator rejection transparently`() {
        val result = GenerateSurpriseWorkoutDraft()(
            GenerateSurpriseWorkoutDraftRequest(
                durationMinutes = 30,
                effort = SurpriseWorkoutEffort.SWEAT,
                userProfileRevision = "anonymous-baseline-r1",
                regenerationIndex = 0,
                userMaxSpeed = SpeedTenths(10),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(70),
                machineMaxIncline = InclineTenths(150),
            ),
        )

        assertEquals(
            SurpriseWorkoutGenerationResult.Rejected(
                SurpriseWorkoutGenerationFailure.CapsDoNotIntersect(
                    dimension = com.echelon.console.domain.SurpriseWorkoutLimit.SPEED,
                    userMaximum = 10,
                    machineMaximum = 120,
                    globalMinimum = 25,
                ),
            ),
            result,
        )
        assertTrue(result is SurpriseWorkoutGenerationResult.Rejected)
    }
}
