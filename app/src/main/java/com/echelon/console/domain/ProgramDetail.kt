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
) {
    val defaultDuration: DurationMinutes
        get() = defaultSettings.duration
}
