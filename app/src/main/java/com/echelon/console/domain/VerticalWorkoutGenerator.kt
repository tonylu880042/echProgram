package com.echelon.console.domain

/** A target time limit proposal that is not a session duration. */
data class VerticalTimeLimitProposal(
    val minutes: Int,
    val status: VerticalTimeLimitStatus,
)

enum class VerticalTimeLimitStatus {
    PROPOSED,
}

/** The four VERTICAL targets currently listed for customer review. */
enum class VerticalTarget(
    val feet: Int,
    val proposedTimeLimit: VerticalTimeLimitProposal,
) {
    FIVE_HUNDRED_FEET(
        feet = 500,
        proposedTimeLimit = VerticalTimeLimitProposal(45, VerticalTimeLimitStatus.PROPOSED),
    ),
    ONE_THOUSAND_FEET(
        feet = 1_000,
        proposedTimeLimit = VerticalTimeLimitProposal(60, VerticalTimeLimitStatus.PROPOSED),
    ),
    TWO_THOUSAND_FEET(
        feet = 2_000,
        proposedTimeLimit = VerticalTimeLimitProposal(120, VerticalTimeLimitStatus.PROPOSED),
    ),
    VERTICAL_MILE(
        feet = 5_280,
        proposedTimeLimit = VerticalTimeLimitProposal(240, VerticalTimeLimitStatus.PROPOSED),
    ),
}

/** Inputs for a target-oriented representative preview. */
data class VerticalWorkoutGeneratorInput(
    val target: VerticalTarget,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
)

enum class VerticalWorkoutDraftMode {
    REPRESENTATIVE_PROFILE_PREVIEW,
}

enum class VerticalWorkoutDraftControlStatus {
    PREVIEW_ONLY,
}

enum class VerticalElevationSource {
    UNAVAILABLE,
}

enum class VerticalProgressStatus {
    NOT_CALCULATED,
}

enum class VerticalProfileSegmentRole {
    WARM_UP,
    BASE_CLIMB,
    BUILD,
    STEEP_BLOCK,
    FINISH_PUSH,
    COOL_DOWN,
}

enum class VerticalClampDimension {
    SPEED,
    INCLINE,
}

/** Identifies one clamped typed segment; the index disambiguates any future repeated labels. */
data class VerticalClampDisclosure(
    val segmentIndex: Int,
    val role: VerticalProfileSegmentRole,
    val dimensions: List<VerticalClampDimension>,
    val proposedSpeed: SpeedTenths,
    val proposedIncline: InclineTenths,
    val effectiveSpeed: SpeedTenths,
    val effectiveIncline: InclineTenths,
)

data class VerticalWorkoutDraftMetadata(
    val programId: ProgramId,
    val target: VerticalTarget,
    val proposedTimeLimit: VerticalTimeLimitProposal,
    val mode: VerticalWorkoutDraftMode,
    val elevationSource: VerticalElevationSource,
    val progressStatus: VerticalProgressStatus,
    val userMaxSpeed: SpeedTenths,
    val machineMaxSpeed: SpeedTenths,
    val userMaxIncline: InclineTenths,
    val machineMaxIncline: InclineTenths,
    val effectiveSpeedCap: SpeedTenths,
    val effectiveInclineCap: InclineTenths,
    val wasClamped: Boolean,
    val clampDisclosure: List<VerticalClampDisclosure>,
)

/** A typed profile segment that remains compatible with the generic timeline profile. */
data class VerticalWorkoutProfileSegment(
    val index: Int,
    val role: VerticalProfileSegmentRole,
    val summary: ProgramSegmentSummary,
)

/**
 * A deterministic VERTICAL representative preview.
 *
 * This is not an elevation calculation, target-completion claim, customer-confirmed time
 * limit, device-control command, or workout session duration. Every target uses
 * the same 50-minute proposal profile while target and proposed-limit metadata
 * remain explicit for customer review.
 */
