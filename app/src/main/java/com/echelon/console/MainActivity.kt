package com.echelon.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echelon.console.application.usecase.ListProgramLibrary
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.WorkoutSessionStarter
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.data.StaticProgramDetailCatalog
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationLimits
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineRange
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ValidatedWorkoutPlan
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

class MainActivity : ComponentActivity() {
    private val programLibraryViewModel: ProgramLibraryViewModel by viewModels {
        ProgramLibraryViewModelFactory(
            listProgramLibrary = ListProgramLibrary(StaticProgramCatalog()),
        )
    }

    private val programSetupViewModel: ProgramSetupViewModel by viewModels {
        ProgramSetupViewModelFactory(
            getProgramDetail = GetProgramDetail(StaticProgramDetailCatalog()),
            startWorkout = StartWorkout(NoOpWorkoutSessionStarter),
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
                    )
                }
            }
        }
    }
}

private object NoOpWorkoutSessionStarter : WorkoutSessionStarter {
    override fun start(plan: ValidatedWorkoutPlan) {
        // Gate3 will replace this composition-root seam with live session wiring.
    }
}
