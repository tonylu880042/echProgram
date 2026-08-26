package com.echelon.console.data

import com.echelon.console.application.usecase.ProgramCatalog
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.Program
import com.echelon.console.domain.ProgramCategory
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths

/**
 * Immutable proposal catalog for every Program in the client review index.
 * Static profiles are for preview and validation only; they do not enable device control.
 */
class StaticProgramCatalog : ProgramCatalog, ProgramDetailCatalog {
    override fun listHeroPrograms(): List<HeroProgram> = definitions
        .filter { it.hero }
        .map { it.program.toHeroProgram() }

    override fun listPrograms(): List<Program> = definitions
        .filterNot { it.hero }
        .map { it.program }

    override fun findProgramDetail(programId: ProgramId): ProgramDetail? = definitions
        .firstOrNull { it.program.id == programId }
        ?.detail

    private companion object {
        val definitions: List<ProgramDefinition> = listOf(
            programDefinition(
                id = "FAT_BURN", title = "FAT BURN", category = ProgramCategory.BURN,
                durationLabel = "30 MIN", promise = "Sustained incline walking and light jogging for a controlled burn.",
                duration = 30, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 55, maxIncline = 120, speedRange = 28..55, inclineRange = 10..120, hero = true,
                profile = listOf(
                    segment("Warm Up", 5, 30, 10), segment("Climb", 3, 33, 50), segment("Climb", 3, 35, 80),
                    segment("Push", 3, 45, 30), segment("Climb", 3, 32, 100), segment("Push", 3, 50, 20),
                    segment("Big Climb", 3, 33, 120), segment("Finish", 3, 52, 20), segment("Cool Down", 4, 28, 10),
                ),
            ),
            programDefinition(
                id = "GLUTE_BLAST", title = "GLUTE BLAST", category = ProgramCategory.GLUTES_LEGS,
                durationLabel = "30 MIN", promise = "Hill-focused power walking for a glutes-and-legs challenge.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 40, maxIncline = 150, speedRange = 25..40, inclineRange = 40..150, hero = true,
                profile = listOf(
                    segment("Warm Up", 5, 27, 40), segment("Round 1 Activate", 5, 30, 50),
                    segment("Round 2 Build", 6, 31, 80), segment("Round 3 Burn", 6, 30, 110),
                    segment("Round 4 Power", 5, 28, 140), segment("Final Climb", 2, 26, 150),
                    segment("Cool Down", 1, 25, 40),
                ),
            ),
            programDefinition(
                id = "VERTICAL", title = "VERTICAL", category = ProgramCategory.CLIMB,
                durationLabel = "1,000 FT", promise = "A representative elevation profile for a clear climbing milestone.",
                duration = 50, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 40, maxIncline = 150, speedRange = 25..40, inclineRange = 20..150,
                previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW, hero = true,
                profile = listOf(
                    segment("Warm Up", 5, 25, 40), segment("Base Climb", 10, 28, 80),
                    segment("Build", 10, 30, 100), segment("Steep Block", 10, 28, 120),
                    segment("Finish Push", 10, 26, 150), segment("Cool Down", 5, 25, 20),
                ),
            ),
            programDefinition(
                id = "SURPRISE_ME", title = "SURPRISE ME", category = ProgramCategory.SURPRISE,
                durationLabel = "10–45 MIN", promise = "A deterministic sample profile to preview before a generator exists.",
                duration = 20, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 80, maxIncline = 100, speedRange = 25..80, inclineRange = 0..100,
                previewMode = ProgramPreviewMode.GENERATED_PREVIEW, hero = true,
                profile = listOf(
                    segment("Warm Up", 3, 28, 10), segment("Build 1", 3, 40, 30), segment("Recovery 1", 2, 30, 10),
                    segment("Build 2", 3, 50, 50), segment("Recovery 2", 2, 30, 10), segment("Build 3", 3, 55, 40),
                    segment("Final Push", 2, 60, 60), segment("Cool Down", 2, 28, 10),
                ),
            ),
            programDefinition(
                id = "SUMMIT", title = "SUMMIT", category = ProgramCategory.CLIMB,
                durationLabel = "30 MIN", promise = "A progressive climb with visible milestones toward a representative summit.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 45, maxIncline = 150, speedRange = 25..45, inclineRange = 10..150,
                profile = listOf(
                    segment("Base Camp", 5, 26, 10), segment("Lower Slope", 5, 28, 40), segment("Mid Climb", 5, 30, 70),
                    segment("Upper Climb", 5, 31, 100), segment("Summit Push", 5, 29, 130),
                    segment("Final Ascent", 3, 27, 150), segment("Descent / Cool Down", 2, 25, 20),
                ),
            ),
            programDefinition(
                id = "HIIT_20", title = "HIIT 20", category = ProgramCategory.SWEAT,
                durationLabel = "20 MIN", promise = "Short sprint and recovery intervals with a clear finish line.",
                duration = 20, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 90, maxIncline = 50, speedRange = 30..90, inclineRange = 10..50,
                profile = listOf(
                    segment("Warm Up", 4, 30, 10), segment("Sprint 1", 2, 70, 20), segment("Recover 1", 2, 35, 10),
                    segment("Sprint 2", 2, 80, 30), segment("Recover 2", 2, 35, 10), segment("Sprint 3", 2, 85, 40),
                    segment("Recover 3", 2, 35, 10), segment("Final Sprint", 2, 90, 50), segment("Cool Down", 2, 30, 10),
                ),
            ),
            programDefinition(
                id = "SWEAT_30", title = "SWEAT 30", category = ProgramCategory.SWEAT,
                durationLabel = "30 MIN", promise = "A mixed steady, build, push, and climb profile for a focused sweat.",
                duration = 30, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 70, maxIncline = 100, speedRange = 30..70, inclineRange = 10..100,
                profile = listOf(
                    segment("Warm Up", 5, 30, 10), segment("Steady", 5, 40, 30), segment("Build", 5, 50, 50),
                    segment("Push", 5, 60, 40), segment("Climb", 5, 45, 80), segment("Cool Down", 5, 30, 10),
                ),
            ),
            programDefinition(
                id = "SPEED_LAB", title = "SPEED LAB", category = ProgramCategory.GET_FASTER,
                durationLabel = "30 MIN", promise = "Speed intervals with form-first recoveries between fast blocks.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 100, maxIncline = 40, speedRange = 40..100, inclineRange = 0..40,
                profile = listOf(
                    segment("Warm Up", 4, 40, 0), segment("Form Drill", 4, 50, 10), segment("Interval 1", 3, 70, 10),
                    segment("Recovery 1", 3, 45, 0), segment("Interval 2", 3, 80, 20), segment("Recovery 2", 3, 45, 0),
                    segment("Interval 3", 3, 90, 30), segment("Recovery 3", 3, 45, 0), segment("Final Interval", 2, 100, 40),
                    segment("Cool Down", 2, 40, 0),
                ),
            ),
            programDefinition(
                id = "BOOTY_BURN_15", title = "BOOTY BURN 15", category = ProgramCategory.GLUTES_LEGS,
                durationLabel = "15 MIN", promise = "A short, steep hill profile for a concentrated glutes-and-legs preview.",
                duration = 15, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 38, maxIncline = 150, speedRange = 25..38, inclineRange = 60..150,
                profile = listOf(
                    segment("Warm Up", 2, 25, 60), segment("Activate", 3, 28, 80), segment("Burn", 3, 30, 110),
                    segment("Power", 3, 28, 140), segment("Final Climb", 2, 26, 150), segment("Cool Down", 2, 25, 60),
                ),
            ),
            programDefinition(
                id = "5K_READY", title = "5K READY", category = ProgramCategory.RUN,
                durationLabel = "30 MIN", promise = "A baseline run-walk sample for previewing time on feet.",
                duration = 30, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 60, maxIncline = 60, speedRange = 28..60, inclineRange = 0..60,
                previewMode = ProgramPreviewMode.BASELINE_PREVIEW,
                profile = listOf(
                    segment("Warm Up Walk", 5, 30, 10), segment("Easy Run", 5, 40, 10), segment("Walk Recovery", 3, 35, 10),
                    segment("Steady Run", 5, 43, 20), segment("Walk Recovery", 2, 35, 10), segment("Steady Run", 5, 43, 20),
                    segment("Easy Walk", 2, 32, 10), segment("Cool Down", 3, 28, 0),
                ),
            ),
            programDefinition(
                id = "ECHELON_CHALLENGE", title = "ECHELON CHALLENGE", category = ProgramCategory.CHALLENGE,
                durationLabel = "30 MIN", promise = "A representative baseline-match profile with one controlled challenge block.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.BALANCED,
                maxSpeed = 60, maxIncline = 30, speedRange = 30..80, inclineRange = 0..100,
                previewMode = ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW,
                profile = listOf(
                    segment("Warm Up", 5, 45, 20), segment("Base Match", 7, 55, 20), segment("Challenge Block", 7, 58, 30),
                    segment("Hold", 6, 56, 30), segment("Final Push", 3, 60, 30), segment("Cool Down", 2, 43, 10),
                ),
            ),
            programDefinition(
                id = "TWELVE_3_30", title = "12-3-30", category = ProgramCategory.CLIMB,
                durationLabel = "30 MIN", promise = "A safe representative preview of the fixed 12% incline walking concept.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 30, maxIncline = 120, speedRange = 25..30, inclineRange = 10..120,
                profile = listOf(
                    segment("Warm Up", 5, 25, 10), segment("12-3-20 Core", 20, 30, 120), segment("Cool Down", 5, 25, 10),
                ),
            ),
            programDefinition(
                id = "POWER_WALK", title = "POWER WALK", category = ProgramCategory.WALK,
                durationLabel = "30 MIN", promise = "A progressive fast-walk profile that builds power without a run block.",
                duration = 30, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 45, maxIncline = 60, speedRange = 28..45, inclineRange = 0..60,
                profile = listOf(
                    segment("Warm Up", 5, 28, 10), segment("Base Walk", 5, 35, 20), segment("Build", 5, 38, 30),
                    segment("Power Block", 5, 42, 40), segment("Finish Push", 5, 45, 50), segment("Cool Down", 5, 28, 10),
                ),
            ),
            programDefinition(
                id = "ROLLING_HILLS", title = "ROLLING HILLS", category = ProgramCategory.STAMINA,
                durationLabel = "45 MIN", promise = "Changing hill and valley blocks for a steady rolling-stamina preview.",
                duration = 45, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.MORE_INCLINE,
                maxSpeed = 55, maxIncline = 80, speedRange = 28..55, inclineRange = 0..80,
                profile = listOf(
                    segment("Warm Up", 5, 28, 10), segment("Hill 1", 7, 34, 40), segment("Valley 1", 5, 42, 10),
                    segment("Hill 2", 7, 36, 60), segment("Valley 2", 5, 42, 10), segment("Hill 3", 7, 35, 80),
                    segment("Valley 3", 5, 40, 20), segment("Final Hill", 3, 33, 60), segment("Cool Down", 1, 28, 10),
                ),
            ),
            programDefinition(
                id = "ZONE_2", title = "ZONE 2", category = ProgramCategory.HEART_RATE,
                durationLabel = "30 MIN", promise = "A representative phase profile that stays preview-only until HR is connected.",
                duration = 30, intensity = PlanIntensity.LOW, focus = PlanFocus.BALANCED,
                maxSpeed = 50, maxIncline = 80, speedRange = 25..50, inclineRange = 0..80,
                previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
                profile = listOf(
                    segment("Warm Up", 5, 25, 10), segment("Settle", 5, 30, 20), segment("Maintain", 10, 32, 30),
                    segment("Check", 5, 32, 30), segment("Cool Down", 5, 25, 10),
                ),
            ),
            programDefinition(
                id = "WALK_RUN", title = "WALK + RUN", category = ProgramCategory.RUN,
                durationLabel = "30 MIN", promise = "Clearly timed walk and run blocks with deliberate recovery windows.",
                duration = 30, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 70, maxIncline = 50, speedRange = 30..70, inclineRange = 0..50,
                profile = listOf(
                    segment("Warm Up Walk", 5, 30, 10), segment("Power Walk", 5, 35, 20), segment("Run 1", 3, 55, 10),
                    segment("Walk Recovery 1", 2, 35, 20), segment("Run 2", 3, 60, 20), segment("Walk Recovery 2", 2, 35, 20),
                    segment("Run 3", 3, 65, 30), segment("Walk Recovery 3", 2, 35, 10), segment("Cool Down", 5, 30, 10),
                ),
            ),
            programDefinition(
                id = "ENDURANCE", title = "ENDURANCE", category = ProgramCategory.STAMINA,
                durationLabel = "45 MIN", promise = "A steady, lower-variation profile for building time on the treadmill.",
                duration = 45, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 60, maxIncline = 60, speedRange = 30..60, inclineRange = 0..60,
                profile = listOf(
                    segment("Warm Up", 5, 30, 10), segment("Settle", 10, 40, 20), segment("Steady 1", 10, 45, 30),
                    segment("Steady 2", 10, 50, 30), segment("Finish", 5, 55, 40), segment("Cool Down", 5, 30, 10),
                ),
            ),
            programDefinition(
                id = "PYRAMID", title = "PYRAMID", category = ProgramCategory.SWEAT,
                durationLabel = "30 MIN", promise = "A clear rise to a peak followed by controlled recovery blocks.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 80, maxIncline = 60, speedRange = 28..80, inclineRange = 0..60,
                profile = listOf(
                    segment("Warm Up", 5, 30, 10), segment("Rise 1", 3, 40, 10), segment("Rise 2", 3, 50, 20),
                    segment("Rise 3", 3, 60, 30), segment("Peak", 3, 70, 40), segment("Fall 1", 3, 60, 30),
                    segment("Fall 2", 3, 50, 20), segment("Fall 3", 3, 40, 10), segment("Cool Down", 4, 28, 10),
                ),
            ),
            programDefinition(
                id = "RECOVERY_WALK", title = "RECOVERY WALK", category = ProgramCategory.RECOVERY,
                durationLabel = "20 MIN", promise = "A low-variation easy walk for a calm reset pace.",
                duration = 20, intensity = PlanIntensity.LOW, focus = PlanFocus.BALANCED,
                maxSpeed = 35, maxIncline = 30, speedRange = 20..35, inclineRange = 0..30,
                profile = listOf(
                    segment("Gentle Start", 5, 20, 0), segment("Easy Walk", 10, 25, 10), segment("Cool Down", 5, 20, 0),
                ),
            ),
            programDefinition(
                id = "QUICK_10", title = "QUICK 10", category = ProgramCategory.QUICK,
                durationLabel = "10 MIN", promise = "A complete short profile with a fast start and safe finish.",
                duration = 10, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.MORE_SPEED,
                maxSpeed = 60, maxIncline = 50, speedRange = 25..60, inclineRange = 0..50,
                profile = listOf(
                    segment("Warm Up", 2, 25, 10), segment("Build 1", 2, 40, 20), segment("Build 2", 2, 50, 30),
                    segment("Finish", 2, 60, 20), segment("Cool Down", 2, 25, 10),
                ),
            ),
            programDefinition(
                id = "CALORIE_TARGET", title = "CALORIE TARGET", category = ProgramCategory.BURN,
                durationLabel = "100–500 CAL", promise = "A representative estimated-calorie profile with an explicit target caveat.",
                duration = 40, intensity = PlanIntensity.MEDIUM, focus = PlanFocus.BALANCED,
                maxSpeed = 60, maxIncline = 100, speedRange = 25..60, inclineRange = 0..100,
                previewMode = ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
                profile = listOf(
                    segment("Warm Up", 5, 28, 10), segment("Base", 10, 35, 20), segment("Build", 10, 45, 40),
                    segment("Push", 10, 55, 60), segment("Cool Down", 5, 28, 10),
                ),
            ),
            programDefinition(
                id = "ECHELON_HYBRID_RUN", title = "ECHELON HYBRID RUN", category = ProgramCategory.HYBRID,
                durationLabel = "30 MIN", promise = "A treadmill-only sample switching between run, climb, walk, and sprint demands.",
                duration = 30, intensity = PlanIntensity.HIGH, focus = PlanFocus.BALANCED,
                maxSpeed = 90, maxIncline = 80, speedRange = 30..90, inclineRange = 0..80,
                profile = listOf(
                    segment("Warm Up", 5, 35, 10), segment("Run", 5, 55, 10), segment("Climb", 5, 45, 60),
                    segment("Run", 5, 60, 20), segment("Power Walk", 5, 40, 80), segment("Sprint", 3, 80, 30),
                    segment("Cool Down", 2, 30, 10),
                ),
            ),
        )
    }
}

