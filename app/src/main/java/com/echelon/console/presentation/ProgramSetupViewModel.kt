package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailResult
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraft
import com.echelon.console.application.usecase.StartSurpriseWorkoutDraftResult
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartWorkoutResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.DurationMinutes
import com.echelon.console.domain.InclineTenths
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
import com.echelon.console.domain.ProgramPreviewMode
import com.echelon.console.domain.SurpriseWorkoutDraft
import com.echelon.console.domain.SurpriseWorkoutEffort
import com.echelon.console.domain.SurpriseWorkoutGenerationResult
import com.echelon.console.domain.SurpriseWorkoutGenerator
import com.echelon.console.domain.SurpriseWorkoutGeneratorInput
import com.echelon.console.domain.SpeedTenths
import com.echelon.console.domain.WorkoutPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProgramSetupViewModel(
    private val getProgramDetail: GetProgramDetail,
    private val startWorkout: StartWorkout,
    private val startSurpriseWorkoutDraft: StartSurpriseWorkoutDraft,
    private val surpriseWorkoutGenerator: SurpriseWorkoutGenerator,
    private val capabilities: DeviceCapabilities?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val _state = MutableStateFlow<ProgramSetupUiState>(ProgramSetupUiState.Library)

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
        if (isSurprise(ready.detail)) {
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
            is ProgramSetupUiState.Configuring -> ProgramSetupUiState.Ready(current.detail)
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
        if (isSurprise(ready.detail)) {
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

    private fun enterSurpriseConfiguring(detail: ProgramDetail): ProgramSetupUiState.Configuring? {
        val deviceCapabilities = capabilities
        if (deviceCapabilities == null) {
            _state.value = ProgramSetupUiState.DeviceUnavailable
            return null
        }
        val configuring = ProgramSetupUiState.Configuring(
            detail = detail,
            duration = SURPRISE_DEFAULT_DURATION,
            effort = SURPRISE_DEFAULT_EFFORT,
            regenerationIndex = 0,
            userMaxSpeed = SURPRISE_USER_MAX_SPEED,
            machineMaxSpeed = deviceCapabilities.speed.max,
            userMaxIncline = SURPRISE_USER_MAX_INCLINE,
            machineMaxIncline = deviceCapabilities.incline.max,
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
        val result = surpriseWorkoutGenerator.generate(
            SurpriseWorkoutGeneratorInput(
                durationMinutes = configuring.duration.value,
                effort = configuring.effort,
                userProfileRevision = SURPRISE_PROFILE_REVISION,
                regenerationIndex = configuring.regenerationIndex,
                generatorVersion = SURPRISE_GENERATOR_VERSION,
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
        const val SURPRISE_PROFILE_REVISION = "anonymous-baseline-r1"
        const val SURPRISE_GENERATOR_VERSION = "v1"
        const val SURPRISE_GENERATION_ERROR = "Unable to generate workout preview"
        const val SURPRISE_ACCEPT_ERROR = "Unable to accept workout preview"

        val SURPRISE_DEFAULT_DURATION = DurationMinutes(20)
        val SURPRISE_DEFAULT_EFFORT = SurpriseWorkoutEffort.SWEAT
        val SURPRISE_USER_MAX_SPEED = SpeedTenths(80)
        val SURPRISE_USER_MAX_INCLINE = InclineTenths(100)
    }
}
