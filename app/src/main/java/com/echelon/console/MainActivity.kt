package com.echelon.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.echelon.console.application.usecase.ListHeroPrograms
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.presentation.ProgramLibraryShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val programs = ListHeroPrograms(StaticProgramCatalog())()
        setContent {
            MaterialTheme {
                Surface {
                    ProgramLibraryShell(programs = programs)
                }
            }
        }
    }
}
