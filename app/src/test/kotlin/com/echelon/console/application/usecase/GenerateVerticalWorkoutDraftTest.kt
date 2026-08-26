package com.echelon.console.application.usecase

import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalWorkoutGenerationFailure
import com.echelon.console.domain.VerticalWorkoutGenerationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateVerticalWorkoutDraftTest {
    private val useCase = GenerateVerticalWorkoutDraft()

    @Test
    fun `application boundary maps target and capability request to representative draft`() {
        val result = useCase(
            GenerateVerticalWorkoutDraftRequest(
                target = VerticalTarget.TWO_THOUSAND_FEET,
                userMaxSpeed = SpeedTenths(37),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(125),
                machineMaxIncline = InclineTenths(150),
            ),
        )

        val generated = result as VerticalWorkoutGenerationResult.Generated
        assertEquals(VerticalTarget.TWO_THOUSAND_FEET, generated.draft.metadata.target)
        assertEquals(SpeedTenths(37), generated.draft.metadata.effectiveSpeedCap)
        assertEquals(InclineTenths(125), generated.draft.metadata.effectiveInclineCap)
        assertEquals(
            listOf(5, 10, 10, 10, 10, 5),
            generated.draft.profile.map { it.duration.value },
        )
        assertEquals(50, generated.draft.profile.sumOf { it.duration.value })
    }

    @Test
    fun `invalid capability request remains an explicit domain rejection`() {
        val result = useCase(
            GenerateVerticalWorkoutDraftRequest(
                target = VerticalTarget.FIVE_HUNDRED_FEET,
                userMaxSpeed = SpeedTenths(24),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
        )

        assertTrue(result is VerticalWorkoutGenerationResult.Rejected)
        assertEquals(
            VerticalWorkoutGenerationFailure.SpeedCapsDoNotIntersect(
                userMaximum = SpeedTenths(24),
                machineMaximum = SpeedTenths(40),
                globalMinimum = SpeedTenths(25),
            ),
            (result as VerticalWorkoutGenerationResult.Rejected).failure,
        )
    }
}
