package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveKReadySessionGeneratorTest {
    private val generator = FiveKReadySessionGenerator()

    @Test
    fun `unsupported duration is explicitly rejected`() {
        val result = generator.generate(input(durationMinutes = 25))

        assertEquals(
            FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.UnsupportedDuration(25),
            ),
            result,
        )
    }

    @Test
    fun `missing baseline is explicitly rejected`() {
        val result = generator.generate(input(baselinePace = null))

        assertEquals(
            FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineRequired,
            ),
            result,
        )
    }

    @Test
    fun `only typed user entered baseline is accepted`() {
        val result = generator.generate(
            input(
                baselinePace = FiveKReadyBaselinePace(
                    speed = SpeedTenths(40),
                    source = FiveKReadyBaselineSource.HISTORY,
                ),
            ),
        )

        assertEquals(
            FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineSourceNotUserEntered(
                    FiveKReadyBaselineSource.HISTORY,
                ),
            ),
            result,
        )
    }

    @Test
    fun `thirty minute proposal matches the documented blocks exactly`() {
        val draft = generate(input(durationMinutes = 30, baselineSpeed = 40))

        assertEquals(ProgramId("5K_READY"), draft.metadata.programId)
        assertEquals(FiveKReadyDraftMode.SINGLE_SESSION_PREVIEW, draft.metadata.mode)
        assertEquals(FiveKReadySessionControlStatus.PREVIEW_ONLY, draft.controlStatus)
        assertEquals(30, draft.metadata.durationMinutes)
        assertEquals(FiveKReadyBaselineSource.USER_ENTERED, draft.metadata.baselineSource)
        assertEquals(FiveKReadyBaselineSource.USER_ENTERED, draft.metadata.baselinePace.source)
        assertEquals(
            listOf(
                FiveKReadySegmentRole.WARM_UP_WALK,
                FiveKReadySegmentRole.RUN,
                FiveKReadySegmentRole.WALK_RECOVERY,
                FiveKReadySegmentRole.RUN,
                FiveKReadySegmentRole.WALK_RECOVERY,
                FiveKReadySegmentRole.RUN,
                FiveKReadySegmentRole.EASY_WALK,
                FiveKReadySegmentRole.COOL_DOWN,
            ),
            draft.segments.map { it.role },
        )
        assertEquals(listOf(null, 1, null, 2, null, 3, null, null), draft.segments.map { it.runOrdinal })
        assertEquals(listOf(null, 3, null, 3, null, 3, null, null), draft.segments.map { it.totalRuns })
        assertEquals(
            listOf("WARM UP WALK", "EASY RUN", "WALK RECOVERY", "STEADY RUN", "WALK RECOVERY", "STEADY RUN", "EASY WALK", "COOL DOWN"),
            draft.profile.map { it.name },
        )
        assertEquals(listOf(5, 5, 3, 5, 2, 5, 2, 3), draft.profile.map { it.duration.value })
        assertEquals(listOf(30, 40, 35, 43, 35, 43, 32, 28), draft.profile.map { it.speed.value })
        assertEquals(listOf(10, 10, 10, 20, 10, 20, 10, 0), draft.profile.map { it.incline.value })
        assertEquals(FiveKReadyRunWalkSummary(runMinutes = 15, walkMinutes = 15), draft.runWalkSummary)
        assertFalse(draft.metadata.wasClamped)
        assertEquals(null, draft.metadata.clampSummary)
    }

    @Test
    fun `all supported durations preserve exact time and run walk structure`() {
        val expectedDurations = mapOf(
            20 to listOf(3, 3, 2, 3, 2, 3, 1, 3),
            30 to listOf(5, 5, 3, 5, 2, 5, 2, 3),
            40 to listOf(5, 7, 3, 7, 3, 7, 3, 5),
            60 to listOf(8, 10, 4, 10, 4, 10, 4, 10),
        )

        expectedDurations.forEach { (durationMinutes, durations) ->
            val draft = generate(input(durationMinutes = durationMinutes, baselineSpeed = 40))

            assertEquals(durationMinutes, draft.profile.sumOf { it.duration.value })
            assertEquals(durations, draft.profile.map { it.duration.value })
            assertEquals(durationMinutes, draft.runWalkSummary.runMinutes + draft.runWalkSummary.walkMinutes)
            assertTrue(draft.profile.any { it.name == "WARM UP WALK" })
            assertTrue(draft.profile.any { it.name == "WALK RECOVERY" })
            assertTrue(draft.profile.any { it.name == "COOL DOWN" })
            assertTrue(draft.profile.any { it.name.endsWith("RUN") })
        }
    }

    @Test
    fun `baseline envelope and effective caps are explicit failures`() {
        val cases = listOf(
            input(baselineSpeed = 24) to FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope(
                SpeedTenths(24),
            ),
            input(baselineSpeed = 27) to FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope(
                SpeedTenths(27),
            ),
            input(baselineSpeed = 61) to FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope(
                SpeedTenths(61),
            ),
            input(baselineSpeed = 50, userMaxSpeed = 49) to
                FiveKReadySessionGenerationFailure.BaselineExceedsEffectiveSpeedCap(
                    baseline = SpeedTenths(50),
                    effectiveCap = SpeedTenths(49),
                ),
            input(baselineSpeed = 50, machineMaxSpeed = 49) to
                FiveKReadySessionGenerationFailure.BaselineExceedsEffectiveSpeedCap(
                    baseline = SpeedTenths(50),
                    effectiveCap = SpeedTenths(49),
                ),
            input(baselineSpeed = 28, userMaxSpeed = 27) to
                FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect(
                    userMaximum = SpeedTenths(27),
                    machineMaximum = SpeedTenths(60),
                    globalMinimum = SpeedTenths(28),
                ),
        )

        cases.forEach { (input, expected) ->
            assertEquals(FiveKReadySessionGenerationResult.Rejected(expected), generator.generate(input))
        }
    }

    @Test
    fun `negative and non intersecting caps are explicit failures`() {
        val cases = listOf(
            input(userMaxSpeed = -1) to FiveKReadySessionGenerationFailure.InvalidSpeedCap::class.java,
            input(machineMaxSpeed = -1) to FiveKReadySessionGenerationFailure.InvalidSpeedCap::class.java,
            input(userMaxIncline = -1) to FiveKReadySessionGenerationFailure.InvalidInclineCap::class.java,
            input(machineMaxIncline = -1) to FiveKReadySessionGenerationFailure.InvalidInclineCap::class.java,
            input(userMaxSpeed = 24) to FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect::class.java,
            input(machineMaxSpeed = 24) to FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect::class.java,
        )

        cases.forEach { (input, expectedType) ->
            val result = generator.generate(input)

            assertTrue("Expected rejection for $input", result is FiveKReadySessionGenerationResult.Rejected)
            val failure = (result as FiveKReadySessionGenerationResult.Rejected).failure
            assertEquals(expectedType, failure::class.java)
        }
    }

    @Test
    fun `non baseline targets clamp and disclose the affected profile`() {
        val draft = generate(
            input(
                baselineSpeed = 40,
                userMaxSpeed = 42,
                machineMaxSpeed = 42,
            ),
        )

        assertTrue(draft.metadata.wasClamped)
        assertNotNull(draft.metadata.clampSummary)
        assertTrue(draft.metadata.clampSummary!!.speedSegmentNames.contains("STEADY RUN"))
        assertEquals(42, draft.profile[3].speed.value)
        assertEquals(40, draft.profile[1].speed.value)
        assertTrue(draft.profile.all { it.speed.value in 25..42 })
        assertTrue(draft.profile.all { it.incline.value in 0..60 })
    }

    @Test
    fun `low accepted baseline keeps every walk target below the run pace`() {
        val draft = generate(input(baselineSpeed = 28))

        val walkIndexes = listOf(0, 2, 4, 6, 7)
        assertTrue(walkIndexes.all { draft.profile[it].speed.value < 28 })
        assertTrue(walkIndexes.all { draft.profile[it].speed.value in 25..60 })
    }

    @Test
    fun `same typed input is deterministic`() {
        val input = input(durationMinutes = 40, baselineSpeed = 43)

        assertEquals(generator.generate(input), generator.generate(input))
    }

    @Test
    fun `draft compiles to a timeline with exact seconds`() {
        val draft = generate(input(durationMinutes = 30, baselineSpeed = 40))

        val result = WorkoutTimelineCompiler.compile(
            programId = draft.metadata.programId,
            profile = draft.profile,
            settings = PlanSettings(
                duration = DurationMinutes(draft.metadata.durationMinutes),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = draft.effectiveSpeedCap,
                maxIncline = draft.effectiveInclineCap,
                adaptToYou = false,
            ),
        )

        val timeline = when (result) {
            is WorkoutTimelineCompileResult.Valid -> result.timeline
            is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
        }
        assertEquals(1_800, timeline.totalDurationSeconds)
        assertEquals(1_800, timeline.segments.last().endSecond)
    }

    private fun generate(input: FiveKReadySessionGeneratorInput): FiveKReadySessionDraft = when (
        val result = generator.generate(input)
    ) {
        is FiveKReadySessionGenerationResult.Generated -> result.draft
        is FiveKReadySessionGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
    }

    private fun input(
        durationMinutes: Int = 30,
        baselineSpeed: Int = 40,
        baselinePace: FiveKReadyBaselinePace? = FiveKReadyBaselinePace(
            speed = SpeedTenths(baselineSpeed),
            source = FiveKReadyBaselineSource.USER_ENTERED,
        ),
        userMaxSpeed: Int = 60,
        machineMaxSpeed: Int = 60,
        userMaxIncline: Int = 60,
        machineMaxIncline: Int = 60,
    ): FiveKReadySessionGeneratorInput = FiveKReadySessionGeneratorInput(
        durationMinutes = durationMinutes,
        baselinePace = baselinePace,
        userMaxSpeed = SpeedTenths(userMaxSpeed),
        machineMaxSpeed = SpeedTenths(machineMaxSpeed),
        userMaxIncline = InclineTenths(userMaxIncline),
        machineMaxIncline = InclineTenths(machineMaxIncline),
    )
}
