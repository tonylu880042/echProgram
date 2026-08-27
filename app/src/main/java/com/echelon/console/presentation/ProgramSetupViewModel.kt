package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraft
import com.echelon.console.application.usecase.GenerateFiveKReadySessionDraftRequest
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraft
import com.echelon.console.application.usecase.GenerateSurpriseWorkoutDraftRequest
import com.echelon.console.application.usecase.GenerateVerticalWorkoutDraft
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailResult
import com.echelon.console.application.usecase.StartCalorieTargetPreview
import com.echelon.console.application.usecase.StartFiveKReadySessionDraft
import com.echelon.console.application.usecase.StartFiveKReadySessionDraftResult
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraftResult
import com.echelon.console.application.usecase.StartVerticalWorkoutDraft
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartWorkoutResult
import com.echelon.console.application.usecase.StartZone2WorkoutPreview
import com.echelon.console.domain.CalorieTargetOption
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.FiveKReadyBaselinePace
import com.echelon.console.domain.FiveKReadyBaselineSource
import com.echelon.console.domain.FiveKReadySessionGenerationFailure
import com.echelon.console.domain.FiveKReadySessionGenerationResult
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.VerticalTarget
import com.echelon.console.domain.WorkoutPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProgramSetupViewModel(
    private val getProgramDetail: GetProgramDetail,
    private val startWorkout: StartWorkout,
    private val startSurpriseWorkoutDraft: StartSurpriseWorkoutDraft,
    private val generateSurpriseWorkoutDraft: GenerateSurpriseWorkoutDraft,
    private val capabilities: DeviceCapabilities?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val startFiveKReadySessionDraft: StartFiveKReadySessionDraft,
    private val generateFiveKReadySessionDraft: GenerateFiveKReadySessionDraft,
    private val startVerticalWorkoutDraft: StartVerticalWorkoutDraft,
    private val generateVerticalWorkoutDraft: GenerateVerticalWorkoutDraft,
    private val startZone2WorkoutPreview: StartZone2WorkoutPreview,
    private val startCalorieTargetPreview: StartCalorieTargetPreview,
) : ViewModel() {
    private val _state = MutableStateFlow<ProgramSetupUiState>(ProgramSetupUiState.Library)
    private val calorieTargetSetupFlow = CalorieTargetSetupFlow(
        startPreview = startCalorieTargetPreview,
        capabilities = capabilities,
    )
    private val zone2SetupFlow = Zone2SetupFlow(
        startPreview = startZone2WorkoutPreview,
        capabilities = capabilities,
    )
    private val verticalSetupFlow = VerticalSetupFlow(
        generateDraft = generateVerticalWorkoutDraft,
        startDraft = startVerticalWorkoutDraft,
        capabilities = capabilities,
    )

    val state: StateFlow<ProgramSetupUiState> = _state.asStateFlow()

    fun onAction(action: ProgramSetupAction) {
        when (action) {
            is ProgramSetupAction.OpenProgram -> openProgram(action.programId)
            ProgramSetupAction.MakeItYours -> makeItYours()
            ProgramSetupAction.Back -> goBack()
            ProgramSetupAction.StartDefault -> startDefault()
            is ProgramSetupAction.SetDuration -> updateSettings { copy(duration = action.duration) }
            is ProgramSetupAction.SetIntensity -> updateSettings { copy(intensity = action.intensity) }
            is ProgramSetupAction.SetMaxSpeed -> updateSettings { copy(maxSpeed = action.maxSpeed) }
            is ProgramSetupAction.SetMaxIncline -> updateSettings { copy(maxIncline = action.maxIncline) }
            is ProgramSetupAction.SetFocus -> updateSettings { copy(focus = action.focus) }
            is ProgramSetupAction.SetAdaptToYou -> updateSettings { copy(adaptToYou = action.adaptToYou) }
            ProgramSetupAction.StartCustomized -> startCustomized()
            is ProgramSetupAction.SetSurpriseDuration -> setSurpriseDuration(action.duration)
            is ProgramSetupAction.SetSurpriseEffort -> setSurpriseEffort(action.effort)
            ProgramSetupAction.GenerateSurprisePreview -> generateSurprisePreview()
            ProgramSetupAction.RegenerateSurprisePreview -> regenerateSurprisePreview()
            ProgramSetupAction.AcceptSurprisePlan -> acceptSurprisePlan()
            is ProgramSetupAction.SetFiveKReadyDuration -> setFiveKReadyDuration(action.duration)
            is ProgramSetupAction.SetFiveKReadyBaselinePace -> setFiveKReadyBaselinePace(action.text)
            ProgramSetupAction.GenerateFiveKReadyPreview -> generateFiveKReadyPreview()
            ProgramSetupAction.AcceptFiveKReadyPlan -> acceptFiveKReadyPlan()
            is ProgramSetupAction.SetVerticalTarget -> setVerticalTarget(action.target)
            ProgramSetupAction.GenerateVerticalPreview -> generateVerticalPreview()
            ProgramSetupAction.AcceptVerticalPlan -> acceptVerticalPlan()
            is ProgramSetupAction.SetZone2Duration -> setZone2Duration(action.duration)
            is ProgramSetupAction.SetZone2LowerBpm -> setZone2LowerBpm(action.text)
            is ProgramSetupAction.SetZone2UpperBpm -> setZone2UpperBpm(action.text)
            ProgramSetupAction.StartZone2Preview -> startZone2Preview()
            is ProgramSetupAction.SelectCalorieTarget -> selectCalorieTarget(action.target)
            ProgramSetupAction.StartCalorieTargetPreview -> startCalorieTargetPreview()
        }
    }

    private fun openProgram(programId: ProgramId) {
        _state.value = ProgramSetupUiState.Loading(programId)
        viewModelScope.launch(dispatcher) {
            try {
                _state.value = when (val result = getProgramDetail(programId)) {
                    is ProgramDetailResult.Ready -> ProgramSetupUiState.Ready(result.detail)
                    is ProgramDetailResult.NotFound -> ProgramSetupUiState.Unavailable(result.programId)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = ProgramSetupUiState.Error("Unable to load program detail")
            }
        }
    }

    private fun makeItYours() {
        val ready = _state.value as? ProgramSetupUiState.Ready ?: return
        if (isCalorieTarget(ready.detail)) {
            enterCalorieTargetConfiguring(ready.detail)
        } else if (isZone2(ready.detail)) {
            enterZone2Configuring(ready.detail)
        } else if (isVertical(ready.detail)) {
            enterVerticalConfiguring(ready.detail)
        } else if (isFiveKReady(ready.detail)) {
            enterFiveKReadyConfiguring(ready.detail)
        } else if (isSurprise(ready.detail)) {
            enterSurpriseConfiguring(ready.detail)
        } else {
            _state.value = ProgramSetupUiState.Personalizing(
                detail = ready.detail,
                settings = ready.detail.defaultSettings,
            )
        }
    }

    private fun goBack() {
        _state.value = when (val current = _state.value) {
            is ProgramSetupUiState.Personalizing -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.FiveKReadyConfiguring -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.Configuring -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.VerticalConfiguring -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.Zone2Configuring -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.CalorieTargetConfiguring -> ProgramSetupUiState.Ready(current.detail)
            is ProgramSetupUiState.FiveKReadyDraftPreview -> ProgramSetupUiState.FiveKReadyConfiguring(
                detail = current.detail,
                duration = DurationMinutes(current.draft.metadata.durationMinutes),
                baselinePaceText = current.baselinePaceText,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
            is ProgramSetupUiState.DraftPreview -> ProgramSetupUiState.Configuring(
                detail = current.detail,
                duration = DurationMinutes(current.draft.metadata.durationMinutes),
                effort = current.draft.metadata.effort,
                regenerationIndex = current.draft.metadata.regenerationIndex,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
            is ProgramSetupUiState.VerticalDraftPreview -> ProgramSetupUiState.VerticalConfiguring(
                detail = current.detail,
                target = current.draft.metadata.target,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
            is ProgramSetupUiState.Ready -> ProgramSetupUiState.Library
            is ProgramSetupUiState.Unavailable,
            ProgramSetupUiState.DeviceUnavailable,
            is ProgramSetupUiState.Error,
            is ProgramSetupUiState.Started,
            -> ProgramSetupUiState.Library
            else -> return
        }
    }

    private fun updateSettings(update: PlanSettings.() -> PlanSettings) {
        val personalizing = _state.value as? ProgramSetupUiState.Personalizing ?: return
        _state.value = personalizing.copy(
            settings = personalizing.settings.update(),
            fieldErrors = emptyList(),
        )
    }

    private fun startDefault() {
        val ready = _state.value as? ProgramSetupUiState.Ready ?: return
        if (isCalorieTarget(ready.detail)) {
            enterCalorieTargetConfiguring(ready.detail)
        } else if (isZone2(ready.detail)) {
            enterZone2Configuring(ready.detail)
        } else if (isVertical(ready.detail)) {
            enterVerticalConfiguring(ready.detail)
        } else if (isFiveKReady(ready.detail)) {
            enterFiveKReadyConfiguring(ready.detail)
        } else if (isSurprise(ready.detail)) {
            enterSurpriseConfiguring(ready.detail)?.let(::generateSurprisePreview)
        } else {
            start(
                detail = ready.detail,
                settings = ready.detail.defaultSettings,
                invalidState = null,
            )
        }
    }

    private fun startCustomized() {
        val personalizing = _state.value as? ProgramSetupUiState.Personalizing ?: return
        if (isSurprise(personalizing.detail)) {
            enterSurpriseConfiguring(personalizing.detail)
        } else {
            start(
                detail = personalizing.detail,
                settings = personalizing.settings,
                invalidState = personalizing,
            )
        }
    }

    private fun enterZone2Configuring(detail: ProgramDetail) {
        _state.value = zone2SetupFlow.enter(detail)
    }

    private fun setZone2Duration(duration: DurationMinutes) {
        val current = _state.value as? ProgramSetupUiState.Zone2Configuring ?: return
        _state.value = zone2SetupFlow.setDuration(current, duration)
    }

    private fun setZone2LowerBpm(text: String) {
        val current = _state.value as? ProgramSetupUiState.Zone2Configuring ?: return
        _state.value = zone2SetupFlow.setLowerBpm(current, text)
    }

    private fun setZone2UpperBpm(text: String) {
        val current = _state.value as? ProgramSetupUiState.Zone2Configuring ?: return
        _state.value = zone2SetupFlow.setUpperBpm(current, text)
    }

    private fun startZone2Preview() {
        val current = _state.value as? ProgramSetupUiState.Zone2Configuring ?: return
        _state.value = zone2SetupFlow.start(current)
    }

    private fun enterCalorieTargetConfiguring(detail: ProgramDetail) {
        _state.value = calorieTargetSetupFlow.enter(detail)
    }

    private fun selectCalorieTarget(target: CalorieTargetOption) {
        val current = _state.value as? ProgramSetupUiState.CalorieTargetConfiguring ?: return
        _state.value = calorieTargetSetupFlow.select(current, target)
    }

    private fun startCalorieTargetPreview() {
        val current = _state.value as? ProgramSetupUiState.CalorieTargetConfiguring ?: return
        _state.value = calorieTargetSetupFlow.start(current)
    }

    private fun enterFiveKReadyConfiguring(detail: ProgramDetail) {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return
        }
        _state.value = ProgramSetupUiState.FiveKReadyConfiguring(
            detail = detail,
            duration = detail.defaultSettings.duration.takeIf {
                it in FiveKReadyDurationOptions
            } ?: FIVE_K_READY_DEFAULT_DURATION,
            baselinePaceText = "",
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
        )
    }

    private fun enterVerticalConfiguring(detail: ProgramDetail) {
        _state.value = verticalSetupFlow.enter(detail)
    }

    private fun setVerticalTarget(target: VerticalTarget) {
        val current = _state.value as? ProgramSetupUiState.VerticalConfiguring ?: return
        _state.value = verticalSetupFlow.setTarget(current, target)
    }

    private fun generateVerticalPreview() {
        val current = _state.value as? ProgramSetupUiState.VerticalConfiguring ?: return
        _state.value = verticalSetupFlow.generatePreview(current)
    }

    private fun acceptVerticalPlan() {
        val current = _state.value as? ProgramSetupUiState.VerticalDraftPreview ?: return
        _state.value = verticalSetupFlow.accept(current)
    }

    private fun setFiveKReadyDuration(duration: DurationMinutes) {
        val current = _state.value as? ProgramSetupUiState.FiveKReadyConfiguring ?: return
        if (duration !in FiveKReadyDurationOptions) return
        _state.value = current.copy(duration = duration, errorMessage = null)
    }

    private fun setFiveKReadyBaselinePace(text: String) {
        val current = _state.value as? ProgramSetupUiState.FiveKReadyConfiguring ?: return
        _state.value = current.copy(baselinePaceText = text, errorMessage = null)
    }

    private fun generateFiveKReadyPreview() {
        val current = _state.value as? ProgramSetupUiState.FiveKReadyConfiguring ?: return
        val baseline = parseFiveKBaseline(current.baselinePaceText)
        if (baseline == null) {
            _state.value = current.copy(errorMessage = FIVE_K_READY_BASELINE_INPUT_ERROR)
            return
        }

        when (
            val result = generateFiveKReadySessionDraft(
                GenerateFiveKReadySessionDraftRequest(
                    durationMinutes = current.duration.value,
                    baselinePace = baseline,
                    userMaxSpeed = current.userMaxSpeed,
                    machineMaxSpeed = current.machineMaxSpeed,
                    userMaxIncline = current.userMaxIncline,
                    machineMaxIncline = current.machineMaxIncline,
                ),
            )
        ) {
            is FiveKReadySessionGenerationResult.Generated -> _state.value =
                ProgramSetupUiState.FiveKReadyDraftPreview(
                    detail = current.detail,
                    draft = result.draft,
                    baselinePaceText = current.baselinePaceText,
                    userMaxSpeed = current.userMaxSpeed,
                    machineMaxSpeed = current.machineMaxSpeed,
                    userMaxIncline = current.userMaxIncline,
                    machineMaxIncline = current.machineMaxIncline,
                )

            is FiveKReadySessionGenerationResult.Rejected -> _state.value = current.copy(
                errorMessage = fiveKGenerationError(result.failure),
            )
        }
    }

    private fun acceptFiveKReadyPlan() {
        val current = _state.value as? ProgramSetupUiState.FiveKReadyDraftPreview ?: return
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return
        }
        _state.value = try {
            when (val result = startFiveKReadySessionDraft(current.draft, deviceCapabilities)) {
                is StartFiveKReadySessionDraftResult.Started -> ProgramSetupUiState.Started(
                    plan = result.plan,
                    previewMode = ProgramPreviewMode.BASELINE_PREVIEW,
                )

                is StartFiveKReadySessionDraftResult.InvalidDraft,
                is StartFiveKReadySessionDraftResult.CapabilityValidationFailed,
                is StartFiveKReadySessionDraftResult.StarterFailed,
                -> ProgramSetupUiState.Error(FIVE_K_READY_ACCEPT_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ProgramSetupUiState.Error(FIVE_K_READY_ACCEPT_ERROR)
        }
    }

    private fun parseFiveKBaseline(text: String): FiveKReadyBaselinePace? {
        val normalized = text.trim()
        if (!FIVE_K_READY_PACE_PATTERN.matches(normalized)) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value > Int.MAX_VALUE / 10.0) return null
        return FiveKReadyBaselinePace(
            speed = SpeedTenths((value * 10.0).roundToInt()),
            source = FiveKReadyBaselineSource.USER_ENTERED,
        )
    }

    private fun fiveKGenerationError(
        failure: FiveKReadySessionGenerationFailure,
    ): String = when (failure) {
        FiveKReadySessionGenerationFailure.BaselineRequired -> FIVE_K_READY_BASELINE_INPUT_ERROR
        is FiveKReadySessionGenerationFailure.BaselineSourceNotUserEntered ->
            FIVE_K_READY_BASELINE_INPUT_ERROR
        is FiveKReadySessionGenerationFailure.BaselineOutsideGlobalEnvelope ->
            "RUN PACE MUST BE BETWEEN 2.8 AND 6.0 MPH"
        is FiveKReadySessionGenerationFailure.BaselineExceedsEffectiveSpeedCap ->
            "RUN PACE EXCEEDS THE EFFECTIVE SPEED CAP"
        is FiveKReadySessionGenerationFailure.BaselineLeavesNoRecoveryMargin ->
            "RUN PACE 2.8 MPH LEAVES NO RECOVERY MARGIN; ENTER AT LEAST 2.9 MPH"
        is FiveKReadySessionGenerationFailure.InvalidSpeedCap,
        is FiveKReadySessionGenerationFailure.SpeedCapsDoNotIntersect,
        -> FIVE_K_READY_CAPABILITIES_ERROR
        is FiveKReadySessionGenerationFailure.InvalidInclineCap,
        is FiveKReadySessionGenerationFailure.InclineCapsDoNotIntersect,
        -> FIVE_K_READY_CAPABILITIES_ERROR
        is FiveKReadySessionGenerationFailure.UnsupportedDuration ->
            "SELECT 20, 30, 40, OR 60 MINUTES"
    }

    private fun enterSurpriseConfiguring(detail: ProgramDetail): ProgramSetupUiState.Configuring? {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return null
        }
        val configuring = ProgramSetupUiState.Configuring(
            detail = detail,
            duration = detail.defaultSettings.duration.takeIf {
                it in SurpriseWorkoutDurationOptions
            } ?: SURPRISE_DEFAULT_DURATION,
            effort = SURPRISE_DEFAULT_EFFORT,
            regenerationIndex = 0,
            userMaxSpeed = detail.defaultSettings.maxSpeed,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = detail.defaultSettings.maxIncline,
            machineMaxIncline = deviceCapabilities.incline.max,
            errorMessage = if (detail.defaultSettings.duration in SurpriseWorkoutDurationOptions) {
                null
            } else {
                SURPRISE_UNSUPPORTED_DURATION_ERROR
            },
        )
        _state.value = configuring
        return configuring
    }

    private fun setSurpriseDuration(duration: DurationMinutes) {
        val current = _state.value as? ProgramSetupUiState.Configuring ?: return
        if (duration !in SurpriseWorkoutDurationOptions) return
        _state.value = current.copy(
            duration = duration,
            regenerationIndex = 0,
            errorMessage = null,
        )
    }

    private fun setSurpriseEffort(effort: SurpriseWorkoutEffort) {
        val current = _state.value as? ProgramSetupUiState.Configuring ?: return
        _state.value = current.copy(
            effort = effort,
            regenerationIndex = 0,
            errorMessage = null,
        )
    }

    private fun generateSurprisePreview() {
        val current = _state.value as? ProgramSetupUiState.Configuring ?: return
        generateSurprisePreview(current)
    }

    private fun generateSurprisePreview(configuring: ProgramSetupUiState.Configuring) {
        val draft = generateDraft(configuring)
        _state.value = if (draft != null) {
            ProgramSetupUiState.DraftPreview(
                detail = configuring.detail,
                draft = draft,
                userMaxSpeed = configuring.userMaxSpeed,
                machineMaxSpeed = configuring.machineMaxSpeed,
                userMaxIncline = configuring.userMaxIncline,
                machineMaxIncline = configuring.machineMaxIncline,
            )
        } else {
            configuring.copy(errorMessage = SURPRISE_GENERATION_ERROR)
        }
    }

    private fun regenerateSurprisePreview() {
        val current = _state.value as? ProgramSetupUiState.DraftPreview ?: return
        val nextConfiguring = ProgramSetupUiState.Configuring(
            detail = current.detail,
            duration = DurationMinutes(current.draft.metadata.durationMinutes),
            effort = current.draft.metadata.effort,
            regenerationIndex = current.draft.metadata.regenerationIndex + 1,
            userMaxSpeed = current.userMaxSpeed,
            machineMaxSpeed = current.machineMaxSpeed,
            userMaxIncline = current.userMaxIncline,
            machineMaxIncline = current.machineMaxIncline,
        )
        val draft = generateDraft(nextConfiguring)
        _state.value = if (draft != null) {
            ProgramSetupUiState.DraftPreview(
                detail = current.detail,
                draft = draft,
                userMaxSpeed = current.userMaxSpeed,
                machineMaxSpeed = current.machineMaxSpeed,
                userMaxIncline = current.userMaxIncline,
                machineMaxIncline = current.machineMaxIncline,
            )
        } else {
            current.copy(errorMessage = SURPRISE_GENERATION_ERROR)
        }
    }

    private fun acceptSurprisePlan() {
        val current = _state.value as? ProgramSetupUiState.DraftPreview ?: return
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return
        }
        _state.value = try {
            when (val result = startSurpriseWorkoutDraft(current.draft, deviceCapabilities)) {
                is StartSurpriseWorkoutDraftResult.Started ->
                    ProgramSetupUiState.Started(
                        result.plan,
                        ProgramPreviewMode.GENERATED_PREVIEW,
                    )

                is StartSurpriseWorkoutDraftResult.InvalidDraft,
                is StartSurpriseWorkoutDraftResult.CapabilityValidationFailed,
                is StartSurpriseWorkoutDraftResult.StarterFailed,
                -> ProgramSetupUiState.Error(SURPRISE_ACCEPT_ERROR)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ProgramSetupUiState.Error(SURPRISE_ACCEPT_ERROR)
        }
    }

    private fun generateDraft(configuring: ProgramSetupUiState.Configuring): SurpriseWorkoutDraft? {
        val result = generateSurpriseWorkoutDraft(
            GenerateSurpriseWorkoutDraftRequest(
                durationMinutes = configuring.duration.value,
                effort = configuring.effort,
                userProfileRevision = SURPRISE_PROFILE_REVISION,
                regenerationIndex = configuring.regenerationIndex,
                userMaxSpeed = configuring.userMaxSpeed,
                machineMaxSpeed = configuring.machineMaxSpeed,
                userMaxIncline = configuring.userMaxIncline,
                machineMaxIncline = configuring.machineMaxIncline,
            ),
        )
        return when (result) {
            is SurpriseWorkoutGenerationResult.Generated -> result.draft
            is SurpriseWorkoutGenerationResult.Rejected -> null
        }
    }

    private fun isSurprise(detail: ProgramDetail): Boolean =
        detail.programId.value == SURPRISE_PROGRAM_ID

    private fun isCalorieTarget(detail: ProgramDetail): Boolean =
        detail.programId.value == CALORIE_TARGET_PROGRAM_ID

    private fun isZone2(detail: ProgramDetail): Boolean =
        detail.programId.value == ZONE_2_PROGRAM_ID

    private fun isVertical(detail: ProgramDetail): Boolean =
        detail.programId.value == VERTICAL_PROGRAM_ID

    private fun isFiveKReady(detail: ProgramDetail): Boolean =
        detail.programId.value == FIVE_K_READY_PROGRAM_ID

    private fun start(
        detail: ProgramDetail,
        settings: PlanSettings,
        invalidState: ProgramSetupUiState.Personalizing?,
    ) {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return
        }

        val plan = WorkoutPlan(programId = detail.programId, settings = settings)
        viewModelScope.launch(dispatcher) {
            try {
                when (val result = startWorkout(plan, deviceCapabilities)) {
                    is StartWorkoutResult.Valid -> _state.value = ProgramSetupUiState.Started(
                        plan = result.plan,
                        previewMode = detail.previewMode,
                    )
                    is StartWorkoutResult.Invalid -> {
                        _state.value = invalidState?.copy(fieldErrors = result.errors)
                            ?: ProgramSetupUiState.Error("Unable to start workout")
                    }
                    is StartWorkoutResult.StarterFailure -> _state.value =
                        ProgramSetupUiState.Error("Unable to start workout")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = ProgramSetupUiState.Error("Unable to start workout")
            }
        }
    }

    private companion object {
        const val SURPRISE_PROGRAM_ID = "SURPRISE_ME"
        const val FIVE_K_READY_PROGRAM_ID = "5K_READY"
        const val VERTICAL_PROGRAM_ID = "VERTICAL"
        const val SURPRISE_PROFILE_REVISION = "anonymous-baseline-r1"
        const val SURPRISE_GENERATION_ERROR = "Unable to generate workout preview"
        const val SURPRISE_ACCEPT_ERROR = "Unable to accept workout preview"
        const val SURPRISE_UNSUPPORTED_DURATION_ERROR =
            "Default duration unavailable; using 20 minutes"
        const val FIVE_K_READY_BASELINE_INPUT_ERROR =
            "SET YOUR RUN PACE BEFORE PREVIEW (MPH, FOR EXAMPLE 4.0)"
        const val FIVE_K_READY_CAPABILITIES_ERROR =
            "CAPABILITIES CANNOT SUPPORT THIS PREVIEW"
        const val FIVE_K_READY_ACCEPT_ERROR = "Unable to accept 5K READY preview"
        const val ZONE_2_PROGRAM_ID = "ZONE_2"
        const val CALORIE_TARGET_PROGRAM_ID = "CALORIE_TARGET"

        val FIVE_K_READY_PACE_PATTERN = Regex("""^\d+(?:\.\d)?$""")
        val FIVE_K_READY_DEFAULT_DURATION = DurationMinutes(30)

        val SURPRISE_DEFAULT_DURATION = DurationMinutes(20)
        val SURPRISE_DEFAULT_EFFORT = SurpriseWorkoutEffort.SWEAT
    }
}
