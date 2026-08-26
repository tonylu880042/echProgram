package com.echelon.console.domain

/** Typed context carried by a compiled timeline without adding progress calculations. */
sealed interface WorkoutTimelineContext {
    data object None : WorkoutTimelineContext

    data class VerticalPreview(
        val programId: ProgramId,
        val target: VerticalTarget,
        val proposedTimeLimit: VerticalTimeLimitProposal,
        val elevationSource: VerticalElevationSource,
        val progressStatus: VerticalProgressStatus,
        val controlStatus: VerticalWorkoutDraftControlStatus,
    ) : WorkoutTimelineContext

    /**
     * Static ZONE 2 phase metadata. This context contains no current sample
     * or computed HR status; those arrive from a later telemetry increment.
     */
    data class Zone2Preview(
        val programId: ProgramId,
        val target: HeartRateTargetRange,
        val intendedSource: Zone2HeartRateIntendedSource,
        val previewStatus: Zone2HeartRatePreviewStatus,
        val adviceMode: Zone2HeartRateAdviceMode,
        val thresholdMode: Zone2HeartRateThresholdMode,
        val hysteresisStatus: Zone2HeartRateHysteresisStatus,
        val duration: DurationMinutes,
        val effectiveMaxSpeed: SpeedTenths,
        val effectiveMaxIncline: InclineTenths,
    ) : WorkoutTimelineContext
}

/** Typed coaching meaning carried with a compiled timeline segment. */
sealed interface WorkoutTimelineAnnotation {
    data object Unannotated : WorkoutTimelineAnnotation

    data object WarmUpWalk : WorkoutTimelineAnnotation

    data class Run(
        val ordinal: Int,
        val total: Int,
    ) : WorkoutTimelineAnnotation

    data object WalkRecovery : WorkoutTimelineAnnotation

    data object EasyWalk : WorkoutTimelineAnnotation

    data object CoolDown : WorkoutTimelineAnnotation
}

/** A profile segment that retains typed coaching metadata through compilation. */
data class AnnotatedWorkoutProfileSegment(
    val summary: ProgramSegmentSummary,
    val annotation: WorkoutTimelineAnnotation,
)

/** A typed profile boundary that protects program identity while compiling. */
data class AnnotatedWorkoutProfile(
    val programId: ProgramId,
    val segments: List<AnnotatedWorkoutProfileSegment>,
    val context: WorkoutTimelineContext = WorkoutTimelineContext.None,
)

data class WorkoutTimelineSegment(
    val name: String,
    val startSecond: Int,
    val endSecond: Int,
    val targetSpeed: SpeedTenths,
    val targetIncline: InclineTenths,
    val annotation: WorkoutTimelineAnnotation = WorkoutTimelineAnnotation.Unannotated,
) {
    val durationSeconds: Int
        get() = endSecond - startSecond
}

data class WorkoutTimeline(
    val programId: ProgramId,
    val totalDurationSeconds: Int,
    val segments: List<WorkoutTimelineSegment>,
    val context: WorkoutTimelineContext = WorkoutTimelineContext.None,
)

sealed interface WorkoutTimelineCompileError {
    data object EmptyProfile : WorkoutTimelineCompileError

    data class ProfileProgramIdMismatch(
        val expected: ProgramId,
        val actual: ProgramId,
    ) : WorkoutTimelineCompileError

    data class ContextProgramIdMismatch(
        val expected: ProgramId,
        val actual: ProgramId,
    ) : WorkoutTimelineCompileError

    data class ContextDurationMismatch(
        val expected: DurationMinutes,
        val actual: DurationMinutes,
    ) : WorkoutTimelineCompileError

    data class ContextMaxSpeedMismatch(
        val expected: SpeedTenths,
        val actual: SpeedTenths,
    ) : WorkoutTimelineCompileError

    data class ContextMaxInclineMismatch(
        val expected: InclineTenths,
        val actual: InclineTenths,
    ) : WorkoutTimelineCompileError

    data class AnnotationCountMismatch(
        val expectedRunCount: Int,
        val actualRunCount: Int,
    ) : WorkoutTimelineCompileError

    data class InvalidRunAnnotation(
        val segmentIndex: Int,
        val annotation: WorkoutTimelineAnnotation,
    ) : WorkoutTimelineCompileError

