package com.echelon.console.domain

/** A baseline pace is typed so a preview cannot silently invent a personal pace. */
enum class FiveKReadyBaselineSource {
    USER_ENTERED,
    HISTORY,
    TEST_FLOW,
}

data class FiveKReadyBaselinePace(
    val speed: SpeedTenths,
    val source: FiveKReadyBaselineSource,
)

data class FiveKReadySessionGeneratorInput(
    val durationMinutes: Int,
    val baselinePace: FiveKReadyBaselinePace?,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

/** This increment intentionally describes one preview session, not progression. */
enum class FiveKReadyDraftMode {
    SINGLE_SESSION_PREVIEW,
}

enum class FiveKReadySessionControlStatus {
    PREVIEW_ONLY,
}

data class FiveKReadyClampSummary(
    val speedSegmentNames: List<String>,
    val inclineSegmentNames: List<String>,
)

data class FiveKReadySessionDraftMetadata(
    val programId: ProgramId,
    val mode: FiveKReadyDraftMode,
    val durationMinutes: Int,
    val baselinePace: FiveKReadyBaselinePace,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
    /** Stable v1 input fingerprint used to reject altered draft provenance. */
    val replayFingerprint: String,
    val wasClamped: Boolean,
    val clampSummary: FiveKReadyClampSummary?,
) {
    val baselineSource: FiveKReadyBaselineSource
        get() = baselinePace.source
}

enum class FiveKReadySegmentRole {
    WARM_UP_WALK,
    RUN,
    WALK_RECOVERY,
    EASY_WALK,
    COOL_DOWN,
}

data class FiveKReadySessionSegment(
    val role: FiveKReadySegmentRole,
    val summary: ProgramSegmentSummary,
    val runOrdinal: Int?,
    val totalRuns: Int?,
)

data class FiveKReadyRunWalkSummary(
    val runMinutes: Int,
    val walkMinutes: Int,
)

/**
 * Immutable, single-session 5K READY preview metadata and concrete profile.
 * It does not represent a multi-week plan, a completion guarantee, history,
 * device control, or a medical recommendation.
 */
data class FiveKReadySessionDraft(
    val metadata: FiveKReadySessionDraftMetadata,
    val segments: List<FiveKReadySessionSegment>,
    val effectiveSpeedCap: SpeedTenths,
    val effectiveInclineCap: InclineTenths,
    val runWalkSummary: FiveKReadyRunWalkSummary,
    val controlStatus: FiveKReadySessionControlStatus,
) {
    /** Compatibility map for WorkoutTimelineCompiler and other domain ports. */
    val profile: List<ProgramSegmentSummary>
        get() = segments.map { it.summary }
}

sealed interface FiveKReadySessionGenerationFailure {
    data class UnsupportedDuration(
        val durationMinutes: Int,
    ) : FiveKReadySessionGenerationFailure

    data object BaselineRequired : FiveKReadySessionGenerationFailure

    data class BaselineSourceNotUserEntered(
        val source: FiveKReadyBaselineSource,
    ) : FiveKReadySessionGenerationFailure

    data class BaselineOutsideGlobalEnvelope(
        val speed: SpeedTenths,
    ) : FiveKReadySessionGenerationFailure

    data class BaselineExceedsEffectiveSpeedCap(
        val baseline: SpeedTenths,
        val effectiveCap: SpeedTenths,
    ) : FiveKReadySessionGenerationFailure

    data class BaselineLeavesNoRecoveryMargin(
        val baseline: SpeedTenths,
        val minimumWalkSpeed: SpeedTenths,
    ) : FiveKReadySessionGenerationFailure

    data class InvalidSpeedCap(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
    ) : FiveKReadySessionGenerationFailure

    data class InvalidInclineCap(
        val userMaximum: InclineTenths,
        val machineMaximum: InclineTenths,
    ) : FiveKReadySessionGenerationFailure

    data class SpeedCapsDoNotIntersect(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
        val globalMinimum: SpeedTenths,
    ) : FiveKReadySessionGenerationFailure

    data class InclineCapsDoNotIntersect(
        val userMaximum: InclineTenths,
        val machineMaximum: InclineTenths,
        val globalMinimum: InclineTenths,
    ) : FiveKReadySessionGenerationFailure
}

sealed interface FiveKReadySessionGenerationResult {
    data class Generated(
        val draft: FiveKReadySessionDraft,
    ) : FiveKReadySessionGenerationResult

    data class Rejected(
        val failure: FiveKReadySessionGenerationFailure,
    ) : FiveKReadySessionGenerationResult
}

/**
 * Deterministic baseline-template generator for one 5K READY session.
 *
 * The 30-minute profile follows the proposal in `08_5k_ready.txt`; the other
 * durations are conservative single-session templates with the same run/walk
 * vocabulary. A baseline is never inferred from the example pace.
 */
class FiveKReadySessionGenerator {
    fun generate(input: FiveKReadySessionGeneratorInput): FiveKReadySessionGenerationResult {
        if (input.durationMinutes !in SUPPORTED_DURATIONS) {
            return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.UnsupportedDuration(input.durationMinutes),
            )
        }

        val baseline = input.baselinePace
            ?: return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineRequired,
            )
        if (baseline.source != FiveKReadyBaselineSource.USER_ENTERED) {
            return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineSourceNotUserEntered(baseline.source),
            )
        }

        val capFailure = validateCaps(input)
        if (capFailure != null) {
            return FiveKReadySessionGenerationResult.Rejected(capFailure)
        }

        val effectiveSpeedCap = SpeedTenths(
            minOf(GLOBAL_MAX_SPEED_TENTHS, input.userMaxSpeed.value, input.machineMaxSpeed.value),
        )
        val effectiveInclineCap = InclineTenths(
            minOf(GLOBAL_MAX_INCLINE_TENTHS, input.userMaxIncline.value, input.machineMaxIncline.value),
        )
        if (baseline.speed.value !in BASELINE_MIN_SPEED_TENTHS..GLOBAL_MAX_SPEED_TENTHS) {
            return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope(baseline.speed),
            )
        }
        if (baseline.speed.value == BASELINE_MIN_SPEED_TENTHS) {
            return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineLeavesNoRecoveryMargin(
                    baseline = baseline.speed,
                    minimumWalkSpeed = SpeedTenths(BASELINE_MIN_SPEED_TENTHS),
                ),
            )
        }
        if (baseline.speed.value > effectiveSpeedCap.value) {
            return FiveKReadySessionGenerationResult.Rejected(
                FiveKReadySessionGenerationFailure.BaselineExceedsEffectiveSpeedCap(
                    baseline = baseline.speed,
                    effectiveCap = effectiveSpeedCap,
                ),
            )
        }

        val blocks = template(input.durationMinutes)
        val speedClamped = mutableListOf<String>()
        val inclineClamped = mutableListOf<String>()
        val totalRuns = blocks.count { it.kind.isRun }
        var nextRunOrdinal = 0
        val segments = blocks.map { block ->
            val rawTarget = target(block.kind, baseline.speed.value)
            val speed = rawTarget.speed.coerceIn(GLOBAL_MIN_SPEED_TENTHS, effectiveSpeedCap.value)
            val incline = rawTarget.incline.coerceIn(GLOBAL_MIN_INCLINE_TENTHS, effectiveInclineCap.value)
            if (speed != rawTarget.speed) {
                speedClamped += block.name
            }
            if (incline != rawTarget.incline) {
                inclineClamped += block.name
            }
            val summary = ProgramSegmentSummary(
                name = block.name,
                duration = DurationMinutes(block.durationMinutes),
                speed = SpeedTenths(speed),
                incline = InclineTenths(incline),
            )
            val runOrdinal = if (block.kind.isRun) {
                nextRunOrdinal += 1
                nextRunOrdinal
            } else {
                null
            }
            FiveKReadySessionSegment(
                role = block.kind.role,
                summary = summary,
                runOrdinal = runOrdinal,
                totalRuns = totalRuns.takeIf { block.kind.isRun },
            )
        }.toList()
        val clampSummary = if (speedClamped.isEmpty() && inclineClamped.isEmpty()) {
            null
        } else {
            FiveKReadyClampSummary(
                speedSegmentNames = speedClamped.toList(),
                inclineSegmentNames = inclineClamped.toList(),
            )
        }
        val runMinutes = blocks.filter { it.kind.isRun }.sumOf { it.durationMinutes }
        val walkMinutes = input.durationMinutes - runMinutes

        return FiveKReadySessionGenerationResult.Generated(
            FiveKReadySessionDraft(
                metadata = FiveKReadySessionDraftMetadata(
                    programId = ProgramId(PROGRAM_ID),
                    mode = FiveKReadyDraftMode.SINGLE_SESSION_PREVIEW,
                    durationMinutes = input.durationMinutes,
                    baselinePace = baseline,
                    userMaxSpeed = input.userMaxSpeed,
                    machineMaxSpeed = input.machineMaxSpeed,
                    userMaxIncline = input.userMaxIncline,
                    machineMaxIncline = input.machineMaxIncline,
                    replayFingerprint = replayFingerprint(input),
                    wasClamped = clampSummary != null,
                    clampSummary = clampSummary,
                ),
                segments = segments,
                effectiveSpeedCap = effectiveSpeedCap,
                effectiveInclineCap = effectiveInclineCap,
                runWalkSummary = FiveKReadyRunWalkSummary(
                    runMinutes = runMinutes,
                    walkMinutes = walkMinutes,
                ),
                controlStatus = FiveKReadySessionControlStatus.PREVIEW_ONLY,
            ),
        )
    }

    private fun validateCaps(
        input: FiveKReadySessionGeneratorInput,
    ): FiveKReadySessionGenerationFailure? {
        if (input.userMaxSpeed.value < 0 || input.machineMaxSpeed.value < 0) {
            return FiveKReadySessionGenerationFailure.InvalidSpeedCap(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
            )
        }
        if (input.userMaxIncline.value < 0 || input.machineMaxIncline.value < 0) {
            return FiveKReadySessionGenerationFailure.InvalidInclineCap(
                userMaximum = input.userMaxIncline,
                machineMaximum = input.machineMaxIncline,
            )
        }
        if (minOf(input.userMaxSpeed.value, input.machineMaxSpeed.value) < BASELINE_MIN_SPEED_TENTHS) {
            return FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
                globalMinimum = SpeedTenths(BASELINE_MIN_SPEED_TENTHS),
            )
        }
        if (minOf(input.userMaxIncline.value, input.machineMaxIncline.value) < GLOBAL_MIN_INCLINE_TENTHS) {
            return FiveKReadySessionGenerationFailure.InclineCapsDoNotIntersect(
                userMaximum = input.userMaxIncline,
                machineMaximum = input.machineMaxIncline,
                globalMinimum = InclineTenths(GLOBAL_MIN_INCLINE_TENTHS),
            )
        }
        return null
    }

    private fun template(durationMinutes: Int): List<TemplateBlock> = when (durationMinutes) {
        20 -> listOf(
            TemplateBlock("WARM UP WALK", 3, BlockKind.WARM_UP),
            TemplateBlock("EASY RUN", 3, BlockKind.BASELINE_RUN),
            TemplateBlock("WALK RECOVERY", 2, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 3, BlockKind.STEADY_RUN),
            TemplateBlock("WALK RECOVERY", 2, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 3, BlockKind.STEADY_RUN),
            TemplateBlock("EASY WALK", 1, BlockKind.EASY_WALK),
            TemplateBlock("COOL DOWN", 3, BlockKind.COOL_DOWN),
        )

        30 -> listOf(
            TemplateBlock("WARM UP WALK", 5, BlockKind.WARM_UP),
            TemplateBlock("EASY RUN", 5, BlockKind.BASELINE_RUN),
            TemplateBlock("WALK RECOVERY", 3, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 5, BlockKind.STEADY_RUN),
            TemplateBlock("WALK RECOVERY", 2, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 5, BlockKind.STEADY_RUN),
            TemplateBlock("EASY WALK", 2, BlockKind.EASY_WALK),
            TemplateBlock("COOL DOWN", 3, BlockKind.COOL_DOWN),
        )

        40 -> listOf(
            TemplateBlock("WARM UP WALK", 5, BlockKind.WARM_UP),
            TemplateBlock("EASY RUN", 7, BlockKind.BASELINE_RUN),
            TemplateBlock("WALK RECOVERY", 3, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 7, BlockKind.STEADY_RUN),
            TemplateBlock("WALK RECOVERY", 3, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 7, BlockKind.STEADY_RUN),
            TemplateBlock("EASY WALK", 3, BlockKind.EASY_WALK),
            TemplateBlock("COOL DOWN", 5, BlockKind.COOL_DOWN),
        )

        60 -> listOf(
            TemplateBlock("WARM UP WALK", 8, BlockKind.WARM_UP),
            TemplateBlock("EASY RUN", 10, BlockKind.BASELINE_RUN),
            TemplateBlock("WALK RECOVERY", 4, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 10, BlockKind.STEADY_RUN),
            TemplateBlock("WALK RECOVERY", 4, BlockKind.RECOVERY),
            TemplateBlock("STEADY RUN", 10, BlockKind.STEADY_RUN),
            TemplateBlock("EASY WALK", 4, BlockKind.EASY_WALK),
            TemplateBlock("COOL DOWN", 10, BlockKind.COOL_DOWN),
        )

        else -> error("Unsupported duration should be rejected before template selection")
    }

    private fun target(kind: BlockKind, baselineSpeed: Int): RawTarget = when (kind) {
        BlockKind.WARM_UP -> RawTarget(speed = walkSpeed(preferred = 30, baselineSpeed), incline = 10)
        BlockKind.BASELINE_RUN -> RawTarget(speed = baselineSpeed, incline = 10)
        BlockKind.RECOVERY -> RawTarget(speed = walkSpeed(preferred = 35, baselineSpeed), incline = 10)
        BlockKind.STEADY_RUN -> RawTarget(speed = baselineSpeed + STEADY_RUN_DELTA, incline = 20)
        BlockKind.EASY_WALK -> RawTarget(speed = walkSpeed(preferred = 32, baselineSpeed), incline = 10)
        BlockKind.COOL_DOWN -> RawTarget(speed = walkSpeed(preferred = 28, baselineSpeed), incline = 0)
    }

    private fun walkSpeed(preferred: Int, baselineSpeed: Int): Int =
        minOf(preferred, baselineSpeed - 1)

    private fun replayFingerprint(input: FiveKReadySessionGeneratorInput): String = buildString {
        append("5K_READY|v1|")
        append(input.durationMinutes)
        append('|')
        append(input.baselinePace?.speed?.value)
        append('|')
        append(input.baselinePace?.source?.name)
        append('|')
        append(input.userMaxSpeed.value)
        append('|')
        append(input.machineMaxSpeed.value)
        append('|')
        append(input.userMaxIncline.value)
        append('|')
        append(input.machineMaxIncline.value)
    }

    private data class TemplateBlock(
        val name: String,
        val durationMinutes: Int,
        val kind: BlockKind,
    )

    private enum class BlockKind(
        val role: FiveKReadySegmentRole,
        val isRun: Boolean,
    ) {
        WARM_UP(FiveKReadySegmentRole.WARM_UP_WALK, false),
        BASELINE_RUN(FiveKReadySegmentRole.RUN, true),
        RECOVERY(FiveKReadySegmentRole.WALK_RECOVERY, false),
        STEADY_RUN(FiveKReadySegmentRole.RUN, true),
        EASY_WALK(FiveKReadySegmentRole.EASY_WALK, false),
        COOL_DOWN(FiveKReadySegmentRole.COOL_DOWN, false),
    }

    private data class RawTarget(
        val speed: Int,
        val incline: Int,
    )

    private companion object {
        const val PROGRAM_ID = "5K_READY"
        const val STEADY_RUN_DELTA = 3
        const val GLOBAL_MIN_SPEED_TENTHS = 28
        const val GLOBAL_MAX_SPEED_TENTHS = 60
        const val BASELINE_MIN_SPEED_TENTHS = 28
        const val GLOBAL_MIN_INCLINE_TENTHS = 0
        const val GLOBAL_MAX_INCLINE_TENTHS = 60
        val SUPPORTED_DURATIONS = setOf(20, 30, 40, 60)
    }
}
