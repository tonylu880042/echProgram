package com.echelon.console.domain

enum class EquipmentSpeedUnit {
    KILOMETERS_PER_HOUR,
    MILES_PER_HOUR,
}

enum class EquipmentDistanceUnit {
    KILOMETERS,
    MILES,
}

@JvmInline
value class SpeedKmh(val value: Double) {
    init {
        require(value.isFinite() && value >= 0.0) { "Canonical speed must be finite and non-negative" }
    }
}

@JvmInline
value class EquipmentInclineLevel(val value: Int) {
    init {
        require(value >= 0) { "Equipment incline level must be non-negative" }
    }
}

data class EquipmentSpeed(
    val canonicalKmh: SpeedKmh,
    val displayValue: Double,
    val unit: EquipmentSpeedUnit,
)

data class EquipmentDistance(
    val displayValue: Double,
    val unit: EquipmentDistanceUnit,
) {
    init {
        require(displayValue.isFinite() && displayValue >= 0.0) {
            "Equipment distance must be finite and non-negative"
        }
    }
}

data class EquipmentTelemetry(
    val elapsedRealtimeMillis: Long,
    val elapsedTime: String?,
    val speed: EquipmentSpeed?,
    val incline: EquipmentInclineLevel?,
    val heartRateBpm: Int?,
    val distance: EquipmentDistance?,
    val calories: Double?,
)

data class EquipmentSpeedRangeKmh(
    val min: Double,
    val max: Double,
) {
    init {
        require(min.isFinite() && max.isFinite() && min >= 0.0 && min <= max) {
            "Equipment speed limits must be finite, non-negative, and ordered"
        }
    }
}

data class EquipmentInclineRange(
    val min: EquipmentInclineLevel,
    val max: EquipmentInclineLevel,
) {
    init {
        require(min.value <= max.value) { "Equipment incline limits must be ordered" }
    }
}

data class EquipmentLimits(
    val runSpeedKmh: EquipmentSpeedRangeKmh?,
    val runIncline: EquipmentInclineRange?,
)
