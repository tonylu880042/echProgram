package com.echelon.console.domain

/** The only mode available when ECHELON_CHALLENGE has no approved history. */
enum class EchelonChallengeBaselinePreviewMode {
    BASELINE_FALLBACK_PREVIEW,
}

/** A fallback profile is never presented as a formal history-based challenge. */
enum class EchelonChallengeBaselineStatus {
    NOT_A_CHALLENGE,
}

enum class EchelonChallengeHistorySource {
    NO_APPROVED_HISTORY,
}

enum class EchelonChallengeHistoryStatus {
    NO_APPROVED_HISTORY_OR_PERSISTENCE,
}

enum class EchelonChallengeComparisonStatus {
    DISABLED_NOT_AVAILABLE,
}

enum class EchelonChallengePersonalBestStatus {
    NOT_EVALUATED_NOT_AUTHORIZED,
}

enum class EchelonChallengeCompletionComparisonStatus {
    NOT_AUTHORIZED,
}

enum class EchelonChallengeAdaptStatus {
    DISABLED,
}

enum class EchelonChallengeProfileProposalStatus {
    NOT_CLIENT_APPROVED,
}

enum class EchelonChallengeBaselineControlStatus {
    PREVIEW_ONLY,
}

enum class EchelonChallengeDeviceCommandStatus {
    NO_DEVICE_COMMANDS,
}

/** Neutral labels keep the fallback free of historical comparison claims. */
enum class EchelonChallengeBaselineSegmentRole {
    WARM_UP,
    STEADY,
    BUILD,
    HOLD,
    FINISH,
    COOL_DOWN,
}

enum class EchelonChallengeBaselineClampDimension {
    SPEED,
    INCLINE,
}

data class EchelonChallengeBaselineClampDisclosure(
    val segmentIndex: Int,
    val role: EchelonChallengeBaselineSegmentRole,
    val dimensions: List<EchelonChallengeBaselineClampDimension>,
    val proposedSpeed: SpeedTenths,
    val proposedIncline: InclineTenths,
    val effectiveSpeed: SpeedTenths,
    val effectiveIncline: InclineTenths,
)

data class EchelonChallengeBaselinePreviewMetadata(
    val programId: ProgramId,
    val representativeProfileDuration: DurationMinutes,
    val mode: EchelonChallengeBaselinePreviewMode,
    val baselineStatus: EchelonChallengeBaselineStatus,
    val historySource: EchelonChallengeHistorySource,
    val historyStatus: EchelonChallengeHistoryStatus,
    val comparisonStatus: EchelonChallengeComparisonStatus,
    val personalBestStatus: EchelonChallengePersonalBestStatus,
    val completionComparisonStatus: EchelonChallengeCompletionComparisonStatus,
    val adaptStatus: EchelonChallengeAdaptStatus,
    val profileProposalStatus: EchelonChallengeProfileProposalStatus,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
    val effectiveSpeedCap: SpeedTenths,
    val effectiveInclineCap: InclineTenths,
    val wasClamped: Boolean,
    val clampDisclosure: List<EchelonChallengeBaselineClampDisclosure>,
)

data class EchelonChallengeBaselinePreviewSegment(
    val index: Int,
    val role: EchelonChallengeBaselineSegmentRole,
    val summary: ProgramSegmentSummary,
)

/** Immutable representative fallback data; it contains no history or challenge result. */
data class EchelonChallengeBaselinePreviewDraft(
    val metadata: EchelonChallengeBaselinePreviewMetadata,
    val segments: List<EchelonChallengeBaselinePreviewSegment>,
    val controlStatus: EchelonChallengeBaselineControlStatus,
    val deviceCommandStatus: EchelonChallengeDeviceCommandStatus,
) {
    val profile: List<ProgramSegmentSummary>
        get() = segments.map { it.summary }
}

