package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.echelon.console.MainActivity
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgramDetailScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `screen shows exact title promise metrics and ordered segments`() {
        setContent()

        composeTestRule.onNodeWithText("FAT BURN").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Sustained calorie-burning work without requiring hard running.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("TOTAL DURATION").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("45:00").assertCountEquals(2)
        composeTestRule.onNodeWithText("SPEED RANGE (MPH)").assertIsDisplayed()
        composeTestRule.onNodeWithText("2.8 - 5.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("INCLINE RANGE (%)").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0 - 12.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("22:30").performScrollTo().assertIsDisplayed()

        val expectedSegments = listOf("Warm Up", "Steady Burn", "Climb", "Push", "Recovery", "Cool Down")
        composeTestRule.onNodeWithText("SEGMENT BREAKDOWN").performScrollTo().assertIsDisplayed()
        expectedSegments.forEach { segment ->
            composeTestRule.onNodeWithText(segment).performScrollTo().assertIsDisplayed()
        }
        val renderedSegments = composeTestRule
            .onRoot(useUnmergedTree = true)
            .fetchSemanticsNode()
            .flatten()
            .mapNotNull { it.textValue() }
            .filter { it in expectedSegments }
        assertEquals(expectedSegments, renderedSegments)
    }

    @Test
    fun `back make it yours and start callbacks are exposed`() {
        var backCount = 0
        var makeItYoursCount = 0
        var startCount = 0
        setContent(
            onBack = { backCount++ },
            onMakeItYours = { makeItYoursCount++ },
            onStartDefault = { startCount++ },
        )

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().performClick()
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().performClick()

        assertEquals(1, backCount)
        assertEquals(1, makeItYoursCount)
        assertEquals(1, startCount)
    }

    @Test
    fun `rail callback is exposed with labeled history item`() {
        val destinations = mutableListOf<ProgramLibraryDestination>()
        setContent(onNavigate = destinations::add)

        composeTestRule.onNodeWithText("History").performClick()

        assertEquals(listOf(ProgramLibraryDestination.HISTORY), destinations)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `landscape content stays inside safe drawing insets`() {
        setContent()
        composeTestRule.runOnIdle {
            WindowCompat.setDecorFitsSystemWindows(composeTestRule.activity.window, false)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 24))
                .build()
            ViewCompat.dispatchApplyWindowInsets(composeTestRule.activity.window.decorView, insets)
        }
        composeTestRule.waitForIdle()

        val header = composeTestRule.onNodeWithText("TELEMETRY").fetchSemanticsNode()
        val title = composeTestRule.onNodeWithText("FAT BURN").fetchSemanticsNode()
        val rail = composeTestRule.onNodeWithText("Programs").fetchSemanticsNode()
        val root = composeTestRule.onRoot().fetchSemanticsNode()

        assertTrue("header overlaps status bar", header.boundsInRoot.top >= 24f)
        assertTrue("title overlaps status bar", title.boundsInRoot.top >= 24f)
        assertTrue("rail overlaps status bar", rail.boundsInRoot.top >= 24f)
        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().assertIsDisplayed()
        val action = composeTestRule.onNodeWithText("START WORKOUT").fetchSemanticsNode()
        assertTrue("action overlaps navigation bar", action.boundsInRoot.bottom <= root.boundsInRoot.bottom - 24f)
    }

    @Test
    @Config(qualifiers = "w720dp-h400dp-land")
    fun `compact landscape can scroll to both actions`() {
        setContent()

        composeTestRule.onNodeWithText("START WORKOUT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("MAKE IT YOURS").performScrollTo().assertIsDisplayed()
    }

    private fun setContent(
        onBack: () -> Unit = {},
        onMakeItYours: () -> Unit = {},
        onStartDefault: () -> Unit = {},
        onNavigate: (ProgramLibraryDestination) -> Unit = {},
    ) {
        composeTestRule.activity.setContent {
            ProgramDetailScreen(
                detail = detail(),
                onBack = onBack,
                onMakeItYours = onMakeItYours,
                onStartDefault = onStartDefault,
                onNavigate = onNavigate,
            )
        }
    }

    private fun detail(): ProgramDetail = ProgramDetail(
        programId = ProgramId("FAT_BURN"),
        title = "FAT BURN",
        promise = "Sustained calorie-burning work without requiring hard running.",
        defaultSettings = PlanSettings(
            duration = DurationMinutes(45),
            intensity = PlanIntensity.MEDIUM,
            focus = PlanFocus.BALANCED,
            maxSpeed = SpeedTenths(55),
            maxIncline = InclineTenths(120),
            adaptToYou = false,
        ),
        speedRange = SpeedRange(SpeedTenths(28), SpeedTenths(55)),
        inclineRange = InclineRange(InclineTenths(10), InclineTenths(120)),
        profile = listOf(
            ProgramSegmentSummary("Warm Up", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
            ProgramSegmentSummary("Steady Burn", DurationMinutes(10), SpeedTenths(40), InclineTenths(20)),
            ProgramSegmentSummary("Climb", DurationMinutes(10), SpeedTenths(36), InclineTenths(45)),
            ProgramSegmentSummary("Push", DurationMinutes(10), SpeedTenths(55), InclineTenths(30)),
            ProgramSegmentSummary("Recovery", DurationMinutes(5), SpeedTenths(30), InclineTenths(10)),
            ProgramSegmentSummary("Cool Down", DurationMinutes(5), SpeedTenths(28), InclineTenths(10)),
        ),
    )

    private fun SemanticsNode.flatten(): List<SemanticsNode> =
        listOf(this) + children.flatMap { it.flatten() }

    private fun SemanticsNode.textValue(): String? =
        config.getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(separator = "") { it.text }
            .takeIf { it.isNotEmpty() }
}
