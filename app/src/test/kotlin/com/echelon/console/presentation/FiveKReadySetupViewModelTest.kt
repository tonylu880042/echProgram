package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
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
import com.echelon.console.domain.WorkoutSessionState
import kotlinx.coroutines.CoroutineDispatcher
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
class FiveKReadySetupViewModelTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(
            min = DurationMinutes(10),
            max = DurationMinutes(60),
            step = DurationMinutes(5),
        ),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `5K make it yours also enters configuring without starting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("5K_READY")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)

            assertTrue(viewModel.state.value is ProgramSetupUiState.FiveKReadyConfiguring)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `5K default enters configuring without starting static profile and accepts exact generated draft`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)

            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("5K_READY")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            val configuring = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertEquals(DurationMinutes(30), configuring.duration)
            assertEquals("", configuring.baselinePaceText)
            assertEquals(SpeedTenths(60), configuring.userMaxSpeed)
            assertEquals(SpeedTenths(120), configuring.machineMaxSpeed)
            assertNull(coordinator.currentState())

            viewModel.onAction(ProgramSetupAction.SetFiveKReadyDuration(DurationMinutes(20)))
            viewModel.onAction(ProgramSetupAction.SetFiveKReadyBaselinePace("4.0"))
            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)

            val preview = viewModel.state.value as ProgramSetupUiState.FiveKReadyDraftPreview
            assertEquals(20, preview.draft.metadata.durationMinutes)
            assertEquals(SpeedTenths(40), preview.draft.metadata.baselinePace.speed)
            assertEquals(
                com.echelon.console.domain.FiveKReadyBaselineSource.USER_ENTERED,
                preview.draft.metadata.baselineSource,
            )
            assertNull(coordinator.currentState())

            viewModel.onAction(ProgramSetupAction.Back)
            val preserved = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertEquals(DurationMinutes(20), preserved.duration)
            assertEquals("4.0", preserved.baselinePaceText)
            assertEquals(SpeedTenths(60), preserved.userMaxSpeed)
            assertEquals(SpeedTenths(120), preserved.machineMaxSpeed)

            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)
            val acceptedDraft = viewModel.state.value
                .let { it as ProgramSetupUiState.FiveKReadyDraftPreview }
                .draft
            viewModel.onAction(ProgramSetupAction.AcceptFiveKReadyPlan)

            val started = viewModel.state.value as ProgramSetupUiState.Started
            assertEquals(ProgramPreviewMode.BASELINE_PREVIEW, started.previewMode)
            val running = coordinator.currentState() as WorkoutSessionState.Running
            assertEquals(ProgramId("5K_READY"), running.timeline.programId)
            assertEquals(
                acceptedDraft.profile.map { it.name },
                running.timeline.segments.map { it.name },
            )
            assertEquals(
                acceptedDraft.profile.map { it.duration.value * 60 },
                running.timeline.segments.map { it.durationSeconds },
            )
            assertEquals(
                acceptedDraft.profile.map { it.speed.value },
                running.timeline.segments.map { it.targetSpeed.value },
            )
            assertEquals(
                acceptedDraft.profile.map { it.incline.value },
                running.timeline.segments.map { it.targetIncline.value },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `5K missing or unsafe baseline stays configuring and never starts`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("5K_READY")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)
            val missing = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertTrue(missing.errorMessage!!.contains("RUN PACE"))
            assertNull(coordinator.currentState())

            viewModel.onAction(ProgramSetupAction.SetFiveKReadyBaselinePace("2.8"))
            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)
            val unsafe = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertTrue(unsafe.errorMessage!!.contains("recovery", ignoreCase = true))
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `5K baseline accepts one decimal pace but rejects extra precision`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("5K_READY")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            viewModel.onAction(ProgramSetupAction.SetFiveKReadyBaselinePace("4.04"))
            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)

            val state = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertTrue(state.errorMessage!!.contains("RUN PACE"))
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `5K unsupported machine speed reports capability failure without starting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(
                dispatcher = dispatcher,
                catalog = catalog,
                coordinator = coordinator,
                capabilitiesOverride = capabilities.copy(
                    speed = SpeedRange(SpeedTenths(20), SpeedTenths(27)),
                ),
            )
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("5K_READY")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(ProgramSetupAction.SetFiveKReadyBaselinePace("4.0"))
            viewModel.onAction(ProgramSetupAction.GenerateFiveKReadyPreview)

            val state = viewModel.state.value as ProgramSetupUiState.FiveKReadyConfiguring
            assertEquals("CAPABILITIES CANNOT SUPPORT THIS PREVIEW", state.errorMessage)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        catalog: StaticProgramCatalog,
        coordinator: InMemoryWorkoutSessionCoordinator,
        capabilitiesOverride: DeviceCapabilities = capabilities,
    ): ProgramSetupViewModel = ProgramSetupViewModel(
        getProgramDetail = GetProgramDetail(catalog),
        startWorkout = StartWorkout(coordinator, catalog),
        startSurpriseWorkoutDraft = StartSurpriseWorkoutDraft(coordinator),
        generateSurpriseWorkoutDraft = GenerateSurpriseWorkoutDraft(),
        startFiveKReadySessionDraft = StartFiveKReadySessionDraft(coordinator),
        generateFiveKReadySessionDraft = GenerateFiveKReadySessionDraft(),
        startVerticalWorkoutDraft = com.echelon.console.application.usecase.StartVerticalWorkoutDraft(coordinator),
        generateVerticalWorkoutDraft = com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft(),
        startZone2WorkoutPreview = StartZone2WorkoutPreview(catalog, coordinator),
        startCalorieTargetPreview = StartCalorieTargetPreview(catalog, coordinator),
        capabilities = capabilitiesOverride,
        dispatcher = dispatcher,
    )
}
