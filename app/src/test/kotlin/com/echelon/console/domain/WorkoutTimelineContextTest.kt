package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimelineContextTest {
    @Test
    fun `vertical draft conversion preserves typed context and exact representative timeline`() {
        val draft = verticalDraft()

        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("VERTICAL"),
            profile = draft.toWorkoutTimelineProfile(),
            settings = verticalSettings(),
        )

        val timeline = assertValid(result)
        assertEquals(
            WorkoutTimelineContext.VerticalPreview(
                programId = ProgramId("VERTICAL"),
                target = draft.metadata.target,
                proposedTimeLimit = draft.metadata.proposedTimeLimit,
                elevationSource = VerticalElevationSource.UNAVAILABLE,
                progressStatus = VerticalProgressStatus.NOT_CALCULATED,
                controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
            ),
            timeline.context,
        )
        assertEquals(3_000, timeline.totalDurationSeconds)
        assertEquals(draft.profile.map { it.name }, timeline.segments.map { it.name })
        assertEquals(
            draft.profile.map { it.speed.value },
            timeline.segments.map { it.targetSpeed.value },
        )
        assertEquals(
            draft.profile.map { it.incline.value },
            timeline.segments.map { it.targetIncline.value },
        )
    }

    @Test
    fun `compiler rejects vertical context whose identity differs from the timeline`() {
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("VERTICAL"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("VERTICAL"),
                context = WorkoutTimelineContext.VerticalPreview(
                    programId = ProgramId("OTHER"),
                    target = VerticalTarget.FIVE_HUNDRED_FEET,
                    proposedTimeLimit = VerticalTimeLimitProposal(45, VerticalTimeLimitStatus.PROPOSED),
                    elevationSource = VerticalElevationSource.UNAVAILABLE,
                    progressStatus = VerticalProgressStatus.NOT_CALCULATED,
                    controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
                ),
                segments = listOf(
                    AnnotatedWorkoutProfileSegment(
                        summary = ProgramSegmentSummary(
                            name = "WARM UP",
                            duration = DurationMinutes(1),
                            speed = SpeedTenths(25),
                            incline = InclineTenths(20),
                        ),
                        annotation = WorkoutTimelineAnnotation.Unannotated,
                    ),
                ),
            ),
            settings = verticalSettings().copy(duration = DurationMinutes(1)),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = ProgramId("VERTICAL"),
                    actual = ProgramId("OTHER"),
                ),
            ),
            result,
        )
    }

    @Test
    fun `generic five k and surprise profiles retain no vertical context`() {
        val generic = WorkoutTimelineCompiler.compile(
            programId = ProgramId("FAT_BURN"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("FAT_BURN"),
                segments = listOf(
                    AnnotatedWorkoutProfileSegment(
                        summary = ProgramSegmentSummary(
                            name = "WARM UP",
                            duration = DurationMinutes(1),
                            speed = SpeedTenths(25),
                            incline = InclineTenths(0),
                        ),
                        annotation = WorkoutTimelineAnnotation.Unannotated,
                    ),
                ),
            ),
            settings = verticalSettings().copy(
                duration = DurationMinutes(1),
                maxIncline = InclineTenths(0),
            ),
        )

        val fiveK = WorkoutTimelineCompiler.compile(
            programId = ProgramId("5K_READY"),
            profile = fiveKDraft().toWorkoutTimelineProfile(),
            settings = PlanSettings(
                duration = DurationMinutes(30),
                intensity = PlanIntensity.MEDIUM,
                focus = PlanFocus.BALANCED,
                maxSpeed = SpeedTenths(60),
                maxIncline = InclineTenths(60),
                adaptToYou = false,
            ),
        )
        val surprise = WorkoutTimelineCompiler.compile(
            programId = ProgramId("SURPRISE_ME"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("SURPRISE_ME"),
                segments = listOf(
                    AnnotatedWorkoutProfileSegment(
                        summary = ProgramSegmentSummary(
                            name = "WARM UP",
                            duration = DurationMinutes(1),
                            speed = SpeedTenths(28),
                            incline = InclineTenths(0),
                        ),
                        annotation = WorkoutTimelineAnnotation.Unannotated,
                    ),
                ),
            ),
            settings = verticalSettings().copy(
                duration = DurationMinutes(1),
                maxSpeed = SpeedTenths(60),
                maxIncline = InclineTenths(60),
            ),
        )

        assertEquals(WorkoutTimelineContext.None, assertValid(generic).context)
        assertEquals(WorkoutTimelineContext.None, assertValid(fiveK).context)
        assertEquals(WorkoutTimelineContext.None, assertValid(surprise).context)
        assertTrue(assertValid(fiveK).segments.any { it.annotation is WorkoutTimelineAnnotation.Run })
    }

    private fun verticalDraft(): VerticalWorkoutDraft = when (
        val result = VerticalWorkoutGenerator().generate(
            VerticalWorkoutGeneratorInput(
                target = VerticalTarget.VERTICAL_MILE,
                userMaxSpeed = SpeedTenths(40),
                machineMaxSpeed = SpeedTenths(40),
                userMaxIncline = InclineTenths(150),
                machineMaxIncline = InclineTenths(150),
            ),
        )
    ) {
        is VerticalWorkoutGenerationResult.Generated -> result.draft
        is VerticalWorkoutGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
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
                machineMaxSpeed = SpeedTenths(60),
                userMaxIncline = InclineTenths(60),
                machineMaxIncline = InclineTenths(60),
            ),
        )
    ) {
        is FiveKReadySessionGenerationResult.Generated -> result.draft
        is FiveKReadySessionGenerationResult.Rejected -> error("Expected generated draft, got ${result.failure}")
    }

    private fun verticalSettings(): PlanSettings = PlanSettings(
        duration = DurationMinutes(50),
        intensity = PlanIntensity.HIGH,
        focus = PlanFocus.MORE_INCLINE,
        maxSpeed = SpeedTenths(40),
        maxIncline = InclineTenths(150),
        adaptToYou = false,
    )

    private fun assertValid(result: WorkoutTimelineCompileResult): WorkoutTimeline = when (result) {
        is WorkoutTimelineCompileResult.Valid -> result.timeline
        is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
    }
}
