package com.echelon.console.presentation

sealed interface LiveWorkoutAction {
    data object PauseResume : LiveWorkoutAction

    data object End : LiveWorkoutAction
}
