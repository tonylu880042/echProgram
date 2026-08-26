package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartCalorieTargetPreview
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
import com.echelon.console.domain.VerticalTarget
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
class VerticalWorkoutSetupViewModelTest {
    @Test
    fun `vertical start default enters target configuration without starting static catalog`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val viewModel = viewModel(dispatcher, coordinator)

            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            val configuring = viewModel.state.value as ProgramSetupUiState.VerticalConfiguring
            assertEquals(VerticalTarget.ONE_THOUSAND_FEET, configuring.target)
            assertEquals(SpeedTenths(40), configuring.userMaxSpeed)
            assertEquals(SpeedTenths(120), configuring.machineMaxSpeed)
            assertEquals(InclineTenths(150), configuring.userMaxIncline)
            assertEquals(InclineTenths(150), configuring.machineMaxIncline)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `vertical make it yours enters the same target configuration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val viewModel = viewModel(dispatcher, coordinator)

            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)

            assertTrue(viewModel.state.value is ProgramSetupUiState.VerticalConfiguring)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `all four vertical targets generate their exact representative draft`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val viewModel = viewModel(dispatcher, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            VerticalTarget.values().forEachIndexed { index, target ->
                viewModel.onAction(ProgramSetupAction.SetVerticalTarget(target))
                viewModel.onAction(ProgramSetupAction.GenerateVerticalPreview)

                val preview = viewModel.state.value as ProgramSetupUiState.VerticalDraftPreview
                assertEquals(target, preview.draft.metadata.target)
                assertEquals(target.proposedTimeLimit, preview.draft.metadata.proposedTimeLimit)
                assertEquals(50, preview.draft.profile.sumOf { it.duration.value })
                assertNull(coordinator.currentState())

                if (index < VerticalTarget.values().lastIndex) {
                    viewModel.onAction(ProgramSetupAction.Back)
                    assertTrue(viewModel.state.value is ProgramSetupUiState.VerticalConfiguring)
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `back from vertical preview preserves target and effective caps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val coordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val viewModel = viewModel(dispatcher, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)
            viewModel.onAction(ProgramSetupAction.SetVerticalTarget(VerticalTarget.VERTICAL_MILE))
            viewModel.onAction(ProgramSetupAction.GenerateVerticalPreview)

            val preview = viewModel.state.value as ProgramSetupUiState.VerticalDraftPreview
            viewModel.onAction(ProgramSetupAction.Back)
            val configuring = viewModel.state.value as ProgramSetupUiState.VerticalConfiguring

            assertEquals(VerticalTarget.VERTICAL_MILE, configuring.target)
            assertEquals(preview.userMaxSpeed, configuring.userMaxSpeed)
            assertEquals(preview.machineMaxSpeed, configuring.machineMaxSpeed)
            assertEquals(preview.userMaxIncline, configuring.userMaxIncline)
            assertEquals(preview.machineMaxIncline, configuring.machineMaxIncline)

            viewModel.onAction(ProgramSetupAction.Back)
            assertEquals(
                ProgramSetupUiState.Ready(requireNotNull(StaticProgramCatalog().findProgramDetail(ProgramId("VERTICAL")))),
                viewModel.state.value,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `accept vertical preview starts exact draft with elevation preview mode`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, coordinator)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(ProgramSetupAction.SetVerticalTarget(VerticalTarget.TWO_THOUSAND_FEET))
            viewModel.onAction(ProgramSetupAction.GenerateVerticalPreview)
            val draft = (viewModel.state.value as ProgramSetupUiState.VerticalDraftPreview).draft

            viewModel.onAction(ProgramSetupAction.AcceptVerticalPlan)

            val started = viewModel.state.value as ProgramSetupUiState.Started
            assertEquals(ProgramPreviewMode.ELEVATION_TARGET_PREVIEW, started.previewMode)
            val running = coordinator.currentState() as WorkoutSessionState.Running
            assertEquals(draft.profile.map { it.name }, running.timeline.segments.map { it.name })
            assertEquals(draft.profile.map { it.duration.value * 60 }, running.timeline.segments.map { it.durationSeconds })
            assertEquals(draft.profile.map { it.speed.value }, running.timeline.segments.map { it.targetSpeed.value })
            assertEquals(draft.profile.map { it.incline.value }, running.timeline.segments.map { it.targetIncline.value })
            assertEquals(draft.metadata.target, running.timeline.context.let { context ->
                (context as com.echelon.console.domain.WorkoutTimelineContext.VerticalPreview).target
            })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `missing or invalid vertical capabilities never starts a preview`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val missingCoordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val missingViewModel = viewModel(dispatcher, missingCoordinator, capabilities = null)
            missingViewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            missingViewModel.onAction(ProgramSetupAction.StartDefault)
            assertEquals(ProgramSetupUiState.DeviceUnavailable, missingViewModel.state.value)
            assertNull(missingCoordinator.currentState())

            val invalidCapabilities = capabilities().copy(
                speed = SpeedRange(SpeedTenths(20), SpeedTenths(20)),
            )
            val invalidCoordinator = InMemoryWorkoutSessionCoordinator(StaticProgramCatalog())
            val invalidViewModel = viewModel(dispatcher, invalidCoordinator, invalidCapabilities)
            invalidViewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("VERTICAL")))
            advanceUntilIdle()
            invalidViewModel.onAction(ProgramSetupAction.StartDefault)
            invalidViewModel.onAction(ProgramSetupAction.GenerateVerticalPreview)

            val configuring = invalidViewModel.state.value as ProgramSetupUiState.VerticalConfiguring
            assertTrue(configuring.errorMessage?.contains("CAPABILITIES") == true)
            assertNull(invalidCoordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        coordinator: InMemoryWorkoutSessionCoordinator,
        capabilities: DeviceCapabilities? = capabilities(),
    ): ProgramSetupViewModel = ProgramSetupViewModel(
        getProgramDetail = GetProgramDetail(StaticProgramCatalog()),
        startWorkout = StartWorkout(coordinator, StaticProgramCatalog()),
        startSurpriseWorkoutDraft = com.echelon.console.application.usecase.StartSurpriseWorkoutDraft(coordinator),
        generateSurpriseWorkoutDraft = com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft(),
        startFiveKReadySessionDraft = com.echelon.console.application.usecase.StartFiveKReadySessionDraft(coordinator),
        generateFiveKReadySessionDraft = com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft(),
        startVerticalWorkoutDraft = StartVerticalWorkoutDraft(coordinator),
        generateVerticalWorkoutDraft = GenerateVerticalWorkoutDraft(),
        startZone2WorkoutPreview = StartZone2WorkoutPreview(StaticProgramCatalog(), coordinator),
        startCalorieTargetPreview = StartCalorieTargetPreview(StaticProgramCatalog(), coordinator),
        capabilities = capabilities,
        dispatcher = dispatcher,
    )

    private fun capabilities() = DeviceCapabilities(
        duration = DurationLimits(DurationMinutes(10), DurationMinutes(90), DurationMinutes(5)),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )
}
