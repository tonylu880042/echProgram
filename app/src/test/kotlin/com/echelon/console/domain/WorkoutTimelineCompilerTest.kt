package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTimelineCompilerTest {
    @Test
    fun `default duration preserves profile order boundaries and targets`() {
        val detail = detail(
            duration = 6,
            profile = listOf(
                segment("Warm Up", 2, speed = 30, incline = 10),
                segment("Build", 3, speed = 45, incline = 30),
                segment("Cool Down", 1, speed = 25, incline = 0),
            ),
        )

        val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)

        val timeline = assertValid(result)
        assertEquals(ProgramId("TEST"), timeline.programId)
        assertEquals(360, timeline.totalDurationSeconds)
        assertEquals(
            listOf(
                WorkoutTimelineSegment("Warm Up", 0, 120, SpeedTenths(30), InclineTenths(10)),
                WorkoutTimelineSegment("Build", 120, 300, SpeedTenths(45), InclineTenths(30)),
                WorkoutTimelineSegment("Cool Down", 300, 360, SpeedTenths(25), InclineTenths(0)),
            ),
            timeline.segments,
        )
    }

    @Test
    fun `shorter duration uses cumulative boundary rounding without gaps`() {
        val detail = detail(
            duration = 3,
            profile = listOf(
                segment("First", 3, speed = 40, incline = 20),
                segment("Second", 2, speed = 50, incline = 30),
                segment("Third", 1, speed = 60, incline = 40),
            ),
        )

        val result = WorkoutTimelineCompiler.compile(
            detail,
            detail.defaultSettings.copy(duration = DurationMinutes(3)),
        )

        val timeline = assertValid(result)
        assertEquals(180, timeline.totalDurationSeconds)
        assertEquals(listOf(0, 90, 150), timeline.segments.map { it.startSecond })
        assertEquals(listOf(90, 150, 180), timeline.segments.map { it.endSecond })
        assertTrue(timeline.segments.all { it.endSecond > it.startSecond })
    }

    @Test
    fun `longer duration uses cumulative boundary rounding and exact final end`() {
        val detail = detail(
            duration = 6,
            profile = listOf(
                segment("First", 1, speed = 40, incline = 20),
                segment("Second", 2, speed = 50, incline = 30),
                segment("Third", 3, speed = 60, incline = 40),
            ),
        )

        val result = WorkoutTimelineCompiler.compile(
            detail,
            detail.defaultSettings.copy(duration = DurationMinutes(9)),
        )

        val timeline = assertValid(result)
        assertEquals(540, timeline.totalDurationSeconds)
        assertEquals(listOf(0, 90, 270), timeline.segments.map { it.startSecond })
        assertEquals(listOf(90, 270, 540), timeline.segments.map { it.endSecond })
        assertEquals(540, timeline.segments.last().endSecond)
    }

    @Test
    fun `cumulative boundaries round to nearest second deterministically`() {
        val detail = detail(
            duration = 7,
            profile = listOf(
                segment("First", 1, speed = 40, incline = 20),
                segment("Second", 2, speed = 50, incline = 30),
                segment("Third", 4, speed = 60, incline = 40),
            ),
        )

        val result = WorkoutTimelineCompiler.compile(
            detail,
            detail.defaultSettings.copy(duration = DurationMinutes(5)),
        )

        val timeline = assertValid(result)
        assertEquals(listOf(0, 43, 129), timeline.segments.map { it.startSecond })
        assertEquals(listOf(43, 129, 300), timeline.segments.map { it.endSecond })
    }

    @Test
    fun `targets are capped without mutating the program detail`() {
        val detail = detail(
            duration = 3,
            profile = listOf(
                segment("One", 1, speed = 40, incline = 20),
                segment("Two", 1, speed = 70, incline = 80),
                segment("Three", 1, speed = 60, incline = 50),
            ),
        )
        val settings = detail.defaultSettings.copy(
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(50),
        )

        val result = WorkoutTimelineCompiler.compile(detail, settings)

        val timeline = assertValid(result)
        assertEquals(listOf(40, 55, 55), timeline.segments.map { it.targetSpeed.value })
        assertEquals(listOf(20, 50, 50), timeline.segments.map { it.targetIncline.value })
        assertEquals(listOf(40, 70, 60), detail.profile.map { it.speed.value })
        assertEquals(listOf(20, 80, 50), detail.profile.map { it.incline.value })
    }

    @Test
    fun `empty profile returns an explicit invalid result`() {
        val detail = detail(duration = 1, profile = emptyList())

        val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(WorkoutTimelineCompileError.EmptyProfile),
            result,
        )
    }

    @Test
    fun `non-positive profile segment duration returns an explicit invalid result`() {
        val detail = detail(
            duration = 2,
            profile = listOf(
                segment("Valid", 1, speed = 30, incline = 10),
                segment("Invalid", 0, speed = 30, incline = 10),
            ),
        )

        val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.NonPositiveProfileDuration(
                    segmentIndex = 1,
                    durationMinutes = 0,
                ),
            ),
            result,
        )
    }

    @Test
    fun `non-positive selected duration returns an explicit invalid result`() {
        val detail = detail(
            duration = 1,
            profile = listOf(segment("Only", 1, speed = 30, incline = 10)),
        )

        val result = WorkoutTimelineCompiler.compile(
            detail,
            detail.defaultSettings.copy(duration = DurationMinutes(0)),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.NonPositiveSelectedDuration(0),
            ),
            result,
        )
    }

    @Test
    fun `duration beyond timeline seconds range returns an explicit invalid result`() {
        val detail = detail(
            duration = 1,
            profile = listOf(segment("Only", 1, speed = 30, incline = 10)),
        )

        val result = WorkoutTimelineCompiler.compile(
            detail,
            detail.defaultSettings.copy(duration = DurationMinutes(Int.MAX_VALUE)),
        )

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooLarge(Int.MAX_VALUE),
            ),
            result,
        )
    }

    @Test
    fun `one minute cannot be divided into more positive segments than seconds`() {
        val detail = detail(
            duration = 1,
            profile = List(61) { index ->
                segment("Block $index", 1, speed = 30, incline = 10)
            },
        )

        val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooShort(
                    durationMinutes = 1,
                    segmentCount = 61,
                ),
            ),
            result,
        )
    }

    @Test
    fun `target preview detail compiles as a representative static timeline`() {
        val detail = detail(
            duration = 4,
            profile = listOf(
                segment("Base Climb", 2, speed = 28, incline = 80),
                segment("Finish", 2, speed = 26, incline = 150),
            ),
        ).copy(
            programId = ProgramId("VERTICAL"),
            previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
        )

        val result = WorkoutTimelineCompiler.compile(detail, detail.defaultSettings)

        val timeline = assertValid(result)
        assertEquals(ProgramId("VERTICAL"), timeline.programId)
        assertEquals(listOf(80, 150), timeline.segments.map { it.targetIncline.value })
        assertEquals(240, timeline.totalDurationSeconds)
    }

    private fun assertValid(result: WorkoutTimelineCompileResult): WorkoutTimeline = when (result) {
        is WorkoutTimelineCompileResult.Valid -> result.timeline
        is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
    }

    private fun detail(
        duration: Int,
        profile: List<ProgramSegmentSummary>,
    ): ProgramDetail = ProgramDetail(
        programId = ProgramId("TEST"),
        title = "TEST",
        promise = "A deterministic test profile.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(duration),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(100),
            maxIncline = InclineTenths(150),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(0), SpeedTenths(120)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(150)),
        profile = profile,
    )

    private fun segment(
        name: String,
        duration: Int,
        speed: Int,
        incline: Int,
    ): ProgramSegmentSummary = ProgramSegmentSummary(
        name = name,
        duration = DurationMinutes(duration),
        speed = SpeedTenths(speed),
        incline = InclineTenths(incline),
    )
}
