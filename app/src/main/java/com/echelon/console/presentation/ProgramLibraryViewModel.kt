package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.ListProgramLibrary
import com.echelon.console.domain.HeroProgram
import com.echelon.console.domain.Program
import com.echelon.console.domain.ProgramCategory
import com.echelon.console.domain.ProgramId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProgramLibraryUiState {
    data object Loading : ProgramLibraryUiState

    data class Ready(
        val heroPrograms: List<HeroProgram>,
        val visiblePrograms: List<Program>,
        val selectedHeroId: ProgramId? = null,
        val allPrograms: List<Program> = visiblePrograms,
    ) : ProgramLibraryUiState

    data class Error(
        val message: String,
    ) : ProgramLibraryUiState
}

sealed interface ProgramLibraryAction {
    data class SelectHero(val id: ProgramId) : ProgramLibraryAction

    data class FilterPrograms(val category: ProgramCategory) : ProgramLibraryAction
}

class ProgramLibraryViewModel(
    private val listProgramLibrary: ListProgramLibrary,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _state = MutableStateFlow<ProgramLibraryUiState>(ProgramLibraryUiState.Loading)

    val state: StateFlow<ProgramLibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(dispatcher) {
            loadProgramLibrary()
        }
    }

    fun onAction(action: ProgramLibraryAction) {
        val ready = _state.value as? ProgramLibraryUiState.Ready ?: return

        _state.value = when (action) {
            is ProgramLibraryAction.SelectHero -> ready.copy(selectedHeroId = action.id)
            is ProgramLibraryAction.FilterPrograms -> ready.copy(
                visiblePrograms = ready.allPrograms.filterBy(action.category),
            )
        }
    }

    private fun List<Program>.filterBy(category: ProgramCategory): List<Program> = when (category) {
        ProgramCategory.ALL -> toList()
        else -> filter { it.category == category }
    }

    private fun loadProgramLibrary() {
        try {
            val library = listProgramLibrary()
            val allPrograms = library.allPrograms.toList()
            _state.value = ProgramLibraryUiState.Ready(
                heroPrograms = library.heroPrograms.toList(),
                visiblePrograms = allPrograms,
                allPrograms = allPrograms,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _state.value = ProgramLibraryUiState.Error(
                message = exception.message ?: "Unable to load program library",
            )
        }
    }
}
