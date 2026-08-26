package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurpriseWorkoutGeneratorTest {
    private val generator = SurpriseWorkoutGenerator()

    @Test
    fun `every supported duration and effort produces an exact safe preview`() {
        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            SurpriseWorkoutEffort.values().forEach { effort ->
                val draft = generate(
                    input(
                        durationMinutes = durationMinutes,
                        effort = effort,
                    ),
                )

                assertEquals(durationMinutes, draft.metadata.durationMinutes)
                assertEquals(effort, draft.metadata.effort)
                assertEquals(
                    durationMinutes,
                    draft.profile.sumOf { it.duration.value },
                )
                assertTrue(draft.profile.any { it.name == "WARM UP" })
                assertTrue(draft.profile.any { it.name == "COOL DOWN" })
                assertTrue(
                    draft.profile.all {
                        it.speed.value in GLOBAL_SPEED_RANGE &&
                            it.incline.value in GLOBAL_INCLINE_RANGE &&
                            it.speed.value <= draft.effectiveSpeedCap.value &&
                            it.incline.value <= draft.effectiveInclineCap.value
                    },
                )
                assertEquals(
                    SurpriseWorkoutDraftControlStatus.PREVIEW_ONLY,
                    draft.controlStatus,
                )

                val timeline = compile(draft)
                assertEquals(durationMinutes * 60, timeline.totalDurationSeconds)
                assertEquals(durationMinutes * 60, timeline.segments.last().endSecond)
            }
        }
    }

    @Test
    fun `same versioned input reproduces the same draft`() {
        val input = input(
            durationMinutes = 20,
            effort = SurpriseWorkoutEffort.SWEAT,
            regenerationIndex = 2,
        )

        val first = generator.generate(input)
        val second = SurpriseWorkoutGenerator().generate(input)

        assertEquals(first, second)
        val draft = generated(first)
        assertEquals("SURPRISE_ME", draft.metadata.programId.value)
        assertEquals(input.generatorVersion, draft.metadata.generatorVersion)
        assertEquals(input.regenerationIndex, draft.metadata.regenerationIndex)
        assertTrue(draft.metadata.stableSeed != 0L)
    }

    @Test
    fun `regeneration index changes the concrete profile as well as the seed`() {
        val first = generate(
            input(
                durationMinutes = 30,
                effort = SurpriseWorkoutEffort.BURN,
                regenerationIndex = 0,
            ),
        )
        val regenerated = generate(
            input(
                durationMinutes = 30,
                effort = SurpriseWorkoutEffort.BURN,
                regenerationIndex = 1,
            ),
        )

        assertNotEquals(first.metadata.stableSeed, regenerated.metadata.stableSeed)
        assertNotEquals(first.profile, regenerated.profile)
    }

    @Test
    fun `effective caps are the intersection of user and machine limits`() {
        val draft = generate(
            input(
                durationMinutes = 20,
                effort = SurpriseWorkoutEffort.HARD,
                userMaxSpeed = SpeedTenths(52),
                machineMaxSpeed = SpeedTenths(47),
                userMaxIncline = InclineTenths(65),
                machineMaxIncline = InclineTenths(34),
            ),
        )

        assertEquals(SpeedTenths(47), draft.effectiveSpeedCap)
        assertEquals(InclineTenths(34), draft.effectiveInclineCap)
        assertTrue(draft.profile.all { it.speed.value <= 47 })
        assertTrue(draft.profile.all { it.incline.value <= 34 })
    }

    @Test
    fun `easy never emits sprint or high incline and hard includes recovery`() {
        val easy = generate(
            input(
                durationMinutes = 45,
                effort = SurpriseWorkoutEffort.EASY,
                regenerationIndex = 3,
            ),
        )
        val hard = generate(
            input(
                durationMinutes = 45,
                effort = SurpriseWorkoutEffort.HARD,
                regenerationIndex = 3,
            ),
        )

        assertTrue(easy.profile.all { it.speed.value <= EASY_MAX_SPEED_TENTHS })
        assertTrue(easy.profile.all { it.incline.value <= EASY_MAX_INCLINE_TENTHS })
        assertTrue(hard.profile.any { it.name.startsWith("RECOVERY") })
    }

    @Test
    fun `adjacent targets follow the conservative baseline ramp proposal`() {
        val draft = generate(
            input(
                durationMinutes = 45,
                effort = SurpriseWorkoutEffort.HARD,
                regenerationIndex = 4,
            ),
        )

        draft.profile.zipWithNext().forEach { (previous, next) ->
            assertTrue(
                kotlin.math.abs(next.speed.value - previous.speed.value) <=
                    SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_SPEED_JUMP_TENTHS,
            )
            assertTrue(
                kotlin.math.abs(next.incline.value - previous.incline.value) <=
                    SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_INCLINE_JUMP_TENTHS,
            )
        }
    }

    @Test
    fun `invalid input returns explicit failures without throwing`() {
        val cases = listOf(
            input(durationMinutes = 15) to
                SurpriseWorkoutGenerationFailure.UnsupportedDuration::class.java,
            input(regenerationIndex = -1) to
                SurpriseWorkoutGenerationFailure.NegativeRegenerationIndex::class.java,
            input(userProfileRevision = "  ") to
                SurpriseWorkoutGenerationFailure.BlankUserProfileRevision::class.java,
            input(generatorVersion = "v 1") to
                SurpriseWorkoutGenerationFailure.InvalidGeneratorVersion::class.java,
            input(userMaxSpeed = SpeedTenths(24)) to
                SurpriseWorkoutGenerationFailure.CapsDoNotIntersect::class.java,
            input(machineMaxIncline = InclineTenths(-1)) to
                SurpriseWorkoutGenerationFailure.CapsDoNotIntersect::class.java,
        )

        cases.forEach { (input, expectedFailureType) ->
            val result = generator.generate(input)
            assertTrue("Expected rejection for $input", result is SurpriseWorkoutGenerationResult.Rejected)
            val failure = (result as SurpriseWorkoutGenerationResult.Rejected).failure
            assertTrue(
                "Expected ${expectedFailureType.simpleName}, got $failure",
                expectedFailureType.isInstance(failure),
            )
        }
    }

    @Test
    fun `rejects a syntactically valid but unsupported generator version`() {
        val result = generator.generate(input(generatorVersion = "v2"))

        assertEquals(
            SurpriseWorkoutGenerationResult.Rejected(
                SurpriseWorkoutGenerationFailure.InvalidGeneratorVersion("v2"),
            ),
            result,
        )
    }

    @Test
    fun `hard recovery and cool down never increase targets and each lowers one target`() {
        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            (0..3).forEach { regenerationIndex ->
                val draft = generate(
                    input(
                        durationMinutes = durationMinutes,
                        effort = SurpriseWorkoutEffort.HARD,
                        regenerationIndex = regenerationIndex,
                    ),
                )
                val recoveryIndex = draft.profile.indexOfFirst { it.name.startsWith("RECOVERY") }
                val recoveryBefore = draft.profile[recoveryIndex - 1]
                val recovery = draft.profile[recoveryIndex]
                assertTrue(recovery.speed.value <= recoveryBefore.speed.value)
                assertTrue(recovery.incline.value <= recoveryBefore.incline.value)
                assertTrue(
                    recovery.speed.value < recoveryBefore.speed.value ||
                        recovery.incline.value < recoveryBefore.incline.value,
                )

                val coolDownBefore = draft.profile[draft.profile.lastIndex - 1]
                val coolDown = draft.profile.last()
                assertTrue(coolDown.speed.value <= coolDownBefore.speed.value)
                assertTrue(coolDown.incline.value <= coolDownBefore.incline.value)
                assertTrue(
                    coolDown.speed.value < coolDownBefore.speed.value ||
                        coolDown.incline.value < coolDownBefore.incline.value,
                )
            }
        }
    }

    @Test
    fun `every effort starts inside the conservative warm-up baseline`() {
        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            (0..3).forEach { regenerationIndex ->
                SurpriseWorkoutEffort.values().forEach { effort ->
                    val warmUp = generate(
                        input(
                            durationMinutes = durationMinutes,
                            effort = effort,
                            regenerationIndex = regenerationIndex,
                        ),
                    ).profile.first()

                    assertEquals("WARM UP", warmUp.name)
                    assertTrue(warmUp.speed.value <= WARM_UP_MAX_SPEED_TENTHS)
                    assertTrue(warmUp.incline.value <= WARM_UP_MAX_INCLINE_TENTHS)
                }
            }
        }
    }

    @Test
    fun `hard recovery follows an active block and precedes final push`() {
        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            (0..3).forEach { regenerationIndex ->
                val profile = generate(
                    input(
                        durationMinutes = durationMinutes,
                        effort = SurpriseWorkoutEffort.HARD,
                        regenerationIndex = regenerationIndex,
                    ),
                ).profile
                val recoveryIndex = profile.indexOfFirst { it.name.startsWith("RECOVERY") }

                assertTrue(recoveryIndex >= 2)
                assertTrue(
                    profile.subList(1, recoveryIndex).any {
                        it.name.startsWith("BUILD") || it.name == "FINAL PUSH"
                    },
                )
                assertTrue(profile.drop(recoveryIndex + 1).any { it.name == "FINAL PUSH" })
            }
        }
    }

    @Test
    fun `consecutive regeneration indexes differ for every effort and duration`() {
        val caps = listOf(
            SpeedTenths(80) to InclineTenths(100),
            SpeedTenths(26) to InclineTenths(1),
            SpeedTenths(25) to InclineTenths(0),
        )

        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            SurpriseWorkoutEffort.values().forEach { effort ->
                caps.forEach { (speedCap, inclineCap) ->
                    val first = generator.generate(
                        input(
                            durationMinutes = durationMinutes,
                            effort = effort,
                            regenerationIndex = 0,
                            userMaxSpeed = speedCap,
                            machineMaxSpeed = speedCap,
                            userMaxIncline = inclineCap,
                            machineMaxIncline = inclineCap,
                        ),
                    )
                    val second = generator.generate(
                        input(
                            durationMinutes = durationMinutes,
                            effort = effort,
                            regenerationIndex = 1,
                            userMaxSpeed = speedCap,
                            machineMaxSpeed = speedCap,
                            userMaxIncline = inclineCap,
                            machineMaxIncline = inclineCap,
                        ),
                    )

                    assertConsecutiveRegenerationIsMeaningful(first, second)
                }
            }
        }
    }

    @Test
    fun `effort workload follows the baseline ordering`() {
        SUPPORTED_DURATIONS.forEach { durationMinutes ->
            val scores = SurpriseWorkoutEffort.values().associateWith { effort ->
                val draft = generate(
                    input(
                        durationMinutes = durationMinutes,
                        effort = effort,
                    ),
                )
                draft.profile
                    .filterNot { it.name == "WARM UP" || it.name == "COOL DOWN" }
                    .filterNot { it.name.startsWith("RECOVERY") }
                    .map { segment ->
                        // Baseline proposal score: tenths-mph + tenths-percent.
                        segment.speed.value + segment.incline.value
                    }
                    .average()
            }

            assertTrue(
                "Expected HARD > BURN, got $scores",
                scores.getValue(SurpriseWorkoutEffort.HARD) > scores.getValue(SurpriseWorkoutEffort.BURN),
            )
            assertTrue(
                "Expected BURN > SWEAT, got $scores",
                scores.getValue(SurpriseWorkoutEffort.BURN) > scores.getValue(SurpriseWorkoutEffort.SWEAT),
            )
            assertTrue(
                "Expected SWEAT > EASY, got $scores",
                scores.getValue(SurpriseWorkoutEffort.SWEAT) > scores.getValue(SurpriseWorkoutEffort.EASY),
            )
        }
    }

    private fun generate(input: SurpriseWorkoutGeneratorInput): SurpriseWorkoutDraft =
        generated(generator.generate(input))

    private fun generated(result: SurpriseWorkoutGenerationResult): SurpriseWorkoutDraft =
        when (result) {
            is SurpriseWorkoutGenerationResult.Generated -> result.draft
            is SurpriseWorkoutGenerationResult.Rejected ->
                error("Expected generated draft, got ${result.failure}")
        }

    private fun assertConsecutiveRegenerationIsMeaningful(
        first: SurpriseWorkoutGenerationResult,
        second: SurpriseWorkoutGenerationResult,
    ) {
        if (first is SurpriseWorkoutGenerationResult.Generated &&
            second is SurpriseWorkoutGenerationResult.Generated
        ) {
            assertNotEquals(first.draft.profile, second.draft.profile)
        }
        if (first is SurpriseWorkoutGenerationResult.Rejected) {
            assertTrue(first.failure is SurpriseWorkoutGenerationFailure.ConstraintsUnsatisfied)
        }
        if (second is SurpriseWorkoutGenerationResult.Rejected) {
            assertTrue(second.failure is SurpriseWorkoutGenerationFailure.ConstraintsUnsatisfied)
        }
    }

    private fun compile(draft: SurpriseWorkoutDraft): WorkoutTimeline {
        val detail = ProgramDetail(
            programId = draft.metadata.programId,
            title = "SURPRISE ME",
            promise = "Deterministic preview",
            defaultSettings = PlanSettings(
                duration = DurationMinutes(draft.metadata.durationMinutes),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = draft.effectiveSpeedCap,
                maxIncline = draft.effectiveInclineCap,
                adaptToYou = false,
            ),
            speedRange = SpeedRange(SpeedTenths(25), draft.effectiveSpeedCap),
            inclineRange = InclineRange(InclineTenths(0), draft.effectiveInclineCap),
            profile = draft.profile,
            previewMode = ProgramPreviewMode.GENERATED_PREVIEW,
        )

        return when (val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)) {
            is WorkoutTimelineCompileResult.Valid -> result.timeline
            is WorkoutTimelineCompileResult.Invalid ->
                error("Expected generated profile to compile, got ${result.error}")
        }
    }

    private fun input(
        durationMinutes: Int = 20,
        effort: SurpriseWorkoutEffort = SurpriseWorkoutEffort.SWEAT,
        userProfileRevision: String = "profile-r1",
        regenerationIndex: Int = 0,
        generatorVersion: String = "v1",
        userMaxSpeed: SpeedTenths = SpeedTenths(80),
        machineMaxSpeed: SpeedTenths = SpeedTenths(80),
        userMaxIncline: InclineTenths = InclineTenths(100),
        machineMaxIncline: InclineTenths = InclineTenths(100),
    ): SurpriseWorkoutGeneratorInput = SurpriseWorkoutGeneratorInput(
        durationMinutes = durationMinutes,
        effort = effort,
        userProfileRevision = userProfileRevision,
        regenerationIndex = regenerationIndex,
        generatorVersion = generatorVersion,
        userMaxSpeed = userMaxSpeed,
        machineMaxSpeed = machineMaxSpeed,
        userMaxIncline = userMaxIncline,
        machineMaxIncline = machineMaxIncline,
    )

    private companion object {
        val SUPPORTED_DURATIONS = listOf(10, 20, 30, 45)
        val GLOBAL_SPEED_RANGE = 25..80
        val GLOBAL_INCLINE_RANGE = 0..100
        const val EASY_MAX_SPEED_TENTHS = 45
        const val EASY_MAX_INCLINE_TENTHS = 20
        const val WARM_UP_MAX_SPEED_TENTHS = SurpriseWorkoutWarmUpBaselineProposal.MAX_SPEED_TENTHS
        const val WARM_UP_MAX_INCLINE_TENTHS = SurpriseWorkoutWarmUpBaselineProposal.MAX_INCLINE_TENTHS
    }
}