data class VerticalWorkoutDraft(
    val metadata: VerticalWorkoutDraftMetadata,
    val segments: List<VerticalWorkoutProfileSegment>,
    val controlStatus: VerticalWorkoutDraftControlStatus,
) {
    /** Compatibility projection for the domain timeline compiler boundary. */
    val profile: List<ProgramSegmentSummary>
        get() = segments.map { it.summary }
}

sealed interface VerticalWorkoutGenerationFailure {
    data class InvalidSpeedCaps(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
    ) : VerticalWorkoutGenerationFailure

    data class InvalidInclineCaps(
        val userMaximum: InclineTenths,
        val machineMaximum: InclineTenths,
    ) : VerticalWorkoutGenerationFailure

    data class SpeedCapsDoNotIntersect(
        val userMaximum: SpeedTenths,
        val machineMaximum: SpeedTenths,
        val globalMinimum: SpeedTenths,
    ) : VerticalWorkoutGenerationFailure

    data class InclineCapsDoNotIntersect(
        val userMaximum: InclineTenths,
        val machineMaximum: InclineTenths,
        val globalMinimum: InclineTenths,
    ) : VerticalWorkoutGenerationFailure
}

sealed interface VerticalWorkoutGenerationResult {
    data class Generated(val draft: VerticalWorkoutDraft) : VerticalWorkoutGenerationResult

    data class Rejected(val failure: VerticalWorkoutGenerationFailure) : VerticalWorkoutGenerationResult
}

/**
 * Pure deterministic generator for the VERTICAL representative preview.
 *
 * The six profile blocks are the only documented representative-preview
 * proposal in the current spec. Their speed envelope is 2.5-4.0 mph and
 * incline envelope is 2-15%; caps can lower a target and every such change is
 * disclosed by typed segment index and role. No target-based scaling or
 * elevation formula is performed here.
 */