    data class NonPositiveProfileDuration(
        val segmentIndex: Int,
        val durationMinutes: Int,
    ) : WorkoutTimelineCompileError

    data class NonPositiveSelectedDuration(
        val durationMinutes: Int,
    ) : WorkoutTimelineCompileError

    data class SelectedDurationTooLarge(
        val durationMinutes: Int,
    ) : WorkoutTimelineCompileError

    data class SelectedDurationTooShort(
        val durationMinutes: Int,
        val segmentCount: Int,
    ) : WorkoutTimelineCompileError
}

sealed interface WorkoutTimelineCompileResult {
    data class Valid(val timeline: WorkoutTimeline) : WorkoutTimelineCompileResult

    data class Invalid(val error: WorkoutTimelineCompileError) : WorkoutTimelineCompileResult
}

object WorkoutTimelineCompiler {
    fun compile(
        detail: ProgramDetail,
        settings: PlanSettings,
    ): WorkoutTimelineCompileResult = compile(
        programId = detail.programId,
        profile = detail.profile,
        settings = settings,
    )

    fun compile(
        programId: ProgramId,
        profile: List<ProgramSegmentSummary>,
        settings: PlanSettings,
    ): WorkoutTimelineCompileResult = compile(
        programId = programId,
        profile = AnnotatedWorkoutProfile(
            programId = programId,
            segments = profile.map { summary ->
                AnnotatedWorkoutProfileSegment(
                    summary = summary,
                    annotation = WorkoutTimelineAnnotation.Unannotated,
                )
            },
        ),
        settings = settings,
    )

