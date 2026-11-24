package com.example.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Vehicle
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.repository.VehicleDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiagnosticsTransportState(val isConnected: Boolean) {
    DISCONNECTED_UNCONFIGURED(false),
    CONNECTING(false),
    CONNECTED_VERIFIED(true),
    ERROR(false)
}

data class DiagnosticsConnectionSummary(
    val wifiState: DiagnosticsTransportState = DiagnosticsTransportState.DISCONNECTED_UNCONFIGURED,
    val bluetoothState: DiagnosticsTransportState = DiagnosticsTransportState.DISCONNECTED_UNCONFIGURED,
    val activeAdapterName: String? = null,
    val protocolLabel: String? = null,
    val wifiEndpointLabel: String = "192.168.0.10:35000",
    val isWifiNetworkReady: Boolean = false,
    val bluetoothDeviceCount: Int = 0,
    val bluetoothArchitecture: BluetoothArchitecture = BluetoothArchitecture.CLASSIC_SPP,
    val statusDetailMessage: String? = null
) {
    val isAnyConnected: Boolean
        get() = wifiState.isConnected || bluetoothState.isConnected
}

data class DiagnosticsHubUiState(
    val isLoadingVehicles: Boolean = true,
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicleId: String? = null,
    val connectionSummary: DiagnosticsConnectionSummary = DiagnosticsConnectionSummary(),
    val savedSessionCount: Int = 0,
    val recentReportCount: Int = 0,
    val liveTelemetry: LiveTelemetryStreamState = LiveTelemetryStreamState(),
    val loadError: String? = null,
    val isReadOnlyMode: Boolean = true
) {
    val selectedVehicle: Vehicle?
        get() = vehicles.firstOrNull { it.id == selectedVehicleId }
            ?: vehicles.firstOrNull { it.primary }
            ?: vehicles.firstOrNull()
}

class DiagnosticsViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    initialVehicleId: String? = null,
    private val connectionManager: ObdConnectionManager = ObdConnectionManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DiagnosticsHubUiState(selectedVehicleId = initialVehicleId)
    )
    val uiState: StateFlow<DiagnosticsHubUiState> = _uiState.asStateFlow()

    private var sessionCountJob: kotlinx.coroutines.Job? = null

    init {
        observeUserVehicles(initialVehicleId)
        observeConnectionState()
        observeLiveTelemetry()
    }

    private fun observeLiveTelemetry() {
        viewModelScope.launch {
            connectionManager.liveTelemetry.collect { telemetry ->
                _uiState.update { it.copy(liveTelemetry = telemetry) }
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionManager.state.collect { managerState ->
                val connected = managerState.connectionStatus as? ObdConnectionStatus.Connected
                val errorMsg = when (val status = managerState.connectionStatus) {
                    is ObdConnectionStatus.Error -> status.message
                    is ObdConnectionStatus.PermissionRequired -> status.reason
                    else -> null
                }
                val summary = DiagnosticsConnectionSummary(
                    wifiState = when {
                        connected != null && connected.method == ObdConnectionMethod.WIFI ->
                            DiagnosticsTransportState.CONNECTED_VERIFIED
                        managerState.connectionStatus is ObdConnectionStatus.Error &&
                            managerState.activeMethod == ObdConnectionMethod.WIFI ->
                            DiagnosticsTransportState.ERROR
                        else -> DiagnosticsTransportState.DISCONNECTED_UNCONFIGURED
                    },
                    bluetoothState = when {
                        connected != null && connected.method == ObdConnectionMethod.BLUETOOTH ->
                            DiagnosticsTransportState.CONNECTED_VERIFIED
                        managerState.connectionStatus is ObdConnectionStatus.Error &&
                            managerState.activeMethod == ObdConnectionMethod.BLUETOOTH ->
                            DiagnosticsTransportState.ERROR
                        else -> DiagnosticsTransportState.DISCONNECTED_UNCONFIGURED
                    },
                    activeAdapterName = connected?.targetLabel,
                    protocolLabel = managerState.protocolPreference.displayName,
                    wifiEndpointLabel = managerState.wifiState.endpointLabel,
                    isWifiNetworkReady = managerState.wifiState.isWifiNetworkConnected,
                    bluetoothDeviceCount = managerState.bluetoothState.devices.size,
                    bluetoothArchitecture = managerState.bluetoothState.architecture,
                    statusDetailMessage = errorMsg
                )
                _uiState.update { it.copy(connectionSummary = summary) }
            }
        }
    }

    private fun observeUserVehicles(initialVehicleId: String?) {
        viewModelScope.launch {
            repository.observeVehicles(currentUserId)
                .catch { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoadingVehicles = false,
                            loadError = error.localizedMessage ?: "Unable to load garage vehicles."
                        )
                    }
                }
                .collect { vehicles ->
                    var newlyResolvedId: String? = null
                    _uiState.update { state ->
                        val resolvedSelectedId = when {
                            state.selectedVehicleId != null && vehicles.any { it.id == state.selectedVehicleId } ->
                                state.selectedVehicleId
                            !initialVehicleId.isNullOrBlank() && vehicles.any { it.id == initialVehicleId } ->
                                initialVehicleId
                            else ->
                                (vehicles.firstOrNull { it.primary } ?: vehicles.firstOrNull())?.id
                        }
                        newlyResolvedId = resolvedSelectedId
                        state.copy(
                            isLoadingVehicles = false,
                            vehicles = vehicles,
                            selectedVehicleId = resolvedSelectedId,
                            loadError = null
                        )
                    }
                    observeSessionCountsForVehicle(newlyResolvedId)
                }
        }
    }

    private fun observeSessionCountsForVehicle(vehicleId: String?) {
        sessionCountJob?.cancel()
        if (vehicleId.isNullOrBlank()) {
            _uiState.update { it.copy(savedSessionCount = 0, recentReportCount = 0) }
            return
        }
        sessionCountJob = viewModelScope.launch {
            repository.observeObdSessions(currentUserId, vehicleId)
                .catch { /* keep prior counts silently if disconnected */ }
                .collect { sessions ->
                    _uiState.update {
                        it.copy(
                            savedSessionCount = sessions.size,
                            recentReportCount = sessions.size
                        )
                    }
                }
        }
    }

    fun selectVehicle(vehicleId: String) {
        _uiState.update { it.copy(selectedVehicleId = vehicleId) }
        observeSessionCountsForVehicle(vehicleId)
    }
}