data class EchelonChallengeBaselinePreviewInput(
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

sealed interface EchelonChallengeBaselinePreviewGenerationFailure {
    data class InvalidSpeedCaps(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
    ) : EchelonChallengeBaselinePreviewGenerationFailure

    data class InvalidInclineCaps(
        val userMaximum: InclineTenths,
        val machineMaximum: InclineTenths,
    ) : EchelonChallengeBaselinePreviewGenerationFailure

    data class SpeedCapsDoNotIntersect(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
        val globalMinimum: SpeedTenths,
    ) : EchelonChallengeBaselinePreviewGenerationFailure
}

sealed interface EchelonChallengeBaselinePreviewResult {
    data class Generated(
        val draft: EchelonChallengeBaselinePreviewDraft,
    ) : EchelonChallengeBaselinePreviewResult

    data class Rejected(
        val failure: EchelonChallengeBaselinePreviewGenerationFailure,
    ) : EchelonChallengeBaselinePreviewResult
}

/**
 * Generates the deterministic 30-minute fallback for ECHELON_CHALLENGE when
 * no approved history or persistence exists. It does not infer a baseline,
 * compare performance, calculate a score, or issue a device command.
 */
class EchelonChallengeBaselinePreviewGenerator {
    fun generate(
        input: EchelonChallengeBaselinePreviewInput,
    ): EchelonChallengeBaselinePreviewResult {
        val capFailure = validateCaps(input)
        if (capFailure != null) {
            return EchelonChallengeBaselinePreviewResult.Rejected(capFailure)
        }

        val effectiveSpeedCap = SpeedTenths(
            minOf(FALLBACK_MAX_SPEED_TENTHS, input.userMaxSpeed.value, input.machineMaxSpeed.value),
        )
        val effectiveInclineCap = InclineTenths(
            minOf(FALLBACK_MAX_INCLINE_TENTHS, input.userMaxIncline.value, input.machineMaxIncline.value),
        )
        val disclosures = mutableListOf<EchelonChallengeBaselineClampDisclosure>()
        val segments = FALLBACK_PROFILE.mapIndexed { index, proposal ->
            val speed = SpeedTenths(minOf(proposal.speed.value, effectiveSpeedCap.value))
            val incline = InclineTenths(minOf(proposal.incline.value, effectiveInclineCap.value))
            val dimensions = buildList {
                if (speed != proposal.speed) add(EchelonChallengeBaselineClampDimension.SPEED)
                if (incline != proposal.incline) add(EchelonChallengeBaselineClampDimension.INCLINE)
            }
            if (dimensions.isNotEmpty()) {
                disclosures += EchelonChallengeBaselineClampDisclosure(
                    segmentIndex = index,
                    role = proposal.role,
                    dimensions = dimensions,
                    proposedSpeed = proposal.speed,
                    proposedIncline = proposal.incline,
                    effectiveSpeed = speed,
                    effectiveIncline = incline,
                )
            }
            EchelonChallengeBaselinePreviewSegment(
                index = index,
                role = proposal.role,
                summary = ProgramSegmentSummary(
                    name = proposal.name,
                    duration = DurationMinutes(proposal.durationMinutes),
                    speed = speed,
                    incline = incline,
                ),
            )
        }.toList()

        return EchelonChallengeBaselinePreviewResult.Generated(
            EchelonChallengeBaselinePreviewDraft(
                metadata = EchelonChallengeBaselinePreviewMetadata(
                    programId = ProgramId(PROGRAM_ID),
                    representativeProfileDuration = DurationMinutes(REPRESENTATIVE_PROFILE_MINUTES),
                    mode = EchelonChallengeBaselinePreviewMode.BASELINE_FALLBACK_PREVIEW,
                    baselineStatus = EchelonChallengeBaselineStatus.NOT_A_CHALLENGE,
                    historySource = EchelonChallengeHistorySource.NO_APPROVED_HISTORY,
                    historyStatus = EchelonChallengeHistoryStatus.NO_APPROVED_HISTORY_OR_PERSISTENCE,
                    comparisonStatus = EchelonChallengeComparisonStatus.DISABLED_NOT_AVAILABLE,
                    personalBestStatus = EchelonChallengePersonalBestStatus.NOT_EVALUATED_NOT_AUTHORIZED,
                    completionComparisonStatus = EchelonChallengeCompletionComparisonStatus.NOT_AUTHORIZED,
                    adaptStatus = EchelonChallengeAdaptStatus.DISABLED,
                    profileProposalStatus = EchelonChallengeProfileProposalStatus.NOT_CLIENT_APPROVED,
                    userMaxSpeed = input.userMaxSpeed,
                    machineMaxSpeed = input.machineMaxSpeed,
                    userMaxIncline = input.userMaxIncline,
                    machineMaxIncline = input.machineMaxIncline,
                    effectiveSpeedCap = effectiveSpeedCap,
                    effectiveInclineCap = effectiveInclineCap,
                    wasClamped = disclosures.isNotEmpty(),
                    clampDisclosure = disclosures.toList(),
                ),
                segments = segments,
                controlStatus = EchelonChallengeBaselineControlStatus.PREVIEW_ONLY,
                deviceCommandStatus = EchelonChallengeDeviceCommandStatus.NO_DEVICE_COMMANDS,
            ),
        )
    }

    private fun validateCaps(
        input: EchelonChallengeBaselinePreviewInput,
    ): EchelonChallengeBaselinePreviewGenerationFailure? {
        if (input.userMaxSpeed.value < 0 || input.machineMaxSpeed.value < 0) {
            return EchelonChallengeBaselinePreviewGenerationFailure.InvalidSpeedCaps(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
            )
        }
        if (input.userMaxIncline.value < 0 || input.machineMaxIncline.value < 0) {
            return EchelonChallengeBaselinePreviewGenerationFailure.InvalidInclineCaps(
                userMaximum = input.userMaxIncline,
                machineMaximum = input.machineMaxIncline,
            )
        }
        if (minOf(input.userMaxSpeed.value, input.machineMaxSpeed.value) < FALLBACK_MIN_SPEED_TENTHS) {
            return EchelonChallengeBaselinePreviewGenerationFailure.SpeedCapsDoNotIntersect(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
                globalMinimum = SpeedTenths(FALLBACK_MIN_SPEED_TENTHS),
            )
        }
        return null
    }

    private data class ProposedSegment(
        val name: String,
        val role: EchelonChallengeBaselineSegmentRole,
        val durationMinutes: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
    )

    private companion object {
        const val PROGRAM_ID = "ECHELON_CHALLENGE"
        const val REPRESENTATIVE_PROFILE_MINUTES = 30
        const val FALLBACK_MIN_SPEED_TENTHS = 30
        const val FALLBACK_MAX_SPEED_TENTHS = 53
        const val FALLBACK_MAX_INCLINE_TENTHS = 40

        val FALLBACK_PROFILE = listOf(
            ProposedSegment(
                name = "WARM UP",
                role = EchelonChallengeBaselineSegmentRole.WARM_UP,
                durationMinutes = 5,
                speed = SpeedTenths(40),
                incline = InclineTenths(30),
            ),
            ProposedSegment(
                name = "STEADY",
                role = EchelonChallengeBaselineSegmentRole.STEADY,
                durationMinutes = 7,
                speed = SpeedTenths(50),
                incline = InclineTenths(30),
            ),
            ProposedSegment(
                name = "BUILD",
                role = EchelonChallengeBaselineSegmentRole.BUILD,
                durationMinutes = 7,
                speed = SpeedTenths(53),
                incline = InclineTenths(40),
            ),
            ProposedSegment(
                name = "HOLD",
                role = EchelonChallengeBaselineSegmentRole.HOLD,
                durationMinutes = 6,
                speed = SpeedTenths(51),
                incline = InclineTenths(40),
            ),
            ProposedSegment(
                name = "FINISH",
                role = EchelonChallengeBaselineSegmentRole.FINISH,
                durationMinutes = 3,
                speed = SpeedTenths(53),
                incline = InclineTenths(40),
            ),
            ProposedSegment(
                name = "COOL DOWN",
                role = EchelonChallengeBaselineSegmentRole.COOL_DOWN,
                durationMinutes = 2,
                speed = SpeedTenths(38),
                incline = InclineTenths(10),
            ),
        )
    }
}
