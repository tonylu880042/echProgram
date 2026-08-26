package com.echelon.console.application.usecase

import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationFailure
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateFiveKReadySessionDraftTest {
    private val useCase = GenerateFiveKReadySessionDraft()

    @Test
    fun `application boundary generates a deterministic single session draft`() {
        val result = useCase(request())

        val generated = result as FiveKReadySessionGenerationResult.Generated
        assertEquals(30, generated.draft.metadata.durationMinutes)
        assertEquals(FiveKReadyBaselineSource.USER_ENTERED, generated.draft.metadata.baselineSource)
        assertEquals(SpeedTenths(60), generated.draft.metadata.userMaxSpeed)
        assertEquals(SpeedTenths(60), generated.draft.metadata.machineMaxSpeed)
        assertEquals(InclineTenths(60), generated.draft.metadata.userMaxIncline)
        assertEquals(InclineTenths(60), generated.draft.metadata.machineMaxIncline)
        assertEquals(
            "5K_READY|v1|30|40|USER_ENTERED|60|60|60|60",
            generated.draft.metadata.replayFingerprint,
        )
        assertEquals(
            listOf(5, 5, 3, 5, 2, 5, 2, 3),
            generated.draft.profile.map { it.duration.value },
        )
        assertEquals(
            generated,
            useCase(request()),
        )
    }

    @Test
    fun `missing baseline remains an explicit generation rejection`() {
        val result = useCase(request(baselinePace = null))

        assertEquals(
            FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineRequired,
            ),
            result,
        )
    }

    @Test
    fun `unsafe baseline and caps remain domain failures at the application boundary`() {
        val result = useCase(
            request(
                baselinePace = FiveKReadyBaselinePace(
                    speed = SpeedTenths(40),
                    source = FiveKReadyBaselineSource.HISTORY,
                ),
                userMaxSpeed = SpeedTenths(-1),
            ),
        )

        assertTrue(result is FiveKReadySessionGenerationResult.Rejected)
        assertTrue(
            (result as FiveKReadySessionGenerationResult.Rejected).failure is
                FiveKReadySessionGenerationFailure.BaselineSourceNotUserEntered,
        )
    }

    private fun request(
        durationMinutes: Int = 30,
        baselinePace: FiveKReadyBaselinePace? = FiveKReadyBaselinePace(
            speed = SpeedTenths(40),
            source = FiveKReadyBaselineSource.USER_ENTERED,
        ),
        userMaxSpeed: SpeedTenths = SpeedTenths(60),
        machineMaxSpeed: SpeedTenths = SpeedTenths(60),
        userMaxIncline: InclineTenths = InclineTenths(60),
        machineMaxIncline: InclineTenths = InclineTenths(60),
    ): GenerateFiveKReadySessionDraftRequest = GenerateFiveKReadySessionDraftRequest(
        durationMinutes = durationMinutes,
        baselinePace = baselinePace,
        userMaxSpeed = userMaxSpeed,
        machineMaxSpeed = machineMaxSpeed,
        userMaxIncline = userMaxIncline,
        machineMaxIncline = machineMaxIncline,
    )
}
