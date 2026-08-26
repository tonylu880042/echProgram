package com.echelon.console.domain

/**
 * Describes who supplied the target rather than how it was calculated.
 *
 * The FitOS API does not own a target zone and this contract deliberately does
 * not derive one from age, maximum heart rate, or another medical formula.
 */
enum class HeartRateTargetSource {
    USER_CONFIRMED,
}

enum class HeartRateTargetBound {
    LOWER,
    UPPER,
}

/** A positive, inclusive heart-rate target range confirmed by the user. */
class HeartRateTargetRange private constructor(
    val lowerBpm: Int,
    val upperBpm: Int,
    val source: HeartRateTargetSource,
) {
    companion object {
        /**
         * Builds the only currently supported target source without throwing
         * for user input errors.
         */
        fun createUserConfirmed(
            lowerBpm: Int?,
            upperBpm: Int?,
        ): HeartRateTargetRangeResult {
            if (lowerBpm == null) {
                return HeartRateTargetRangeResult.Rejected(
                    HeartRateTargetRangeFailure.MissingLowerBound,
                )
            }
            if (upperBpm == null) {
                return HeartRateTargetRangeResult.Rejected(
                    HeartRateTargetRangeFailure.MissingUpperBound,
                )
            }
            if (lowerBpm <= 0) {
                return HeartRateTargetRangeResult.Rejected(
                    HeartRateTargetRangeFailure.NonPositiveBound(
                        bound = HeartRateTargetBound.LOWER,
                        value = lowerBpm,
                    ),
                )
            }
            if (upperBpm <= 0) {
                return HeartRateTargetRangeResult.Rejected(
                    HeartRateTargetRangeFailure.NonPositiveBound(
                        bound = HeartRateTargetBound.UPPER,
                        value = upperBpm,
                    ),
                )
            }
            if (lowerBpm > upperBpm) {
                return HeartRateTargetRangeResult.Rejected(
                    HeartRateTargetRangeFailure.LowerAboveUpper(
                        lowerBpm = lowerBpm,
                        upperBpm = upperBpm,
                    ),
                )
            }
            return HeartRateTargetRangeResult.Accepted(
                HeartRateTargetRange(
                    lowerBpm = lowerBpm,
                    upperBpm = upperBpm,
                    source = HeartRateTargetSource.USER_CONFIRMED,
                ),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is HeartRateTargetRange &&
            lowerBpm == other.lowerBpm &&
            upperBpm == other.upperBpm &&
            source == other.source

    override fun hashCode(): Int = 31 * (31 * lowerBpm + upperBpm) + source.hashCode()

    override fun toString(): String =
        "HeartRateTargetRange(lowerBpm=$lowerBpm, upperBpm=$upperBpm, source=$source)"
}

sealed interface HeartRateTargetRangeResult {
    data class Accepted(val target: HeartRateTargetRange) : HeartRateTargetRangeResult

    data class Rejected(val failure: HeartRateTargetRangeFailure) : HeartRateTargetRangeResult
}

sealed interface HeartRateTargetRangeFailure {
    data object MissingLowerBound : HeartRateTargetRangeFailure

    data object MissingUpperBound : HeartRateTargetRangeFailure

    data class NonPositiveBound(
        val bound: HeartRateTargetBound,
        val value: Int,
    ) : HeartRateTargetRangeFailure

    data class LowerAboveUpper(
        val lowerBpm: Int,
        val upperBpm: Int,
    ) : HeartRateTargetRangeFailure
}

/** A validated FitOS heart-rate sample and its host elapsed-realtime timestamp. */
class HeartRateSample private constructor(
    val bpm: Int,
    val elapsedRealtimeMillis: Long,
) {
    companion object {
        /** Creates a sample without interpreting the BPM as a medical limit. */
        fun create(
            bpm: Int?,
            elapsedRealtimeMillis: Long?,
        ): HeartRateSampleResult {
            if (bpm == null) {
                return HeartRateSampleResult.Rejected(HeartRateSampleFailure.MissingBpm)
            }
            if (elapsedRealtimeMillis == null) {
                return HeartRateSampleResult.Rejected(HeartRateSampleFailure.MissingTimestamp)
            }
            if (bpm <= 0) {
                return HeartRateSampleResult.Rejected(
                    HeartRateSampleFailure.NonPositiveBpm(bpm),
                )
            }
            if (elapsedRealtimeMillis < 0L) {
                return HeartRateSampleResult.Rejected(
                    HeartRateSampleFailure.NegativeTimestamp(elapsedRealtimeMillis),
                )
            }
            return HeartRateSampleResult.Accepted(
                HeartRateSample(
                    bpm = bpm,
                    elapsedRealtimeMillis = elapsedRealtimeMillis,
                ),
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is HeartRateSample &&
            bpm == other.bpm &&
            elapsedRealtimeMillis == other.elapsedRealtimeMillis

    override fun hashCode(): Int = 31 * bpm + elapsedRealtimeMillis.hashCode()

    override fun toString(): String =
        "HeartRateSample(bpm=$bpm, elapsedRealtimeMillis=$elapsedRealtimeMillis)"
}

sealed interface HeartRateSampleResult {
    data class Accepted(val sample: HeartRateSample) : HeartRateSampleResult

    data class Rejected(val failure: HeartRateSampleFailure) : HeartRateSampleResult
}

sealed interface HeartRateSampleFailure {
    data object MissingBpm : HeartRateSampleFailure

    data object MissingTimestamp : HeartRateSampleFailure

    data class NonPositiveBpm(val value: Int) : HeartRateSampleFailure

    data class NegativeTimestamp(val value: Long) : HeartRateSampleFailure
}

enum class Zone2HeartRateStatus {
    TOO_LOW,
    IN_ZONE,
    TOO_HIGH,
    HR_SIGNAL_LOST,
}

/** Advisory text is intentionally a recommendation, never an equipment command. */
enum class Zone2HeartRateAdvice {
    SUGGEST_INCLINE,
    HOLD,
    SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE,
    NO_ADJUSTMENT_MANUAL_MODE,
}

enum class Zone2HeartRatePreviewStatus {
    PREVIEW_ONLY,
}

enum class Zone2HeartRateAdviceMode {
    ADVISORY_ONLY,
}

/** Explicitly records that this increment uses direct comparisons. */
enum class Zone2HeartRateThresholdMode {
    DIRECT_THRESHOLD_PREVIEW,
}

/** FitOS does not approve hysteresis semantics for this contract. */
enum class Zone2HeartRateHysteresisStatus {
    NO_HYSTERESIS_APPROVED,
}

/** The intended HR telemetry source for the ZONE 2 preview boundary. */
enum class Zone2HeartRateIntendedSource {
    FITOS_EQUIPMENT_SNAPSHOT,
}

data class Zone2HeartRateEvaluation(
    val status: Zone2HeartRateStatus,
    val advice: Zone2HeartRateAdvice,
    val sampleAgeMillis: Long,
    val currentBpm: Int,
    val target: HeartRateTargetRange,
    val previewStatus: Zone2HeartRatePreviewStatus,
    val adviceMode: Zone2HeartRateAdviceMode,
    val thresholdMode: Zone2HeartRateThresholdMode,
    val hysteresisStatus: Zone2HeartRateHysteresisStatus,
) {
    /** Backward-compatible source projection from the immutable target snapshot. */
    val targetSource: HeartRateTargetSource
        get() = target.source
}

sealed interface Zone2HeartRateEvaluationResult {
    data class Evaluated(val evaluation: Zone2HeartRateEvaluation) : Zone2HeartRateEvaluationResult

    data class Unavailable(val failure: Zone2HeartRateEvaluationFailure) : Zone2HeartRateEvaluationResult
}

sealed interface Zone2HeartRateEvaluationFailure {
    data object MissingTarget : Zone2HeartRateEvaluationFailure

    data object MissingHeartRate : Zone2HeartRateEvaluationFailure

    data class InvalidNowElapsedRealtimeMillis(val value: Long) : Zone2HeartRateEvaluationFailure

    data class InvalidStaleAfterMillis(val value: Long) : Zone2HeartRateEvaluationFailure

    data class FutureSampleTimestamp(
        val sampleElapsedRealtimeMillis: Long,
        val nowElapsedRealtimeMillis: Long,
    ) : Zone2HeartRateEvaluationFailure
}

/**
 * Evaluates a user-confirmed range against one host-timestamped sample.
 *
 * This is a deterministic, direct-threshold preview. It has no smoothing,
 * target-zone ownership, medical formula, motor setpoint, command, or ACK.
 */
class Zone2HeartRateEvaluator {
    fun evaluate(
        target: HeartRateTargetRange?,
        sample: HeartRateSample?,
        nowElapsedRealtimeMillis: Long,
        staleAfterMillis: Long,
    ): Zone2HeartRateEvaluationResult {
        if (nowElapsedRealtimeMillis < 0L) {
            return Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.InvalidNowElapsedRealtimeMillis(
                    nowElapsedRealtimeMillis,
                ),
            )
        }
        if (staleAfterMillis <= 0L) {
            return Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.InvalidStaleAfterMillis(staleAfterMillis),
            )
        }
        if (target == null) {
            return Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.MissingTarget,
            )
        }
        if (sample == null) {
            return Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.MissingHeartRate,
            )
        }
        if (sample.elapsedRealtimeMillis > nowElapsedRealtimeMillis) {
            return Zone2HeartRateEvaluationResult.Unavailable(
                Zone2HeartRateEvaluationFailure.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = sample.elapsedRealtimeMillis,
                    nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                ),
            )
        }

        val sampleAgeMillis = nowElapsedRealtimeMillis - sample.elapsedRealtimeMillis
        val statusAndAdvice = when {
            sampleAgeMillis >= staleAfterMillis ->
                Zone2HeartRateStatus.HR_SIGNAL_LOST to
                    Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE
            sample.bpm < target.lowerBpm ->
                Zone2HeartRateStatus.TOO_LOW to Zone2HeartRateAdvice.SUGGEST_INCLINE
            sample.bpm > target.upperBpm ->
                Zone2HeartRateStatus.TOO_HIGH to
                    Zone2HeartRateAdvice.SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE
            else -> Zone2HeartRateStatus.IN_ZONE to Zone2HeartRateAdvice.HOLD
        }
        return Zone2HeartRateEvaluationResult.Evaluated(
            Zone2HeartRateEvaluation(
                status = statusAndAdvice.first,
                advice = statusAndAdvice.second,
                sampleAgeMillis = sampleAgeMillis,
                currentBpm = sample.bpm,
                target = target,
                previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            ),
        )
    }
}
