package com.echelon.console.presentation

import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.VerticalTarget

sealed interface ProgramSetupAction {
    data class OpenProgram(val programId: ProgramId) : ProgramSetupAction

    data object MakeItYours : ProgramSetupAction

    data object Back : ProgramSetupAction

    data object StartDefault : ProgramSetupAction

    data class SetDuration(val duration: DurationMinutes) : ProgramSetupAction

    data class SetIntensity(val intensity: PlanIntensity) : ProgramSetupAction

    data class SetMaxSpeed(val maxSpeed: SpeedTenths) : ProgramSetupAction

    data class SetMaxIncline(val maxIncline: InclineTenths) : ProgramSetupAction

    data class SetFocus(val focus: PlanFocus) : ProgramSetupAction

    data class SetAdaptToYou(val adaptToYou: Boolean) : ProgramSetupAction

    data object StartCustomized : ProgramSetupAction

    data class SetSurpriseDuration(val duration: DurationMinutes) : ProgramSetupAction

    data class SetSurpriseEffort(val effort: SurpriseWorkoutEffort) : ProgramSetupAction

    data object GenerateSurprisePreview : ProgramSetupAction

    data object RegenerateSurprisePreview : ProgramSetupAction

    data object AcceptSurprisePlan : ProgramSetupAction

    data class SetFiveKReadyDuration(val duration: DurationMinutes) : ProgramSetupAction

    data class SetFiveKReadyBaselinePace(val text: String) : ProgramSetupAction

    data object GenerateFiveKReadyPreview : ProgramSetupAction

    data object AcceptFiveKReadyPlan : ProgramSetupAction

    data class SetVerticalTarget(val target: VerticalTarget) : ProgramSetupAction

    data object GenerateVerticalPreview : ProgramSetupAction

    data object AcceptVerticalPlan : ProgramSetupAction
}
