package com.echelon.console.presentation

import com.echelon.console.application.usecase.ListProgramLibrary
import com.echelon.console.application.usecase.ProgramCatalog
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.ProgramCategory
import com.echelon.console.domain.ProgramId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramLibraryViewModelTest {
    @Test
    fun `starts loading then loads four hero programs in exact order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ProgramLibraryViewModel(
                listProgramLibrary = ListProgramLibrary(StaticProgramCatalog()),
                dispatcher = dispatcher,
            )

            assertTrue(viewModel.state.value is ProgramLibraryUiState.Loading)

            advanceUntilIdle()

            val loaded = viewModel.state.value as ProgramLibraryUiState.Ready
            assertEquals(
                listOf("FAT_BURN", "GLUTE_BLAST", "VERTICAL", "SURPRISE_ME"),
                loaded.heroPrograms.map { it.id.value },
            )

        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selection action updates selected hero id`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ProgramLibraryViewModel(
                listProgramLibrary = ListProgramLibrary(StaticProgramCatalog()),
                dispatcher = dispatcher,
            )

            advanceUntilIdle()

            viewModel.onAction(ProgramLibraryAction.SelectHero(ProgramId("VERTICAL")))

            assertEquals(ProgramId("VERTICAL"), (viewModel.state.value as ProgramLibraryUiState.Ready).selectedHeroId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `filter action updates visible program list`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ProgramLibraryViewModel(
                listProgramLibrary = ListProgramLibrary(StaticProgramCatalog()),
                dispatcher = dispatcher,
            )

            advanceUntilIdle()

            viewModel.onAction(ProgramLibraryAction.FilterPrograms(ProgramCategory.SWEAT))

            assertEquals(
                listOf("HIIT_20", "SWEAT_30", "PYRAMID"),
                (viewModel.state.value as ProgramLibraryUiState.Ready).visiblePrograms.map { it.id.value },
            )
            assertEquals(
                ProgramCategory.SWEAT,
                (viewModel.state.value as ProgramLibraryUiState.Ready).activeCategory,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `repository failure becomes error state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = ProgramLibraryViewModel(
                listProgramLibrary = ListProgramLibrary(
                    ProgramCatalog {
                        throw IllegalStateException("catalog unavailable")
                    },
                ),
                dispatcher = dispatcher,
            )

            advanceUntilIdle()

            val error = viewModel.state.value as ProgramLibraryUiState.Error
            assertEquals("catalog unavailable", error.message)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
