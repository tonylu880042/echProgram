package com.echelon.console.presentation

import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.application.usecase.WorkoutSessionStartFailure
import com.echelon.console.application.usecase.WorkoutSessionStarterResult
import com.echelon.console.application.usecase.Zone2WorkoutPreviewSessionStarter
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

            openZone2Ready(viewModel)
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

            openZone2Ready(viewModel)
            viewModel.onAction(ProgramSetupAction.MakeItYours)

            val configuring = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
            assertEquals(DurationMinutes(30), configuring.duration)
            assertEquals("", configuring.lowerBpmText)
            assertEquals("", configuring.upperBpmText)
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
            openZone2Ready(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)

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
    fun `zone 2 target validation maps every input failure to exact copy`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val catalog = StaticProgramCatalog()
            val viewModel = viewModel(dispatcher, catalog, InMemoryWorkoutSessionCoordinator(catalog))
            openZone2Ready(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)

            val cases = listOf(
                Zone2TargetCase("", "140", "LOWER BPM IS REQUIRED"),
                Zone2TargetCase("120", "", "UPPER BPM IS REQUIRED"),
                Zone2TargetCase("-1", "140", "LOWER BPM MUST BE GREATER THAN 0"),
                Zone2TargetCase("120", "0", "UPPER BPM MUST BE GREATER THAN 0"),
                Zone2TargetCase("141", "140", "LOWER BPM MUST NOT EXCEED UPPER BPM"),
                Zone2TargetCase("malformed", "140", "LOWER BPM MUST BE A WHOLE NUMBER"),
                Zone2TargetCase(
                    "999999999999999999999999999999",
                    "140",
                    "LOWER BPM MUST BE A WHOLE NUMBER",
                ),
                Zone2TargetCase("120", "malformed", "UPPER BPM MUST BE A WHOLE NUMBER"),
                Zone2TargetCase(
                    "120",
                    "999999999999999999999999999999",
                    "UPPER BPM MUST BE A WHOLE NUMBER",
                ),
            )
            cases.forEach { case ->
                viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm(case.lower))
                viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm(case.upper))
                viewModel.onAction(ProgramSetupAction.StartZone2Preview)

                val state = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
                assertEquals(case.expectedMessage, state.errorMessage)
                assertTrue(viewModel.state.value !is ProgramSetupUiState.Started)
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
            val machineLimitedCapabilities = capabilities.copy(
                speed = SpeedRange(SpeedTenths(20), SpeedTenths(40)),
                incline = InclineRange(InclineTenths(0), InclineTenths(60)),
            )
            val viewModel = viewModel(
                dispatcher = dispatcher,
                detailCatalog = catalog,
                coordinator = coordinator,
                capabilitiesOverride = machineLimitedCapabilities,
            )
            openZone2Ready(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)
            val configuring = viewModel.state.value as ProgramSetupUiState.Zone2Configuring
            assertEquals(SpeedTenths(40), configuring.machineMaxSpeed)
            assertEquals(InclineTenths(60), configuring.machineMaxIncline)
            viewModel.onAction(ProgramSetupAction.SetZone2Duration(DurationMinutes(45)))
            viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm("123"))
            viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm("145"))
            viewModel.onAction(ProgramSetupAction.StartZone2Preview)

            val started = viewModel.state.value as ProgramSetupUiState.Started
            assertEquals(ProgramPreviewMode.HEART_RATE_PREVIEW, started.previewMode)
            assertEquals(DurationMinutes(45), started.plan.plan.settings.duration)
            assertEquals(SpeedTenths(40), started.plan.plan.settings.maxSpeed)
            assertEquals(InclineTenths(60), started.plan.plan.settings.maxIncline)
            val context = (coordinator.currentState() as WorkoutSessionState.Running).timeline.context
                as com.echelon.console.domain.WorkoutTimelineContext.Zone2Preview
            assertEquals(ProgramId("ZONE_2"), context.programId)
            assertEquals(acceptedTarget(123, 145), context.target)
            assertEquals(DurationMinutes(45), context.duration)
            assertEquals(SpeedTenths(40), context.effectiveMaxSpeed)
            assertEquals(InclineTenths(60), context.effectiveMaxIncline)
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
            val notFoundCoordinator = InMemoryWorkoutSessionCoordinator(staticCatalog)
            val unsupportedCoordinator = InMemoryWorkoutSessionCoordinator(staticCatalog)
            val capabilityCoordinator = InMemoryWorkoutSessionCoordinator(staticCatalog)
            val starterCoordinator = InMemoryWorkoutSessionCoordinator(staticCatalog)
            val failures = listOf(
                Zone2FailureCase(
                    viewModel = viewModel(
                        dispatcher = dispatcher,
                        detailCatalog = staticCatalog,
                        coordinator = notFoundCoordinator,
                        zone2UseCase = StartZone2WorkoutPreview(
                            programCatalog = ProgramDetailCatalog { null },
                            sessionStarter = InMemoryWorkoutSessionCoordinator(staticCatalog),
                        ),
                    ),
                    expectedMessage = "ZONE 2 PREVIEW IS UNAVAILABLE",
                    coordinator = notFoundCoordinator,
                ),
                Zone2FailureCase(
                    viewModel = viewModel(
                        dispatcher = dispatcher,
                        detailCatalog = staticCatalog,
                        coordinator = unsupportedCoordinator,
                        zone2UseCase = StartZone2WorkoutPreview(
                            programCatalog = ProgramDetailCatalog { programId ->
                                staticCatalog.findProgramDetail(programId)?.let { detail ->
                                    detail.copy(
                                        defaultSettings = detail.defaultSettings.copy(
                                            duration = DurationMinutes(20),
                                        ),
                                        supportedDurations = listOf(DurationMinutes(20)),
                                    )
                                }
                            },
                            sessionStarter = InMemoryWorkoutSessionCoordinator(staticCatalog),
                        ),
                    ),
                    expectedMessage = "SELECT 20, 30, 45, OR 60 MINUTES",
                    coordinator = unsupportedCoordinator,
                ),
                Zone2FailureCase(
                    viewModel = viewModel(
                        dispatcher = dispatcher,
                        detailCatalog = staticCatalog,
                        coordinator = capabilityCoordinator,
                        capabilitiesOverride = capabilities.copy(
                            speed = SpeedRange(SpeedTenths(60), SpeedTenths(120)),
                        ),
                    ),
                    expectedMessage = "CAPABILITIES CANNOT SUPPORT ZONE 2 PREVIEW",
                    coordinator = capabilityCoordinator,
                ),
                Zone2FailureCase(
                    viewModel = viewModel(
                        dispatcher = dispatcher,
                        detailCatalog = staticCatalog,
                        coordinator = starterCoordinator,
                        zone2UseCase = StartZone2WorkoutPreview(
                            programCatalog = staticCatalog,
                            sessionStarter = RecordingZone2Starter(
                                WorkoutSessionStarterResult.Failed(
                                    WorkoutSessionStartFailure.ActiveSessionExists,
                                ),
                            ),
                        ),
                    ),
                    expectedMessage = "UNABLE TO START ZONE 2 PREVIEW",
                    coordinator = starterCoordinator,
                ),
            )

            failures.forEach { failure ->
                openZone2Ready(failure.viewModel)
                failure.viewModel.onAction(ProgramSetupAction.StartDefault)
                failure.viewModel.onAction(ProgramSetupAction.SetZone2LowerBpm("120"))
                failure.viewModel.onAction(ProgramSetupAction.SetZone2UpperBpm("140"))
                failure.viewModel.onAction(ProgramSetupAction.StartZone2Preview)

                val state = failure.viewModel.state.value as ProgramSetupUiState.Zone2Configuring
                assertEquals(failure.expectedMessage, state.errorMessage)
                assertTrue(failure.viewModel.state.value !is ProgramSetupUiState.Started)
                assertNull(failure.coordinator.currentState())
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
            openZone2Ready(viewModel)
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
            openZone2Ready(viewModel)
            viewModel.onAction(ProgramSetupAction.StartDefault)

            assertEquals(ProgramSetupUiState.DeviceUnavailable, viewModel.state.value)
            assertNull(coordinator.currentState())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.openZone2Ready(viewModel: ProgramSetupViewModel) {
        viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("ZONE_2")))
        advanceUntilIdle()
        assertTrue(viewModel.state.value is ProgramSetupUiState.Ready)
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        detailCatalog: ProgramDetailCatalog,
        coordinator: InMemoryWorkoutSessionCoordinator,
        capabilitiesOverride: DeviceCapabilities? = capabilities,
        zone2UseCase: StartZone2WorkoutPreview = StartZone2WorkoutPreview(detailCatalog, coordinator),
    ): ProgramSetupViewModel = createProgramSetupViewModel(
        catalog = detailCatalog,
        coordinator = coordinator,
        zone2UseCase = zone2UseCase,
        calorieUseCase = StartCalorieTargetPreview(StaticProgramCatalog(), coordinator),
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

private data class Zone2FailureCase(
    val viewModel: ProgramSetupViewModel,
    val expectedMessage: String,
    val coordinator: InMemoryWorkoutSessionCoordinator,
)

private data class Zone2TargetCase(
    val lower: String,
    val upper: String,
    val expectedMessage: String,
)
