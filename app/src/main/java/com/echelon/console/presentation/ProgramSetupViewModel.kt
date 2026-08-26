package com.echelon.console.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echelon.console.application.usecase.GetProgramDetail
import com.echelon.console.application.usecase.ProgramDetailResult
import com.echelon.console.application.usecase.StartWorkout
import com.echelon.console.application.usecase.StartWorkoutResult
import com.echelon.console.domain.DeviceCapabilities
import com.echelon.console.domain.PlanSettings
import com.echelon.console.domain.ProgramDetail
import com.echelon.console.domain.ProgramId
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
        _state.value = ProgramSetupUiState.Personalizing(
            detail = ready.detail,
            settings = ready.detail.defaultSettings,
        )
    }

    private fun goBack() {
        _state.value = when (val current = _state.value) {
            is ProgramSetupUiState.Personalizing -> ProgramSetupUiState.Ready(current.detail)
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
        start(
            detail = ready.detail,
            settings = ready.detail.defaultSettings,
            invalidState = null,
        )
    }

    private fun startCustomized() {
        val personalizing = _state.value as? ProgramSetupUiState.Personalizing ?: return
        start(
            detail = personalizing.detail,
            settings = personalizing.settings,
            invalidState = personalizing,
        )
    }

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
                    is StartWorkoutResult.Valid -> _state.value = ProgramSetupUiState.Started(result.plan)
                    is StartWorkoutResult.Invalid -> {
                        _state.value = invalidState?.copy(fieldErrors = result.errors)
                            ?: ProgramSetupUiState.Error("Unable to start workout")
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = ProgramSetupUiState.Error("Unable to start workout")
            }
        }
    }
}
