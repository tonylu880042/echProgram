package com.echelon.console.data

import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EchelonChallengeProfileTest {
    @Test
    fun `uses the documented compatible baseline example`() {
        val detail = StaticProgramCatalog().findProgramDetail(ProgramId("ECHELON_CHALLENGE"))

        assertNotNull(detail)
        requireNotNull(detail)
        assertEquals(SpeedTenths(53), detail.defaultSettings.maxSpeed)
        assertEquals(InclineTenths(40), detail.defaultSettings.maxIncline)
        assertEquals(ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW, detail.previewMode)
        assertEquals(
            listOf(
                SpeedTenths(40),
                SpeedTenths(50),
                SpeedTenths(53),
                SpeedTenths(51),
                SpeedTenths(53),
                SpeedTenths(38),
            ),
            detail.profile.map { it.speed },
        )
        assertEquals(
            listOf(
                InclineTenths(30),
                InclineTenths(30),
                InclineTenths(40),
                InclineTenths(40),
                InclineTenths(40),
                InclineTenths(10),
            ),
            detail.profile.map { it.incline },
        )
        assertEquals(
            listOf(5, 7, 7, 6, 3, 2).map(::DurationMinutes),
            detail.profile.map { it.duration },
        )
    }
}
