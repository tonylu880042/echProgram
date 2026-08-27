package com.echelon.console.presentation

import com.echelon.console.application.usecase.CalorieTargetPreviewSessionStarter
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.WorkoutSessionStartFailure
import com.echelon.console.application.usecase.WorkoutSessionStarterResult
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutSessionState
import com.echelon.console.domain.WorkoutTimelineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalorieTargetWorkoutSetupViewModelTest {
    @Test
    fun `start default enters calorie configuring without starting a coordinator session`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(catalog, coordinator, dispatcher)

            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            val configuring = viewModel.state.value as ProgramSetupUiState.CalorieTargetConfiguring
            assertTrue(configuring.selectedTarget == null)
            assertEquals(DurationMinutes(40), configuring.representativeProfileDuration)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `make it yours and start default both require explicit target selection`() = runTest {
        listOf(ProgramSetupAction.MakeItYours, ProgramSetupAction.StartDefault).forEach { entryAction ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val catalog = StaticProgramCatalog()
                val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
                val viewModel = viewModel(catalog, coordinator, dispatcher)

                viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
                advanceUntilIdle()
                viewModel.onAction(entryAction)

                val configuring = viewModel.state.value as ProgramSetupUiState.CalorieTargetConfiguring
                assertNull(configuring.selectedTarget)
                assertEquals(DurationMinutes(40), configuring.representativeProfileDuration)
                assertNull(coordinator.currentState())
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun `missing capabilities block both calorie setup entry actions`() = runTest {
        listOf(ProgramSetupAction.MakeItYours, ProgramSetupAction.StartDefault).forEach { entryAction ->
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val catalog = StaticProgramCatalog()
                val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
                val viewModel = viewModel(
                    catalog = catalog,
                    coordinator = coordinator,
                    dispatcher = dispatcher,
                    capabilitiesOverride = null,
                )

                viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
                advanceUntilIdle()
                viewModel.onAction(entryAction)

                assertEquals(ProgramSetupUiState.DeviceUnavailable, viewModel.state.value)
                assertNull(coordinator.currentState())
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun `selecting each target stores a validated selection and clears an existing error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(catalog, coordinator, dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(ProgramSetupAction.StartCalorieTargetPreview)

            assertEquals(
                "SELECT A CALORIE TARGET BEFORE STARTING",
                (viewModel.state.value as ProgramSetupUiState.CalorieTargetConfiguring).errorMessage,
            )

            mapOf(
                CalorieTargetOption.ONE_HUNDRED_KCAL to 60,
                CalorieTargetOption.TWO_HUNDRED_KCAL to 60,
                CalorieTargetOption.THREE_HUNDRED_KCAL to 60,
                CalorieTargetOption.FIVE_HUNDRED_KCAL to 90,
            ).forEach { (option, proposedMaxTime) ->
                viewModel.onAction(ProgramSetupAction.SelectCalorieTarget(option))
                val state = viewModel.state.value as ProgramSetupUiState.CalorieTargetConfiguring
                assertEquals(option.estimatedKcal, state.selectedTarget?.estimatedKcal)
                assertEquals(proposedMaxTime, state.selectedTarget?.proposedMaxTime?.minutes)
                assertNull(state.errorMessage)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `starting without selection stays configuring and does not start coordinator`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(catalog, coordinator, dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(ProgramSetupAction.StartCalorieTargetPreview)

            assertEquals(
                ProgramSetupUiState.CalorieTargetConfiguring(
                    detail = requireNotNull(catalog.findProgramDetail(ProgramId("CALORIE_TARGET"))),
                    representativeProfileDuration = DurationMinutes(40),
                    selectedTarget = null,
                    userMaxSpeed = SpeedTenths(60),
                    machineMaxSpeed = SpeedTenths(120),
                    userMaxIncline = InclineTenths(100),
                    machineMaxIncline = InclineTenths(150),
                    errorMessage = "SELECT A CALORIE TARGET BEFORE STARTING",
                ),
                viewModel.state.value,
            )
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selected target starts exact preview plan and running coordinator`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(catalog, coordinator, dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(
                ProgramSetupAction.SelectCalorieTarget(CalorieTargetOption.THREE_HUNDRED_KCAL),
            )
            viewModel.onAction(ProgramSetupAction.StartCalorieTargetPreview)

            val started = viewModel.state.value as ProgramSetupUiState.Started
            assertEquals(ProgramId("CALORIE_TARGET"), started.plan.plan.programId)
            assertEquals(DurationMinutes(40), started.plan.plan.settings.duration)
            assertEquals(ProgramPreviewMode.CALORIE_TARGET_PREVIEW, started.previewMode)
            val running = coordinator.currentState() as WorkoutSessionState.Running
            assertEquals(2_400, running.timeline.totalDurationSeconds)
            val context = running.timeline.context as WorkoutTimelineContext.CalorieTargetPreview
            assertEquals(300, context.target.estimatedKcal)
            assertEquals(CalorieTargetOption.THREE_HUNDRED_KCAL, context.target.target)
            assertEquals(DurationMinutes(40), context.representativeProfileDuration)
            assertEquals(SpeedTenths(60), context.effectiveMaxSpeed)
            assertEquals(InclineTenths(100), context.effectiveMaxIncline)
            assertEquals(CalorieEstimateStatus.ESTIMATED, context.estimateStatus)
            assertEquals(
                CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
                context.source,
            )
            assertEquals(CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED, context.unitSemantics)
            assertEquals(
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
                context.sessionResetSemantics,
            )
            assertEquals(
                CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
                context.completionAuthority,
            )
            assertEquals(
                CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
                context.progressSemantics,
            )
            assertEquals(CaloriePreviewStatus.PREVIEW_ONLY, context.previewStatus)
            assertEquals(CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS, context.deviceCommandStatus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `typed calorie start failures map to safe setup errors`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertStartError(
                dispatcher = dispatcher,
                catalog = stagedCatalog(secondDetail = null),
                expected = "CALORIE TARGET PREVIEW IS UNAVAILABLE",
            )
            assertStartError(
                dispatcher = dispatcher,
                catalog = stagedCatalog(secondDetail = staticDetail().copy(programId = ProgramId("OTHER"))),
                expected = "CALORIE TARGET PREVIEW DETAIL IS INVALID",
            )
            assertStartError(
                dispatcher = dispatcher,
                catalog = stagedCatalog(
                    secondDetail = staticDetail().copy(
                        defaultSettings = staticDetail().defaultSettings.copy(
                            duration = DurationMinutes(30),
                        ),
                        supportedDurations = listOf(DurationMinutes(30)),
                    ),
                ),
                expected = "CALORIE TARGET REQUIRES A 40-MINUTE REPRESENTATIVE PROFILE",
            )
            assertStartError(
                dispatcher = dispatcher,
                catalog = stagedCatalog(secondDetail = staticDetail().copy(profile = staticDetail().profile.dropLast(1))),
                expected = "CALORIE TARGET PROFILE IS INVALID",
            )

            val constrainedCapabilities = capabilities.copy(
                speed = SpeedRange(SpeedTenths(70), SpeedTenths(120)),
                incline = InclineRange(InclineTenths(110), InclineTenths(150)),
            )
            assertStartError(
                dispatcher = dispatcher,
                catalog = staticCatalogAsDetailCatalog(),
                capabilitiesOverride = constrainedCapabilities,
                expected = "CAPABILITIES CANNOT SUPPORT CALORIE TARGET PREVIEW",
            )

            val failedStarter = CalorieTargetPreviewSessionStarter { _, _ ->
                WorkoutSessionStarterResult.Failed(
                    WorkoutSessionStartFailure.CalorieTargetPreviewProgramNotFound(
                        ProgramId("CALORIE_TARGET"),
                    ),
                )
            }
            assertStartError(
                dispatcher = dispatcher,
                catalog = staticCatalogAsDetailCatalog(),
                calorieUseCase = StartCalorieTargetPreview(
                    staticCatalogAsDetailCatalog(),
                    failedStarter,
                ),
                expected = "UNABLE TO START CALORIE TARGET PREVIEW",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `calorie start exceptions are safe and cancellation is rethrown`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            assertStartError(
                dispatcher = dispatcher,
                catalog = staticCatalogAsDetailCatalog(),
                calorieUseCase = StartCalorieTargetPreview(
                    staticCatalogAsDetailCatalog(),
                    CalorieTargetPreviewSessionStarter { _, _ ->
                        throw IllegalStateException("private failure")
                    },
                ),
                expected = "UNABLE TO START CALORIE TARGET PREVIEW",
            )

            val cancellationViewModel = viewModel(
                catalog = staticCatalogAsDetailCatalog(),
                coordinator = InMemoryWorkoutSessionCoordinator(staticCatalogAsDetailCatalog()),
                dispatcher = dispatcher,
                calorieUseCase = StartCalorieTargetPreview(
                    staticCatalogAsDetailCatalog(),
                    CalorieTargetPreviewSessionStarter { _, _ ->
                        throw CancellationException("cancelled")
                    },
                ),
            )
            cancellationViewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            cancellationViewModel.onAction(ProgramSetupAction.StartDefault)
            cancellationViewModel.onAction(
                ProgramSetupAction.SelectCalorieTarget(CalorieTargetOption.ONE_HUNDRED_KCAL),
            )

            var cancellationThrown = false
            try {
                cancellationViewModel.onAction(ProgramSetupAction.StartCalorieTargetPreview)
            } catch (exception: CancellationException) {
                cancellationThrown = true
            }
            assertTrue(cancellationThrown)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `back from calorie configuring returns to ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(catalog, coordinator, dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)
            viewModel.onAction(
                ProgramSetupAction.SelectCalorieTarget(CalorieTargetOption.FIVE_HUNDRED_KCAL),
            )
            viewModel.onAction(ProgramSetupAction.Back)

            assertEquals(
                ProgramSetupUiState.Ready(
                    requireNotNull(catalog.findProgramDetail(ProgramId("CALORIE_TARGET"))),
                ),
                viewModel.state.value,
            )
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        catalog: ProgramDetailCatalog,
        coordinator: InMemoryWorkoutSessionCoordinator,
        dispatcher: CoroutineDispatcher,
        capabilitiesOverride: DeviceCapabilities? = capabilities,
        calorieUseCase: StartCalorieTargetPreview = StartCalorieTargetPreview(catalog, coordinator),
    ): ProgramSetupViewModel = createProgramSetupViewModel(
        catalog = catalog,
        coordinator = coordinator,
        calorieUseCase = calorieUseCase,
        capabilities = capabilitiesOverride,
        dispatcher = dispatcher,
    )

    private fun TestScope.assertStartError(
        dispatcher: CoroutineDispatcher,
        catalog: ProgramDetailCatalog,
        expected: String,
        capabilitiesOverride: DeviceCapabilities? = capabilities,
        calorieUseCase: StartCalorieTargetPreview? = null,
    ) {
        val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
        val viewModel = viewModel(
            catalog = catalog,
            coordinator = coordinator,
            dispatcher = dispatcher,
            capabilitiesOverride = capabilitiesOverride,
            calorieUseCase = calorieUseCase ?: StartCalorieTargetPreview(catalog, coordinator),
        )
        viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("CALORIE_TARGET")))
        advanceUntilIdle()
        viewModel.onAction(ProgramSetupAction.StartDefault)
        viewModel.onAction(
            ProgramSetupAction.SelectCalorieTarget(CalorieTargetOption.THREE_HUNDRED_KCAL),
        )
        viewModel.onAction(ProgramSetupAction.StartCalorieTargetPreview)

        assertEquals(
            expected,
            (viewModel.state.value as ProgramSetupUiState.CalorieTargetConfiguring).errorMessage,
        )
        assertNull(coordinator.currentState())
    }

    private fun stagedCatalog(secondDetail: ProgramDetail?): ProgramDetailCatalog {
        var lookupCount = 0
        return ProgramDetailCatalog { requestedId ->
            if (requestedId != ProgramId("CALORIE_TARGET")) {
                null
            } else {
                lookupCount += 1
                if (lookupCount == 1) staticDetail() else secondDetail
            }
        }
    }

    private fun staticDetail(): ProgramDetail = requireNotNull(
        staticCatalog.findProgramDetail(ProgramId("CALORIE_TARGET")),
    )

    private fun staticCatalogAsDetailCatalog(): ProgramDetailCatalog = ProgramDetailCatalog {
        staticCatalog.findProgramDetail(it)
    }

    private companion object {
        val staticCatalog = StaticProgramCatalog()

        val capabilities = DeviceCapabilities(
            duration = DurationLimits(DurationMinutes(10), DurationMinutes(90), DurationMinutes(5)),
            speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
            incline = InclineRange(InclineTenths(0), InclineTenths(150)),
        )
    }
}
