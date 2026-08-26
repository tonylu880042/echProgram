package com.echelon.console.domain

/** The only target source approved for this preview contract. */
enum class CalorieTargetSource {
    USER_SELECTED,
}

/** Proposed limit metadata; this is not a session duration. */
data class CalorieTargetMaxTimeProposal(
    val minutes: Int,
    val status: CalorieTargetMaxTimeStatus,
)

enum class CalorieTargetMaxTimeStatus {
    PROPOSED_NOT_CLIENT_APPROVED,
}

/** The four estimated calorie target values listed in the customer proposal. */
enum class CalorieTargetOption(
    val estimatedKcal: Int,
    val proposedMaxTime: CalorieTargetMaxTimeProposal,
) {
    ONE_HUNDRED_KCAL(
        estimatedKcal = 100,
        proposedMaxTime = CalorieTargetMaxTimeProposal(
            minutes = 60,
            status = CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED,
        ),
    ),
    TWO_HUNDRED_KCAL(
        estimatedKcal = 200,
        proposedMaxTime = CalorieTargetMaxTimeProposal(
            minutes = 60,
            status = CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED,
        ),
    ),
    THREE_HUNDRED_KCAL(
        estimatedKcal = 300,
        proposedMaxTime = CalorieTargetMaxTimeProposal(
            minutes = 60,
            status = CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED,
        ),
    ),
    FIVE_HUNDRED_KCAL(
        estimatedKcal = 500,
        proposedMaxTime = CalorieTargetMaxTimeProposal(
            minutes = 90,
            status = CalorieTargetMaxTimeStatus.PROPOSED_NOT_CLIENT_APPROVED,
        ),
    ),
}

