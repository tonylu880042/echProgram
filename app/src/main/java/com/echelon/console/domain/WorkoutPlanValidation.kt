package com.echelon.console.domain

enum class PlanField {
    DURATION,
    MAX_SPEED,
    MAX_INCLINE,
}

sealed interface PlanValidationError {
    val field: PlanField

    data class DurationOutOfRange(
        val value: DurationMinutes,
        val limits: DurationLimits,
    ) : PlanValidationError {
        override val field: PlanField = PlanField.DURATION
    }

    data class DurationStepMismatch(
        val value: DurationMinutes,
        val limits: DurationLimits,
    ) : PlanValidationError {
        override val field: PlanField = PlanField.DURATION
    }

    data class MaxSpeedOutOfRange(
        val value: SpeedTenths,
        val limits: SpeedRange,
    ) : PlanValidationError {
        override val field: PlanField = PlanField.MAX_SPEED
    }

    data class MaxInclineOutOfRange(
        val value: InclineTenths,
        val limits: InclineRange,
    ) : PlanValidationError {
        override val field: PlanField = PlanField.MAX_INCLINE
    }
}

object WorkoutPlanValidator {
    fun validate(
        plan: WorkoutPlan,
        capabilities: DeviceCapabilities,
    ): List<PlanValidationError> = buildList {
        val duration = plan.settings.duration.value
        val durationLimits = capabilities.duration
        if (duration !in durationLimits.min.value..durationLimits.max.value) {
            add(PlanValidationError.DurationOutOfRange(plan.settings.duration, durationLimits))
        } else if ((duration - durationLimits.min.value) % durationLimits.step.value != 0) {
            add(PlanValidationError.DurationStepMismatch(plan.settings.duration, durationLimits))
        }

        val speed = plan.settings.maxSpeed.value
        val speedLimits = capabilities.speed
        if (speed !in speedLimits.min.value..speedLimits.max.value) {
            add(PlanValidationError.MaxSpeedOutOfRange(plan.settings.maxSpeed, speedLimits))
        }

        val incline = plan.settings.maxIncline.value
        val inclineLimits = capabilities.incline
        if (incline !in inclineLimits.min.value..inclineLimits.max.value) {
            add(PlanValidationError.MaxInclineOutOfRange(plan.settings.maxIncline, inclineLimits))
        }
    }
}
