package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.echelon.console.MainActivity
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentControlState
import com.echelon.console.domain.EquipmentDescriptor
import com.echelon.console.domain.EquipmentInclineLevel
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.EquipmentSpeed
import com.echelon.console.domain.EquipmentSpeedUnit
import com.echelon.console.domain.EquipmentTelemetry
import com.echelon.console.domain.EquipmentType
import com.echelon.console.domain.SpeedKmh
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EquipmentTelemetryPanelTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `service unavailable state is clear when FitOS host is absent`() {
        setContent(EquipmentReadState(connection = EquipmentConnection.ServiceUnavailable("timeout")))

        composeTestRule.onNodeWithText("EQUIPMENT UNAVAILABLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("FitOS equipment service is not available.").assertIsDisplayed()
    }

    @Test
    fun `ready state renders live telemetry without exposing controls`() {
        setContent(
            EquipmentReadState(
                connection = EquipmentConnection.Ready,
                apiVersion = 1,
                equipment = EquipmentDescriptor(
                    connectionStatus = "CONNECTED",
                    equipmentType = EquipmentType.RUN,
                    runType = "NORMAL",
                    deviceName = "Treadmill",
                    isMetric = false,
                    isBindDevice = true,
                    controlState = EquipmentControlState.STARTED,
                ),
                telemetry = EquipmentTelemetry(
                    elapsedRealtimeMillis = 1_000L,
                    elapsedTime = "00:12:34",
                    speed = EquipmentSpeed(SpeedKmh(12.87472), 8.0, EquipmentSpeedUnit.MILES_PER_HOUR),
                    incline = EquipmentInclineLevel(3),
                    heartRateBpm = 142,
                    distance = 2.1,
                    calories = 88.5,
                ),
            ),
        )

        composeTestRule.onNodeWithText("LIVE TELEMETRY").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:12:34").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.0 MPH").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 LEVEL").assertIsDisplayed()
        composeTestRule.onNodeWithText("142 BPM").assertIsDisplayed()
    }

    @Test
    fun `stale state is visible instead of presenting old readings as live`() {
        setContent(
            EquipmentReadState(
                connection = EquipmentConnection.Stale(3_200L),
                telemetry = EquipmentTelemetry(
                    elapsedRealtimeMillis = 1_000L,
                    elapsedTime = "00:01:00",
                    speed = null,
                    incline = null,
                    heartRateBpm = null,
                    distance = null,
                    calories = null,
                ),
            ),
        )

        composeTestRule.onNodeWithText("TELEMETRY STALE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting for a fresh FitOS reading.").assertIsDisplayed()
    }

    private fun setContent(state: EquipmentReadState) {
        composeTestRule.activity.setContent {
            EquipmentTelemetryPanel(state = state)
        }
    }
}
