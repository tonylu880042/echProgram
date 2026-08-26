package com.echelon.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echelon.console.application.usecase.EquipmentTelemetrySource
import com.echelon.console.application.usecase.ListProgramLibrary
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.InMemoryWorkoutSessionCoordinator
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.data.fitos.AndroidFitOsClientFactory
import com.echelon.console.data.fitos.FitOsEquipmentAdapter
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.SpeedRange
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.presentation.ProgramLibraryDestination
import com.echelon.console.presentation.ProgramLibraryRoute
import com.echelon.console.presentation.ProgramLibraryViewModel
import com.echelon.console.presentation.ProgramLibraryViewModelFactory
import com.echelon.console.presentation.ProgramSetupAction
import com.echelon.console.presentation.ProgramSetupRoute
import com.echelon.console.presentation.ProgramSetupUiState
import com.echelon.console.presentation.ProgramSetupViewModel
import com.echelon.console.presentation.ProgramSetupViewModelFactory
import com.echelon.console.presentation.EquipmentTelemetryViewModel
import com.echelon.console.presentation.EquipmentTelemetryViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {
    private val programCatalog: StaticProgramCatalog by lazy { StaticProgramCatalog() }

    internal val workoutSessionCoordinator: InMemoryWorkoutSessionCoordinator by lazy {
        InMemoryWorkoutSessionCoordinator(programCatalog)
    }

    private val equipmentTelemetrySource: EquipmentTelemetrySource by lazy {
        FitOsEquipmentAdapter(
            clientFactory = AndroidFitOsClientFactory(applicationContext),
            queryDispatcher = Dispatchers.IO,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }

    private val equipmentTelemetryViewModel: EquipmentTelemetryViewModel by viewModels {
        EquipmentTelemetryViewModelFactory(equipmentTelemetrySource)
    }

    private val programLibraryViewModel: ProgramLibraryViewModel by viewModels {
        ProgramLibraryViewModelFactory(
            listProgramLibrary = ListProgramLibrary(programCatalog),
        )
    }

    private val programSetupViewModel: ProgramSetupViewModel by viewModels {
        ProgramSetupViewModelFactory(
            getProgramDetail = GetProgramDetail(programCatalog),
            startWorkout = StartWorkout(workoutSessionCoordinator),
            capabilities = DeviceCapabilities(
                duration = DurationLimits(
                    min = DurationMinutes(10),
                    max = DurationMinutes(60),
                    step = DurationMinutes(5),
                ),
                speed = SpeedRange(SpeedTenths(20), SpeedTenths(120)),
                incline = InclineRange(InclineTenths(0), InclineTenths(150)),
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val setupState = programSetupViewModel.state.collectAsStateWithLifecycle().value
                val equipmentState = equipmentTelemetryViewModel.state.collectAsStateWithLifecycle().value
                if (setupState == ProgramSetupUiState.Library) {
                    ProgramLibraryRoute(
                        viewModel = programLibraryViewModel,
                        onNavigate = { _: ProgramLibraryDestination ->
                            // Rail navigation remains an explicit seam for future console modes.
                        },
                        onOpenProgram = { programId ->
                            programSetupViewModel.onAction(ProgramSetupAction.OpenProgram(programId))
                        },
                    )
                } else {
                    ProgramSetupRoute(
                        viewModel = programSetupViewModel,
                        onNavigate = { _: ProgramLibraryDestination ->
                            // Rail navigation remains an explicit seam for future console modes.
                        },
                        onShowLibrary = {
                            programSetupViewModel.onAction(ProgramSetupAction.Back)
                        },
                        equipmentState = equipmentState,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        equipmentTelemetryViewModel.onStart()
    }

    override fun onStop() {
        equipmentTelemetryViewModel.onStop()
        super.onStop()
    }
}
