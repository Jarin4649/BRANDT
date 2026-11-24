package com.example.ui.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.data.ai.FirebaseVehicleVisionAnalyzer
import com.example.data.ai.VehicleVisionAnalyzer
import com.example.data.ai.VehicleVisionResult
import com.example.data.catalog.DemoVehicleCatalog
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.SpecSource
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleModel
import com.example.data.model.VehicleValidator
import com.example.data.model.VehicleVariant
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.appStrings
import com.example.ui.components.LicensePlatePrivacyProtector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VehicleFormUiState(
    val isEditMode: Boolean = false,
    val isLoadingExisting: Boolean = false,
    val isSaving: Boolean = false,
    val isProtectingPlate: Boolean = false,
    val platePrivacyNotice: String? = null,
    val isAnalyzingPhoto: Boolean = false,
    val aiAnalysisError: String? = null,
    val selectedCaptureAngle: VehicleAngle = VehicleAngle.FRONT,
    val draft: VehicleDraft = VehicleDraft(),
    val availableModels: List<VehicleModel> = emptyList(),
    val availableVariants: List<VehicleVariant> = emptyList(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val submitError: String? = null,
    val savedVehicleId: String? = null,
    val useCustomEntry: Boolean = false
)

class VehicleFormViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val editingVehicleId: String?,
    private val visionAnalyzer: VehicleVisionAnalyzer = FirebaseVehicleVisionAnalyzer(),
    private val languageState: StateFlow<AppLanguage> = LanguagePreferenceStore.currentLanguage
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VehicleFormUiState(
            isEditMode = !editingVehicleId.isNullOrBlank(),
            isLoadingExisting = !editingVehicleId.isNullOrBlank()
        )
    )
    val uiState: StateFlow<VehicleFormUiState> = _uiState.asStateFlow()

    private var loadedVehicle: Vehicle? = null
    private var aiAnalysisJob: Job? = null
    private var photoAttachJob: Job? = null

    init {
        if (!editingVehicleId.isNullOrBlank()) {
            loadExistingVehicle(editingVehicleId)
        }
    }

    private fun loadExistingVehicle(vehicleId: String) {
        viewModelScope.launch {
            val vehicle = repository.observeVehicle(currentUserId, vehicleId).firstOrNull()
            if (vehicle == null) {
                _uiState.update {
                    it.copy(
                        isLoadingExisting = false,
                        submitError = "Vehicle record could not be found."
                    )
                }
                return@launch
            }
            loadedVehicle = vehicle
            val draft = VehicleDraft(
                manufacturer = vehicle.manufacturer,
                model = vehicle.model,
                modelYearInput = vehicle.modelYear.toString(),
                trim = vehicle.trim,
                bodyStyle = vehicle.resolvedBodyStyle(),
                engineType = if (VehicleValidator.FIELD_ENGINE_TYPE in vehicle.unknownFields) "" else vehicle.engineType,
                engineDisplacement = if (VehicleValidator.FIELD_ENGINE_DISPLACEMENT in vehicle.unknownFields) "" else vehicle.engineDisplacement,
                transmission = if (VehicleValidator.FIELD_TRANSMISSION in vehicle.unknownFields) "" else vehicle.transmission,
                fuelType = if (VehicleValidator.FIELD_FUEL_TYPE in vehicle.unknownFields) "" else vehicle.fuelType,
                mileageInput = vehicle.mileage.toString(),
                mileageUnit = vehicle.resolvedMileageUnit(),
                market = vehicle.market,
                photoUri = vehicle.photoUri,
                anglePhotos = vehicle.resolvedAnglePhotos(),
                aiInspectionSummary = vehicle.aiInspectionSummary,
                setAsPrimary = vehicle.primary,
                specSource = vehicle.resolvedSpecSource(),
                unknownFields = vehicle.unknownFields.toSet()
            )
            val models = DemoVehicleCatalog.modelsForManufacturer(vehicle.manufacturer)
            val variants = DemoVehicleCatalog.variantsForModel(vehicle.manufacturer, vehicle.model)
            val inCatalog = models.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoadingExisting = false,
                    draft = draft,
                    availableModels = models,
                    availableVariants = variants,
                    useCustomEntry = !inCatalog
                )
            }
        }
    }

    fun selectCatalogManufacturer(manufacturerName: String) {
        val models = DemoVehicleCatalog.modelsForManufacturer(manufacturerName)
        _uiState.update { state ->
            state.copy(
                useCustomEntry = false,
                availableModels = models,
                availableVariants = emptyList(),
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MANUFACTURER,
                draft = state.draft.copy(
                    manufacturer = manufacturerName,
                    model = "",
                    trim = "",
                    specSource = SpecSource.DEMO_CATALOG_PREFILL
                )
            )
        }
    }

    fun selectCatalogModel(model: VehicleModel) {
        val variants = DemoVehicleCatalog.variantsForModel(_uiState.value.draft.manufacturer, model.name)
        _uiState.update { state ->
            state.copy(
                availableVariants = variants,
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MODEL,
                draft = state.draft.copy(
                    model = model.name,
                    bodyStyle = model.bodyStyle,
                    modelYearInput = state.draft.modelYearInput.ifBlank { model.maxYear.toString() },
                    trim = "",
                    specSource = SpecSource.DEMO_CATALOG_PREFILL
                )
            )
        }
    }

    fun selectCatalogVariant(variant: VehicleVariant) {
        _uiState.update { state ->
            val cleanedUnknown = state.draft.unknownFields - setOf(
                VehicleValidator.FIELD_ENGINE_TYPE,
                VehicleValidator.FIELD_ENGINE_DISPLACEMENT,
                VehicleValidator.FIELD_TRANSMISSION,
                VehicleValidator.FIELD_FUEL_TYPE
            )
            state.copy(
                fieldErrors = state.fieldErrors - setOf(
                    VehicleValidator.FIELD_TRIM,
                    VehicleValidator.FIELD_ENGINE_TYPE,
                    VehicleValidator.FIELD_TRANSMISSION,
                    VehicleValidator.FIELD_FUEL_TYPE
                ),
                draft = state.draft.copy(
                    trim = variant.trimName,
                    engineType = variant.engineType,
                    engineDisplacement = variant.engineDisplacement,
                    transmission = variant.transmission,
                    fuelType = variant.fuelType,
                    market = variant.suggestedMarkets.firstOrNull() ?: state.draft.market,
                    specSource = SpecSource.DEMO_CATALOG_PREFILL,
                    unknownFields = cleanedUnknown
                )
            )
        }
    }

    fun toggleCustomEntry(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                useCustomEntry = enabled,
                draft = if (enabled) {
                    state.draft.copy(specSource = SpecSource.USER_ENTERED)
                } else {
                    state.draft
                }
            )
        }
    }

    fun updateManufacturer(value: String) = updateDraftUserEdited(VehicleValidator.FIELD_MANUFACTURER) {
        val models = DemoVehicleCatalog.modelsForManufacturer(value)
        _uiState.update { state ->
            state.copy(
                availableModels = models,
                availableVariants = DemoVehicleCatalog.variantsForModel(value, state.draft.model),
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MANUFACTURER,
                draft = state.draft.copy(
                    manufacturer = value,
                    specSource = SpecSource.USER_ENTERED
                )
            )
        }
    }

    fun updateModel(value: String) = updateDraftUserEdited(VehicleValidator.FIELD_MODEL) {
        _uiState.update { state ->
            state.copy(
                availableVariants = DemoVehicleCatalog.variantsForModel(state.draft.manufacturer, value),
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MODEL,
                draft = state.draft.copy(
                    model = value,
                    specSource = SpecSource.USER_ENTERED
                )
            )
        }
    }

    fun updateModelYear(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(4)
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MODEL_YEAR,
                draft = state.draft.copy(modelYearInput = digitsOnly)
            )
        }
    }

    fun updateTrim(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_TRIM,
                draft = state.draft.copy(
                    trim = value,
                    specSource = if (state.draft.specSource == SpecSource.AI_VISION_PREFILL) {
                        SpecSource.AI_VISION_PREFILL
                    } else {
                        SpecSource.USER_ENTERED
                    }
                )
            )
        }
    }

    fun updateBodyStyle(style: BodyStyle) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(bodyStyle = style))
        }
    }

    fun updateEngineType(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_ENGINE_TYPE,
                draft = state.draft.copy(
                    engineType = value,
                    unknownFields = state.draft.unknownFields - VehicleValidator.FIELD_ENGINE_TYPE,
                    specSource = if (state.draft.specSource == SpecSource.AI_VISION_PREFILL) {
                        SpecSource.AI_VISION_PREFILL
                    } else {
                        SpecSource.USER_ENTERED
                    }
                )
            )
        }
    }

    fun updateEngineDisplacement(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_ENGINE_DISPLACEMENT,
                draft = state.draft.copy(
                    engineDisplacement = value,
                    unknownFields = state.draft.unknownFields - VehicleValidator.FIELD_ENGINE_DISPLACEMENT,
                    specSource = if (state.draft.specSource == SpecSource.AI_VISION_PREFILL) {
                        SpecSource.AI_VISION_PREFILL
                    } else {
                        SpecSource.USER_ENTERED
                    }
                )
            )
        }
    }

    fun updateTransmission(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_TRANSMISSION,
                draft = state.draft.copy(
                    transmission = value,
                    unknownFields = state.draft.unknownFields - VehicleValidator.FIELD_TRANSMISSION,
                    specSource = if (state.draft.specSource == SpecSource.AI_VISION_PREFILL) {
                        SpecSource.AI_VISION_PREFILL
                    } else {
                        SpecSource.USER_ENTERED
                    }
                )
            )
        }
    }

    fun updateFuelType(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_FUEL_TYPE,
                draft = state.draft.copy(
                    fuelType = value,
                    unknownFields = state.draft.unknownFields - VehicleValidator.FIELD_FUEL_TYPE,
                    specSource = if (state.draft.specSource == SpecSource.AI_VISION_PREFILL) {
                        SpecSource.AI_VISION_PREFILL
                    } else {
                        SpecSource.USER_ENTERED
                    }
                )
            )
        }
    }

    fun toggleUnknownField(fieldKey: String) {
        _uiState.update { state ->
            val currentUnknown = state.draft.unknownFields
            val nextUnknown = if (fieldKey in currentUnknown) currentUnknown - fieldKey else currentUnknown + fieldKey
            val nextDraft = when (fieldKey) {
                VehicleValidator.FIELD_ENGINE_TYPE -> state.draft.copy(
                    unknownFields = nextUnknown,
                    engineType = if (fieldKey in nextUnknown) UNKNOWN_SPEC_LABEL else ""
                )
                VehicleValidator.FIELD_ENGINE_DISPLACEMENT -> state.draft.copy(
                    unknownFields = nextUnknown,
                    engineDisplacement = if (fieldKey in nextUnknown) UNKNOWN_SPEC_LABEL else ""
                )
                VehicleValidator.FIELD_TRANSMISSION -> state.draft.copy(
                    unknownFields = nextUnknown,
                    transmission = if (fieldKey in nextUnknown) UNKNOWN_SPEC_LABEL else ""
                )
                VehicleValidator.FIELD_FUEL_TYPE -> state.draft.copy(
                    unknownFields = nextUnknown,
                    fuelType = if (fieldKey in nextUnknown) UNKNOWN_SPEC_LABEL else ""
                )
                else -> state.draft.copy(unknownFields = nextUnknown)
            }
            state.copy(
                fieldErrors = state.fieldErrors - fieldKey,
                draft = nextDraft
            )
        }
    }

    fun updateMileage(value: String) {
        val digitsOnly = value.filter { it.isDigit() }.take(7)
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MILEAGE,
                draft = state.draft.copy(mileageInput = digitsOnly)
            )
        }
    }

    fun updateMileageUnit(unit: MileageUnit) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(mileageUnit = unit))
        }
    }

    fun updateMarket(value: String) {
        _uiState.update { state ->
            state.copy(
                fieldErrors = state.fieldErrors - VehicleValidator.FIELD_MARKET,
                draft = state.draft.copy(market = value)
            )
        }
    }

    fun selectCaptureAngle(angle: VehicleAngle) {
        _uiState.update { state ->
            state.copy(selectedCaptureAngle = angle)
        }
    }

    fun clearAnglePhoto(angle: VehicleAngle) {
        _uiState.update { state ->
            val updatedAngles = state.draft.anglePhotos - angle
            val nextPrimary = updatedAngles[VehicleAngle.FRONT]
                ?: updatedAngles.values.firstOrNull()
                ?: ""
            state.copy(
                aiAnalysisError = null,
                platePrivacyNotice = if (updatedAngles.isEmpty()) null else state.platePrivacyNotice,
                draft = state.draft.copy(
                    anglePhotos = updatedAngles,
                    photoUri = nextPrimary,
                    aiInspectionSummary = if (updatedAngles.isEmpty()) "" else state.draft.aiInspectionSummary
                )
            )
        }
    }

    fun updatePhotoUri(uri: String) {
        _uiState.update { state ->
            state.copy(
                aiAnalysisError = null,
                platePrivacyNotice = if (uri.isBlank()) null else state.platePrivacyNotice,
                draft = if (uri.isBlank()) {
                    state.draft.copy(
                        photoUri = "",
                        anglePhotos = emptyMap(),
                        aiInspectionSummary = ""
                    )
                } else {
                    state.draft.copy(photoUri = uri)
                }
            )
        }
    }

    fun attachPhotoAndAnalyze(
        context: Context,
        photoUri: String,
        angle: VehicleAngle = _uiState.value.selectedCaptureAngle
    ) {
        photoAttachJob?.cancel()
        _uiState.update { state ->
            val updatedAngles = if (photoUri.isNotBlank()) {
                state.draft.anglePhotos + (angle to photoUri)
            } else {
                state.draft.anglePhotos - angle
            }
            val resolvedPrimary = updatedAngles[VehicleAngle.FRONT]
                ?: if (state.draft.photoUri.isNotBlank() && state.draft.photoUri in updatedAngles.values) {
                    state.draft.photoUri
                } else {
                    updatedAngles.values.firstOrNull() ?: photoUri
                }
            state.copy(
                aiAnalysisError = null,
                platePrivacyNotice = if (photoUri.isBlank()) null else state.platePrivacyNotice,
                isProtectingPlate = photoUri.isNotBlank(),
                selectedCaptureAngle = angle,
                draft = state.draft.copy(
                    photoUri = resolvedPrimary,
                    anglePhotos = updatedAngles
                )
            )
        }
        if (photoUri.isBlank()) return

        photoAttachJob = viewModelScope.launch {
            val protection = LicensePlatePrivacyProtector.protectVehiclePhoto(
                context = context.applicationContext,
                sourceUri = photoUri
            )
            val safeUri = protection.outputUri
            _uiState.update { state ->
                val currentAngleUri = state.draft.anglePhotos[angle]
                val nextAngles = if (currentAngleUri == photoUri) {
                    state.draft.anglePhotos + (angle to safeUri)
                } else {
                    state.draft.anglePhotos
                }
                val nextPrimary = nextAngles[VehicleAngle.FRONT]
                    ?: if (state.draft.photoUri == photoUri) safeUri else state.draft.photoUri
                state.copy(
                    isProtectingPlate = false,
                    platePrivacyNotice = null,
                    draft = state.draft.copy(
                        photoUri = nextPrimary,
                        anglePhotos = nextAngles
                    )
                )
            }
            if (!_uiState.value.isEditMode) {
                analyzeAttachedPhotosWithAi(context)
            }
        }
    }

    fun analyzeAttachedPhotosWithAi(context: Context) {
        val currentDraft = _uiState.value.draft
        val photos = when {
            currentDraft.anglePhotos.isNotEmpty() -> currentDraft.anglePhotos
            currentDraft.photoUri.isNotBlank() -> mapOf(VehicleAngle.FRONT to currentDraft.photoUri)
            else -> emptyMap()
        }
        if (photos.isEmpty()) {
            _uiState.update {
                it.copy(aiAnalysisError = "Attach at least one vehicle photo first.")
            }
            return
        }

        aiAnalysisJob?.cancel()
        aiAnalysisJob = viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingPhoto = true, aiAnalysisError = null) }
            try {
                // Strip GPS/location metadata from all attached angles before AI vision analysis
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
                        val nextPrimary = safePhotos[VehicleAngle.FRONT]
                            ?: safePhotos.values.firstOrNull()
                            ?: state.draft.photoUri
                        state.copy(
                            platePrivacyNotice = null,
                            draft = state.draft.copy(
                                anglePhotos = safePhotos,
                                photoUri = nextPrimary
                            )
                        )
                    }
                }

                val result = visionAnalyzer.analyzeVehicleAngles(context.applicationContext, safePhotos)
                result.fold(
                    onSuccess = { vision ->
                        _uiState.update { it.copy(isAnalyzingPhoto = false, aiAnalysisError = null) }
                        applyStudioCapture(safePhotos, vision)
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isAnalyzingPhoto = false,
                                aiAnalysisError = error.localizedMessage ?: "AI analysis failed."
                            )
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isAnalyzingPhoto = false,
                        aiAnalysisError = e.localizedMessage ?: "AI analysis failed."
                    )
                }
            }
        }
    }

    fun applyStudioCapture(
        anglePhotos: Map<VehicleAngle, String>,
        visionResult: VehicleVisionResult?
    ) {
        _uiState.update { state ->
            val current = state.draft
            val resolvedPrimaryPhoto = anglePhotos[VehicleAngle.FRONT]
                ?: anglePhotos.values.firstOrNull()
                ?: current.photoUri
            if (visionResult == null) {
                return@update state.copy(
                    draft = current.copy(
                        anglePhotos = anglePhotos,
                        photoUri = resolvedPrimaryPhoto
                    )
                )
            }

            val nextManufacturer = if (
                visionResult.manufacturer != UNKNOWN_SPEC_LABEL &&
                visionResult.manufacturer.isNotBlank()
            ) {
                visionResult.manufacturer
            } else {
                current.manufacturer
            }
            val nextModel = if (
                visionResult.model != UNKNOWN_SPEC_LABEL &&
                visionResult.model.isNotBlank()
            ) {
                visionResult.model
            } else {
                current.model
            }
            val nextYear = if (
                visionResult.modelYearGuess.length == 4 &&
                (current.modelYearInput.isBlank() || current.specSource == SpecSource.AI_VISION_PREFILL)
            ) {
                visionResult.modelYearGuess
            } else {
                current.modelYearInput
            }
            val nextTrim = if (
                (current.trim.isBlank() || current.specSource == SpecSource.AI_VISION_PREFILL) &&
                visionResult.trimOrBadgeHints != UNKNOWN_SPEC_LABEL &&
                visionResult.trimOrBadgeHints.isNotBlank()
            ) {
                visionResult.trimOrBadgeHints
            } else {
                current.trim
            }
            val nextFuel = if (
                visionResult.fuelTypeHint != UNKNOWN_SPEC_LABEL &&
                visionResult.fuelTypeHint.isNotBlank()
            ) {
                visionResult.fuelTypeHint
            } else {
                current.fuelType
            }

            state.copy(
                useCustomEntry = true,
                availableModels = DemoVehicleCatalog.modelsForManufacturer(nextManufacturer),
                availableVariants = DemoVehicleCatalog.variantsForModel(nextManufacturer, nextModel),
                fieldErrors = state.fieldErrors - setOf(
                    VehicleValidator.FIELD_MANUFACTURER,
                    VehicleValidator.FIELD_MODEL,
                    VehicleValidator.FIELD_FUEL_TYPE
                ),
                draft = current.copy(
                    manufacturer = nextManufacturer,
                    model = nextModel,
                    modelYearInput = nextYear,
                    trim = nextTrim,
                    bodyStyle = visionResult.bodyStyle,
                    fuelType = nextFuel,
                    photoUri = resolvedPrimaryPhoto,
                    anglePhotos = anglePhotos,
                    aiInspectionSummary = visionResult.exteriorSummary.ifBlank {
                        "$nextManufacturer $nextModel".trim()
                    },
                    specSource = SpecSource.AI_VISION_PREFILL,
                    unknownFields = current.unknownFields - VehicleValidator.FIELD_FUEL_TYPE
                )
            )
        }
    }

    fun updateSetAsPrimary(primary: Boolean) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(setAsPrimary = primary))
        }
    }

    fun submit() {
        val currentState = _uiState.value
        val validation = VehicleValidator.validate(currentState.draft)
        if (!validation.isValid) {
            _uiState.update {
                it.copy(
                    fieldErrors = validation.fieldErrors,
                    submitError = "Please resolve the highlighted registration fields."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, submitError = null) }
            val existing = loadedVehicle
            if (currentState.isEditMode && existing != null) {
                val res = repository.updateVehicle(currentUserId, existing, currentState.draft)
                res.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isSaving = false, savedVehicleId = existing.id) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                submitError = error.localizedMessage ?: "Failed to update vehicle."
                            )
                        }
                    }
                )
            } else {
                val res = repository.createVehicle(currentUserId, currentState.draft)
                res.fold(
                    onSuccess = { newId ->
                        _uiState.update { it.copy(isSaving = false, savedVehicleId = newId) }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                submitError = error.localizedMessage ?: "Failed to register vehicle."
                            )
                        }
                    }
                )
            }
        }
    }

    private inline fun updateDraftUserEdited(@Suppress("UNUSED_PARAMETER") field: String, block: () -> Unit) {
        block()
    }
}