    fun compile(
        programId: ProgramId,
        profile: AnnotatedWorkoutProfile,
        settings: PlanSettings,
    ): WorkoutTimelineCompileResult {
        if (profile.programId != programId) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.ProfileProgramIdMismatch(
                    expected = programId,
                    actual = profile.programId,
                ),
            )
        }

        val contextError = validateContext(programId, profile.context, settings)
        if (contextError != null) {
            return WorkoutTimelineCompileResult.Invalid(contextError)
        }

        val selectedDurationMinutes = settings.duration.value
        if (selectedDurationMinutes <= 0) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.NonPositiveSelectedDuration(selectedDurationMinutes),
            )
        }

        if (profile.segments.isEmpty()) {
            return WorkoutTimelineCompileResult.Invalid(WorkoutTimelineCompileError.EmptyProfile)
        }

        val annotationError = validateAnnotations(profile)
        if (annotationError != null) {
            return WorkoutTimelineCompileResult.Invalid(annotationError)
        }

        profile.segments.forEachIndexed { index, annotatedSegment ->
            if (annotatedSegment.summary.duration.value <= 0) {
                return WorkoutTimelineCompileResult.Invalid(
                    WorkoutTimelineCompileError.NonPositiveProfileDuration(
                        segmentIndex = index,
                        durationMinutes = annotatedSegment.summary.duration.value,
                    ),
                )
            }
        }

        val totalProfileMinutes = profile.segments.sumOf { it.summary.duration.value.toLong() }
        val totalDurationSeconds = selectedDurationMinutes.toLong() * SECONDS_PER_MINUTE
        if (totalDurationSeconds > Int.MAX_VALUE) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooLarge(selectedDurationMinutes),
            )
        }
        if (totalDurationSeconds < profile.segments.size) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooShort(
                    durationMinutes = selectedDurationMinutes,
                    segmentCount = profile.segments.size,
                ),
            )
        }

        var cumulativeProfileMinutes = 0L
        val roundedBoundaries = profile.segments.mapIndexed { index, annotatedSegment ->
            cumulativeProfileMinutes += annotatedSegment.summary.duration.value
            if (index == profile.segments.lastIndex) {
                totalDurationSeconds
            } else {
                roundedBoundarySeconds(
                    cumulativeProfileMinutes = cumulativeProfileMinutes,
                    totalProfileMinutes = totalProfileMinutes,
                    selectedDurationMinutes = selectedDurationMinutes.toLong(),
                )
            }
        }

        var previousEndSecond = 0
        val segments = profile.segments.mapIndexed { index, annotatedSegment ->
            val profileSegment = annotatedSegment.summary
            val minimumEndSecond = previousEndSecond + 1
            val maximumEndSecond = totalDurationSeconds.toInt() - (profile.segments.size - index - 1)
            val endSecond = roundedBoundaries[index]
                .toInt()
                .coerceIn(minimumEndSecond, maximumEndSecond)
            val timelineSegment = WorkoutTimelineSegment(
                name = profileSegment.name,
                startSecond = previousEndSecond,
                endSecond = endSecond,
                targetSpeed = SpeedTenths(
                    minOf(profileSegment.speed.value, settings.maxSpeed.value),
                ),
                targetIncline = InclineTenths(
                    minOf(profileSegment.incline.value, settings.maxIncline.value),
                ),
                annotation = annotatedSegment.annotation,
            )
            previousEndSecond = endSecond
            timelineSegment
        }

        return WorkoutTimelineCompileResult.Valid(
            WorkoutTimeline(
                programId = programId,
                totalDurationSeconds = totalDurationSeconds.toInt(),
                segments = segments.toList(),
                context = profile.context,
            ),
        )
    }

    private fun validateContext(
        programId: ProgramId,
        context: WorkoutTimelineContext,
        settings: PlanSettings,
    ): WorkoutTimelineCompileError? = when (context) {
        WorkoutTimelineContext.None -> null
        is WorkoutTimelineContext.VerticalPreview -> when {
            context.programId != VERTICAL_PROGRAM_ID ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = VERTICAL_PROGRAM_ID,
                    actual = context.programId,
                )

            programId != VERTICAL_PROGRAM_ID ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = VERTICAL_PROGRAM_ID,
                    actual = programId,
                )

            context.programId != programId ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = programId,
                    actual = context.programId,
                )

            else -> null
        }
        is WorkoutTimelineContext.Zone2Preview -> when {
            context.programId != ZONE_2_PROGRAM_ID ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = ZONE_2_PROGRAM_ID,
                    actual = context.programId,
                )

            programId != ZONE_2_PROGRAM_ID ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = ZONE_2_PROGRAM_ID,
                    actual = programId,
                )

            context.programId != programId ->
                WorkoutTimelineCompileError.ContextProgramIdMismatch(
                    expected = programId,
                    actual = context.programId,
                )

            context.duration != settings.duration ->
                WorkoutTimelineCompileError.ContextDurationMismatch(
                    expected = settings.duration,
                    actual = context.duration,
                )

            context.effectiveMaxSpeed != settings.maxSpeed ->
                WorkoutTimelineCompileError.ContextMaxSpeedMismatch(
                    expected = settings.maxSpeed,
                    actual = context.effectiveMaxSpeed,
                )

            context.effectiveMaxIncline != settings.maxIncline ->
                WorkoutTimelineCompileError.ContextMaxInclineMismatch(
                    expected = settings.maxIncline,
                    actual = context.effectiveMaxIncline,
                )

            else -> null
        }
    }

    private fun validateAnnotations(
        profile: AnnotatedWorkoutProfile,
    ): WorkoutTimelineCompileError? {
        val runs = profile.segments.mapIndexedNotNull { index, segment ->
            (segment.annotation as? WorkoutTimelineAnnotation.Run)?.let { index to it }
        }
        if (runs.isEmpty()) return null

        val expectedRunCount = runs.first().second.total
        val malformed = runs.firstOrNull { (_, annotation) ->
            annotation.total <= 0 || annotation.ordinal <= 0
        }
        if (malformed != null) {
            return WorkoutTimelineCompileError.InvalidRunAnnotation(
                segmentIndex = malformed.first,
                annotation = malformed.second,
            )
        }

        if (runs.size != expectedRunCount) {
            return WorkoutTimelineCompileError.AnnotationCountMismatch(
                expectedRunCount = expectedRunCount,
                actualRunCount = runs.size,
            )
        }

        val inconsistentTotal = runs.firstOrNull { (_, annotation) ->
            annotation.total != expectedRunCount
        }
        if (inconsistentTotal != null) {
            return WorkoutTimelineCompileError.InvalidRunAnnotation(
                segmentIndex = inconsistentTotal.first,
                annotation = inconsistentTotal.second,
            )
        }

        val invalidOrdinal = runs.firstOrNull { (_, annotation) ->
            annotation.ordinal !in 1..expectedRunCount
        }
        if (invalidOrdinal != null) {
            return WorkoutTimelineCompileError.InvalidRunAnnotation(
                segmentIndex = invalidOrdinal.first,
                annotation = invalidOrdinal.second,
            )
        }

        val outOfOrder = runs.withIndex().firstOrNull { (position, entry) ->
            entry.second.ordinal != position + 1
        }
        if (outOfOrder != null) {
            return WorkoutTimelineCompileError.InvalidRunAnnotation(
                segmentIndex = outOfOrder.value.first,
                annotation = outOfOrder.value.second,
            )
        }
        return null
    }

    private fun roundedBoundarySeconds(
        cumulativeProfileMinutes: Long,
        totalProfileMinutes: Long,
        selectedDurationMinutes: Long,
    ): Long {
        val product = cumulativeProfileMinutes * selectedDurationMinutes
        val wholeMinutes = product / totalProfileMinutes
        val minuteRemainder = product % totalProfileMinutes
        val fractionalSecondsNumerator = minuteRemainder * SECONDS_PER_MINUTE
        val wholeFractionalSeconds = fractionalSecondsNumerator / totalProfileMinutes
        val fractionalRemainder = fractionalSecondsNumerator % totalProfileMinutes
        val roundedFractionalSecond = if (fractionalRemainder * 2 >= totalProfileMinutes) 1 else 0
        return wholeMinutes * SECONDS_PER_MINUTE + wholeFractionalSeconds + roundedFractionalSecond
    }

    private const val SECONDS_PER_MINUTE = 60L
    private val VERTICAL_PROGRAM_ID = ProgramId("VERTICAL")
    private val ZONE_2_PROGRAM_ID = ProgramId("ZONE_2")
}