/** A validated user selection over the finite set of proposed targets. */
class CalorieTargetSelection private constructor(
    val target: CalorieTargetOption,
    val source: CalorieTargetSource,
) {
    val estimatedKcal: Int
        get() = target.estimatedKcal

    val proposedMaxTime: CalorieTargetMaxTimeProposal
        get() = target.proposedMaxTime

    companion object {
        /** Selects a target without throwing for missing or unsupported input. */
        fun createUserSelected(estimatedKcal: Int?): CalorieTargetSelectionResult {
            if (estimatedKcal == null) {
                return CalorieTargetSelectionResult.Rejected(
                    CalorieTargetSelectionFailure.MissingTarget,
                )
            }
            val target = CalorieTargetOption.entries.firstOrNull {
                it.estimatedKcal == estimatedKcal
            } ?: return CalorieTargetSelectionResult.Rejected(
                CalorieTargetSelectionFailure.UnsupportedTarget(estimatedKcal),
            )
            return CalorieTargetSelectionResult.Accepted(
                CalorieTargetSelection(
                    target = target,
                    source = CalorieTargetSource.USER_SELECTED,
                ),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is CalorieTargetSelection &&
            target == other.target &&
            source == other.source

    override fun hashCode(): Int = 31 * target.hashCode() + source.hashCode()

    override fun toString(): String =
        "CalorieTargetSelection(target=$target, source=$source)"
}

sealed interface CalorieTargetSelectionResult {
    data class Accepted(val selection: CalorieTargetSelection) : CalorieTargetSelectionResult

    data class Rejected(val failure: CalorieTargetSelectionFailure) : CalorieTargetSelectionResult
}

sealed interface CalorieTargetSelectionFailure {
    data object MissingTarget : CalorieTargetSelectionFailure

    data class UnsupportedTarget(val value: Int) : CalorieTargetSelectionFailure
}

/** A validated FitOS snapshot calorie value and host elapsed-realtime timestamp. */
class FitOsCalorieSample private constructor(
    val displayValue: Double,
    val elapsedRealtimeMillis: Long,
) {
    companion object {
        /** Creates a sample only when both values satisfy the read-only boundary. */
        fun create(
            displayValue: Double?,
            elapsedRealtimeMillis: Long?,
        ): FitOsCalorieSampleResult {
            if (displayValue == null) {
                return FitOsCalorieSampleResult.Rejected(
                    FitOsCalorieSampleFailure.MissingDisplayValue,
                )
            }
            if (!displayValue.isFinite()) {
                return FitOsCalorieSampleResult.Rejected(
                    FitOsCalorieSampleFailure.NonFiniteDisplayValue(displayValue),
                )
            }
            if (displayValue < 0.0) {
                return FitOsCalorieSampleResult.Rejected(
                    FitOsCalorieSampleFailure.NegativeDisplayValue(displayValue),
                )
            }
            if (elapsedRealtimeMillis == null) {
                return FitOsCalorieSampleResult.Rejected(
                    FitOsCalorieSampleFailure.MissingTimestamp,
                )
            }
            if (elapsedRealtimeMillis < 0L) {
                return FitOsCalorieSampleResult.Rejected(
                    FitOsCalorieSampleFailure.NegativeTimestamp(elapsedRealtimeMillis),
                )
            }
            return FitOsCalorieSampleResult.Accepted(
                FitOsCalorieSample(
                    displayValue = displayValue,
                    elapsedRealtimeMillis = elapsedRealtimeMillis,
                ),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is FitOsCalorieSample &&
            displayValue == other.displayValue &&
            elapsedRealtimeMillis == other.elapsedRealtimeMillis

    override fun hashCode(): Int = 31 * displayValue.hashCode() + elapsedRealtimeMillis.hashCode()

    override fun toString(): String =
        "FitOsCalorieSample(displayValue=$displayValue, elapsedRealtimeMillis=$elapsedRealtimeMillis)"
}

sealed interface FitOsCalorieSampleResult {
    data class Accepted(val sample: FitOsCalorieSample) : FitOsCalorieSampleResult

    data class Rejected(val failure: FitOsCalorieSampleFailure) : FitOsCalorieSampleResult
}

sealed interface FitOsCalorieSampleFailure {
    data object MissingDisplayValue : FitOsCalorieSampleFailure

    data class NonFiniteDisplayValue(val value: Double) : FitOsCalorieSampleFailure

    data class NegativeDisplayValue(val value: Double) : FitOsCalorieSampleFailure

    data object MissingTimestamp : FitOsCalorieSampleFailure

    data class NegativeTimestamp(val value: Long) : FitOsCalorieSampleFailure
}

enum class CalorieSampleFreshness {
    FRESH,
    STALE,
}

enum class CalorieEstimateStatus {
    ESTIMATED,
}

enum class CalorieTelemetrySource {
    FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
}

enum class CalorieUnitSemantics {
    UNIT_SEMANTICS_UNCONFIRMED,
}

enum class CalorieSessionResetSemantics {
    SESSION_RESET_SEMANTICS_UNCONFIRMED,
}

enum class CalorieCompletionAuthority {
    COMPLETION_AUTHORITY_NOT_APPROVED,
}

enum class CalorieProgressSemantics {
    DISPLAY_ONLY_NO_TARGET_PROGRESS,
}

enum class CaloriePreviewStatus {
    PREVIEW_ONLY,
}

enum class CalorieDeviceCommandStatus {
    NO_DEVICE_COMMANDS,
}

data class CalorieTargetEvaluation(
    val target: CalorieTargetSelection,
    val displayValue: Double,
    val sampleAgeMillis: Long,
    val freshness: CalorieSampleFreshness,
    val estimateStatus: CalorieEstimateStatus,
    val source: CalorieTelemetrySource,
    val unitSemantics: CalorieUnitSemantics,
    val sessionResetSemantics: CalorieSessionResetSemantics,
    val completionAuthority: CalorieCompletionAuthority,
    val progressSemantics: CalorieProgressSemantics,
    val previewStatus: CaloriePreviewStatus,
    val deviceCommandStatus: CalorieDeviceCommandStatus,
)

sealed interface CalorieTargetEvaluationResult {
    data class Evaluated(val evaluation: CalorieTargetEvaluation) : CalorieTargetEvaluationResult

    data class Unavailable(val failure: CalorieTargetEvaluationFailure) : CalorieTargetEvaluationResult
}

sealed interface CalorieTargetEvaluationFailure {
    data object MissingTarget : CalorieTargetEvaluationFailure

    data object MissingSample : CalorieTargetEvaluationFailure

    data class InvalidNowElapsedRealtimeMillis(val value: Long) : CalorieTargetEvaluationFailure

    data class InvalidStaleAfterMillis(val value: Long) : CalorieTargetEvaluationFailure

    data class FutureSampleTimestamp(
        val sampleElapsedRealtimeMillis: Long,
        val nowElapsedRealtimeMillis: Long,
    ) : CalorieTargetEvaluationFailure
}

/**
 * Deterministically evaluates freshness only. It does not calculate progress,
 * completion, energy expenditure, or any equipment adjustment.
 */
class CalorieTargetEvaluator {
    fun evaluate(
        target: CalorieTargetSelection?,
        sample: FitOsCalorieSample?,
        nowElapsedRealtimeMillis: Long,
        staleAfterMillis: Long,
    ): CalorieTargetEvaluationResult {
        if (nowElapsedRealtimeMillis < 0L) {
            return CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.InvalidNowElapsedRealtimeMillis(
                    nowElapsedRealtimeMillis,
                ),
            )
        }
        if (staleAfterMillis <= 0L) {
            return CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.InvalidStaleAfterMillis(staleAfterMillis),
            )
        }
        if (target == null) {
            return CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.MissingTarget,
            )
        }
        if (sample == null) {
            return CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.MissingSample,
            )
        }
        if (sample.elapsedRealtimeMillis > nowElapsedRealtimeMillis) {
            return CalorieTargetEvaluationResult.Unavailable(
                CalorieTargetEvaluationFailure.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = sample.elapsedRealtimeMillis,
                    nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                ),
            )
        }

        val sampleAgeMillis = nowElapsedRealtimeMillis - sample.elapsedRealtimeMillis
        return CalorieTargetEvaluationResult.Evaluated(
            CalorieTargetEvaluation(
                target = target,
                displayValue = sample.displayValue,
                sampleAgeMillis = sampleAgeMillis,
                freshness = if (sampleAgeMillis < staleAfterMillis) {
                    CalorieSampleFreshness.FRESH
                } else {
                    CalorieSampleFreshness.STALE
                },
                estimateStatus = CalorieEstimateStatus.ESTIMATED,
                source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
                unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
                sessionResetSemantics = CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
                completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
                progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
                previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
                deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            ),
        )
    }
}
