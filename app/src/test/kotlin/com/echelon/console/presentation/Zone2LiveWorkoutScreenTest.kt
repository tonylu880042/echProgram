package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.echelon.console.MainActivity
import com.echelon.console.domain.EquipmentReadState
import com.echelon.console.domain.HeartRateTargetRange
import com.echelon.console.domain.HeartRateTargetRangeResult
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.VerticalElevationSource
import com.echelon.console.domain.VerticalProgressStatus
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.VerticalTimeLimitProposal
import com.echelon.console.domain.VerticalTimeLimitStatus
import com.echelon.console.domain.VerticalWorkoutDraftControlStatus
import com.echelon.console.domain.Zone2HeartRateAdvice
import com.echelon.console.domain.Zone2HeartRateAdviceMode
import com.echelon.console.domain.Zone2HeartRateHysteresisStatus
import com.echelon.console.domain.Zone2HeartRateIntendedSource
import com.echelon.console.domain.Zone2HeartRatePreviewStatus
import com.echelon.console.domain.Zone2HeartRateStatus
import com.echelon.console.domain.Zone2HeartRateThresholdMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class Zone2LiveWorkoutScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `active evaluated zone2 context renders the heart rate panel`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    zone2Context = LiveZone2HeartRateContext(
                        target = target(),
                        intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
                        previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
                        adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
                        thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
                        hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
                        reading = LiveZone2HeartRateReading.Evaluated(
                            currentBpm = 130,
                            sampleAgeMillis = 500L,
                            status = Zone2HeartRateStatus.IN_ZONE,
                            advice = Zone2HeartRateAdvice.HOLD,
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("ZONE 2 HEART RATE").assertIsDisplayed()
        listOf(
            "120–140 BPM",
            "FITOS EQUIPMENT SNAPSHOT",
            "USER-CONFIRMED TARGET",
            "PREVIEW ONLY",
            "ADVISORY ONLY",
            "DIRECT THRESHOLD PREVIEW",
            "NO HYSTERESIS APPROVED",
            "NO DEVICE COMMANDS",
            "130 BPM",
            "500 MS",
            "IN ZONE",
            "HOLD CURRENT EFFORT MANUALLY",
        ).filter { text -> text != "PREVIEW ONLY" }
            .forEach { text -> composeTestRule.onNodeWithText(text).assertIsDisplayed() }
        composeTestRule.onAllNodesWithText("PREVIEW ONLY").assertCountEquals(2)
    }

    @Test
    fun `too high advice explicitly describes manual reduction and manual stop`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    zone2Context = zone2Context(
                        reading = LiveZone2HeartRateReading.Evaluated(
                            currentBpm = 155,
                            sampleAgeMillis = 200L,
                            status = Zone2HeartRateStatus.TOO_HIGH,
                            advice = Zone2HeartRateAdvice.SUGGEST_REDUCE_MANUAL_STOP_AVAILABLE,
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("TOO HIGH").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("REDUCE EFFORT MANUALLY · MANUAL STOP AVAILABLE")
            .assertIsDisplayed()
    }

    @Test
    fun `signal lost advice explicitly says no adjustment and manual mode`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    zone2Context = zone2Context(
                        reading = LiveZone2HeartRateReading.Evaluated(
                            currentBpm = 130,
                            sampleAgeMillis = 3_000L,
                            status = Zone2HeartRateStatus.HR_SIGNAL_LOST,
                            advice = Zone2HeartRateAdvice.NO_ADJUSTMENT_MANUAL_MODE,
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("HR SIGNAL LOST").assertIsDisplayed()
        composeTestRule.onNodeWithText("NO ADJUSTMENT · USE MANUAL MODE").assertIsDisplayed()
        composeTestRule.onNodeWithText("LAST HEART RATE").assertIsDisplayed()
        composeTestRule.onNodeWithText("CURRENT HEART RATE").assertDoesNotExist()
    }

    @Test
    fun `unavailable readings render typed reason families without evaluated payload`() {
        val reasons = listOf(
            LiveZone2HeartRateUnavailableReason.EvaluationSnapshotMismatch(
                LiveZone2HeartRateSnapshotField.TARGET,
            ),
            LiveZone2HeartRateUnavailableReason.ContextContractMismatch(
                LiveZone2HeartRateContextField.INTENDED_SOURCE,
            ),
            LiveZone2HeartRateUnavailableReason.SourceUnavailable(
                LiveZone2HeartRateSourceReason.ServiceUnavailable("timeout"),
            ),
            LiveZone2HeartRateUnavailableReason.InvalidHeartRateSample(
                LiveZone2HeartRateSampleReason.NonPositiveBpm(0),
            ),
            LiveZone2HeartRateUnavailableReason.EvaluatorFailure(
                LiveZone2HeartRateEvaluatorReason.FutureSampleTimestamp(
                    sampleElapsedRealtimeMillis = 2_000L,
                    nowElapsedRealtimeMillis = 1_000L,
                ),
            ),
        )

        reasons.forEach { reason ->
            setContent(
                LiveWorkoutUiState.Active(
                    workout = workout(
                        zone2Context = zone2Context(
                            reading = LiveZone2HeartRateReading.Unavailable(reason),
                        ),
                    ),
                ),
            )

            composeTestRule.onNodeWithText("HEART RATE UNAVAILABLE").assertIsDisplayed()
            composeTestRule
                .onNodeWithText(formatZone2UnavailableReason(reason))
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("CURRENT HEART RATE").assertDoesNotExist()
            composeTestRule.onNodeWithText("STATUS").assertDoesNotExist()
            composeTestRule.onNodeWithText("ADVICE").assertDoesNotExist()
        }
    }

    @Test
    fun `completed and stopped terminal states render the typed zone2 panel`() {
        setContent(
            LiveWorkoutUiState.Completed(
                summary = terminalSummary(),
            ),
        )
        composeTestRule.onNodeWithText("ZONE 2 HEART RATE").assertIsDisplayed()
        composeTestRule.onNodeWithText("IN ZONE").assertIsDisplayed()

        setContent(
            LiveWorkoutUiState.Stopped(
                summary = terminalSummary(),
            ),
        )
        composeTestRule.onNodeWithText("ZONE 2 HEART RATE").assertIsDisplayed()
        composeTestRule.onNodeWithText("IN ZONE").assertIsDisplayed()
    }

    @Test
    fun `generic and vertical paths do not render a zone2 panel`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    programId = ProgramId("FAT_BURN"),
                    programTitle = "FAT BURN",
                    previewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                ),
            ),
        )
        composeTestRule.onNodeWithText("ZONE 2 HEART RATE").assertDoesNotExist()

        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    programId = ProgramId("VERTICAL"),
                    programTitle = "VERTICAL",
                    previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                    verticalContext = verticalContext(),
                ),
            ),
        )
        composeTestRule.onNodeWithText("ZONE 2 HEART RATE").assertDoesNotExist()
        composeTestRule.onNodeWithText("VERTICAL PREVIEW CONTEXT").assertIsDisplayed()
    }

    @Test
    fun `zone2 active context keeps end workout visible at supported landscape size`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(zone2Context = zone2Context()),
            ),
        )

        composeTestRule.onNodeWithText("END WORKOUT").assertIsDisplayed()
    }

    @Test
    fun `sample age formatter preserves millisecond precision`() {
        assertEquals("0 MS", formatZone2SampleAge(0L))
        assertEquals("3,000 MS", formatZone2SampleAge(3_000L))
    }

    private fun setContent(state: LiveWorkoutUiState) {
        composeTestRule.activity.setContent {
            LiveWorkoutScreen(
                state = state,
                equipmentState = EquipmentReadState(),
                onAction = {},
                onBackToPrograms = {},
                onNavigate = {},
            )
        }
    }

    private fun workout(
        programId: ProgramId = ProgramId("ZONE_2"),
        programTitle: String = "ZONE 2",
        previewMode: ProgramPreviewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
        zone2Context: LiveZone2HeartRateContext? = null,
        verticalContext: LiveVerticalWorkoutContext? = null,
    ): LiveWorkoutReadModel = LiveWorkoutReadModel(
        programId = programId,
        elapsedSeconds = 120,
        remainingSeconds = 1_680,
        currentSegment = LiveWorkoutSegment(index = 0, name = "ZONE 2"),
        nextSegment = null,
        secondsUntilNextSegment = null,
        targetSpeed = SpeedTenths(50),
        targetIncline = InclineTenths(80),
        isPaused = false,
        programTitle = programTitle,
        previewMode = previewMode,
        verticalContext = verticalContext,
        zone2Context = zone2Context,
    )

    private fun zone2Context(
        reading: LiveZone2HeartRateReading = LiveZone2HeartRateReading.Evaluated(
            currentBpm = 130,
            sampleAgeMillis = 500L,
            status = Zone2HeartRateStatus.IN_ZONE,
            advice = Zone2HeartRateAdvice.HOLD,
        ),
    ): LiveZone2HeartRateContext = LiveZone2HeartRateContext(
        target = target(),
        intendedSource = Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
        previewStatus = Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
        adviceMode = Zone2HeartRateAdviceMode.ADVISORY_ONLY,
        thresholdMode = Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
        hysteresisStatus = Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
        reading = reading,
    )

    private fun terminalSummary(): LiveWorkoutSummary = LiveWorkoutSummary(
        programId = ProgramId("ZONE_2"),
        elapsedSeconds = 300,
        totalDurationSeconds = 1_800,
        programTitle = "ZONE 2",
        previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
        zone2Context = zone2Context(),
    )

    private fun verticalContext(): LiveVerticalWorkoutContext = LiveVerticalWorkoutContext(
        target = VerticalTarget.FIVE_HUNDRED_FEET,
        proposedTimeLimit = VerticalTimeLimitProposal(
            minutes = 45,
            status = VerticalTimeLimitStatus.PROPOSED,
        ),
        elevationSource = VerticalElevationSource.UNAVAILABLE,
        progressStatus = VerticalProgressStatus.NOT_CALCULATED,
        controlStatus = VerticalWorkoutDraftControlStatus.PREVIEW_ONLY,
    )

    private fun target(): HeartRateTargetRange = when (
        val result = HeartRateTargetRange.createUserConfirmed(120, 140)
    ) {
        is HeartRateTargetRangeResult.Accepted -> result.target
        is HeartRateTargetRangeResult.Rejected -> error("Expected target, got $result")
    }
}
