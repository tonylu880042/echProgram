package com.echelon.console.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalorieTargetWorkoutTimelineContextTest {
    @Test
    fun `calorie target contexts preserve target proposal separately from forty minute profile`() {
        mapOf(
            100 to 60,
            200 to 60,
            300 to 60,
            500 to 90,
        ).forEach { (estimatedKcal, proposedMaxTime) ->
            val context = calorieContext(estimatedKcal)
            val timeline = assertValid(compile(context))

            assertEquals(40 * 60, timeline.totalDurationSeconds)
            assertEquals(context, timeline.context)
            assertEquals(estimatedKcal, context.target.estimatedKcal)
            assertEquals(proposedMaxTime, context.target.proposedMaxTime.minutes)
            assertEquals(DurationMinutes(40), context.representativeProfileDuration)
            assertTrue(
                timeline.segments.all {
                    it.annotation == WorkoutTimelineAnnotation.Unannotated
                },
            )
        }
    }

    @Test
    fun `calorie target context carries only the approved fixed metadata`() {
        val context = calorieContext(300)

        assertEquals(CalorieEstimateStatus.ESTIMATED, context.estimateStatus)
        assertEquals(
            CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            context.source,
        )
        assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, context.unitSemantics)
        assertEquals(
            CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            context.sessionResetSemantics,
        )
        assertEquals(
            CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            context.completionAuthority,
        )
        assertEquals(
            CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            context.progressSemantics,
        )
        assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, context.previewStatus)
        assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, context.deviceCommandStatus)
    }

    @Test
    fun `compiler rejects a calorie context with the wrong identity`() {
        val wrongContext = calorieContext(100).copy(programId = ProgramId("OTHER"))

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = CALORIE_TARGET_PROGRAM_ID,
                    actual = ProgramId("OTHER"),
                ),
            ),
            compile(wrongContext),
        )
    }

    @Test
    fun `compiler rejects a calorie timeline with the wrong identity`() {
        val context = calorieContext(100)
        val profile = calorieDetail()
            .toCalorieTargetWorkoutTimelineProfile(context)
            .copy(programId = ProgramId("OTHER"))

        assertEquals(
            WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = CALORIE_TARGET_PROGRAM_ID,
                    actual = ProgramId("OTHER"),
                ),
            ),
            WorkoutTimelineCompiler.compile(
                programId = ProgramId("OTHER"),
                profile = profile,
                settings = calorieSettings(),
            ),
        )
    }

    @Test
    fun `compiler rejects calorie context duration and cap mismatches`() {
        val mismatches = listOf(
            WorkoutTimelineCompileError.ContextDurationMismatch(
                expected = DurationMinutes(40),
                actual = DurationMinutes(35),
            ) to calorieContext(100).copy(
                representativeProfileDuration = DurationMinutes(35),
            ),
            WorkoutTimelineCompileError.ContextMaxSpeedMismatch(
                expected = SpeedTenths(60),
                actual = SpeedTenths(59),
            ) to calorieContext(100).copy(effectiveMaxSpeed = SpeedTenths(59)),
            WorkoutTimelineCompileError.ContextMaxInclineMismatch(
                expected = InclineTenths(100),
                actual = InclineTenths(99),
            ) to calorieContext(100).copy(effectiveMaxIncline = InclineTenths(99)),
        )

        mismatches.forEach { (expectedError, context) ->
            assertEquals(
                WorkoutTimelineCompileResult.Invalid(expectedError),
                compile(context),
            )
        }
    }

    private fun calorieContext(estimatedKcal: Int): WorkoutTimelineContext.CalorieTargetPreview =
        WorkoutTimelineContext.CalorieTargetPreview(
            programId = CALORIE_TARGET_PROGRAM_ID,
            target = acceptedTarget(estimatedKcal),
            estimateStatus = CalorieEstimateStatus.ESTIMATED,
            source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
            sessionResetSemantics = CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
            deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            representativeProfileDuration = DurationMinutes(40),
            effectiveMaxSpeed = SpeedTenths(60),
            effectiveMaxIncline = InclineTenths(100),
        )

    private fun calorieDetail(): ProgramDetail = ProgramDetail(
        programId = CALORIE_TARGET_PROGRAM_ID,
        title = "CALORIE TARGET",
        promise = "A representative estimated-calorie profile.",
        defaultSettings = calorieSettings(),
        speedRange = SpeedRange(SpeedTenths(25), SpeedTenths(60)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(100)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
            ProgramSegmentSummary("Base", DurationMinutes(10), SpeedTenths(35), InclineTenths(20)),
            ProgramSegmentSummary("Build", DurationMinutes(10), SpeedTenths(45), InclineTenths(40)),
            ProgramSegmentSummary("Push", DurationMinutes(10), SpeedTenths(55), InclineTenths(60)),
            ProgramSegmentSummary("Cool Down", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
        ),
        previewMode = ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
        supportedDurations = listOf(DurationMinutes(40)),
    )

    private fun calorieSettings(): PlanSettings = PlanSettings(
        duration = DurationMinutes(40),
        intensity = PlanIntensity.MEDIUM,
        focus = PlanFocus.BALANCED,
        maxSpeed = SpeedTenths(60),
        maxIncline = InclineTenths(100),
        adaptToYou = false,
    )

    private fun compile(
        context: WorkoutTimelineContext.CalorieTargetPreview,
    ): WorkoutTimelineCompileResult = WorkoutTimelineCompiler.compile(
        programId = CALORIE_TARGET_PROGRAM_ID,
        profile = calorieDetail().toCalorieTargetWorkoutTimelineProfile(context),
        settings = calorieSettings(),
    )

    private fun acceptedTarget(estimatedKcal: Int): CalorieTargetSelection = when (
        val result = CalorieTargetSelection.createUserSelected(estimatedKcal)
    ) {
        is CalorieTargetSelectionResult.Accepted -> result.selection
        is CalorieTargetSelectionResult.Rejected -> error("Expected accepted target, got $result")
    }

    private fun assertValid(result: WorkoutTimelineCompileResult): WorkoutTimeline = when (result) {
        is WorkoutTimelineCompileResult.Valid -> result.timeline
        is WorkoutTimelineCompileResult.Invalid -> error("Expected valid timeline, got $result")
    }

    private companion object {
        val CALORIE_TARGET_PROGRAM_ID = ProgramId("CALORIE_TARGET")
    }
}
