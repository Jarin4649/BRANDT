package com.example.ui.garage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Vehicle
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionManagerState
import com.example.data.repository.VehicleDataSource
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GarageUiState {
    data object Loading : GarageUiState
    data class Ready(val vehicles: List<Vehicle>) : GarageUiState
    data class Error(val message: String) : GarageUiState
}

class GarageViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val displayName: String,
    connectionManager: ObdConnectionManager? = null
) : ViewModel() {

    val obdConnectionState: StateFlow<ObdConnectionManagerState?> =
        connectionManager?.state ?: MutableStateFlow<ObdConnectionManagerState?>(null).asStateFlow()

    val obdLiveTelemetry: StateFlow<LiveTelemetryStreamState?> =
        connectionManager?.liveTelemetry ?: MutableStateFlow<LiveTelemetryStreamState?>(null).asStateFlow()

    private val _selectedPreviewId = MutableStateFlow<String?>(null)
    val selectedPreviewId: StateFlow<String?> = _selectedPreviewId.asStateFlow()

    private val _pendingDeleteVehicle = MutableStateFlow<Vehicle?>(null)
    val pendingDeleteVehicle: StateFlow<Vehicle?> = _pendingDeleteVehicle.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, displayName)
        }
    }

    val uiState: StateFlow<GarageUiState> = repository.observeVehicles(currentUserId)
        .map<List<Vehicle>, GarageUiState> { vehicles -> GarageUiState.Ready(vehicles) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing garage vehicles", root)
            emit(GarageUiState.Error(root.localizedMessage ?: "Failed to load your BRANDT garage."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GarageUiState.Loading
        )

    fun selectVehicleForStage(vehicleId: String) {
        _selectedPreviewId.value = vehicleId
    }

    fun requestDeleteVehicle(vehicle: Vehicle) {
        _pendingDeleteVehicle.value = vehicle
    }

    fun dismissDeleteConfirmation() {
        _pendingDeleteVehicle.value = null
    }

    fun confirmDeleteVehicle() {
        val target = _pendingDeleteVehicle.value ?: return
        _pendingDeleteVehicle.value = null
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteVehicle(currentUserId, target.id)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    if (_selectedPreviewId.value == target.id) {
                        _selectedPreviewId.value = null
                    }
                    _actionMessage.value = "Removed ${target.modelYear} ${target.manufacturer} ${target.model}."
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Unable to remove vehicle."
                }
            )
        }
    }

    fun setPrimaryVehicle(vehicle: Vehicle) {
        if (vehicle.primary) return
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.setPrimaryVehicle(currentUserId, vehicle.id)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _selectedPreviewId.value = vehicle.id
                    _actionMessage.value = "${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model} set as primary."
                },
                onFailure = { error ->
                    _actionMessage.value = error.localizedMessage ?: "Failed to update primary vehicle."
                }
            )
        }
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "GarageViewModel"
    }
}
