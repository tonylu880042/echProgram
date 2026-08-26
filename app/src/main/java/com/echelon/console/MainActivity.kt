package com.echelon.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.echelon.console.application.usecase.ListProgramLibrary
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.presentation.ProgramLibraryDestination
import com.echelon.console.presentation.ProgramLibraryRoute
import com.echelon.console.presentation.ProgramLibraryViewModel
import com.echelon.console.presentation.ProgramLibraryViewModelFactory

class MainActivity : ComponentActivity() {
    private val programLibraryViewModel: ProgramLibraryViewModel by viewModels {
        ProgramLibraryViewModelFactory(
            listProgramLibrary = ListProgramLibrary(StaticProgramCatalog()),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                ProgramLibraryRoute(
                    viewModel = programLibraryViewModel,
                    onNavigate = { _: ProgramLibraryDestination ->
                        // Navigation remains an explicit seam until the destination screens exist.
                    },
                )
            }
        }
    }
}
