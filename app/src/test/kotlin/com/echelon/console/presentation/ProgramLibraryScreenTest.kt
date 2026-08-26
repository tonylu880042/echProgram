package com.echelon.console.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.activity.compose.setContent
import com.echelon.console.MainActivity
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.ProgramCategory
import com.echelon.console.domain.ProgramId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgramLibraryScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `ready page exposes heading hero goals in order and all programs`() {
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = readyState(),
                onAction = {},
                onNavigate = {},
            )
        }

        composeTestRule.onNodeWithText("WHAT DO YOU WANT TODAY?").assertIsDisplayed()
        val expectedHeroOrder = listOf("FAT BURN", "GLUTE BLAST", "VERTICAL", "SURPRISE ME")
        expectedHeroOrder.forEach { title ->
            composeTestRule.onNodeWithText(title).assertIsDisplayed()
        }
        val renderedHeroOrder = composeTestRule
            .onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .flatten()
            .mapNotNull { it.textValue() }
            .filter { it in expectedHeroOrder }
        assertEquals(expectedHeroOrder, renderedHeroOrder)
        composeTestRule.onNodeWithText("ALL PROGRAMS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `clicking a hero emits select action`() {
        val actions = mutableListOf<ProgramLibraryAction>()
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = readyState(),
                onAction = actions::add,
                onNavigate = {},
            )
        }

        composeTestRule.onNodeWithText("VERTICAL").performClick()

        assertEquals(
            listOf(ProgramLibraryAction.SelectHero(ProgramId("VERTICAL"))),
            actions,
        )
    }

    @Test
    fun `filter menu emits category action`() {
        val actions = mutableListOf<ProgramLibraryAction>()
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = readyState(),
                onAction = actions::add,
                onNavigate = {},
            )
        }

        composeTestRule.onNodeWithText("FILTER").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Filter HIIT").performScrollTo().performClick()

        assertEquals(
            listOf(ProgramLibraryAction.FilterPrograms(ProgramCategory.HIIT)),
            actions,
        )
    }

    @Test
    fun `loading state is visible`() {
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = ProgramLibraryUiState.Loading,
                onAction = {},
                onNavigate = {},
            )
        }
        composeTestRule.onNodeWithText("LOADING PROGRAM LIBRARY").assertIsDisplayed()
    }

    @Test
    fun `empty state is visible`() {
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = ProgramLibraryUiState.Ready(
                    heroPrograms = readyState().heroPrograms,
                    visiblePrograms = emptyList(),
                    activeCategory = ProgramCategory.HIIT,
                ),
                onAction = {},
                onNavigate = {},
            )
        }
        composeTestRule.onNodeWithText("NO MATCHING PROGRAMS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `error state is visible`() {
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = ProgramLibraryUiState.Error("Catalog offline"),
                onAction = {},
                onNavigate = {},
            )
        }
        composeTestRule.onNodeWithText("PROGRAM LIBRARY ERROR").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catalog offline").assertIsDisplayed()
    }

    @Test
    fun `rail selection invokes navigation callback`() {
        val destinations = mutableListOf<ProgramLibraryDestination>()
        composeTestRule.activity.setContent {
            ProgramLibraryScreen(
                state = readyState(),
                onAction = {},
                onNavigate = destinations::add,
            )
        }

        composeTestRule.onNodeWithText("Programs").performClick()
        composeTestRule.onNodeWithText("History").performClick()

        assertEquals(
            listOf(ProgramLibraryDestination.PROGRAMS, ProgramLibraryDestination.HISTORY),
            destinations,
        )
    }

    private fun readyState(): ProgramLibraryUiState.Ready {
        val catalog = StaticProgramCatalog()
        return ProgramLibraryUiState.Ready(
            heroPrograms = catalog.listHeroPrograms(),
            visiblePrograms = catalog.listPrograms(),
        )
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.flatten() }

    private fun SemanticsNode.textValue(): String? =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(separator = "") { it.text }
            .takeIf { it.isNotEmpty() }
}
