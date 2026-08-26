package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.echelon.console.application.usecase.ListProgramLibrary

class ProgramLibraryViewModelFactory(
    private val listProgramLibrary: ListProgramLibrary,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProgramLibraryViewModel::class.java)) {
            return ProgramLibraryViewModel(listProgramLibrary) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
