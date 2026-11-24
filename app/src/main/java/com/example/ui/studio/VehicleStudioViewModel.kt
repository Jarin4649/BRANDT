package com.example.ui.studio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.FirebaseVehicleVisionAnalyzer
import com.example.data.ai.VehicleVisionAnalyzer
import com.example.data.ai.VehicleVisionResult
import com.example.data.model.VehicleAngle
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.appStrings
import com.example.ui.components.LicensePlatePrivacyProtector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StudioAnalysisState {
    data object Idle : StudioAnalysisState
    data object Analyzing : StudioAnalysisState
    data class Success(val result: VehicleVisionResult) : StudioAnalysisState
    data class Error(val message: String) : StudioAnalysisState
}

data class VehicleStudioUiState(
    val activeAngle: VehicleAngle = VehicleAngle.FRONT,
    val anglePhotos: Map<VehicleAngle, String> = emptyMap(),
    val analysisState: StudioAnalysisState = StudioAnalysisState.Idle,
    val platePrivacyNotice: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val savedSuccessfully: Boolean = false,
    val isLoadingVehicle: Boolean = false
)

class VehicleStudioViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val targetVehicleId: String?,
    initialAnglePhotos: Map<VehicleAngle, String> = emptyMap(),
    initialAngle: VehicleAngle = VehicleAngle.FRONT,
    private val visionAnalyzer: VehicleVisionAnalyzer = FirebaseVehicleVisionAnalyzer(),
    private val languageState: StateFlow<AppLanguage> = LanguagePreferenceStore.currentLanguage
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VehicleStudioUiState(
            activeAngle = initialAngle,
            anglePhotos = initialAnglePhotos,
            isLoadingVehicle = !targetVehicleId.isNullOrBlank()
        )
    )
    val uiState: StateFlow<VehicleStudioUiState> = _uiState.asStateFlow()

    private var loadedPrimaryPhotoUri: String = ""
    private var loadedAiSummary: String = ""

    init {
        if (!targetVehicleId.isNullOrBlank()) {
            loadExistingVehicle(targetVehicleId)
        }
    }

    private fun loadExistingVehicle(vehicleId: String) {
        viewModelScope.launch {
            val vehicle = repository.observeVehicle(currentUserId, vehicleId).firstOrNull()
            if (vehicle != null) {
                loadedPrimaryPhotoUri = vehicle.photoUri
                loadedAiSummary = vehicle.aiInspectionSummary
                _uiState.update { state ->
                    val merged = vehicle.resolvedAnglePhotos() + state.anglePhotos
                    state.copy(
                        anglePhotos = merged,
                        isLoadingVehicle = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingVehicle = false) }
            }
        }
    }

    fun selectAngle(angle: VehicleAngle) {
        _uiState.update { it.copy(activeAngle = angle, saveError = null) }
    }

    fun assignPhotoToActiveAngle(photoUri: String, context: Context? = null) {
        val currentAngle = _uiState.value.activeAngle
        assignPhotoToAngle(currentAngle, photoUri, advanceToNext = true, context = context)
    }

    fun assignPhotoToAngle(
        angle: VehicleAngle,
        photoUri: String,
        advanceToNext: Boolean = false,
        context: Context? = null
    ) {
        _uiState.update { state ->
            val updatedMap = state.anglePhotos + (angle to photoUri)
            val nextAngle = if (advanceToNext) {
                val angles = VehicleAngle.entries
                val currentIdx = angles.indexOf(angle)
                val nextUncaptured = ((1..angles.size).map { offset ->
                    angles[(currentIdx + offset) % angles.size]
                }).firstOrNull { updatedMap[it].isNullOrBlank() }
                nextUncaptured ?: angles[(currentIdx + 1) % angles.size]
            } else {
                state.activeAngle
            }
            state.copy(
                activeAngle = nextAngle,
                anglePhotos = updatedMap,
                saveError = null
            )
        }
        if (context != null && photoUri.isNotBlank()) {
            viewModelScope.launch {
                val protection = LicensePlatePrivacyProtector.protectVehiclePhoto(
                    context = context.applicationContext,
                    sourceUri = photoUri
                )
                if (protection.outputUri != photoUri) {
                    _uiState.update { state ->
                        val nextAngles = if (state.anglePhotos[angle] == photoUri) {
                            state.anglePhotos + (angle to protection.outputUri)
                        } else {
                            state.anglePhotos
                        }
                        state.copy(
                            anglePhotos = nextAngles,
                            platePrivacyNotice = null
                        )
                    }
                }
            }
        }
    }

    fun clearAngle(angle: VehicleAngle) {
        _uiState.update { state ->
            val nextAngles = state.anglePhotos - angle
            state.copy(
                anglePhotos = nextAngles,
                platePrivacyNotice = if (nextAngles.isEmpty()) null else state.platePrivacyNotice,
                saveError = null
            )
        }
    }

    fun analyzeWithGeminiVision(context: Context) {
        val photos = _uiState.value.anglePhotos
        if (photos.isEmpty()) {
            _uiState.update {
                it.copy(analysisState = StudioAnalysisState.Error("Capture or attach at least one photo first."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(analysisState = StudioAnalysisState.Analyzing, saveError = null) }
            try {
                val safePhotos = mutableMapOf<VehicleAngle, String>()
                photos.forEach { (angle, uri) ->
                    val protection = LicensePlatePrivacyProtector.protectVehiclePhoto(
                        context = context.applicationContext,
                        sourceUri = uri
                    )
                    safePhotos[angle] = protection.outputUri
                }
                if (safePhotos != photos) {
                    _uiState.update { state ->
                        state.copy(
                            anglePhotos = safePhotos,
                            platePrivacyNotice = null
                        )
                    }
                }

                val result = visionAnalyzer.analyzeVehicleAngles(context.applicationContext, safePhotos)
                result.fold(
                    onSuccess = { vision ->
                        loadedAiSummary = vision.exteriorSummary
                        _uiState.update { it.copy(analysisState = StudioAnalysisState.Success(vision)) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                analysisState = StudioAnalysisState.Error(
                                    error.localizedMessage ?: "Gemini Vision analysis failed."
                                )
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        analysisState = StudioAnalysisState.Error(
                            e.localizedMessage ?: "Gemini Vision analysis failed."
                        )
                    )
                }
            }
        }
    }

    fun saveStudioToExistingVehicle() {
        val vehicleId = targetVehicleId ?: return
        val currentState = _uiState.value
        val summary = (currentState.analysisState as? StudioAnalysisState.Success)
            ?.result?.exteriorSummary
            ?: loadedAiSummary

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val result = repository.updateStudioAngles(
                userId = currentUserId,
                vehicleId = vehicleId,
                anglePhotos = currentState.anglePhotos,
                primaryPhotoUri = loadedPrimaryPhotoUri,
                aiInspectionSummary = summary
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveError = err.localizedMessage ?: "Failed to save studio angles."
                        )
                    }
                }
            )
        }
    }
}
