package com.example.ui.diagnostics.scan

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Vehicle
import com.example.data.obd.DiagnosticFileImporter
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcStatus
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.settings.AppLanguage
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.toObdVehicleSnapshot
import com.example.ui.diagnostics.unregisteredObdVehicleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanInputMode(val label: String) {
    LIVE_OBD("Live OBD-II"),
    MANUAL_DTC("Manual DTC"),
    IMPORT_FILE("Import File")
}

data class ManualDtcChip(
    val code: String,
    val status: DtcStatus
)

data class DiagnosticsScanUiState(
    val activeMode: ScanInputMode = ScanInputMode.LIVE_OBD,
    val dtcInputText: String = "",
    val dtcStatusSelection: DtcStatus = DtcStatus.CONFIRMED,
    val manualDtcs: List<ManualDtcChip> = emptyList(),
    val ownerSymptoms: String = "",
    val manualInputError: String? = null,
    val importedFileName: String? = null,
    val importedSession: ObdDiagnosticSession? = null,
    val importError: String? = null,
    val isImportingFile: Boolean = false
)

class DiagnosticsScanViewModel(
    val connectionManager: ObdConnectionManager,
    val coordinator: DiagnosticsSessionCoordinator,
    private val selectedVehicleProvider: () -> Vehicle?
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsScanUiState())
    val uiState: StateFlow<DiagnosticsScanUiState> = _uiState.asStateFlow()

    val managerState = connectionManager.state
    val sessionCoordinatorState = coordinator.state
    val liveTelemetry = connectionManager.liveTelemetry

    fun selectMode(mode: ScanInputMode) {
        coordinator.clearError()
        _uiState.update {
            it.copy(
                activeMode = mode,
                manualInputError = null,
                importError = null
            )
        }
    }

    fun updateDtcInputText(raw: String) {
        _uiState.update { it.copy(dtcInputText = raw.uppercase(), manualInputError = null) }
    }

    fun selectDtcStatus(status: DtcStatus) {
        _uiState.update { it.copy(dtcStatusSelection = status) }
    }

    fun updateOwnerSymptoms(text: String) {
        _uiState.update { it.copy(ownerSymptoms = text.take(800), manualInputError = null) }
    }

    fun addManualDtcCodes() {
        val raw = _uiState.value.dtcInputText.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(manualInputError = "Enter an OBD-II trouble code (for example P0171 or C0035).") }
            return
        }
        val tokens = raw.split(Regex("[,;\\s]+"))
            .map { DiagnosticTroubleCode.normalize(it) }
            .filter { it.isNotEmpty() }

        val invalid = tokens.filterNot { DiagnosticTroubleCode.isValidDtcFormat(it) }
        if (invalid.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    manualInputError = "Invalid code format: ${invalid.joinToString(", ")}. Use 5 characters like P0171, C0035, B0100, or U0100."
                )
            }
            return
        }

        val targetStatus = _uiState.value.dtcStatusSelection
        _uiState.update { state ->
            val merged = (state.manualDtcs + tokens.map { ManualDtcChip(it, targetStatus) })
                .distinctBy { it.code }
            state.copy(
                manualDtcs = merged,
                dtcInputText = "",
                manualInputError = null
            )
        }
    }

    fun removeManualDtc(code: String) {
        _uiState.update { state ->
            state.copy(manualDtcs = state.manualDtcs.filterNot { it.code == code })
        }
    }

    fun runLiveScan(
        language: AppLanguage,
        onSuccessNavigateToResults: () -> Unit
    ) {
        val vehicle = selectedVehicleProvider()
        val symptoms = _uiState.value.ownerSymptoms
        viewModelScope.launch {
            val res = coordinator.runLiveObdScanAndAnalyze(
                vehicle = vehicle,
                ownerSymptoms = symptoms,
                language = language
            )
            if (res.isSuccess) {
                onSuccessNavigateToResults()
            }
        }
    }

    fun analyzeManualEntry(
        language: AppLanguage,
        onSuccessNavigateToResults: () -> Unit
    ) {
        val state = _uiState.value
        val pendingTyped = DiagnosticTroubleCode.normalize(state.dtcInputText)
        val combinedChips = if (pendingTyped.isNotEmpty()) {
            if (!DiagnosticTroubleCode.isValidDtcFormat(pendingTyped)) {
                _uiState.update {
                    it.copy(manualInputError = "Invalid code format: '$pendingTyped'. Expected format like P0171.")
                }
                return
            }
            (state.manualDtcs + ManualDtcChip(pendingTyped, state.dtcStatusSelection)).distinctBy { it.code }
        } else {
            state.manualDtcs
        }

        val vehicle = selectedVehicleProvider()
        val snapshot = vehicle?.toObdVehicleSnapshot() ?: unregisteredObdVehicleSnapshot()

        val built = DiagnosticFileImporter.buildManualSession(
            vehicle = snapshot,
            manualDtcs = combinedChips.map { it.code to it.status },
            ownerSymptoms = state.ownerSymptoms
        )
        val session = built.getOrElse { err ->
            _uiState.update { it.copy(manualInputError = err.localizedMessage ?: "Invalid manual entry.") }
            return
        }

        _uiState.update {
            it.copy(
                manualDtcs = combinedChips,
                dtcInputText = "",
                manualInputError = null
            )
        }

        viewModelScope.launch {
            val res = coordinator.analyzeSessionAndPersist(session, vehicle, language)
            if (res.isSuccess) {
                onSuccessNavigateToResults()
            }
        }
    }

    fun importDiagnosticUri(context: Context, uri: Uri) {
        val vehicle = selectedVehicleProvider()
        val snapshot = vehicle?.toObdVehicleSnapshot() ?: unregisteredObdVehicleSnapshot()
        val symptoms = _uiState.value.ownerSymptoms

        _uiState.update { it.copy(isImportingFile = true, importError = null) }
        viewModelScope.launch {
            val displayName = resolveFileDisplayName(context, uri) ?: "diagnostic_log"
            val result = withContext(Dispatchers.IO) {
                DiagnosticFileImporter.importFromUri(
                    context = context,
                    uri = uri,
                    fileDisplayName = displayName,
                    vehicle = snapshot,
                    ownerSymptoms = symptoms
                )
            }
            result.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isImportingFile = false,
                        importedFileName = displayName,
                        importedSession = session,
                        importError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isImportingFile = false,
                        importedFileName = displayName,
                        importedSession = null,
                        importError = err.localizedMessage ?: "Unable to parse diagnostic file."
                    )
                }
            }
        }
    }

    fun analyzeImportedSession(
        language: AppLanguage,
        onSuccessNavigateToResults: () -> Unit
    ) {
        val base = _uiState.value.importedSession ?: run {
            _uiState.update { it.copy(importError = "Import a JSON, CSV, or ELM327 log file first.") }
            return
        }
        val extraSymptoms = _uiState.value.ownerSymptoms.trim().takeIf { it.isNotBlank() }
        val mergedSymptoms = listOfNotNull(base.ownerSymptoms, extraSymptoms)
            .distinct()
            .joinToString(" | ")
            .takeIf { it.isNotBlank() }
        val session = base.copy(ownerSymptoms = mergedSymptoms)
        val vehicle = selectedVehicleProvider()

        viewModelScope.launch {
            val res = coordinator.analyzeSessionAndPersist(session, vehicle, language)
            if (res.isSuccess) {
                onSuccessNavigateToResults()
            }
        }
    }

    private fun resolveFileDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    fun isLiveAdapterConnected(): Boolean {
        val status = connectionManager.state.value.connectionStatus
        return status is ObdConnectionStatus.Connected && connectionManager.activeTransportStream != null
    }
}
