package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.WorkoutSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramSetupStaticCatalogTest {
    @Test
    fun `vertical and surprise me enter representative live sessions from setup`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            listOf(
                ProgramId("VERTICAL") to ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                ProgramId("SURPRISE_ME") to ProgramPreviewMode.GENERATED_PREVIEW,
            ).forEach { (programId, expectedMode) ->
                val catalog = StaticProgramCatalog()
                val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
                val viewModel = ProgramSetupViewModel(
                    getProgramDetail = GetProgramDetail(catalog),
                    startWorkout = StartWorkout(coordinator, catalog),
                    startSurpriseWorkoutDraft = StartSurpriseWorkoutDraft(coordinator),
                    generateSurpriseWorkoutDraft = GenerateSurpriseWorkoutDraft(),
                    startFiveKReadySessionDraft = StartFiveKReadySessionDraft(coordinator),
                    generateFiveKReadySessionDraft = GenerateFiveKReadySessionDraft(),
                    startVerticalWorkoutDraft = StartVerticalWorkoutDraft(coordinator),
                    generateVerticalWorkoutDraft = GenerateVerticalWorkoutDraft(),
                    capabilities = compositionCapabilities,
                    dispatcher = dispatcher,
                )

                viewModel.onAction(ProgramSetupAction.OpenProgram(programId))
                advanceUntilIdle()
                viewModel.onAction(ProgramSetupAction.StartDefault)
                advanceUntilIdle()

                if (programId == ProgramId("VERTICAL")) {
                    val configuring = viewModel.state.value as ProgramSetupUiState.VerticalConfiguring
                    assertEquals(VerticalTarget.ONE_THOUSAND_FEET, configuring.target)
                    assertNull(coordinator.currentState())

                    viewModel.onAction(ProgramSetupAction.GenerateVerticalPreview)
                    val draftPreview = viewModel.state.value as ProgramSetupUiState.VerticalDraftPreview
                    assertEquals(programId, draftPreview.draft.metadata.programId)
                    assertNull(coordinator.currentState())

                    viewModel.onAction(ProgramSetupAction.AcceptVerticalPlan)
                } else if (programId == ProgramId("SURPRISE_ME")) {
                    val draftPreview = viewModel.state.value as ProgramSetupUiState.DraftPreview
                    assertEquals(programId, draftPreview.draft.metadata.programId)
                    assertNull(coordinator.currentState())

                    viewModel.onAction(ProgramSetupAction.AcceptSurprisePlan)
                }

                val started = viewModel.state.value as ProgramSetupUiState.Started
                assertEquals(programId, started.plan.plan.programId)
                assertEquals(expectedMode, started.previewMode)
                assertTrue(coordinator.currentState() is WorkoutSessionState.Running)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        val compositionCapabilities = DeviceCapabilities(
            duration = DurationLimits(
                min = DurationMinutes(10),
                max = DurationMinutes(60),
                step = DurationMinutes(5),
            ),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )
    }
}
