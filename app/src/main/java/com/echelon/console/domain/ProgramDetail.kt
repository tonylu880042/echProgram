package com.echelon.console.domain

data class ProgramSegmentSummary(
    val name: String,
    val duration: DurationMinutes,
    val speed: SpeedTenths,
    val incline: InclineTenths,
)

data class ProgramDetail(
    val programId: ProgramId,
    val title: String,
    val promise: String,
    val defaultSettings: PlanSettings,
    val speedRange: SpeedRange,
    val inclineRange: InclineRange,
    val profile: List<ProgramSegmentSummary>,
) {
    val defaultDuration: DurationMinutes
        get() = defaultSettings.duration
}
