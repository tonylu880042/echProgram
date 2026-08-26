package com.echelon.console.domain

@JvmInline
value class DurationMinutes(val value: Int)

@JvmInline
value class SpeedTenths(val value: Int)

@JvmInline
value class InclineTenths(val value: Int)

data class DurationLimits(
    val min: DurationMinutes,
    val max: DurationMinutes,
    val step: DurationMinutes,
) {
    init {
        require(step.value > 0) { "Duration step must be positive" }
        require(min.value <= max.value) { "Duration minimum must not exceed maximum" }
    }
}

data class SpeedRange(
    val min: SpeedTenths,
    val max: SpeedTenths,
) {
    init {
        require(min.value <= max.value) { "Speed minimum must not exceed maximum" }
    }
}

data class InclineRange(
    val min: InclineTenths,
    val max: InclineTenths,
) {
    init {
        require(min.value <= max.value) { "Incline minimum must not exceed maximum" }
    }
}

data class DeviceCapabilities(
    val duration: DurationLimits,
    val speed: SpeedRange,
    val incline: InclineRange,
)

enum class PlanIntensity {
    LOW,
    MEDIUM,
    HIGH,
}

enum class PlanFocus {
    MORE_INCLINE,
    BALANCED,
    MORE_SPEED,
}

data class PlanSettings(
    val duration: DurationMinutes,
    val intensity: PlanIntensity,
    val focus: PlanFocus,
    val maxSpeed: SpeedTenths,
    val maxIncline: InclineTenths,
    val adaptToYou: Boolean,
)

data class WorkoutPlan(
    val programId: ProgramId,
    val settings: PlanSettings,
)