/** Maps a generated 5K draft into the generic typed timeline boundary. */
fun FiveKReadySessionDraft.toWorkoutTimelineProfile(): AnnotatedWorkoutProfile =
    AnnotatedWorkoutProfile(
        programId = metadata.programId,
        segments = segments.map { segment ->
            AnnotatedWorkoutProfileSegment(
                summary = segment.summary,
                annotation = when (segment.role) {
                    FiveKReadySegmentRole.WARM_UP_WALK -> WorkoutTimelineAnnotation.WarmUpWalk
                    FiveKReadySegmentRole.RUN -> WorkoutTimelineAnnotation.Run(
                        ordinal = segment.runOrdinal ?: 0,
                        total = segment.totalRuns ?: 0,
                    )
                    FiveKReadySegmentRole.WALK_RECOVERY -> WorkoutTimelineAnnotation.WalkRecovery
                    FiveKReadySegmentRole.EASY_WALK -> WorkoutTimelineAnnotation.EasyWalk
                    FiveKReadySegmentRole.COOL_DOWN -> WorkoutTimelineAnnotation.CoolDown
                },
            )
        },
    )

/** Maps an accepted VERTICAL draft into its exact profile and typed preview context. */
fun VerticalWorkoutDraft.toWorkoutTimelineProfile(): AnnotatedWorkoutProfile =
    AnnotatedWorkoutProfile(
        programId = metadata.programId,
        context = WorkoutTimelineContext.VerticalPreview(
            programId = metadata.programId,
            target = metadata.target,
            proposedTimeLimit = metadata.proposedTimeLimit,
            elevationSource = metadata.elevationSource,
            progressStatus = metadata.progressStatus,
            controlStatus = controlStatus,
        ),
        segments = segments.map { segment ->
            AnnotatedWorkoutProfileSegment(
                summary = segment.summary,
                annotation = WorkoutTimelineAnnotation.Unannotated,
            )
        },
    )

/** Maps the reviewed static ZONE 2 profile into its typed preview context. */
fun ProgramDetail.toZone2WorkoutTimelineProfile(
    context: WorkoutTimelineContext.Zone2Preview,
): AnnotatedWorkoutProfile = AnnotatedWorkoutProfile(
    programId = programId,
    context = context,
    segments = profile.map { summary ->
        AnnotatedWorkoutProfileSegment(
            summary = summary,
            annotation = WorkoutTimelineAnnotation.Unannotated,
        )
    },
)
