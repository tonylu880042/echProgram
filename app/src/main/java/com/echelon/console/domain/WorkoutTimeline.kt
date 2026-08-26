package com.echelon.console.domain

data class WorkoutTimelineSegment(
    val name: String,
    val startSecond: Int,
    val endSecond: Int,
    val targetSpeed: SpeedTenths,
    val targetIncline: InclineTenths,
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
    ): WorkoutTimelineCompileResult {
        val selectedDurationMinutes = settings.duration.value
        if (selectedDurationMinutes <= 0) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.NonPositiveSelectedDuration(selectedDurationMinutes),
            )
        }

        if (profile.isEmpty()) {
            return WorkoutTimelineCompileResult.Invalid(WorkoutTimelineCompileError.EmptyProfile)
        }

        profile.forEachIndexed { index, segment ->
            if (segment.duration.value <= 0) {
                return WorkoutTimelineCompileResult.Invalid(
                    WorkoutTimelineCompileError.NonPositiveProfileDuration(
                        segmentIndex = index,
                        durationMinutes = segment.duration.value,
                    ),
                )
            }
        }

        val totalProfileMinutes = profile.sumOf { it.duration.value.toLong() }
        val totalDurationSeconds = selectedDurationMinutes.toLong() * SECONDS_PER_MINUTE
        if (totalDurationSeconds > Int.MAX_VALUE) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooLarge(selectedDurationMinutes),
            )
        }
        if (totalDurationSeconds < profile.size) {
            return WorkoutTimelineCompileResult.Invalid(
                WorkoutTimelineCompileError.SelectedDurationTooShort(
                    durationMinutes = selectedDurationMinutes,
                    segmentCount = profile.size,
                ),
            )
        }

        var cumulativeProfileMinutes = 0L
        val roundedBoundaries = profile.mapIndexed { index, segment ->
            cumulativeProfileMinutes += segment.duration.value
            if (index == profile.lastIndex) {
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
        val segments = profile.mapIndexed { index, profileSegment ->
            val minimumEndSecond = previousEndSecond + 1
            val maximumEndSecond = totalDurationSeconds.toInt() - (profile.size - index - 1)
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
