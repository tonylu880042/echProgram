package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.echelon.console.MainActivity
import com.echelon.console.data.StaticProgramCatalog
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.SpeedTenths
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Zone2WorkoutSetupScreenTest {
    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    @Test
    fun `zone 2 setup renders safety source promise and current equipment bpm`() {
        setContent(
            state = zone2State(),
            equipmentState = EquipmentReadState(
                connection = EquipmentConnection.Ready,
                telemetry = EquipmentTelemetry(
                    elapsedRealtimeMillis = 12_000L,
                    elapsedTime = "00:12",
                    speed = null,
                    incline = null,
                    heartRateBpm = 132,
                    distance = null,
                    calories = null,
                ),
            ),
        )

        composeTestRule.onNodeWithText("CONFIGURE ZONE 2").assertIsDisplayed()
        composeTestRule.onNodeWithText(zone2State().detail.promise).assertIsDisplayed()
        listOf(
            "PREVIEW ONLY",
            "USER-CONFIRMED TARGET",
            "NO AGE OR MAX-HR FORMULA",
            "ADVISORY ONLY",
            "NO DEVICE COMMANDS",
            "USER CONFIRMED",
            "FITOS EQUIPMENT SNAPSHOT",
            "FITOS TELEMETRY READY",
            "CURRENT EQUIPMENT BPM",
            "132 BPM",
        ).forEach { text ->
            composeTestRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("ZONE 2 SETUP UI IS COMING NEXT").assertDoesNotExist()
    }

    @Test
    fun `zone 2 controls emit exact actions and selected duration semantics`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setInteractiveContent(actions)

        composeTestRule.onNodeWithContentDescription("LOWER TARGET BPM").performTextInput("118")
        composeTestRule.onNodeWithContentDescription("UPPER TARGET BPM").performTextInput("142")
        composeTestRule.onNodeWithContentDescription("Select Zone 2 duration 30 minutes")
            .assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Select Zone 2 duration 20 minutes")
            .assertIsNotSelected()
        listOf(20, 30, 45, 60).forEach { duration ->
            composeTestRule.onNodeWithContentDescription("Select Zone 2 duration $duration minutes")
                .performScrollTo()
                .performClick()
        }
        composeTestRule.onNodeWithContentDescription("START ZONE 2 PREVIEW")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf(
                ProgramSetupAction.SetZone2LowerBpm("118"),
                ProgramSetupAction.SetZone2UpperBpm("142"),
                ProgramSetupAction.SetZone2Duration(DurationMinutes(20)),
                ProgramSetupAction.SetZone2Duration(DurationMinutes(30)),
                ProgramSetupAction.SetZone2Duration(DurationMinutes(45)),
                ProgramSetupAction.SetZone2Duration(DurationMinutes(60)),
                ProgramSetupAction.StartZone2Preview,
                ProgramSetupAction.Back,
            ),
            actions,
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-land")
    fun `zone 2 setup exposes error and exact caps with scrollable primary action`() {
        val actions = mutableListOf<ProgramSetupAction>()
        setContent(
            state = zone2State(
                errorMessage = "LOWER BPM IS REQUIRED",
                userMaxSpeed = SpeedTenths(50),
                machineMaxSpeed = SpeedTenths(120),
                userMaxIncline = InclineTenths(80),
                machineMaxIncline = InclineTenths(150),
            ),
            onAction = actions::add,
        )

        composeTestRule.onNodeWithText("LOWER BPM IS REQUIRED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("USER CAP").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("5.0 MPH / 8.0%").assertCountEquals(2)
        composeTestRule.onNodeWithText("MACHINE CAP").assertIsDisplayed()
        composeTestRule.onNodeWithText("12.0 MPH / 15.0%").assertIsDisplayed()
        composeTestRule.onNodeWithText("EFFECTIVE CAP").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("START ZONE 2 PREVIEW")
            .performScrollTo()
            .performClick()

        assertEquals(listOf(ProgramSetupAction.StartZone2Preview), actions)
    }

    @Test
    fun `zone 2 source card is truthful when equipment or bpm is unavailable`() {
        val state = zone2State()
        setContent(
            state = state,
            equipmentState = EquipmentReadState(connection = EquipmentConnection.Disconnected),
        )

        composeTestRule.onNodeWithText("EQUIPMENT NOT CONNECTED").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("USER CONFIRMED").assertIsDisplayed()
        composeTestRule.onNodeWithText("FITOS EQUIPMENT SNAPSHOT").assertIsDisplayed()
        composeTestRule.onNodeWithText("CURRENT EQUIPMENT BPM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NOT AVAILABLE").assertIsDisplayed()

        setContent(
            state = state,
            equipmentState = EquipmentReadState(connection = EquipmentConnection.Ready),
        )
        composeTestRule.onNodeWithText("FITOS TELEMETRY READY").assertIsDisplayed()
        composeTestRule.onNodeWithText("CURRENT EQUIPMENT BPM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NOT AVAILABLE").assertIsDisplayed()
    }

    private fun setContent(
        state: ProgramSetupUiState.Zone2Configuring,
        onAction: (ProgramSetupAction) -> Unit = {},
        equipmentState: EquipmentReadState = EquipmentReadState(),
    ) {
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state,
                onAction = onAction,
                onNavigate = {},
                onShowLibrary = {},
                equipmentState = equipmentState,
            )
        }
    }

    private fun setInteractiveContent(actions: MutableList<ProgramSetupAction>) {
        var state by mutableStateOf(zone2State())
        composeTestRule.activity.setContent {
            ProgramSetupScreen(
                state = state,
                onAction = { action ->
                    actions += action
                    state = when (action) {
                        is ProgramSetupAction.SetZone2Duration -> state.copy(duration = action.duration)
                        is ProgramSetupAction.SetZone2LowerBpm -> state.copy(lowerBpmText = action.text)
                        is ProgramSetupAction.SetZone2UpperBpm -> state.copy(upperBpmText = action.text)
                        else -> state
                    }
                },
                onNavigate = {},
                onShowLibrary = {},
                equipmentState = EquipmentReadState(),
            )
        }
    }

    private fun zone2State(
        duration: DurationMinutes = DurationMinutes(30),
        errorMessage: String? = null,
        userMaxSpeed: SpeedTenths = SpeedTenths(50),
        machineMaxSpeed: SpeedTenths = SpeedTenths(120),
        userMaxIncline: InclineTenths = InclineTenths(80),
        machineMaxIncline: InclineTenths = InclineTenths(150),
    ): ProgramSetupUiState.Zone2Configuring = ProgramSetupUiState.Zone2Configuring(
        detail = requireNotNull(
            StaticProgramCatalog().findProgramDetail(ProgramId("ZONE_2")),
        ),
        duration = duration,
        lowerBpmText = "",
        upperBpmText = "",
        userMaxSpeed = userMaxSpeed,
        machineMaxSpeed = machineMaxSpeed,
        userMaxIncline = userMaxIncline,
        machineMaxIncline = machineMaxIncline,
        errorMessage = errorMessage,
    )
}
