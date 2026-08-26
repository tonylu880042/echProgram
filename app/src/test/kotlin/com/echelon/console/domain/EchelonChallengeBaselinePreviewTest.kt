package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchelonChallengeBaselinePreviewTest {
    private val generator = EchelonChallengeBaselinePreviewGenerator()

    @Test
    fun `fallback preview has exact challenge identity duration and neutral profile`() {
        val draft = generate()

        assertEquals(ProgramId("ECHELON_CHALLENGE"), draft.metadata.programId)
        assertEquals(DurationMinutes(30), draft.metadata.representativeProfileDuration)
        assertEquals(
            EchelonChallengeBaselinePreviewMode.BASELINE_FALLBACK_PREVIEW,
            draft.metadata.mode,
        )
        assertEquals(EchelonChallengeBaselineStatus.NOT_A_CHALLENGE, draft.metadata.baselineStatus)
        assertEquals(
            EchelonChallengeHistorySource.NO_APPROVED_HISTORY,
            draft.metadata.historySource,
        )
        assertEquals(
            EchelonChallengeHistoryStatus.NO_APPROVED_HISTORY_OR_PERSISTENCE,
            draft.metadata.historyStatus,
        )
        assertEquals(
            EchelonChallengeComparisonStatus.DISABLED_NOT_AVAILABLE,
            draft.metadata.comparisonStatus,
        )
        assertEquals(
            EchelonChallengePersonalBestStatus.NOT_EVALUATED_NOT_AUTHORIZED,
            draft.metadata.personalBestStatus,
        )
        assertEquals(
            EchelonChallengeCompletionComparisonStatus.NOT_AUTHORIZED,
            draft.metadata.completionComparisonStatus,
        )
        assertEquals(EchelonChallengeAdaptStatus.DISABLED, draft.metadata.adaptStatus)
        assertEquals(
            EchelonChallengeProfileProposalStatus.NOT_CLIENT_APPROVED,
            draft.metadata.profileProposalStatus,
        )
        assertEquals(
            EchelonChallengeBaselineControlStatus.PREVIEW_ONLY,
            draft.controlStatus,
        )
        assertEquals(
            EchelonChallengeDeviceCommandStatus.NO_DEVICE_COMMANDS,
            draft.deviceCommandStatus,
        )
        assertEquals(53, draft.metadata.effectiveSpeedCap.value)
        assertEquals(40, draft.metadata.effectiveInclineCap.value)
        assertFalse(draft.metadata.wasClamped)
        assertTrue(draft.metadata.clampDisclosure.isEmpty())
        assertEquals(
            listOf("WARM UP", "STEADY", "BUILD", "HOLD", "FINISH", "COOL DOWN"),
            draft.profile.map { it.name },
        )
        assertEquals(
            listOf(
                EchelonChallengeBaselineSegmentRole.WARM_UP,
                EchelonChallengeBaselineSegmentRole.STEADY,
                EchelonChallengeBaselineSegmentRole.BUILD,
                EchelonChallengeBaselineSegmentRole.HOLD,
                EchelonChallengeBaselineSegmentRole.FINISH,
                EchelonChallengeBaselineSegmentRole.COOL_DOWN,
            ),
            draft.segments.map { it.role },
        )
        assertEquals(listOf(5, 7, 7, 6, 3, 2), draft.profile.map { it.duration.value })
        assertEquals(listOf(40, 50, 53, 51, 53, 38), draft.profile.map { it.speed.value })
        assertEquals(listOf(30, 30, 40, 40, 40, 10), draft.profile.map { it.incline.value })
        assertEquals(30, draft.profile.sumOf { it.duration.value })
        assertTrue(
            draft.profile.none { segment ->
                listOf("BASE MATCH", "CHALLENGE BLOCK", "BEAT", "PR").any {
                    segment.name.contains(it)
                }
            },
        )
    }

    @Test
    fun `caps use user machine intersection and disclose each affected dimension`() {
        val draft = generate(
            input(
                userMaxSpeed = SpeedTenths(47),
                machineMaxSpeed = SpeedTenths(60),
                userMaxIncline = InclineTenths(34),
                machineMaxIncline = InclineTenths(80),
            ),
        )

        assertEquals(SpeedTenths(47), draft.metadata.effectiveSpeedCap)
        assertEquals(InclineTenths(34), draft.metadata.effectiveInclineCap)
        assertEquals(
            listOf(1, 2, 3, 4),
            draft.metadata.clampDisclosure
                .filter { EchelonChallengeBaselineClampDimension.SPEED in it.dimensions }
                .map { it.segmentIndex },
        )
        assertEquals(
            listOf(2, 3, 4),
            draft.metadata.clampDisclosure
                .filter { EchelonChallengeBaselineClampDimension.INCLINE in it.dimensions }
                .map { it.segmentIndex },
        )
        assertEquals(listOf(40, 47, 47, 47, 47, 38), draft.profile.map { it.speed.value })
        assertEquals(listOf(30, 30, 34, 34, 34, 10), draft.profile.map { it.incline.value })
        assertTrue(draft.metadata.wasClamped)
        assertTrue(
            draft.metadata.clampDisclosure.all { disclosure ->
                disclosure.dimensions.isNotEmpty() &&
                    disclosure.effectiveSpeed.value <= disclosure.proposedSpeed.value &&
                    disclosure.effectiveIncline.value <= disclosure.proposedIncline.value
            },
        )
    }

    @Test
    fun `minimum speed cap is accepted while zero incline cap clamps every incline`() {
        val draft = generate(
            input(
                userMaxSpeed = SpeedTenths(30),
                machineMaxSpeed = SpeedTenths(30),
                userMaxIncline = InclineTenths(0),
                machineMaxIncline = InclineTenths(0),
            ),
        )

        assertEquals(SpeedTenths(30), draft.metadata.effectiveSpeedCap)
        assertEquals(InclineTenths(0), draft.metadata.effectiveInclineCap)
        assertTrue(draft.profile.all { it.speed.value == 30 })
        assertTrue(draft.profile.all { it.incline.value == 0 })
        assertEquals((0..5).toList(), draft.metadata.clampDisclosure.map { it.segmentIndex })
        assertTrue(
            draft.metadata.clampDisclosure.all {
                it.dimensions == listOf(
                    EchelonChallengeBaselineClampDimension.SPEED,
                    EchelonChallengeBaselineClampDimension.INCLINE,
                )
            },
        )
    }

    @Test
    fun `invalid and non intersecting caps are typed rejections`() {
        val cases = listOf(
            input(userMaxSpeed = SpeedTenths(-1)) to
                EchelonChallengeBaselinePreviewGenerationFailure.InvalidSpeedCaps::class.java,
            input(machineMaxSpeed = SpeedTenths(-1)) to
                EchelonChallengeBaselinePreviewGenerationFailure.InvalidSpeedCaps::class.java,
            input(userMaxIncline = InclineTenths(-1)) to
                EchelonChallengeBaselinePreviewGenerationFailure.InvalidInclineCaps::class.java,
            input(machineMaxIncline = InclineTenths(-1)) to
                EchelonChallengeBaselinePreviewGenerationFailure.InvalidInclineCaps::class.java,
            input(userMaxSpeed = SpeedTenths(29)) to
                EchelonChallengeBaselinePreviewGenerationFailure.SpeedCapsDoNotIntersect::class.java,
            input(machineMaxSpeed = SpeedTenths(29)) to
                EchelonChallengeBaselinePreviewGenerationFailure.SpeedCapsDoNotIntersect::class.java,
        )

        cases.forEach { (caseInput, expectedFailureType) ->
            val result = generator.generate(caseInput)
            assertTrue("Expected rejection for $caseInput", result is EchelonChallengeBaselinePreviewResult.Rejected)
            val failure = (result as EchelonChallengeBaselinePreviewResult.Rejected).failure
            assertTrue(
                "Expected ${expectedFailureType.simpleName}, got $failure",
                expectedFailureType.isInstance(failure),
            )
        }
    }

    @Test
    fun `same typed input is deterministic and profile projection is defensive`() {
        val input = input(
            userMaxSpeed = SpeedTenths(47),
            machineMaxSpeed = SpeedTenths(50),
            userMaxIncline = InclineTenths(34),
            machineMaxIncline = InclineTenths(40),
        )

        val first = generate(input)
        val second = generate(input)

        assertEquals(first, second)
        assertNotEquals(first.profile, emptyList<ProgramSegmentSummary>())
        val projected = first.profile.toMutableList()
        projected.clear()
        assertEquals(6, first.profile.size)
    }

    private fun generate(
        input: EchelonChallengeBaselinePreviewInput = input(),
    ): EchelonChallengeBaselinePreviewDraft = when (
        val result = generator.generate(input)
    ) {
        is EchelonChallengeBaselinePreviewResult.Generated -> result.draft
        is EchelonChallengeBaselinePreviewResult.Rejected ->
            error("Expected generated draft, got ${result.failure}")
    }

    private fun input(
        userMaxSpeed: SpeedTenths = SpeedTenths(80),
        machineMaxSpeed: SpeedTenths = SpeedTenths(80),
        userMaxIncline: InclineTenths = InclineTenths(100),
        machineMaxIncline: InclineTenths = InclineTenths(100),
    ): EchelonChallengeBaselinePreviewInput = EchelonChallengeBaselinePreviewInput(
        userMaxSpeed = userMaxSpeed,
        machineMaxSpeed = machineMaxSpeed,
        userMaxIncline = userMaxIncline,
        machineMaxIncline = machineMaxIncline,
    )
}
