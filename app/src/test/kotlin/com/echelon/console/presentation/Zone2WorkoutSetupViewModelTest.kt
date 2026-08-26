package com.echelon.console.presentation

import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.application.usecase.Zone2WorkoutPreviewSessionStarter
import com.echelon.console.application.usecase.WorkoutSessionStarterResult
import com.echelon.console.application.usecase.WorkoutSessionStartFailure
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Zone2WorkoutSetupViewModelTest {
    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(
            min = DurationMinutes(10),
            max = DurationMinutes(90),
            step = DurationMinutes(5),
        ),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(150)),
    )

    @Test
    fun `zone 2 start default enters target configuration without starting`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)

            openZone2(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)

            val configuring = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
            assertEquals(DurationMinutes(30), configuring.duration)
            assertEquals("", configuring.lowerBpmText)
            assertEquals("", configuring.upperBpmText)
            assertEquals(SpeedTenths(50), configuring.userMaxSpeed)
            assertEquals(SpeedTenths(120), configuring.machineMaxSpeed)
            assertEquals(InclineTenths(80), configuring.userMaxIncline)
            assertEquals(InclineTenths(150), configuring.machineMaxIncline)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 make it yours enters the same target configuration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)

            openZone2(viewModel)
            viewModel.onAction(ProgramSetupAction.MakeItYours)

            assertTrue(viewModel.state.value is ProgramSetupUiState.Zone2Configuring)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 duration choices are exact and edits clear the previous error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val viewModel = viewModel(dispatcher, catalog, InMemoryWorkoutSessionCoordinator(catalog))
            openZone2(viewModel)

            viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm("141"))
            viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm("120"))
            viewModel.onAction(ProgramSetupAction.StartZone2Preview)
            assertTrue(
                (viewModel.state.value as ProgramSetupUiState.Zone2Configuring)
                    .errorMessage
                    ?.contains("LOWER BPM") == true,
            )

            viewModel.onAction(ProgramSetupAction.SetZone2Duration(DurationMinutes(45)))
            assertNull((viewModel.state.value as ProgramSetupUiState.Zone2Configuring).errorMessage)

            listOf(20, 30, 45, 60).forEach { duration ->
                viewModel.onAction(ProgramSetupAction.SetZone2Duration(DurationMinutes(duration)))
                assertEquals(
                    DurationMinutes(duration),
                    (viewModel.state.value as ProgramSetupUiState.Zone2Configuring).duration,
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 target validation maps missing nonpositive ordered and overflow input`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val viewModel = viewModel(dispatcher, catalog, InMemoryWorkoutSessionCoordinator(catalog))
            openZone2(viewModel)

            val cases = listOf(
                Triple("", "140", "LOWER BPM"),
                Triple("120", "", "UPPER BPM"),
                Triple("0", "140", "LOWER BPM"),
                Triple("120", "0", "UPPER BPM"),
                Triple("141", "140", "LOWER BPM"),
                Triple("999999999999999999999999999999", "140", "LOWER BPM"),
            )
            cases.forEach { (lower, upper, expectedText) ->
                viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm(lower))
                viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm(upper))
                viewModel.onAction(ProgramSetupAction.StartZone2Preview)

                val state = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
                assertTrue("Expected $expectedText in ${state.errorMessage}", state.errorMessage?.contains(expectedText) == true)
                assertNull((viewModel.state.value as? ProgramSetupUiState.Started)?.plan)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 success forwards exact target duration and caps and uses heart rate preview`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(dispatcher, catalog, coordinator)
            openZone2(viewModel)
            viewModel.onAction(ProgramSetupAction.SetZone2Duration(DurationMinutes(45)))
            viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm("123"))
            viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm("145"))
            viewModel.onAction(ProgramSetupAction.StartZone2Preview)

            val started = viewModel.state.value as ProgramSetupUiState.Started
            assertEquals(ProgramPreviewMode.HEART_RATE_PREVIEW, started.previewMode)
            assertEquals(DurationMinutes(45), started.plan.plan.settings.duration)
            assertEquals(SpeedTenths(50), started.plan.plan.settings.maxSpeed)
            assertEquals(InclineTenths(80), started.plan.plan.settings.maxIncline)
            val context = (coordinator.currentState() as WorkoutSessionState.Running).timeline.context
                as com.echelon.console.domain.WorkoutTimelineContext.Zone2Preview
            assertEquals(ProgramId("ZONE_2"), context.programId)
            assertEquals(acceptedTarget(123, 145), context.target)
            assertEquals(DurationMinutes(45), context.duration)
            assertEquals(SpeedTenths(50), context.effectiveMaxSpeed)
            assertEquals(InclineTenths(80), context.effectiveMaxIncline)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 use case failures stay configuring with clear errors and never fake a session`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val staticCatalog = StaticProgramCatalog()
            val failures = listOf(
                viewModel(
                    dispatcher = dispatcher,
                    detailCatalog = staticCatalog,
                    coordinator = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    zone2UseCase = StartZone2WorkoutPreview(
                        programCatalog = ProgramDetailCatalog { null },
                        sessionStarter = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    ),
                ),
                viewModel(
                    dispatcher = dispatcher,
                    detailCatalog = staticCatalog,
                    coordinator = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    zone2UseCase = StartZone2WorkoutPreview(
                        programCatalog = ProgramDetailCatalog { programId ->
                            requireNotNull(staticCatalog.findProgramDetail(programId)).copy(
                                defaultSettings = requireNotNull(staticCatalog.findProgramDetail(programId))
                                    .defaultSettings.copy(duration = DurationMinutes(20)),
                                supportedDurations = listOf(DurationMinutes(20)),
                            )
                        },
                        sessionStarter = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    ),
                ),
                viewModel(
                    dispatcher = dispatcher,
                    detailCatalog = staticCatalog,
                    coordinator = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    capabilitiesOverride = capabilities.copy(
                        speed = SpeedRange(SpeedTenths(60), SpeedTenths(120)),
                    ),
                ),
                viewModel(
                    dispatcher = dispatcher,
                    detailCatalog = staticCatalog,
                    coordinator = InMemoryWorkoutSessionCoordinator(staticCatalog),
                    zone2UseCase = StartZone2WorkoutPreview(
                        programCatalog = staticCatalog,
                        sessionStarter = RecordingZone2Starter(
                            WorkoutSessionStarterResult.Failed(
                                WorkoutSessionStartFailure.ActiveSessionExists,
                            ),
                        ),
                    ),
                ),
            )

            failures.forEach { viewModel ->
                openZone2(viewModel)
                viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm("120"))
                viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm("140"))
                viewModel.onAction(ProgramSetupAction.StartZone2Preview)

                val state = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
                assertNotNull(state.errorMessage)
                assertTrue(viewModel.state.value !is ProgramSetupUiState.Started)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 back returns to ready and non-zone2 still opens generic personalization`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val viewModel = viewModel(dispatcher, catalog, InMemoryWorkoutSessionCoordinator(catalog))
            openZone2(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)
            viewModel.onAction(ProgramSetupAction.Back)
            assertTrue(viewModel.state.value is ProgramSetupUiState.Ready)

            viewModel.onAction(ProgramSetupAction.Back)
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("FAT_BURN")))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)
            assertTrue(viewModel.state.value is ProgramSetupUiState.Personalizing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `zone 2 missing capabilities cannot enter a startable configuration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val coordinator = InMemoryWorkoutSessionCoordinator(catalog)
            val viewModel = viewModel(
                dispatcher = dispatcher,
                detailCatalog = catalog,
                coordinator = coordinator,
                capabilitiesOverride = null,
            )
            openZone2(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)

            assertEquals(ProgramSetupUiState.DeviceUnavailable, viewModel.state.value)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.openZone2(viewModel: ProgramSetupViewModel) {
        viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("ZONE_2")))
        advanceUntilIdle()
        viewModel.onAction(ProgramSetupAction.StartDefault)
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        detailCatalog: ProgramDetailCatalog,
        coordinator: InMemoryWorkoutSessionCoordinator,
        capabilitiesOverride: DeviceCapabilities? = capabilities,
        zone2UseCase: StartZone2WorkoutPreview = StartZone2WorkoutPreview(detailCatalog, coordinator),
    ): ProgramSetupViewModel = ProgramSetupViewModel(
        getProgramDetail = GetProgramDetail(detailCatalog),
        startWorkout = StartWorkout(coordinator, detailCatalog),
        startSurpriseWorkoutDraft = StartSurpriseWorkoutDraft(coordinator),
        generateSurpriseWorkoutDraft = GenerateSurpriseWorkoutDraft(),
        startFiveKReadySessionDraft = StartFiveKReadySessionDraft(coordinator),
        generateFiveKReadySessionDraft = GenerateFiveKReadySessionDraft(),
        startVerticalWorkoutDraft = StartVerticalWorkoutDraft(coordinator),
        generateVerticalWorkoutDraft = GenerateVerticalWorkoutDraft(),
        startZone2WorkoutPreview = zone2UseCase,
        capabilities = capabilitiesOverride,
        dispatcher = dispatcher,
    )

    private fun acceptedTarget(lower: Int, upper: Int): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(lower, upper)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }

    private class RecordingZone2Starter(
        private val result: WorkoutSessionStarterResult,
    ) : Zone2WorkoutPreviewSessionStarter {
        override fun start(
            context: com.echelon.console.domain.WorkoutTimelineContext.Zone2Preview,
            plan: com.echelon.console.domain.ValidatedWorkoutPlan,
        ): WorkoutSessionStarterResult = result
    }
}
