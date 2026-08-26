package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalWorkoutGeneratorTest {
    private val generator = VerticalWorkoutGenerator()

    @Test
    fun `each reviewed target carries its proposed time limit without changing the profile`() {
        val expectedLimits = mapOf(
            VerticalTarget.FIVE_HUNDRED_FEET to 45,
            VerticalTarget.ONE_THOUSAND_FEET to 60,
            VerticalTarget.TWO_THOUSAND_FEET to 120,
            VerticalTarget.VERTICAL_MILE to 240,
        )

        val drafts = expectedLimits.map { (target, minutes) ->
            val draft = generate(input(target = target))
            assertEquals(ProgramId("VERTICAL"), draft.metadata.programId)
            assertEquals(target, draft.metadata.target)
            assertEquals(minutes, draft.metadata.proposedTimeLimit.minutes)
            assertEquals(VerticalTimeLimitStatus.PROPOSED, draft.metadata.proposedTimeLimit.status)
            draft
        }

        assertEquals(1, drafts.map { it.profile }.distinct().size)
        assertEquals(
            listOf(5, 10, 10, 10, 10, 5),
            drafts.first().profile.map { it.duration.value },
        )
        assertEquals(50, drafts.first().profile.sumOf { it.duration.value })
        assertFalse(drafts.first().metadata.wasClamped)
        assertTrue(drafts.first().metadata.clampDisclosure.isEmpty())
    }

    @Test
    fun `draft is explicitly preview only and does not calculate elevation progress`() {
        val draft = generate(input())

        assertEquals(
            VerticalWorkoutDraftMode.REPRESENTATIVE_PROFILE_PREVIEW,
            draft.metadata.mode,
        )
        assertEquals(VerticalWorkoutDraftControlStatus.PREVIEW_ONLY, draft.controlStatus)
        assertEquals(VerticalElevationSource.UNAVAILABLE, draft.metadata.elevationSource)
        assertEquals(VerticalProgressStatus.NOT_CALCULATED, draft.metadata.progressStatus)
    }

    @Test
    fun `representative profile matches the documented six block proposal`() {
        val draft = generate(input())

        assertEquals(
            listOf(
                VerticalProfileSegmentRole.WARM_UP,
                VerticalProfileSegmentRole.BASE_CLIMB,
                VerticalProfileSegmentRole.BUILD,
                VerticalProfileSegmentRole.STEEP_BLOCK,
                VerticalProfileSegmentRole.FINISH_PUSH,
                VerticalProfileSegmentRole.COOL_DOWN,
            ),
            draft.segments.map { it.role },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5), draft.segments.map { it.index })
        assertEquals(
            listOf(25, 28, 30, 28, 26, 25),
            draft.profile.map { it.speed.value },
        )
        assertEquals(
            listOf(40, 80, 100, 120, 150, 20),
            draft.profile.map { it.incline.value },
        )
    }

    @Test
    fun `same typed input deterministically reproduces the complete draft`() {
        val input = input(target = VerticalTarget.TWO_THOUSAND_FEET)

        assertEquals(generator.generate(input), VerticalWorkoutGenerator().generate(input))
    }

    @Test
    fun `effective caps are the user and machine intersection inside proposal envelope`() {
        val draft = generate(
            input(
                userMaxSpeed = SpeedTenths(38),
                machineMaxSpeed = SpeedTenths(36),
                userMaxIncline = InclineTenths(140),
                machineMaxIncline = InclineTenths(130),
            ),
        )

        assertEquals(SpeedTenths(36), draft.metadata.effectiveSpeedCap)
        assertEquals(InclineTenths(130), draft.metadata.effectiveInclineCap)
        assertEquals(SpeedTenths(38), draft.metadata.userMaxSpeed)
        assertEquals(SpeedTenths(36), draft.metadata.machineMaxSpeed)
        assertEquals(InclineTenths(140), draft.metadata.userMaxIncline)
        assertEquals(InclineTenths(130), draft.metadata.machineMaxIncline)
        assertTrue(draft.metadata.wasClamped)
        assertEquals(listOf(4), draft.metadata.clampDisclosure.map { it.segmentIndex })
        assertTrue(draft.profile.all { it.speed.value <= 36 && it.incline.value <= 130 })
    }

    @Test
    fun `caps clamp only affected typed segment indexes and disclose the clamp`() {
        val draft = generate(
            input(
                userMaxSpeed = SpeedTenths(27),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(100),
                machineMaxIncline = InclineTenths(150),
            ),
        )

        assertTrue(draft.metadata.wasClamped)
        assertEquals(
            listOf(1, 2, 3, 4),
            draft.metadata.clampDisclosure.map { it.segmentIndex },
        )
        assertEquals(
            listOf(
                VerticalProfileSegmentRole.BASE_CLIMB,
                VerticalProfileSegmentRole.BUILD,
                VerticalProfileSegmentRole.STEEP_BLOCK,
                VerticalProfileSegmentRole.FINISH_PUSH,
            ),
            draft.metadata.clampDisclosure.map { it.role },
        )
        assertTrue(
            draft.metadata.clampDisclosure.all {
                VerticalClampDimension.SPEED in it.dimensions ||
                    VerticalClampDimension.INCLINE in it.dimensions
            },
        )
        assertEquals(25, draft.profile.first().speed.value)
        assertEquals(25, draft.profile.last().speed.value)
        assertEquals(40, draft.profile.first().incline.value)
        assertEquals(20, draft.profile.last().incline.value)
    }

    @Test
    fun `negative and non intersecting caps are explicit rejections`() {
        val cases = listOf(
            input(userMaxSpeed = SpeedTenths(-1)) to VerticalWorkoutGenerationFailure.InvalidSpeedCaps::class.java,
            input(machineMaxSpeed = SpeedTenths(-1)) to VerticalWorkoutGenerationFailure.InvalidSpeedCaps::class.java,
            input(userMaxIncline = InclineTenths(-1)) to VerticalWorkoutGenerationFailure.InvalidInclineCaps::class.java,
            input(machineMaxIncline = InclineTenths(-1)) to VerticalWorkoutGenerationFailure.InvalidInclineCaps::class.java,
            input(userMaxSpeed = SpeedTenths(24)) to VerticalWorkoutGenerationFailure.SpeedCapsDoNotIntersect::class.java,
            input(machineMaxSpeed = SpeedTenths(24)) to VerticalWorkoutGenerationFailure.SpeedCapsDoNotIntersect::class.java,
            input(userMaxIncline = InclineTenths(19)) to VerticalWorkoutGenerationFailure.InclineCapsDoNotIntersect::class.java,
            input(machineMaxIncline = InclineTenths(19)) to VerticalWorkoutGenerationFailure.InclineCapsDoNotIntersect::class.java,
        )

        cases.forEach { (input, expectedFailureType) ->
            val result = generator.generate(input)
            assertTrue("Expected rejection for $input", result is VerticalWorkoutGenerationResult.Rejected)
            val failure = (result as VerticalWorkoutGenerationResult.Rejected).failure
            assertTrue(
                "Expected ${expectedFailureType.simpleName}, got $failure",
                expectedFailureType.isInstance(failure),
            )
        }
    }

    @Test
    fun `compiled representative profile is exactly 3000 seconds for every target`() {
        VerticalTarget.values().forEach { target ->
            val draft = generate(input(target = target))
            val result = WorkoutTimelineCompiler.compile(
                programId = draft.metadata.programId,
                profile = draft.profile,
                settings = PlanSettings(
                    duration = DurationMinutes(50),
                    intensity = PlanIntensity.HIGH,
                    focus = PlanFocus.MORE_INCLINE,
                    maxSpeed = draft.metadata.effectiveSpeedCap,
                    maxIncline = draft.metadata.effectiveInclineCap,
                    adaptToYou = false,
                ),
            )

            val timeline = when (result) {
                is WorkoutTimelineCompileResult.Valid -> result.timeline
                is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
            }
            assertEquals(3_000, timeline.totalDurationSeconds)
            assertEquals(3_000, timeline.segments.last().endSecond)
        }
    }

    private fun generate(input: VerticalWorkoutGeneratorInput): VerticalWorkoutDraft = when (
        val result = generator.generate(input)
    ) {
        is VerticalWorkoutGenerationResult.Generated -> result.draft
        is VerticalWorkoutGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
    }

    private fun input(
        target: VerticalTarget = VerticalTarget.ONE_THOUSAND_FEET,
        userMaxSpeed: SpeedTenths = SpeedTenths(40),
        machineMaxSpeed: SpeedTenths = SpeedTenths(40),
        userMaxIncline: InclineTenths = InclineTenths(150),
        machineMaxIncline: InclineTenths = InclineTenths(150),
    ): VerticalWorkoutGeneratorInput = VerticalWorkoutGeneratorInput(
        target = target,
        userMaxSpeed = userMaxSpeed,
        machineMaxSpeed = machineMaxSpeed,
        userMaxIncline = userMaxIncline,
        machineMaxIncline = machineMaxIncline,
    )
}
