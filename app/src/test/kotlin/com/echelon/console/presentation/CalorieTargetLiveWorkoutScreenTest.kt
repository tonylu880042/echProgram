package com.echelon.console.presentation

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.echelon.console.MainActivity
import com.echelon.console.domain.CalorieCompletionAuthority
import com.echelon.console.domain.CalorieDeviceCommandStatus
import com.echelon.console.domain.CalorieEstimateStatus
import com.echelon.console.domain.CaloriePreviewStatus
import com.echelon.console.domain.CalorieProgressSemantics
import com.echelon.console.domain.CalorieSampleFreshness
import com.echelon.console.domain.CalorieSessionResetSemantics
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.CalorieTargetSelection
import com.echelon.console.domain.CalorieTargetSelectionResult
import com.echelon.console.domain.CalorieTelemetrySource
import com.echelon.console.domain.CalorieUnitSemantics
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.EquipmentConnection
import com.echelon.console.domain.EquipmentType
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class CalorieTargetLiveWorkoutScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `active fresh reading renders target display age and safety metadata`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    context = context(
                        targetOption = CalorieTargetOption.THREE_HUNDRED_KCAL,
                        reading = LiveCalorieTargetReading.Evaluated(
                            displayValue = 183.25,
                            sampleAgeMillis = 500L,
                            freshness = CalorieSampleFreshness.FRESH,
                        ),
                    ),
                ),
            ),
        )

        listOf(
            "CALORIE TARGET PREVIEW",
            "300 CAL EST",
            "USER SELECTED",
            "40 MIN",
            "60 MIN",
            "NOT SESSION DURATION",
            "NOT CLIENT APPROVED",
            "ESTIMATED",
            "FITOS EQUIPMENT SNAPSHOT CALORIES",
            "UNIT SEMANTICS UNCONFIRMED",
            "SESSION RESET UNCONFIRMED",
            "COMPLETION AUTHORITY NOT APPROVED",
            "DISPLAY ONLY / NO TARGET PROGRESS",
            "NO DEVICE COMMANDS",
            "FITOS SNAPSHOT DISPLAY",
            "183.25",
            "500 MS",
            "FRESH",
            "END WORKOUT",
        ).forEach { text -> composeTestRule.onNodeWithText(text).assertIsDisplayed() }
        composeTestRule.onAllNodesWithText("PREVIEW ONLY").assertCountEquals(2)
    }

    @Test
    fun `stale reading uses last snapshot wording and keeps stale status`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    context = context(
                        reading = LiveCalorieTargetReading.Evaluated(
                            displayValue = 1_234.5,
                            sampleAgeMillis = 3_000L,
                            freshness = CalorieSampleFreshness.STALE,
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("LAST FITOS SNAPSHOT DISPLAY").assertIsDisplayed()
        composeTestRule.onNodeWithText("1,234.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("3,000 MS").assertIsDisplayed()
        composeTestRule.onNodeWithText("STALE").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("FITOS SNAPSHOT DISPLAY").assertCountEquals(0)
    }

    @Test
    fun `all target options show their typed proposed max without changing representative duration`() {
        listOf(
            CalorieTargetOption.ONE_HUNDRED_KCAL to 60,
            CalorieTargetOption.TWO_HUNDRED_KCAL to 60,
            CalorieTargetOption.THREE_HUNDRED_KCAL to 60,
            CalorieTargetOption.FIVE_HUNDRED_KCAL to 90,
        ).forEach { (option, proposedMinutes) ->
            setContent(
                LiveWorkoutUiState.Active(
                    workout = workout(context = context(targetOption = option)),
                ),
            )

            composeTestRule.onNodeWithText("${option.estimatedKcal} CAL EST").assertIsDisplayed()
            composeTestRule.onNodeWithText("40 MIN").assertIsDisplayed()
            composeTestRule.onNodeWithText("$proposedMinutes MIN").assertIsDisplayed()
            composeTestRule.onNodeWithText("NOT SESSION DURATION").assertIsDisplayed()
        }
    }

    @Test
    fun `unavailable reading renders reason without an evaluated numeric payload`() {
        val reason = LiveCalorieTargetUnavailableReason.SourceUnavailable(
            LiveCalorieTargetSourceReason.ServiceUnavailable("timeout"),
        )
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    context = context(
                        reading = LiveCalorieTargetReading.Unavailable(reason),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("CALORIES DISPLAY UNAVAILABLE").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(formatLiveCalorieTargetUnavailableReason(reason))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("183.25").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("SAMPLE AGE").assertCountEquals(0)
    }

    @Test
    fun `unavailable formatter covers every typed reason family`() {
        val reasons = buildList {
            addAll(
                LiveCalorieTargetSnapshotField.entries.map(
                    LiveCalorieTargetUnavailableReason::EvaluationSnapshotMismatch,
                ),
            )
            addAll(
                LiveCalorieTargetContextField.entries.map(
                    LiveCalorieTargetUnavailableReason::ContextContractMismatch,
                ),
            )
            addAll(
                listOf(
                    LiveCalorieTargetSourceReason.Connecting,
                    LiveCalorieTargetSourceReason.Disconnected,
                    LiveCalorieTargetSourceReason.ServiceUnavailable("timeout"),
                    LiveCalorieTargetSourceReason.UnsupportedApi(2),
                    LiveCalorieTargetSourceReason.EquipmentDisconnected("offline"),
                    LiveCalorieTargetSourceReason.Ready,
                    LiveCalorieTargetSourceReason.Stale(3_000L),
                ).map(LiveCalorieTargetUnavailableReason::SourceUnavailable),
            )
            add(LiveCalorieTargetUnavailableReason.MissingEquipmentDescriptor)
            addAll(
                EquipmentType.entries.map(LiveCalorieTargetUnavailableReason::UnsupportedEquipment),
            )
            add(LiveCalorieTargetUnavailableReason.MissingTelemetry)
            addAll(
                listOf(
                    LiveCalorieTargetSampleReason.MissingDisplayValue,
                    LiveCalorieTargetSampleReason.NonFiniteDisplayValue(Double.NaN),
                    LiveCalorieTargetSampleReason.NegativeDisplayValue(-1.0),
                    LiveCalorieTargetSampleReason.MissingTimestamp,
                    LiveCalorieTargetSampleReason.NegativeTimestamp(-1L),
                ).map(LiveCalorieTargetUnavailableReason::InvalidCalorieSample),
            )
            addAll(
                listOf(
                    LiveCalorieTargetEvaluatorReason.MissingTarget,
                    LiveCalorieTargetEvaluatorReason.MissingSample,
                    LiveCalorieTargetEvaluatorReason.InvalidNowElapsedRealtimeMillis(-1L),
                    LiveCalorieTargetEvaluatorReason.InvalidStaleAfterMillis(0L),
                    LiveCalorieTargetEvaluatorReason.FutureSampleTimestamp(2_000L, 1_000L),
                ).map(LiveCalorieTargetUnavailableReason::EvaluatorFailure),
            )
        }

        reasons.forEach { reason ->
            val label = formatLiveCalorieTargetUnavailableReason(reason)
            check(label.isNotBlank()) { "Missing label for $reason" }
        }
    }

    @Test
    fun `completed and stopped screens retain the typed panel`() {
        val summary = terminalSummary(context())
        setContent(LiveWorkoutUiState.Completed(summary))
        composeTestRule.onNodeWithText("CALORIE TARGET PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("183.25").assertIsDisplayed()
        composeTestRule.onNodeWithText("DONE").assertIsDisplayed()

        setContent(LiveWorkoutUiState.Stopped(summary))
        composeTestRule.onNodeWithText("CALORIE TARGET PREVIEW").assertIsDisplayed()
        composeTestRule.onNodeWithText("183.25").assertIsDisplayed()
        composeTestRule.onNodeWithText("DONE").assertIsDisplayed()
    }

    @Test
    fun `generic vertical and zone2 paths do not render a calorie panel`() {
        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    programId = ProgramId("FAT_BURN"),
                    programTitle = "FAT BURN",
                    previewMode = ProgramPreviewMode.FIXED_PROFILE_PREVIEW,
                    context = null,
                ),
            ),
        )
        composeTestRule.onAllNodesWithText("CALORIE TARGET PREVIEW").assertCountEquals(0)

        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    programId = ProgramId("VERTICAL"),
                    programTitle = "VERTICAL",
                    previewMode = ProgramPreviewMode.ELEVATION_TARGET_PREVIEW,
                    context = null,
                    verticalContext = verticalContext(),
                ),
            ),
        )
        composeTestRule.onAllNodesWithText("CALORIE TARGET PREVIEW").assertCountEquals(0)

        setContent(
            LiveWorkoutUiState.Active(
                workout = workout(
                    programId = ProgramId("ZONE_2"),
                    programTitle = "ZONE 2",
                    previewMode = ProgramPreviewMode.HEART_RATE_PREVIEW,
                    context = null,
                    zone2Context = zone2Context(),
                ),
            ),
        )
        composeTestRule.onAllNodesWithText("CALORIE TARGET PREVIEW").assertCountEquals(0)
    }

    @Test
    fun `active calorie panel keeps end workout visible at supported landscape size`() {
        setContent(LiveWorkoutUiState.Active(workout = workout(context = context())))

        composeTestRule.onNodeWithText("END WORKOUT").assertIsDisplayed()
    }

    @Test
    fun `formatters use stable grouped Locale US output`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            org.junit.Assert.assertEquals("1,234.57", formatCalorieTargetDisplay(1_234.567))
            org.junit.Assert.assertEquals("3,000 MS", formatCalorieTargetSampleAge(3_000L))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `calorie panel exposes no progress or completion claims`() {
        setContent(LiveWorkoutUiState.Active(workout = workout(context = context())))

        listOf(
            "CAL TO GO",
            "CALORIES REMAINING",
            "TARGET REACHED",
            "183 / 300",
            "100%",
        ).forEach { forbidden -> composeTestRule.onAllNodesWithText(forbidden).assertCountEquals(0) }
    }

    private fun setContent(state: LiveWorkoutUiState) {
        composeTestRule.activity.setContent {
            LiveWorkoutScreen(
                state = state,
                equipmentState = com.echelon.console.domain.EquipmentReadState(
                    connection = EquipmentConnection.Ready,
                ),
                onAction = {},
                onBackToPrograms = {},
                onNavigate = {},
            )
        }
    }

    private fun workout(
        programId: ProgramId = ProgramId("CALORIE_TARGET"),
        programTitle: String = "CALORIE TARGET",
        previewMode: ProgramPreviewMode = ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
        context: LiveCalorieTargetContext? = context(),
        verticalContext: LiveVerticalWorkoutContext? = null,
        zone2Context: LiveZone2HeartRateContext? = null,
    ): LiveWorkoutReadModel = LiveWorkoutReadModel(
        programId = programId,
        elapsedSeconds = 120,
        remainingSeconds = 2_280,
        currentSegment = LiveWorkoutSegment(index = 0, name = "CALORIE TARGET"),
        nextSegment = null,
        secondsUntilNextSegment = null,
        targetSpeed = SpeedTenths(50),
        targetIncline = InclineTenths(80),
        isPaused = false,
        programTitle = programTitle,
        previewMode = previewMode,
        verticalContext = verticalContext,
        zone2Context = zone2Context,
        calorieTargetContext = context,
    )

    private fun context(
        targetOption: CalorieTargetOption = CalorieTargetOption.THREE_HUNDRED_KCAL,
        reading: LiveCalorieTargetReading = LiveCalorieTargetReading.Evaluated(
            displayValue = 183.25,
            sampleAgeMillis = 500L,
            freshness = CalorieSampleFreshness.FRESH,
        ),
    ): LiveCalorieTargetContext {
        val selection = when (
            val result = CalorieTargetSelection.createUserSelected(targetOption.estimatedKcal)
        ) {
            is CalorieTargetSelectionResult.Accepted -> result.selection
            is CalorieTargetSelectionResult.Rejected ->
                error("Expected target selection, got $result")
        }
        return LiveCalorieTargetContext(
            target = selection,
            representativeProfileDuration = DurationMinutes(40),
            effectiveMaxSpeed = SpeedTenths(50),
            effectiveMaxIncline = InclineTenths(80),
            estimateStatus = CalorieEstimateStatus.ESTIMATED,
            source = CalorieTelemetrySource.FITOS_EQUIPMENT_SNAPSHOT_CALORIES,
            unitSemantics = CalorieUnitSemantics.UNIT_SEMANTICS_UNCONFIRMED,
            sessionResetSemantics =
                CalorieSessionResetSemantics.SESSION_RESET_SEMANTICS_UNCONFIRMED,
            completionAuthority = CalorieCompletionAuthority.COMPLETION_AUTHORITY_NOT_APPROVED,
            progressSemantics = CalorieProgressSemantics.DISPLAY_ONLY_NO_TARGET_PROGRESS,
            previewStatus = CaloriePreviewStatus.PREVIEW_ONLY,
            deviceCommandStatus = CalorieDeviceCommandStatus.NO_DEVICE_COMMANDS,
            reading = reading,
        )
    }

    private fun terminalSummary(context: LiveCalorieTargetContext): LiveWorkoutSummary =
        LiveWorkoutSummary(
            programId = ProgramId("CALORIE_TARGET"),
            elapsedSeconds = 300,
            totalDurationSeconds = 2_400,
            programTitle = "CALORIE TARGET",
            previewMode = ProgramPreviewMode.CALORIE_TARGET_PREVIEW,
            calorieTargetContext = context,
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

    private fun zone2Context(): LiveZone2HeartRateContext {
        val target = when (
            val result = HeartRateTargetRange.createUserConfirmed(120, 140)
        ) {
            is HeartRateTargetRangeResult.Accepted -> result.target
            is HeartRateTargetRangeResult.Rejected -> error("Expected HR target, got $result")
        }
        return LiveZone2HeartRateContext(
            target = target,
            intendedSource = com.echelon.console.domain.Zone2HeartRateIntendedSource.FITOS_EQUIPMENT_SNAPSHOT,
            previewStatus = com.echelon.console.domain.Zone2HeartRatePreviewStatus.PREVIEW_ONLY,
            adviceMode = com.echelon.console.domain.Zone2HeartRateAdviceMode.ADVISORY_ONLY,
            thresholdMode = com.echelon.console.domain.Zone2HeartRateThresholdMode.DIRECT_THRESHOLD_PREVIEW,
            hysteresisStatus = com.echelon.console.domain.Zone2HeartRateHysteresisStatus.NO_HYSTERESIS_APPROVED,
            reading = LiveZone2HeartRateReading.Unavailable(
                LiveZone2HeartRateUnavailableReason.MissingTelemetry,
            ),
        )
    }
}
