package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.domain.DeviceCapabilities
import kotlinx.coroutines.CoroutineDispatcher

internal fun createProgramSetupViewModel(
    catalog: ProgramDetailCatalog,
    coordinator: InMemoryWorkoutSessionCoordinator,
    dispatcher: CoroutineDispatcher,
    capabilities: DeviceCapabilities?,
    zone2UseCase: StartZone2WorkoutPreview = StartZone2WorkoutPreview(catalog, coordinator),
    calorieUseCase: StartCalorieTargetPreview = StartCalorieTargetPreview(catalog, coordinator),
): ProgramSetupViewModel = ProgramSetupViewModel(
    getProgramDetail = GetProgramDetail(catalog),
    startWorkout = StartWorkout(coordinator, catalog),
    startSurpriseWorkoutDraft = StartSurpriseWorkoutDraft(coordinator),
    generateSurpriseWorkoutDraft = GenerateSurpriseWorkoutDraft(),
    startFiveKReadySessionDraft = StartFiveKReadySessionDraft(coordinator),
    generateFiveKReadySessionDraft = GenerateFiveKReadySessionDraft(),
    startVerticalWorkoutDraft = StartVerticalWorkoutDraft(coordinator),
    generateVerticalWorkoutDraft = GenerateVerticalWorkoutDraft(),
    startZone2WorkoutPreview = zone2UseCase,
    startCalorieTargetPreview = calorieUseCase,
    capabilities = capabilities,
    dispatcher = dispatcher,
)
