package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class Zone2WorkoutTimelineContextTest {
    @Test
    fun `zone 2 context is retained with reviewed five phase profile`() {
        val target = target()
        val context = context(target)
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("ZONE_2"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("ZONE_2"),
                context = context,
                segments = reviewedProfile().map { summary ->
                    AnnotatedWorkoutProfileSegment(
                        summary = summary,
                        annotation = WorkoutTimelineAnnotation.Unannotated,
                    )
                },
            ),
            settings = settings(),
        )

        val timeline = assertValid(result)
        assertEquals(context, timeline.context)
        assertEquals(1_800, timeline.totalDurationSeconds)
        assertEquals(
            listOf("Warm Up", "Settle", "Maintain", "Check", "Cool Down"),
            timeline.segments.map { it.name },
        )
        assertEquals(listOf(25, 30, 32, 32, 25), timeline.segments.map { it.targetSpeed.value })
        assertEquals(listOf(10, 20, 30, 30, 10), timeline.segments.map { it.targetIncline.value })
    }

    @Test
    fun `compiler rejects zone 2 context whose identity differs from timeline`() {
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("ZONE_2"),
            profile = AnnotatedWorkoutProfile(
                programId = ProgramId("ZONE_2"),
                context = context(target(), programId = ProgramId("OTHER")),
                segments = reviewedProfile().map { summary ->
                    AnnotatedWorkoutProfileSegment(
                        summary = summary,
                        annotation = WorkoutTimelineAnnotation.Unannotated,
                    )
                },
            ),
            settings = settings(),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = ProgramId("ZONE_2"),
                    actual = ProgramId("OTHER"),
                ),
            ),
            result,
        )
    }

    @Test
    fun `compiler rejects zone 2 context when duration or caps differ from plan`() {
        val target = target()
        val mismatches = listOf(
            WorkoutTimelineCompileError.ContextDurationMismatch(
                expected = DurationMinutes(30),
                actual = DurationMinutes(20),
            ) to context(target, duration = DurationMinutes(20)),
            WorkoutTimelineCompileError.ContextMaxSpeedMismatch(
                expected = SpeedTenths(50),
                actual = SpeedTenths(45),
            ) to context(target, maxSpeed = SpeedTenths(45)),
            WorkoutTimelineCompileError.ContextMaxInclineMismatch(
                expected = InclineTenths(80),
                actual = InclineTenths(60),
            ) to context(target, maxIncline = InclineTenths(60)),
        )

        mismatches.forEach { (expectedError, mismatchedContext) ->
            val result = WorkoutTimelineCompiler.compile(
                programId = ProgramId("ZONE_2"),
                profile = AnnotatedWorkoutProfile(
                    programId = ProgramId("ZONE_2"),
                    context = mismatchedContext,
                    segments = reviewedProfile().map { summary ->
                        AnnotatedWorkoutProfileSegment(
                            summary = summary,
                            annotation = WorkoutTimelineAnnotation.Unannotated,
                        )
                    },
                ),
                settings = settings(),
            )

            assertEquals(WorkoutTimelineCompileResult.Invalid(expectedError), result)
        }
    }

    @Test
    fun `generic timelines keep none context`() {
        val result = WorkoutTimelineCompiler.compile(
            programId = ProgramId("FAT_BURN"),
            profile = listOf(
                ProgramSegmentSummary(
                    name = "Warm Up",
                    duration = DurationMinutes(5),
                    speed = SpeedTenths(25),
                    incline = InclineTenths(10),
                ),
            ),
            settings = settings().copy(
                duration = DurationMinutes(5),
                maxSpeed = SpeedTenths(50),
                maxIncline = InclineTenths(80),
            ),
        )

        assertEquals(WorkoutTimelineContext.None, assertValid(result).context)
    }

    private fun target(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }

    private fun context(
        target: HeartRateTargetRange,
        programId: ProgramId = ProgramId("ZONE_2"),
        duration: DurationMinutes = DurationMinutes(30),
        maxSpeed: SpeedTenths = SpeedTenths(50),
        maxIncline: InclineTenths = InclineTenths(80),
    ): WorkoutTimelineContext.Zone2Preview = WorkoutTimelineContext.Zone2Preview(
        programId = programId,
        target = target,
        intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
        previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
        adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
        thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
        hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
        duration = duration,
        effectiveMaxSpeed = maxSpeed,
        effectiveMaxIncline = maxIncline,
    )

    private fun reviewedProfile(): List<ProgramSegmentSummary> = listOf(
        ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(25), InclineTenths(10)),
        ProgramSegmentSummary("Settle", DurationMinutes(5), SpeedTenths(30), InclineTenths(20)),
        ProgramSegmentSummary("Maintain", DurationMinutes(10), SpeedTenths(32), InclineTenths(30)),
        ProgramSegmentSummary("Check", DurationMinutes(5), SpeedTenths(32), InclineTenths(30)),
        ProgramSegmentSummary("Cool Down", DurationMinutes(5), SpeedTenths(25), InclineTenths(10)),
    )

    private fun settings(): PlanSettings = PlanSettings(
        duration = DurationMinutes(30),
        intensity = PlanIntensity.LOW,
        focus = PlanFocus.BALANCED,
        maxSpeed = SpeedTenths(50),
        maxIncline = InclineTenths(80),
        adaptToYou = false,
    )

    private fun assertValid(result: WorkoutTimelineCompileResult): WorkoutTimeline = when (result) {
        is WorkoutTimelineCompileResult.Valid -> result.timeline
        is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
    }
}