class VerticalWorkoutGenerator {
    fun generate(input: VerticalWorkoutGeneratorInput): VerticalWorkoutGenerationResult {
        val validationFailure = validateCaps(input)
        if (validationFailure != null) {
            return VerticalWorkoutGenerationResult.Rejected(validationFailure)
        }

        val effectiveSpeedCap = SpeedTenths(
            minOf(
                GLOBAL_MAX_SPEED_TENTHS,
                input.userMaxSpeed.value,
                input.machineMaxSpeed.value,
            ),
        )
        val effectiveInclineCap = InclineTenths(
            minOf(
                GLOBAL_MAX_INCLINE_TENTHS,
                input.userMaxIncline.value,
                input.machineMaxIncline.value,
            ),
        )

        val disclosures = mutableListOf<VerticalClampDisclosure>()
        val segments = PROPOSED_PROFILE.mapIndexed { index, proposal ->
            val speed = SpeedTenths(minOf(proposal.speed.value, effectiveSpeedCap.value))
            val incline = InclineTenths(minOf(proposal.incline.value, effectiveInclineCap.value))
            val dimensions = buildList {
                if (speed != proposal.speed) add(VerticalClampDimension.SPEED)
                if (incline != proposal.incline) add(VerticalClampDimension.INCLINE)
            }
            if (dimensions.isNotEmpty()) {
                disclosures += VerticalClampDisclosure(
                    segmentIndex = index,
                    role = proposal.role,
                    dimensions = dimensions,
                    proposedSpeed = proposal.speed,
                    proposedIncline = proposal.incline,
                    effectiveSpeed = speed,
                    effectiveIncline = incline,
                )
            }
            VerticalWorkoutProfileSegment(
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

        return VerticalWorkoutGenerationResult.Generated(
            VerticalWorkoutDraft(
                metadata = VerticalWorkoutDraftMetadata(
                    programId = ProgramId(PROGRAM_ID),
                    target = input.target,
                    proposedTimeLimit = input.target.proposedTimeLimit,
                    mode = VerticalWorkoutDraftMode.REPRESENTATIVE_PROFILE_PREVIEW,
                    elevationSource = VerticalElevationSource.UNAVAILABLE,
                    progressStatus = VerticalProgressStatus.NOT_CALCULATED,
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
                controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
            ),
        )
    }

    private fun validateCaps(
        input: VerticalWorkoutGeneratorInput,
    ): VerticalWorkoutGenerationFailure? {
        if (input.userMaxSpeed.value < 0 || input.machineMaxSpeed.value < 0) {
            return VerticalWorkoutGenerationFailure.InvalidSpeedCaps(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
            )
        }
        if (input.userMaxIncline.value < 0 || input.machineMaxIncline.value < 0) {
            return VerticalWorkoutGenerationFailure.InvalidInclineCaps(
                userMaximum = input.userMaxIncline,
                machineMaximum = input.machineMaxIncline,
            )
        }
        if (minOf(input.userMaxSpeed.value, input.machineMaxSpeed.value) < GLOBAL_MIN_SPEED_TENTHS) {
            return VerticalWorkoutGenerationFailure.SpeedCapsDoNotIntersect(
                userMaximum = input.userMaxSpeed,
                machineMaximum = input.machineMaxSpeed,
                globalMinimum = SpeedTenths(GLOBAL_MIN_SPEED_TENTHS),
            )
        }
        if (minOf(input.userMaxIncline.value, input.machineMaxIncline.value) < GLOBAL_MIN_INCLINE_TENTHS) {
            return VerticalWorkoutGenerationFailure.InclineCapsDoNotIntersect(
                userMaximum = input.userMaxIncline,
                machineMaximum = input.machineMaxIncline,
                globalMinimum = InclineTenths(GLOBAL_MIN_INCLINE_TENTHS),
            )
        }
        return null
    }

    private data class ProposedSegment(
        val name: String,
        val role: VerticalProfileSegmentRole,
        val durationMinutes: Int,
        val speed: SpeedTenths,
        val incline: InclineTenths,
    )

    private companion object {
        const val PROGRAM_ID = "VERTICAL"
        const val GLOBAL_MIN_SPEED_TENTHS = 25
        const val GLOBAL_MAX_SPEED_TENTHS = 40
        const val GLOBAL_MIN_INCLINE_TENTHS = 20
        const val GLOBAL_MAX_INCLINE_TENTHS = 150

        val PROPOSED_PROFILE = listOf(
            ProposedSegment(
                name = "WARM UP",
                role = VerticalProfileSegmentRole.WARM_UP,
                durationMinutes = 5,
                speed = SpeedTenths(25),
                incline = InclineTenths(40),
            ),
            ProposedSegment(
                name = "BASE CLIMB",
                role = VerticalProfileSegmentRole.BASE_CLIMB,
                durationMinutes = 10,
                speed = SpeedTenths(28),
                incline = InclineTenths(80),
            ),
            ProposedSegment(
                name = "BUILD",
                role = VerticalProfileSegmentRole.BUILD,
                durationMinutes = 10,
                speed = SpeedTenths(30),
                incline = InclineTenths(100),
            ),
            ProposedSegment(
                name = "STEEP BLOCK",
                role = VerticalProfileSegmentRole.STEEP_BLOCK,
                durationMinutes = 10,
                speed = SpeedTenths(28),
                incline = InclineTenths(120),
            ),
            ProposedSegment(
                name = "FINISH PUSH",
                role = VerticalProfileSegmentRole.FINISH_PUSH,
                durationMinutes = 10,
                speed = SpeedTenths(26),
                incline = InclineTenths(150),
            ),
            ProposedSegment(
                name = "COOL DOWN",
                role = VerticalProfileSegmentRole.COOL_DOWN,
                durationMinutes = 5,
                speed = SpeedTenths(25),
                incline = InclineTenths(20),
            ),
        )
    }
}