private data class ProgramDefinition(
    val program: Program,
    val detail: ProgramDetail,
    val hero: Boolean,
)

private fun programDefinition(
    id: String,
    title: String,
    category: ProgramCategory,
    durationLabel: String,
    promise: String,
    duration: Int,
    intensity: PlanIntensity,
    focus: PlanFocus,
    maxSpeed: Int,
    maxIncline: Int,
    speedRange: IntRange,
    inclineRange: IntRange,
    profile: List<ProgramSegmentSummary>,
    hero: Boolean = false,
    previewMode: ProgramPreviewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
): ProgramDefinition {
    val programId = ProgramId(id)
    val settings = PlanSettings(
        duration = DurationMinutes(duration),
        intensity = intensity,
        focus = focus,
        maxSpeed = SpeedTenths(maxSpeed),
        maxIncline = InclineTenths(maxIncline),
        adaptToYou = false,
    )
    return ProgramDefinition(
        program = Program(programId, title, category, durationLabel, promise),
        detail = ProgramDetail(
            programId = programId,
            title = title,
            promise = promise,
            defaultSettings = settings,
            speedRange = SpeedRange(SpeedTenths(speedRange.first), SpeedTenths(speedRange.last)),
            inclineRange = InclineRange(InclineTenths(inclineRange.first), InclineTenths(inclineRange.last)),
            profile = profile,
            previewMode = previewMode,
        ),
        hero = hero,
    )
}

private fun segment(name: String, duration: Int, speed: Int, incline: Int): ProgramSegmentSummary =
    ProgramSegmentSummary(name, DurationMinutes(duration), SpeedTenths(speed), InclineTenths(incline))

private fun Program.toHeroProgram(): HeroProgram = HeroProgram(id, title, promise, durationLabel)
