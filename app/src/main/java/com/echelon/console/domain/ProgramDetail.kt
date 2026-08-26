package com.echelon.console.domain

data class ProgramSegmentSummary(
    val name: String,
    val duration: DurationMinutes,
    val speed: SpeedTenths,
    val incline: InclineTenths,
)

/**
 * Describes what the static profile means while the live session contracts are pending.
 * These values are intentionally read-only metadata; they do not enable device control.
 */
enum class ProgramPreviewMode {
    FIXED_PROFILE_PREVIEW,
    BASELINE_PREVIEW,
    ELEVATION_TARGET_PREVIEW,
    HISTORY_ADAPTIVE_PREVIEW,
    HEART_RATE_PREVIEW,
    GENERATED_PREVIEW,
    CALORIE_TARGET_PREVIEW,
}

data class ProgramDetail(
    val programId: ProgramId,
    val title: String,
    val promise: String,
    val defaultSettings: PlanSettings,
    val speedRange: SpeedRange,
    val inclineRange: InclineRange,
    val profile: List<ProgramSegmentSummary>,
    val previewMode: ProgramPreviewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
    val supportedDurations: List<DurationMinutes> = listOf(defaultSettings.duration),
) {
    init {
        require(supportedDurations.isNotEmpty()) { "Supported durations must not be empty" }
        require(supportedDurations.all { it.value > 0 }) {
            "Supported durations must be positive"
        }
        require(supportedDurations.map { it.value }.distinct().size == supportedDurations.size) {
            "Supported durations must be distinct"
        }
        require(supportedDurations == supportedDurations.sortedBy { it.value }) {
            "Supported durations must be sorted"
        }
        require(defaultSettings.duration in supportedDurations) {
            "Default duration must be supported"
        }
    }

    val defaultDuration: DurationMinutes
        get() = defaultSettings.duration
}
