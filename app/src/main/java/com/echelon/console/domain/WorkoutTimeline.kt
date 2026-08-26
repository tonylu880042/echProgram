package com.echelon.console.domain

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
)

sealed interface WorkoutTimelineCompileError {
    data object EmptyProfile : WorkoutTimelineCompileError

    data class ProfileProgramIdMismatch(
        val expected: ProgramId,
        val actual: ProgramId,
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
            ),
        )
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
