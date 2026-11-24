package com.example.ui.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDiagnosisLog
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.appStrings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class IssueLogFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    RESOLVED("Resolved")
}

sealed interface VehicleIssueLogUiState {
    data object Loading : VehicleIssueLogUiState
    data class Ready(
        val vehicle: Vehicle?,
        val allLogs: List<VehicleDiagnosisLog>,
        val filteredLogs: List<VehicleDiagnosisLog>,
        val activeFilter: IssueLogFilter
    ) : VehicleIssueLogUiState
    data class Error(val message: String) : VehicleIssueLogUiState
}

class VehicleIssueLogViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val vehicleId: String,
    private val languageState: StateFlow<AppLanguage> = LanguagePreferenceStore.currentLanguage
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(IssueLogFilter.ALL)
    val selectedFilter: StateFlow<IssueLogFilter> = _selectedFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    val uiState: StateFlow<VehicleIssueLogUiState> = combine(
        repository.observeVehicle(currentUserId, vehicleId),
        repository.observeDiagnosisLogs(currentUserId, vehicleId),
        _selectedFilter
    ) { vehicle, logs, filter ->
        val filtered = when (filter) {
            IssueLogFilter.ALL -> logs
            IssueLogFilter.OPEN -> logs.filterNot { it.resolved }
            IssueLogFilter.RESOLVED -> logs.filter { it.resolved }
        }
        VehicleIssueLogUiState.Ready(
            vehicle = vehicle,
            allLogs = logs,
            filteredLogs = filtered,
            activeFilter = filter
        ) as VehicleIssueLogUiState
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Error observing vehicle diagnosis logs", root)
            emit(VehicleIssueLogUiState.Error(root.localizedMessage ?: appStrings(languageState.value).logLoadErrorFallback))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = VehicleIssueLogUiState.Loading
        )

    fun setFilter(filter: IssueLogFilter) {
        _selectedFilter.value = filter
    }

    fun toggleResolved(log: VehicleDiagnosisLog) {
        viewModelScope.launch {
            val strings = appStrings(languageState.value)
            _isMutating.value = true
            val nextResolved = !log.resolved
            val result = repository.toggleDiagnosisLogResolved(
                userId = currentUserId,
                vehicleId = vehicleId,
                logId = log.id,
                resolved = nextResolved
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (nextResolved) strings.logMarkedResolvedToast else strings.logReopenedToast
                },
                onFailure = {
                    _actionMessage.value = it.localizedMessage ?: strings.logUpdateFailedFallback
                }
            )
        }
    }

    fun deleteLog(logId: String) {
        viewModelScope.launch {
            val strings = appStrings(languageState.value)
            _isMutating.value = true
            val result = repository.deleteDiagnosisLog(currentUserId, vehicleId, logId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _actionMessage.value = strings.logDeletedToast },
                onFailure = { _actionMessage.value = it.localizedMessage ?: strings.logDeleteFailedFallback }
            )
        }
    }

    fun consumeMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "VehicleIssueLogVM"
    }
}
