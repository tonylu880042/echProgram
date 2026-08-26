package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimelineAnnotationTest {
    @Test
    fun `5K draft compiles typed run walk annotations into timeline`() {
        val draft = fiveKDraft()
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("5K_READY"),
            profile = draft.toWorkoutTimelineProfile(),
            settings = PlanSettings(
                duration = DurationMinutes(30),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = SpeedTenths(60),
                maxIncline = InclineTenths(60),
                adaptToYou = false,
            ),
        )

        val timeline = when (result) {
            is WorkoutTimelineCompileResult.Valid -> result.timeline
            is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
        }
        assertEquals(
            listOf(
                WorkoutTimelineAnnotation.WarmUpWalk,
                WorkoutTimelineAnnotation.Run(1, 3),
                WorkoutTimelineAnnotation.WalkRecovery,
                WorkoutTimelineAnnotation.Run(2, 3),
                WorkoutTimelineAnnotation.WalkRecovery,
                WorkoutTimelineAnnotation.Run(3, 3),
                WorkoutTimelineAnnotation.EasyWalk,
                WorkoutTimelineAnnotation.CoolDown,
            ),
            timeline.segments.map { it.annotation },
        )
    }

    @Test
    fun `compiler rejects annotated profile with a different program identity`() {
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("5K_READY"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("OTHER"),
                segments = listOf(
                    AnnotatedWorkoutProfileSegment(
                        summary = ProgramSegmentSummary(
                            name = "WARM UP WALK",
                            duration = DurationMinutes(1),
                            speed = SpeedTenths(30),
                            incline = InclineTenths(0),
                        ),
                        annotation = WorkoutTimelineAnnotation.WarmUpWalk,
                    ),
                ),
            ),
            settings = settings(duration = 1),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ProfileProgramIdMismatch(
                    expected = ProgramId("5K_READY"),
                    actual = ProgramId("OTHER"),
                ),
            ),
            result,
        )
    }

    @Test
    fun `compiler rejects run annotation count and ordinal drift`() {
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("5K_READY"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("5K_READY"),
                segments = listOf(
                    AnnotatedWorkoutProfileSegment(
                        summary = ProgramSegmentSummary(
                            name = "RUN",
                            duration = DurationMinutes(1),
                            speed = SpeedTenths(40),
                            incline = InclineTenths(0),
                        ),
                        annotation = WorkoutTimelineAnnotation.Run(2, 3),
                    ),
                ),
            ),
            settings = settings(duration = 1),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.AnnotationCountMismatch(
                    expectedRunCount = 3,
                    actualRunCount = 1,
                ),
            ),
            result,
        )
    }

    @Test
    fun `compiler returns invalid for duplicate run ordinals without throwing`() {
        val result = compileRunAnnotations(
            WorkoutTimelineAnnotation.Run(1, 3),
            WorkoutTimelineAnnotation.Run(1, 3),
            WorkoutTimelineAnnotation.Run(3, 3),
        )

        assertInvalidRunAnnotation(result)
    }

    @Test
    fun `compiler returns invalid for out of order run ordinals without throwing`() {
        val result = compileRunAnnotations(
            WorkoutTimelineAnnotation.Run(1, 3),
            WorkoutTimelineAnnotation.Run(3, 3),
            WorkoutTimelineAnnotation.Run(2, 3),
        )

        assertInvalidRunAnnotation(result)
    }

    @Test
    fun `compiler returns invalid for zero or negative run totals and ordinals`() {
        listOf(
            WorkoutTimelineAnnotation.Run(0, 3),
            WorkoutTimelineAnnotation.Run(-1, 3),
            WorkoutTimelineAnnotation.Run(1, 0),
            WorkoutTimelineAnnotation.Run(1, -1),
        ).forEach { malformed ->
            val result = compileRunAnnotations(malformed)
            assertInvalidRunAnnotation(result)
        }
    }

    private fun compileRunAnnotations(
        vararg annotations: WorkoutTimelineAnnotation.Run,
    ): WorkoutTimelineCompileResult = WorkoutTimelineCompiler.compile(
        programId = ProgramId("5K_READY"),
        profile = AnnotatedWorkoutProfile(
            programId = ProgramId("5K_READY"),
            segments = annotations.mapIndexed { index, annotation ->
                AnnotatedWorkoutProfileSegment(
                    summary = ProgramSegmentSummary(
                        name = "RUN ${index + 1}",
                        duration = DurationMinutes(1),
                        speed = SpeedTenths(40),
                        incline = InclineTenths(0),
                    ),
                    annotation = annotation,
                )
            },
        ),
        settings = settings(duration = annotations.size),
    )

    private fun assertInvalidRunAnnotation(result: WorkoutTimelineCompileResult) {
        require(result is WorkoutTimelineCompileResult.Invalid) {
            "Expected malformed annotation to be invalid, got $result"
        }
        assertTrue(result.error is WorkoutTimelineCompileError.InvalidRunAnnotation)
    }

    private fun fiveKDraft(): FiveKReadySessionDraft = when (
        val result = FiveKReadySessionGenerator().generate(
            FiveKReadySessionGeneratorInput(
                durationMinutes = 30,
                baselinePace = FiveKReadyBaselinePace(
                    speed = SpeedTenths(40),
                    source = FiveKReadyBaselineSource.USER_ENTERED,
                ),
                userMaxSpeed = SpeedTenths(60),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is FiveKReadySessionGenerationResult.Generated -> result.draft
        is FiveKReadySessionGenerationResult.Rejected -> error("Expected generated draft")
    }

    private fun settings(duration: Int): PlanSettings = PlanSettings(
        duration = DurationMinutes(duration),
        intensity = PlanIntensity.MEDIUM,
        focus = PlanFocus.BALANCED,
        maxSpeed = SpeedTenths(60),
        maxIncline = InclineTenths(60),
        adaptToYou = false,
    )
}
