package com.example.ui.overview

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.DiagnosticStatusAiExplainer
import com.example.data.ai.DiagnosticStatusPlainExplanation
import com.example.data.ai.FirebaseVehicleMechanicAiService
import com.example.data.ai.VehicleMechanicAiService
import com.example.data.ai.VehicleModelIssuesResult
import com.example.data.model.MAX_MILEAGE
import com.example.data.model.MileageUnit
import com.example.data.model.Vehicle
import com.example.data.obd.BatteryChemistryType
import com.example.data.obd.ElectricalOperatingCondition
import com.example.data.obd.ElectricalSystemType
import com.example.data.obd.LiveVehicleDataMapper
import com.example.data.obd.LiveVehicleDataSnapshot
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.ReadOnlyObdClient
import com.example.data.obd.SystemVoltageCriteria
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.appStrings
import com.example.ui.components.LicensePlatePrivacyProtector
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.toObdVehicleSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VehicleOverviewUiState {
    data object Loading : VehicleOverviewUiState
    data class Ready(val vehicle: Vehicle) : VehicleOverviewUiState
    data object NotFound : VehicleOverviewUiState
    data class Error(val message: String) : VehicleOverviewUiState
}

sealed interface VehicleKnownIssuesUiState {
    data object Idle : VehicleKnownIssuesUiState
    data object Loading : VehicleKnownIssuesUiState
    data class Ready(val result: VehicleModelIssuesResult) : VehicleKnownIssuesUiState
    data class Error(val message: String) : VehicleKnownIssuesUiState
}

sealed interface DiagnosticStatusAiUiState {
    data object Idle : DiagnosticStatusAiUiState
    data object Loading : DiagnosticStatusAiUiState
    data class Ready(val explanation: DiagnosticStatusPlainExplanation) : DiagnosticStatusAiUiState
    data class Error(val message: String) : DiagnosticStatusAiUiState
}

class VehicleOverviewViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val vehicleId: String,
    private val aiService: VehicleMechanicAiService = FirebaseVehicleMechanicAiService(),
    private val languageState: StateFlow<AppLanguage> = LanguagePreferenceStore.currentLanguage,
    autoLoadKnownIssues: Boolean = true,
    private val connectionManager: ObdConnectionManager? = null,
    private val diagnosticsCoordinator: DiagnosticsSessionCoordinator? = null,
    private val obdClient: ReadOnlyObdClient = ReadOnlyObdClient(),
    private val diagnosticAiExplainer: DiagnosticStatusAiExplainer = FirebaseVehicleMechanicAiService()
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _showDeleteDialog = MutableStateFlow(false)
    val showDeleteDialog: StateFlow<Boolean> = _showDeleteDialog.asStateFlow()

    private val _deletedSuccessfully = MutableStateFlow(false)
    val deletedSuccessfully: StateFlow<Boolean> = _deletedSuccessfully.asStateFlow()

    private val _knownIssuesState = MutableStateFlow<VehicleKnownIssuesUiState>(VehicleKnownIssuesUiState.Idle)
    val knownIssuesState: StateFlow<VehicleKnownIssuesUiState> = _knownIssuesState.asStateFlow()

    private val _directPolledSession = MutableStateFlow<ObdDiagnosticSession?>(null)
    private val _isRefreshingEcu = MutableStateFlow(false)
    val isRefreshingEcu: StateFlow<Boolean> = _isRefreshingEcu.asStateFlow()

    private val _diagnosticAiState = MutableStateFlow<DiagnosticStatusAiUiState>(DiagnosticStatusAiUiState.Idle)
    val diagnosticAiState: StateFlow<DiagnosticStatusAiUiState> = _diagnosticAiState.asStateFlow()

    private val _systemVoltageCriteria = MutableStateFlow(
        connectionManager?.state?.value?.systemVoltageCriteria ?: SystemVoltageCriteria()
    )

    private var lastLoadedVehicleKey: String? = null
    private var lastLoadedDiagnosticAiKey: String? = null

    val uiState: StateFlow<VehicleOverviewUiState> = repository.observeVehicle(currentUserId, vehicleId)
        .map { vehicle ->
            if (vehicle != null) VehicleOverviewUiState.Ready(vehicle) else VehicleOverviewUiState.NotFound
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing vehicle overview", root)
            val fallback = appStrings(languageState.value).overviewVehicleErrorFallback
            emit(VehicleOverviewUiState.Error(root.localizedMessage ?: fallback))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = VehicleOverviewUiState.Loading
        )

    private val connectionStateFlow = connectionManager?.state ?: flowOf(null)
    private val coordinatorStateFlow = diagnosticsCoordinator?.state ?: flowOf(null)
    private val liveTelemetryFlow = connectionManager?.liveTelemetry ?: flowOf(null)

    private val telemetryInputsFlow = combine(
        connectionStateFlow,
        coordinatorStateFlow,
        _directPolledSession,
        liveTelemetryFlow,
        _systemVoltageCriteria
    ) { connState, coordState, polled, liveTelemetry, criteria ->
        TelemetryCombineInputs(
            connState = connState,
            coordState = coordState,
            polled = polled,
            liveTelemetry = liveTelemetry,
            criteria = criteria
        )
    }

    val liveVehicleData: StateFlow<LiveVehicleDataSnapshot> = combine(
        telemetryInputsFlow,
        uiState,
        languageState
    ) { inputs, currentUiState, lang ->
        val connState = inputs.connState
        val coordState = inputs.coordState
        val polled = inputs.polled
        val liveTelemetry = inputs.liveTelemetry
        val vehicleObj = (currentUiState as? VehicleOverviewUiState.Ready)?.vehicle

        val connectedStatus = connState?.connectionStatus as? ObdConnectionStatus.Connected
        val isConnected = connectedStatus != null && connectionManager?.activeTransportStream?.isConnected == true

        val coordSession = coordState?.activeSession?.takeIf { s ->
            s.vehicle.vehicleId.isBlank() || s.vehicle.vehicleId == vehicleId
        }
        val streamedSession = liveTelemetry?.latestSession?.takeIf { s ->
            s.vehicle.vehicleId.isBlank() || s.vehicle.vehicleId == vehicleId
        }
        val activeSession = listOfNotNull(polled, coordSession, streamedSession)
            .maxByOrNull { it.capturedAtEpochMs }

        val mergedCriteria = inputs.criteria.let { base ->
            val vSys = ElectricalSystemType.fromCode(vehicleObj?.electricalSystemType)
            val vBat = BatteryChemistryType.fromCode(vehicleObj?.batteryChemistryType)
            base.copy(
                systemType = if (base.systemType != ElectricalSystemType.UNKNOWN) base.systemType else vSys,
                batteryType = if (base.batteryType != BatteryChemistryType.UNSPECIFIED) base.batteryType else vBat
            )
        }

        LiveVehicleDataMapper.map(
            session = activeSession,
            isAdapterConnected = isConnected,
            connectedAdapterLabel = connectedStatus?.targetLabel,
            language = lang,
            recentVoltageSamples = liveTelemetry?.recentVoltageSamples.orEmpty(),
            connectionManagerState = connState,
            systemVoltageCriteria = mergedCriteria,
            isCommunicationInterrupted = liveTelemetry?.isCommunicationInterrupted == true,
            lastDisconnectReason = liveTelemetry?.lastDisconnectReason
                ?: (connState?.connectionStatus as? ObdConnectionStatus.Error)?.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LiveVehicleDataMapper.map(
            session = null,
            isAdapterConnected = false,
            language = languageState.value
        )
    )

    init {
        if (autoLoadKnownIssues) {
            viewModelScope.launch {
                combine(uiState, languageState) { state, lang -> state to lang }
                    .collectLatest { (state, lang) ->
                        if (state is VehicleOverviewUiState.Ready) {
                            val v = state.vehicle
                            val key = "${v.id}|${v.modelYear}|${v.manufacturer}|${v.model}|${v.trim}|${lang.code}"
                            if (lastLoadedVehicleKey != key) {
                                lastLoadedVehicleKey = key
                                fetchCommonIssues(v, lang)
                            }
                        }
                    }
            }
        }
    }

    fun refreshLiveEcuData() {
        val isThai = languageState.value == AppLanguage.THAI
        val stream = connectionManager?.activeTransportStream
        if (stream == null || !stream.isConnected) {
            _actionMessage.value = if (isThai) {
                "กรุณาเชื่อมต่อตัวสแกน OBD-II ที่หน้าวิเคราะห์ระบบรถก่อนดึงข้อมูล ECU สด"
            } else {
                "Connect your OBD-II adapter in Vehicle Diagnostics before refreshing live ECU data."
            }
            return
        }
        val vehicle = (uiState.value as? VehicleOverviewUiState.Ready)?.vehicle ?: return
        val protocol = connectionManager.state.value.protocolPreference

        viewModelScope.launch {
            _isRefreshingEcu.value = true
            val result = try {
                connectionManager.withPollingPaused {
                    obdClient.runReadOnlyDiagnosticScan(
                        transport = stream,
                        vehicle = vehicle.toObdVehicleSnapshot(),
                        preferredProtocol = protocol,
                        requestedPids = ReadOnlyObdClient.OVERVIEW_TELEMETRY_PIDS,
                        ownerSymptoms = null
                    )
                }
            } finally {
                _isRefreshingEcu.value = false
            }
            result.fold(
                onSuccess = { session ->
                    _directPolledSession.value = session
                    connectionManager.publishLatestSession(session)
                    _actionMessage.value = if (isThai) {
                        "อัปเดตข้อมูล ECU สดเรียบร้อย (${session.availableReadings.size} ค่า)"
                    } else {
                        "Live ECU telemetry updated (${session.availableReadings.size} values)"
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: if (isThai) {
                        "ไม่สามารถอ่านข้อมูลจากกล่อง ECU ได้"
                    } else {
                        "Unable to read telemetry from the ECU."
                    }
                }
            )
        }
    }

    fun updateSystemVoltageCriteria(
        systemType: ElectricalSystemType? = null,
        batteryType: BatteryChemistryType? = null,
        operatingCondition: ElectricalOperatingCondition? = null
    ) {
        val current = _systemVoltageCriteria.value
        val updated = current.copy(
            systemType = systemType ?: current.systemType,
            batteryType = batteryType ?: current.batteryType,
            operatingCondition = operatingCondition ?: current.operatingCondition
        )
        _systemVoltageCriteria.value = updated
        connectionManager?.updateSystemVoltageCriteria(updated)
    }

    fun refreshCommonIssues() {
        val currentVehicle = (uiState.value as? VehicleOverviewUiState.Ready)?.vehicle ?: return
        val lang = languageState.value
        viewModelScope.launch {
            fetchCommonIssues(currentVehicle, lang)
        }
    }

    fun explainDiagnosticStatus(forceRefresh: Boolean = false) {
        val currentVehicle = (uiState.value as? VehicleOverviewUiState.Ready)?.vehicle ?: return
        val detail = liveVehicleData.value.diagnosticStatusDetail
        val targetLang = languageState.value
        val isThai = targetLang == AppLanguage.THAI
        val requestKey = "${currentVehicle.id}|${detail.sessionKey}|${targetLang.code}"
        if (!forceRefresh && lastLoadedDiagnosticAiKey == requestKey && _diagnosticAiState.value is DiagnosticStatusAiUiState.Ready) {
            return
        }
        lastLoadedDiagnosticAiKey = requestKey
        viewModelScope.launch {
            _diagnosticAiState.value = DiagnosticStatusAiUiState.Loading
            val result = diagnosticAiExplainer.explainDiagnosticStatusPlainLanguage(
                vehicle = currentVehicle,
                status = detail,
                language = targetLang
            )
            result.fold(
                onSuccess = { explanation ->
                    _diagnosticAiState.value = DiagnosticStatusAiUiState.Ready(explanation)
                },
                onFailure = { err ->
                    _diagnosticAiState.value = DiagnosticStatusAiUiState.Error(
                        err.localizedMessage ?: if (isThai) {
                            "ไม่สามารถสร้างคำอธิบายจาก AI ได้ในขณะนี้"
                        } else {
                            "Unable to generate an AI explanation right now."
                        }
                    )
                }
            )
        }
    }

    private suspend fun fetchCommonIssues(vehicle: Vehicle, language: AppLanguage) {
        _knownIssuesState.value = VehicleKnownIssuesUiState.Loading
        val result = aiService.loadCommonIssuesForVehicle(vehicle, language)
        result.fold(
            onSuccess = { data ->
                _knownIssuesState.value = VehicleKnownIssuesUiState.Ready(data)
            },
            onFailure = { error ->
                val fallback = appStrings(language).overviewKnownIssuesLoadErrorFallback
                _knownIssuesState.value = VehicleKnownIssuesUiState.Error(
                    error.localizedMessage ?: fallback
                )
            }
        )
    }

    fun setAsPrimary() {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.setPrimaryVehicle(currentUserId, vehicleId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Primary vehicle updated." },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to set primary vehicle." }
            )
        }
    }

    fun updateMileage(rawInput: String, unit: MileageUnit): Boolean {
        val parsed = rawInput.trim().replace(",", "").toLongOrNull()
        if (parsed == null || parsed !in 0L..MAX_MILEAGE) {
            _actionMessage.value = "Enter a valid mileage between 0 and 9,999,999."
            return false
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.updateMileage(currentUserId, vehicleId, parsed, unit)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = "Odometer updated to $parsed ${unit.code}." },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to update mileage." }
            )
        }
        return true
    }

    fun updatePhoto(photoUri: String, context: Context? = null) {
        viewModelScope.launch {
            _isMutating.value = true
            val protection = if (context != null && photoUri.isNotBlank()) {
                LicensePlatePrivacyProtector.protectVehiclePhoto(context.applicationContext, photoUri)
            } else {
                LicensePlatePrivacyProtector.PlateProtectionResult(photoUri, 0)
            }
            val safeUri = protection.outputUri
            val result = repository.updatePhoto(currentUserId, vehicleId, safeUri)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (safeUri.isBlank()) {
                        "Vehicle photo cleared."
                    } else {
                        "Vehicle photo updated."
                    }
                },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to update photo." }
            )
        }
    }

    fun requestDelete() {
        _showDeleteDialog.value = true
    }

    fun dismissDelete() {
        _showDeleteDialog.value = false
    }

    fun confirmDelete() {
        _showDeleteDialog.value = false
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteVehicle(currentUserId, vehicleId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _deletedSuccessfully.value = true },
                onFailure = { _actionMessage.value = it.localizedMessage ?: "Failed to remove vehicle." }
            )
        }
    }

    fun consumeMessage() {
        _actionMessage.value = null
    }

    private data class TelemetryCombineInputs(
        val connState: com.example.data.obd.ObdConnectionManagerState?,
        val coordState: com.example.ui.diagnostics.DiagnosticsSessionUiState?,
        val polled: ObdDiagnosticSession?,
        val liveTelemetry: com.example.data.obd.LiveTelemetryStreamState?,
        val criteria: SystemVoltageCriteria
    )

    private companion object {
        const val TAG = "VehicleOverviewVM"
    }
}
