package com.example.ui.diagnostics.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SavedObdSessionRecord
import com.example.data.repository.VehicleDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsHistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<SavedObdSessionRecord> = emptyList(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class DiagnosticsHistoryViewModel(
    private val repository: VehicleDataSource,
    private val userId: String,
    private val vehicleId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DiagnosticsHistoryUiState(isLoading = !vehicleId.isNullOrBlank())
    )
    val uiState: StateFlow<DiagnosticsHistoryUiState> = _uiState.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        val targetId = vehicleId
        if (targetId.isNullOrBlank()) {
            _uiState.update { it.copy(isLoading = false, sessions = emptyList()) }
            return
        }
        viewModelScope.launch {
            repository.observeObdSessions(userId, targetId)
                .catch { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Unable to load diagnostic history."
                        )
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessions = items,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun deleteSession(sessionId: String) {
        val targetVehicleId = vehicleId ?: return
        viewModelScope.launch {
            repository.deleteObdSession(userId, targetVehicleId, sessionId)
                .onSuccess {
                    _uiState.update { it.copy(actionMessage = "Diagnostic session removed from history.") }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(errorMessage = err.localizedMessage ?: "Failed to delete diagnostic session.")
                    }
                }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
