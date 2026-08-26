package com.echelon.console.presentation

import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailCatalog
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.WorkoutSessionStarter
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanFocus
import com.echelon.console.domain.PlanIntensity
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramSegmentSummary
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramSetupViewModelTest {
    private val detail = ProgramDetail(
        programId = ProgramId("FAT_BURN"),
        title = "FAT BURN",
        promise = "Sustained calorie-burning work without requiring hard running.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(45),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(85),
            maxIncline = InclineTenths(80),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        inclineRange = InclineRange(InclineTenths(0), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(35), InclineTenths(0)),
        ),
    )

    private val capabilities = DeviceCapabilities(
        duration = DurationLimits(
            min = DurationMinutes(10),
            max = DurationMinutes(60),
            step = DurationMinutes(5),
        ),
        speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
        incline = InclineRange(InclineTenths(0), InclineTenths(120)),
    )

    @Test
    fun `initial state is library and opening program is observable as loading then ready`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher)

            assertEquals(ProgramSetupUiState.Library, viewModel.state.value)

            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            assertTrue(viewModel.state.value is ProgramSetupUiState.Loading)

            advanceUntilIdle()

            assertEquals(ProgramSetupUiState.Ready(detail), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `missing detail becomes unavailable`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val missingId = ProgramId("MISSING")
            val viewModel = viewModel(
                dispatcher = dispatcher,
                catalog = ProgramDetailCatalog { null },
            )

            viewModel.onAction(ProgramSetupAction.OpenProgram(missingId))
            advanceUntilIdle()

            assertEquals(ProgramSetupUiState.Unavailable(missingId), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `missing capabilities become device unavailable and cannot start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val starter = RecordingStarter()
            val viewModel = viewModel(
                dispatcher = dispatcher,
                starter = starter,
                capabilities = null,
            )

            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            advanceUntilIdle()

            assertEquals(ProgramSetupUiState.DeviceUnavailable, viewModel.state.value)
            assertNull(starter.received)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `make it yours enters personalization and back returns to ready then library`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()

            viewModel.onAction(ProgramSetupAction.MakeItYours)
            assertEquals(
                ProgramSetupUiState.Personalizing(detail, detail.defaultSettings),
                viewModel.state.value,
            )

            viewModel.onAction(ProgramSetupAction.Back)
            assertEquals(ProgramSetupUiState.Ready(detail), viewModel.state.value)

            viewModel.onAction(ProgramSetupAction.Back)
            assertEquals(ProgramSetupUiState.Library, viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `personalization actions update typed settings without viewmodel validation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)

            viewModel.onAction(ProgramSetupAction.SetDuration(DurationMinutes(12)))
            viewModel.onAction(ProgramSetupAction.SetIntensity(PlanIntensity.HIGH))
            viewModel.onAction(ProgramSetupAction.SetMaxSpeed(SpeedTenths(121)))
            viewModel.onAction(ProgramSetupAction.SetMaxIncline(InclineTenths(-1)))
            viewModel.onAction(ProgramSetupAction.SetFocus(PlanFocus.MORE_SPEED))
            viewModel.onAction(ProgramSetupAction.SetAdaptToYou(true))

            val state = viewModel.state.value as ProgramSetupUiState.Personalizing
            assertEquals(
                PlanSettings(
                    duration = DurationMinutes(12),
                    intensity = PlanIntensity.HIGH,
                    focus = PlanFocus.MORE_SPEED,
                    maxSpeed = SpeedTenths(121),
                    maxIncline = InclineTenths(-1),
                    adaptToYou = true,
                ),
                state.settings,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `start default forwards exact default plan and becomes started`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val starter = RecordingStarter()
            val viewModel = viewModel(dispatcher = dispatcher, starter = starter)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()

            viewModel.onAction(ProgramSetupAction.StartDefault)
            advanceUntilIdle()

            val expected = detail.let {
                com.echelon.console.domain.WorkoutPlan(it.programId, it.defaultSettings)
            }
            assertEquals(expected, starter.received?.plan)
            assertEquals(ProgramSetupUiState.Started(starter.received!!), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `valid customized plan forwards exact settings and becomes started`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val starter = RecordingStarter()
            val viewModel = viewModel(dispatcher = dispatcher, starter = starter)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)
            val expectedSettings = PlanSettings(
                duration = DurationMinutes(50),
                intensity = PlanIntensity.HIGH,
                focus = PlanFocus.MORE_INCLINE,
                maxSpeed = SpeedTenths(90),
                maxIncline = InclineTenths(100),
                adaptToYou = true,
            )
            viewModel.onAction(ProgramSetupAction.SetDuration(expectedSettings.duration))
            viewModel.onAction(ProgramSetupAction.SetIntensity(expectedSettings.intensity))
            viewModel.onAction(ProgramSetupAction.SetFocus(expectedSettings.focus))
            viewModel.onAction(ProgramSetupAction.SetMaxSpeed(expectedSettings.maxSpeed))
            viewModel.onAction(ProgramSetupAction.SetMaxIncline(expectedSettings.maxIncline))
            viewModel.onAction(ProgramSetupAction.SetAdaptToYou(expectedSettings.adaptToYou))

            viewModel.onAction(ProgramSetupAction.StartCustomized)
            advanceUntilIdle()

            assertEquals(
                com.echelon.console.domain.WorkoutPlan(detail.programId, expectedSettings),
                starter.received?.plan,
            )
            assertTrue(viewModel.state.value is ProgramSetupUiState.Started)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid customized plan stays personalizing with field errors and does not start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val starter = RecordingStarter()
            val viewModel = viewModel(dispatcher = dispatcher, starter = starter)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.MakeItYours)
            viewModel.onAction(ProgramSetupAction.SetMaxSpeed(SpeedTenths(121)))

            viewModel.onAction(ProgramSetupAction.StartCustomized)
            advanceUntilIdle()

            val state = viewModel.state.value as ProgramSetupUiState.Personalizing
            assertTrue(state.fieldErrors.isNotEmpty())
            assertNull(starter.received)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `load cancellation is not converted to an error state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(
                dispatcher = dispatcher,
                catalog = ProgramDetailCatalog { throw CancellationException("cancelled") },
            )

            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()

            assertEquals(ProgramSetupUiState.Loading(detail.programId), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `start cancellation is not converted to an error state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(
                dispatcher = dispatcher,
                starter = RecordingStarter(CancellationException("cancelled")),
            )

            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)
            advanceUntilIdle()

            assertEquals(ProgramSetupUiState.Ready(detail), viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `load and start failures become safe error states`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val loadingFailure = viewModel(
                dispatcher = dispatcher,
                catalog = ProgramDetailCatalog { throw IllegalStateException("private stack") },
            )
            loadingFailure.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            assertEquals(
                ProgramSetupUiState.Error("Unable to load program detail"),
                loadingFailure.state.value,
            )

            val startFailure = viewModel(
                dispatcher = dispatcher,
                starter = RecordingStarter(IllegalStateException("private stack")),
            )
            startFailure.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            startFailure.onAction(ProgramSetupAction.StartDefault)
            advanceUntilIdle()
            assertEquals(
                ProgramSetupUiState.Error("Unable to start workout"),
                startFailure.state.value,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `back from unavailable returns to library`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(
                dispatcher = dispatcher,
                catalog = ProgramDetailCatalog { null },
            )
            viewModel.onAction(ProgramSetupAction.OpenProgram(ProgramId("MISSING")))
            advanceUntilIdle()

            viewModel.onAction(ProgramSetupAction.Back)

            assertEquals(ProgramSetupUiState.Library, viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `back from device unavailable returns to library`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher = dispatcher, capabilities = null)
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()
            viewModel.onAction(ProgramSetupAction.StartDefault)

            viewModel.onAction(ProgramSetupAction.Back)

            assertEquals(ProgramSetupUiState.Library, viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `back from error returns to library`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(
                dispatcher = dispatcher,
                catalog = ProgramDetailCatalog { throw IllegalStateException("private stack") },
            )
            viewModel.onAction(ProgramSetupAction.OpenProgram(detail.programId))
            advanceUntilIdle()

            viewModel.onAction(ProgramSetupAction.Back)

            assertEquals(ProgramSetupUiState.Library, viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        dispatcher: CoroutineDispatcher,
        catalog: ProgramDetailCatalog = ProgramDetailCatalog { detail },
        starter: RecordingStarter = RecordingStarter(),
        capabilities: DeviceCapabilities? = this.capabilities,
    ): ProgramSetupViewModel = ProgramSetupViewModel(
        getProgramDetail = GetProgramDetail(catalog),
        startWorkout = StartWorkout(starter),
        capabilities = capabilities,
        dispatcher = dispatcher,
    )

    private class RecordingStarter(
        private val failure: Throwable? = null,
    ) : WorkoutSessionStarter {
        var received: ValidatedWorkoutPlan? = null

        override fun start(plan: ValidatedWorkoutPlan) {
            failure?.let { throw it }
            received = plan
        }
    }
}
