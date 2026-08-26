package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.echelon.console.MainActivity
import com.echelon.console.domain.ProgramPreviewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgramPreviewNoticeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `setup notice omits fixed mode and renders every special mode`() {
        val specialModes = listOf(
            ProgramPreviewMode.BASELINE_PREVIEW,
            ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
            ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW,
            ProgramPreviewMode.HEART_RATE_PREVIEW,
            ProgramPreviewMode.GENERATED_PREVIEW,
            ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
        )

        composeTestRule.activity.setContent {
            Column {
                ProgramPreviewNotice(ProgramPreviewMode.FIXED_PROFILE_PREVIEW)
                specialModes.forEach { mode ->
                    ProgramPreviewNotice(mode)
                }
            }
        }

        composeTestRule.onAllNodesWithText("PREVIEW ONLY").assertCountEquals(specialModes.size)
        composeTestRule
            .onNodeWithText(ProgramPreviewMode.FIXED_PROFILE_PREVIEW.disclosureMessage())
            .assertDoesNotExist()
        specialModes.forEach { mode ->
            composeTestRule.onNodeWithText(mode.disclosureMessage()).assertExists()
        }
    }

    @Test
    fun `each preview mode exposes one explicit disclosure`() {
        val expected = mapOf(
            ProgramPreviewMode.FIXED_PROFILE_PREVIEW to
                "Follow the displayed targets manually; FitOS control is not enabled.",
            ProgramPreviewMode.BASELINE_PREVIEW to
                "Baseline progression is not connected to approved workout history yet.",
            ProgramPreviewMode.ELEVATION_TARGET_PREVIEW to
                "Elevation target and completion rules require approved elevation data.",
            ProgramPreviewMode.HISTORY_ADAPTIVE_PREVIEW to
                "History-based adaptation requires approved workout history.",
            ProgramPreviewMode.HEART_RATE_PREVIEW to
                "Heart-rate zone control requires an approved HR source.",
            ProgramPreviewMode.GENERATED_PREVIEW to
                "Generated plans require an approved deterministic generator.",
            ProgramPreviewMode.CALORIE_TARGET_PREVIEW to
                "Calorie target progress requires FitOS estimated calories; estimator semantics are pending.",
        )

        expected.forEach { (mode, message) ->
            assertEquals(message, mode.disclosureMessage())
        }
    }
}
