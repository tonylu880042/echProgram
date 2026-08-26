package com.echelon.console.domain

/** The four customer-facing effort choices for SURPRISE ME. */
enum class SurpriseWorkoutEffort {
    EASY,
    SWEAT,
    BURN,
    HARD,
}

/**
 * Inputs used to create a deterministic SURPRISE ME draft.
 *
 * Speed values are tenths of mph and incline values are tenths of a percent.
 * The two maxima for each dimension are intersected with the approved global
 * envelope before any profile value is generated.
 */
data class SurpriseWorkoutGeneratorInput(
    val durationMinutes: Int,
    val effort: SurpriseWorkoutEffort,
    val userProfileRevision: String,
    val regenerationIndex: Int,
    val generatorVersion: String,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

/** A generated draft is still a preview and has not been accepted for control. */
enum class SurpriseWorkoutDraftControlStatus {
    PREVIEW_ONLY,
}

data class SurpriseWorkoutDraftMetadata(
    val programId: ProgramId,
    val durationMinutes: Int,
    val effort: SurpriseWorkoutEffort,
    val userProfileRevision: String,
    val regenerationIndex: Int,
    val generatorVersion: String,
    val stableSeed: Long,
)

/**
 * Immutable, pre-control metadata and concrete profile for a SURPRISE ME
 * preview. This type intentionally carries no accepted/started state and no
 * device command data.
 */
data class SurpriseWorkoutDraft(
    val metadata: SurpriseWorkoutDraftMetadata,
    val profile: List<ProgramSegmentSummary>,
    val effectiveSpeedCap: SpeedTenths,
    val effectiveInclineCap: InclineTenths,
    val controlStatus: SurpriseWorkoutDraftControlStatus,
)

enum class SurpriseWorkoutLimit {
    SPEED,
    INCLINE,
}

sealed interface SurpriseWorkoutGenerationFailure {
    data class UnsupportedDuration(
        val durationMinutes: Int,
    ) : SurpriseWorkoutGenerationFailure

    data object NegativeRegenerationIndex : SurpriseWorkoutGenerationFailure

    data object BlankUserProfileRevision : SurpriseWorkoutGenerationFailure

    data class InvalidGeneratorVersion(
        val version: String,
    ) : SurpriseWorkoutGenerationFailure

    data class CapsDoNotIntersect(
        val dimension: SurpriseWorkoutLimit,
        val userMaximum: Int,
        val machineMaximum: Int,
        val globalMinimum: Int,
    ) : SurpriseWorkoutGenerationFailure

    data class ConstraintsUnsatisfied(
        val detail: String,
    ) : SurpriseWorkoutGenerationFailure
}

sealed interface SurpriseWorkoutGenerationResult {
    data class Generated(
        val draft: SurpriseWorkoutDraft,
    ) : SurpriseWorkoutGenerationResult

    data class Rejected(
        val failure: SurpriseWorkoutGenerationFailure,
    ) : SurpriseWorkoutGenerationResult
}

/**
 * Pure deterministic generator for the SURPRISE ME preview contract.
 *
 * No Kotlin/Java runtime random source is used. The versioned FNV-1a plus
 * SplitMix-style arithmetic below is deliberately small and stable. Replay
 * also requires the draft's effective speed/incline caps: the seed identifies
 * the request, while those caps constrain the concrete profile.
 */
class SurpriseWorkoutGenerator {
    fun generate(input: SurpriseWorkoutGeneratorInput): SurpriseWorkoutGenerationResult {
        val validationFailure = validateInput(input)
        if (validationFailure != null) {
            return SurpriseWorkoutGenerationResult.Rejected(validationFailure)
        }

        val speedCap = minOf(
            GLOBAL_MAX_SPEED_TENTHS,
            input.userMaxSpeed.value,
            input.machineMaxSpeed.value,
        )
        val inclineCap = minOf(
            GLOBAL_MAX_INCLINE_TENTHS,
            input.userMaxIncline.value,
            input.machineMaxIncline.value,
        )
        val effortSpeedCap = minOf(speedCap, effortSpeedMaximum(input.effort))
        val effortInclineCap = minOf(inclineCap, effortInclineMaximum(input.effort))
        if (effortSpeedCap < GLOBAL_MIN_SPEED_TENTHS || effortInclineCap < GLOBAL_MIN_INCLINE_TENTHS) {
            return SurpriseWorkoutGenerationResult.Rejected(
                SurpriseWorkoutGenerationFailure.ConstraintsUnsatisfied(
                    "Effort envelope cannot produce a global-safe target",
                ),
            )
        }

        val seed = stableSeed(input)
        val random = StablePrng(seed)
        val profile = buildProfile(
            input = input,
            speedCap = effortSpeedCap,
            inclineCap = effortInclineCap,
            random = random,
        )
        val profileFailure = validateProfile(
            input = input,
            profile = profile,
            speedCap = speedCap,
            inclineCap = inclineCap,
        )
        if (profileFailure != null) {
            return SurpriseWorkoutGenerationResult.Rejected(
                SurpriseWorkoutGenerationFailure.ConstraintsUnsatisfied(profileFailure),
            )
        }

        return SurpriseWorkoutGenerationResult.Generated(
            SurpriseWorkoutDraft(
                metadata = SurpriseWorkoutDraftMetadata(
                    programId = ProgramId(PROGRAM_ID),
                    durationMinutes = input.durationMinutes,
                    effort = input.effort,
                    userProfileRevision = input.userProfileRevision,
                    regenerationIndex = input.regenerationIndex,
                    generatorVersion = input.generatorVersion,
                    stableSeed = seed,
                ),
                profile = profile.toList(),
                effectiveSpeedCap = SpeedTenths(speedCap),
                effectiveInclineCap = InclineTenths(inclineCap),
                controlStatus = SurpriseWorkoutDraftControlStatus.PREVIEW_ONLY,
            ),
        )
    }

    private fun validateInput(
        input: SurpriseWorkoutGeneratorInput,
    ): SurpriseWorkoutGenerationFailure? {
        if (input.durationMinutes !in SUPPORTED_DURATIONS) {
            return SurpriseWorkoutGenerationFailure.UnsupportedDuration(input.durationMinutes)
        }
        if (input.regenerationIndex < 0) {
            return SurpriseWorkoutGenerationFailure.NegativeRegenerationIndex
        }
        if (input.userProfileRevision.isBlank()) {
            return SurpriseWorkoutGenerationFailure.BlankUserProfileRevision
        }
        if (!isValidGeneratorVersion(input.generatorVersion)) {
            return SurpriseWorkoutGenerationFailure.InvalidGeneratorVersion(input.generatorVersion)
        }

        val speedMaximum = minOf(input.userMaxSpeed.value, input.machineMaxSpeed.value)
        if (speedMaximum < GLOBAL_MIN_SPEED_TENTHS) {
            return SurpriseWorkoutGenerationFailure.CapsDoNotIntersect(
                dimension = SurpriseWorkoutLimit.SPEED,
                userMaximum = input.userMaxSpeed.value,
                machineMaximum = input.machineMaxSpeed.value,
                globalMinimum = GLOBAL_MIN_SPEED_TENTHS,
            )
        }

        val inclineMaximum = minOf(input.userMaxIncline.value, input.machineMaxIncline.value)
        if (inclineMaximum < GLOBAL_MIN_INCLINE_TENTHS) {
            return SurpriseWorkoutGenerationFailure.CapsDoNotIntersect(
                dimension = SurpriseWorkoutLimit.INCLINE,
                userMaximum = input.userMaxIncline.value,
                machineMaximum = input.machineMaxIncline.value,
                globalMinimum = GLOBAL_MIN_INCLINE_TENTHS,
            )
        }

        return null
    }

    private fun buildProfile(
        input: SurpriseWorkoutGeneratorInput,
        speedCap: Int,
        inclineCap: Int,
        random: StablePrng,
    ): List<ProgramSegmentSummary> {
        val activeBlockCount = activeBlockCount(
            durationMinutes = input.durationMinutes,
            regenerationIndex = input.regenerationIndex,
        )
        val activeDurations = activeDurations(
            activeMinutes = input.durationMinutes - WARM_UP_MINUTES - COOL_DOWN_MINUTES,
            blockCount = activeBlockCount,
            random = random,
        )
        val recoveryIndex = when {
            input.effort != SurpriseWorkoutEffort.HARD -> -1
            else -> 1 + random.nextInt(activeBlockCount - 2)
        }

        var previous = Target(GLOBAL_MIN_SPEED_TENTHS, GLOBAL_MIN_INCLINE_TENTHS)
        val profile = mutableListOf<ProgramSegmentSummary>()

        fun appendSegment(name: String, durationMinutes: Int, target: Target) {
            profile += ProgramSegmentSummary(
                name = name,
                duration = DurationMinutes(durationMinutes),
                speed = SpeedTenths(target.speed),
                incline = InclineTenths(target.incline),
            )
            previous = target
        }

        appendSegment(
            name = "WARM UP",
            durationMinutes = WARM_UP_MINUTES,
            target = nextTarget(
                stage = Stage.WARM_UP,
                previous = previous,
                speedCap = speedCap,
                inclineCap = inclineCap,
                effort = input.effort,
                random = random,
            ),
        )

        activeDurations.forEachIndexed { index, durationMinutes ->
            val recovery = index == recoveryIndex
            val target = nextTarget(
                stage = if (recovery) Stage.RECOVERY else Stage.ACTIVE,
                previous = previous,
                speedCap = speedCap,
                inclineCap = inclineCap,
                effort = input.effort,
                random = random,
            )
            val ordinal = index + 1
            val name = when {
                recovery -> "RECOVERY $ordinal"
                index == activeDurations.lastIndex -> "FINAL PUSH"
                else -> "BUILD $ordinal"
            }
            appendSegment(name, durationMinutes, target)
        }

        appendSegment(
            name = "COOL DOWN",
            durationMinutes = COOL_DOWN_MINUTES,
            target = nextTarget(
                stage = Stage.COOL_DOWN,
                previous = previous,
                speedCap = speedCap,
                inclineCap = inclineCap,
                effort = input.effort,
                random = random,
            ),
        )
        return profile
    }

    private fun nextTarget(
        stage: Stage,
        previous: Target,
        speedCap: Int,
        inclineCap: Int,
        effort: SurpriseWorkoutEffort,
        random: StablePrng,
    ): Target {
        if (stage == Stage.RECOVERY || stage == Stage.COOL_DOWN) {
            return nonIncreasingTarget(
                previous = previous,
                speedCap = speedCap,
                inclineCap = inclineCap,
            )
        }

        val band = when (stage) {
            Stage.WARM_UP -> warmUpBand(speedCap, inclineCap)
            Stage.ACTIVE -> activeBand(effort, speedCap, inclineCap)
            Stage.RECOVERY,
            Stage.COOL_DOWN -> error("Handled before selecting a target band")
        }
        var desired = Target(
            speed = random.nextIntInclusive(band.minimumSpeed, band.maximumSpeed),
            incline = random.nextIntInclusive(band.minimumIncline, band.maximumIncline),
        )
        if (stage == Stage.ACTIVE && desired == Target(GLOBAL_MIN_SPEED_TENTHS, GLOBAL_MIN_INCLINE_TENTHS)) {
            desired = when {
                speedCap > GLOBAL_MIN_SPEED_TENTHS -> desired.copy(speed = GLOBAL_MIN_SPEED_TENTHS + 1)
                inclineCap > GLOBAL_MIN_INCLINE_TENTHS -> desired.copy(incline = GLOBAL_MIN_INCLINE_TENTHS + 1)
                else -> desired
            }
        }
        if (stage == Stage.WARM_UP) {
            return desired
        }
        return Target(
            speed = ramp(previous.speed, desired.speed, SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_SPEED_JUMP_TENTHS)
                .coerceIn(GLOBAL_MIN_SPEED_TENTHS, speedCap),
            incline = ramp(previous.incline, desired.incline, SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_INCLINE_JUMP_TENTHS)
                .coerceIn(GLOBAL_MIN_INCLINE_TENTHS, inclineCap),
        )
    }

    private fun nonIncreasingTarget(
        previous: Target,
        speedCap: Int,
        inclineCap: Int,
    ): Target {
        val maximumSpeed = minOf(
            previous.speed - 1,
            recoverySpeedCeiling(speedCap),
        )
        val maximumIncline = minOf(
            previous.incline - 1,
            recoveryInclineCeiling(inclineCap),
        )
        val desired = Target(
            speed = if (maximumSpeed >= GLOBAL_MIN_SPEED_TENTHS) {
                maximumSpeed
            } else {
                GLOBAL_MIN_SPEED_TENTHS
            },
            incline = if (maximumIncline >= GLOBAL_MIN_INCLINE_TENTHS) {
                maximumIncline
            } else {
                GLOBAL_MIN_INCLINE_TENTHS
            },
        )
        return Target(
            speed = ramp(previous.speed, desired.speed, SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_SPEED_JUMP_TENTHS)
                .coerceIn(GLOBAL_MIN_SPEED_TENTHS, speedCap),
            incline = ramp(previous.incline, desired.incline, SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_INCLINE_JUMP_TENTHS)
                .coerceIn(GLOBAL_MIN_INCLINE_TENTHS, inclineCap),
        )
    }

    /** Baseline proposal only: warm-up stays below 4.0 mph and 2% incline. */
    private fun warmUpBand(
        speedCap: Int,
        inclineCap: Int,
    ): TargetBand = TargetBand(
        SurpriseWorkoutWarmUpBaselineProposal.BASE_SPEED_TENTHS,
        SurpriseWorkoutWarmUpBaselineProposal.BASE_SPEED_TENTHS,
        0,
        0,
    )
        .clampedTo(speedCap, inclineCap)

    /**
     * Baseline proposal only: disjoint active lanes make effort ordering
     * observable while client-specific effort envelopes remain unapproved.
     */
    private fun activeBand(
        effort: SurpriseWorkoutEffort,
        speedCap: Int,
        inclineCap: Int,
    ): TargetBand = when (effort) {
        SurpriseWorkoutEffort.EASY -> TargetBand(28, 29, 0, 1).clampedTo(speedCap, inclineCap)
        SurpriseWorkoutEffort.SWEAT -> TargetBand(30, 31, 3, 4).clampedTo(speedCap, inclineCap)
        SurpriseWorkoutEffort.BURN -> TargetBand(33, 33, 6, 6).clampedTo(speedCap, inclineCap)
        SurpriseWorkoutEffort.HARD -> TargetBand(48, 80, 20, 35).clampedTo(speedCap, inclineCap)
    }

    private fun validateProfile(
        input: SurpriseWorkoutGeneratorInput,
        profile: List<ProgramSegmentSummary>,
        speedCap: Int,
        inclineCap: Int,
    ): String? {
        if (profile.isEmpty()) {
            return "Profile must contain at least one segment"
        }
        if (profile.sumOf { it.duration.value } != input.durationMinutes) {
            return "Profile duration must equal selected duration"
        }
        if (profile.none { it.name == "WARM UP" } || profile.none { it.name == "COOL DOWN" }) {
            return "Profile must include warm-up and cool-down"
        }
        if (profile.any { it.duration.value <= 0 }) {
            return "Profile segments must have positive durations"
        }
        if (profile.any { it.speed.value !in GLOBAL_MIN_SPEED_TENTHS..GLOBAL_MAX_SPEED_TENTHS }) {
            return "Speed target is outside the global envelope"
        }
        if (profile.any { it.incline.value !in GLOBAL_MIN_INCLINE_TENTHS..GLOBAL_MAX_INCLINE_TENTHS }) {
            return "Incline target is outside the global envelope"
        }
        if (profile.any { it.speed.value > speedCap || it.incline.value > inclineCap }) {
            return "Target exceeds the effective cap intersection"
        }
        if (input.effort == SurpriseWorkoutEffort.EASY && profile.any {
                it.speed.value > EASY_MAX_SPEED_TENTHS || it.incline.value > EASY_MAX_INCLINE_TENTHS
            }
        ) {
            return "EASY target exceeds its conservative baseline envelope"
        }
        if (input.effort == SurpriseWorkoutEffort.HARD && profile.none { it.name.startsWith("RECOVERY") }) {
            return "HARD profile must contain a recovery block"
        }
        profile.indexOfFirst { it.name.startsWith("RECOVERY") }
            .takeIf { it > 0 }
            ?.let { recoveryIndex ->
                val before = profile[recoveryIndex - 1]
                val recovery = profile[recoveryIndex]
                if (recovery.speed.value > before.speed.value || recovery.incline.value > before.incline.value) {
                    return "Recovery targets must not increase"
                }
                if (recovery.speed.value == before.speed.value && recovery.incline.value == before.incline.value) {
                    return "Recovery must lower at least one target when feasible"
                }
            }
        val coolDownBefore = profile[profile.lastIndex - 1]
        val coolDown = profile.last()
        if (coolDown.speed.value > coolDownBefore.speed.value || coolDown.incline.value > coolDownBefore.incline.value) {
            return "Cool-down targets must not increase"
        }
        if (coolDown.speed.value == coolDownBefore.speed.value && coolDown.incline.value == coolDownBefore.incline.value) {
            return "Cool-down must lower at least one target when feasible"
        }
        profile.zipWithNext().forEach { (previous, next) ->
            if (kotlin.math.abs(next.speed.value - previous.speed.value) >
                SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_SPEED_JUMP_TENTHS
            ) {
                return "Adjacent speed targets exceed the conservative ramp proposal"
            }
            if (kotlin.math.abs(next.incline.value - previous.incline.value) >
                SurpriseWorkoutRampBaselineProposal.MAX_ADJACENT_INCLINE_JUMP_TENTHS
            ) {
                return "Adjacent incline targets exceed the conservative ramp proposal"
            }
        }
        return null
    }

    private fun activeDurations(
        activeMinutes: Int,
        blockCount: Int,
        random: StablePrng,
    ): List<Int> {
        val baseDuration = activeMinutes / blockCount
        val remainder = activeMinutes % blockCount
        val durations = MutableList(blockCount) { baseDuration }
        repeat(remainder) { remainderIndex ->
            val blockIndex = (remainderIndex + random.nextInt(blockCount)) % blockCount
            durations[blockIndex] += 1
        }
        return durations
    }

    private fun stableSeed(input: SurpriseWorkoutGeneratorInput): Long {
        val material = buildString {
            append(PROGRAM_ID)
            append('|')
            append(input.durationMinutes)
            append('|')
            append(input.effort.name)
            append('|')
            append(input.userProfileRevision)
            append('|')
            append(input.generatorVersion)
            append('|')
            append(input.regenerationIndex)
        }
        var hash = FNV_OFFSET_BASIS
        material.toByteArray(Charsets.UTF_8).forEach { byte ->
            hash = (hash xor (byte.toInt() and 0xff).toLong()) * FNV_PRIME
        }
        return mix64(hash)
    }

    private fun isValidGeneratorVersion(version: String): Boolean =
        version == SUPPORTED_GENERATOR_VERSION

    private fun effortSpeedMaximum(effort: SurpriseWorkoutEffort): Int = when (effort) {
        SurpriseWorkoutEffort.EASY -> EASY_MAX_SPEED_TENTHS
        SurpriseWorkoutEffort.SWEAT -> SWEAT_MAX_SPEED_TENTHS
        SurpriseWorkoutEffort.BURN -> BURN_MAX_SPEED_TENTHS
        SurpriseWorkoutEffort.HARD -> GLOBAL_MAX_SPEED_TENTHS
    }

    private fun effortInclineMaximum(effort: SurpriseWorkoutEffort): Int = when (effort) {
        SurpriseWorkoutEffort.EASY -> EASY_MAX_INCLINE_TENTHS
        SurpriseWorkoutEffort.SWEAT -> SWEAT_MAX_INCLINE_TENTHS
        SurpriseWorkoutEffort.BURN -> BURN_MAX_INCLINE_TENTHS
        SurpriseWorkoutEffort.HARD -> GLOBAL_MAX_INCLINE_TENTHS
    }

    private fun recoverySpeedCeiling(speedCap: Int): Int =
        GLOBAL_MIN_SPEED_TENTHS + ((speedCap - GLOBAL_MIN_SPEED_TENTHS) * RECOVERY_RATIO_NUMERATOR / RECOVERY_RATIO_DENOMINATOR)

    private fun recoveryInclineCeiling(inclineCap: Int): Int =
        GLOBAL_MIN_INCLINE_TENTHS + ((inclineCap - GLOBAL_MIN_INCLINE_TENTHS) * RECOVERY_RATIO_NUMERATOR / RECOVERY_RATIO_DENOMINATOR)

    private fun ramp(current: Int, desired: Int, maximumJump: Int): Int =
        current + (desired - current).coerceIn(-maximumJump, maximumJump)

    private enum class Stage {
        WARM_UP,
        ACTIVE,
        RECOVERY,
        COOL_DOWN,
    }

    private data class Target(
        val speed: Int,
        val incline: Int,
    )

    private data class TargetBand(
        val minimumSpeed: Int,
        val maximumSpeed: Int,
        val minimumIncline: Int,
        val maximumIncline: Int,
    ) {
        fun clampedTo(speedCap: Int, inclineCap: Int): TargetBand {
            val boundedMaximumSpeed = maximumSpeed.coerceAtMost(speedCap)
            val boundedMaximumIncline = maximumIncline.coerceAtMost(inclineCap)
            return copy(
                minimumSpeed = minimumSpeed.coerceAtMost(boundedMaximumSpeed),
                maximumSpeed = boundedMaximumSpeed,
                minimumIncline = minimumIncline.coerceAtMost(boundedMaximumIncline),
                maximumIncline = boundedMaximumIncline,
            )
        }
    }

    private class StablePrng(seed: Long) {
        private var state = seed

        fun nextInt(untilExclusive: Int): Int {
            return ((nextLong() ushr 1) % untilExclusive.toLong()).toInt()
        }

        fun nextIntInclusive(minimum: Int, maximum: Int): Int {
            return minimum + nextInt(maximum - minimum + 1)
        }

        private fun nextLong(): Long {
            state += SPLIT_MIX_GOLDEN_GAMMA
            var mixed = state
            mixed = (mixed xor (mixed ushr 30)) * SPLIT_MIX_MULTIPLIER_ONE
            mixed = (mixed xor (mixed ushr 27)) * SPLIT_MIX_MULTIPLIER_TWO
            return mixed xor (mixed ushr 31)
        }
    }

    private companion object {
        const val PROGRAM_ID = "SURPRISE_ME"
        const val GLOBAL_MIN_SPEED_TENTHS = 25
        const val GLOBAL_MAX_SPEED_TENTHS = 80
        const val GLOBAL_MIN_INCLINE_TENTHS = 0
        const val GLOBAL_MAX_INCLINE_TENTHS = 100
        const val EASY_MAX_SPEED_TENTHS = 45
        const val EASY_MAX_INCLINE_TENTHS = 20
        const val SWEAT_MAX_SPEED_TENTHS = 60
        const val SWEAT_MAX_INCLINE_TENTHS = 50
        const val BURN_MAX_SPEED_TENTHS = 70
        const val BURN_MAX_INCLINE_TENTHS = 80
        const val WARM_UP_MINUTES = 2
        const val COOL_DOWN_MINUTES = 2
        const val RECOVERY_RATIO_NUMERATOR = 3
        const val RECOVERY_RATIO_DENOMINATOR = 10
        const val SUPPORTED_GENERATOR_VERSION = "v1"
        const val FNV_OFFSET_BASIS = -3750763034362895579L
        const val FNV_PRIME = 1099511628211L
        const val SPLIT_MIX_GOLDEN_GAMMA = -7046029254386353131L
        const val SPLIT_MIX_MULTIPLIER_ONE = -4658895280553007687L
        const val SPLIT_MIX_MULTIPLIER_TWO = -7723592293110705685L
        val SUPPORTED_DURATIONS = setOf(10, 20, 30, 45)

        fun activeBlockCount(durationMinutes: Int, regenerationIndex: Int): Int {
            val baseline = when (durationMinutes) {
                10 -> 3
                20 -> 5
                30 -> 7
                45 -> 9
                else -> error("Duration must be validated before profile generation")
            }
            return baseline + (regenerationIndex and 1)
        }

        fun mix64(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * SPLIT_MIX_MULTIPLIER_ONE
            mixed = (mixed xor (mixed ushr 27)) * SPLIT_MIX_MULTIPLIER_TWO
            return mixed xor (mixed ushr 31)
        }
    }
}

/**
 * Baseline proposal only: exact ramp limits remain a client/device safety
 * decision, but this conservative draft contract keeps adjacent changes small
 * until that decision is approved.
 */
object SurpriseWorkoutRampBaselineProposal {
    const val MAX_ADJACENT_SPEED_JUMP_TENTHS: Int = 5
    const val MAX_ADJACENT_INCLINE_JUMP_TENTHS: Int = 10
}

/**
 * Baseline proposal only: warm-up bounds are conservative defaults until the
 * customer/device safety envelope is approved.
 */
object SurpriseWorkoutWarmUpBaselineProposal {
    const val BASE_SPEED_TENTHS: Int = 28
    const val MAX_SPEED_TENTHS: Int = 40
    const val MAX_INCLINE_TENTHS: Int = 20
}
