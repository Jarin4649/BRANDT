package com.example.ui.diagnostics

import com.example.data.ai.DiagnosticReportGenerator
import com.example.data.ai.EverydayDiagnosticResult
import com.example.data.ai.EverydayExplanationEngine
import com.example.data.ai.FirebaseEverydayExplanationEngine
import com.example.data.ai.GeneratedDiagnosticReport
import com.example.data.model.SavedObdSessionRecord
import com.example.data.model.Vehicle
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.obd.ReadOnlyObdClient
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiagnosticsSessionUiState(
    val activeSession: ObdDiagnosticSession? = null,
    val activeResult: EverydayDiagnosticResult? = null,
    val activeReport: GeneratedDiagnosticReport? = null,
    val selectedSavedSession: SavedObdSessionRecord? = null,
    val isBusy: Boolean = false,
    val busyStepLabel: String? = null,
    val errorMessage: String? = null,
    val lastSavedSessionId: String? = null,
    val saveWarningMessage: String? = null
)

/**
 * Shared coordinator scoped per signed-in user session in MotoraAppShell.
 * Bridges Phase 4 (Manual/Import Scan), Phase 5 (AI Everyday Explanation Engine),
 * Phase 6 (Results, History Persistence, Mechanic Report), and Phase 7 (Live OBD Stream).
 */
class DiagnosticsSessionCoordinator(
    private val userId: String,
    private val repository: VehicleDataSource,
    private val connectionManager: ObdConnectionManager,
    private val explanationEngine: EverydayExplanationEngine = FirebaseEverydayExplanationEngine(),
    private val obdClient: ReadOnlyObdClient = ReadOnlyObdClient()
) {
    private val _state = MutableStateFlow(DiagnosticsSessionUiState())
    val state: StateFlow<DiagnosticsSessionUiState> = _state.asStateFlow()

    fun clearError() {
        _state.update { it.copy(errorMessage = null, saveWarningMessage = null) }
    }

    suspend fun runLiveObdScanAndAnalyze(
        vehicle: Vehicle?,
        ownerSymptoms: String?,
        language: AppLanguage
    ): Result<EverydayDiagnosticResult> {
        val stream = connectionManager.activeTransportStream
        if (stream == null || !stream.isConnected) {
            val msg = "No verified OBD-II scanner is connected. Connect a Wi-Fi or Bluetooth adapter in Connection Setup first."
            _state.update { it.copy(errorMessage = msg) }
            return Result.failure(IllegalStateException(msg))
        }

        val vehicleSnapshot = vehicle?.toObdVehicleSnapshot()
            ?: unregisteredObdVehicleSnapshot()

        _state.update {
            it.copy(
                isBusy = true,
                busyStepLabel = "Reading DTCs, Freeze Frame & live PIDs (Read-Only)...",
                errorMessage = null,
                saveWarningMessage = null
            )
        }

        val scanResult = connectionManager.withPollingPaused {
            obdClient.runReadOnlyDiagnosticScan(
                transport = stream,
                vehicle = vehicleSnapshot,
                preferredProtocol = connectionManager.state.value.protocolPreference,
                ownerSymptoms = ownerSymptoms
            )
        }

        val session = scanResult.getOrElse { err ->
            _state.update {
                it.copy(
                    isBusy = false,
                    busyStepLabel = null,
                    errorMessage = err.localizedMessage ?: "Live OBD-II scan failed."
                )
            }
            return Result.failure(err)
        }

        connectionManager.publishLatestSession(session)
        return analyzeSessionAndPersist(session, vehicle, language)
    }

    suspend fun analyzeSessionAndPersist(
        session: ObdDiagnosticSession,
        vehicle: Vehicle?,
        language: AppLanguage
    ): Result<EverydayDiagnosticResult> {
        _state.update {
            it.copy(
                activeSession = session,
                selectedSavedSession = null,
                isBusy = true,
                busyStepLabel = "Translating ECU codes & evidence into everyday guidance...",
                errorMessage = null,
                saveWarningMessage = null
            )
        }

        return try {
            val explanationResult = explanationEngine.explainDiagnosticSession(session, language)
                .getOrElse { err ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            busyStepLabel = null,
                            errorMessage = err.localizedMessage ?: "Diagnostic explanation engine failed."
                        )
                    }
                    return Result.failure(err)
                }

            val generatedReport = DiagnosticReportGenerator.generate(session, explanationResult)
            var savedId: String? = null
            var saveWarning: String? = null

            if (vehicle != null && vehicle.id.isNotBlank()) {
                val record = SavedObdSessionRecord(
                    userId = userId,
                    vehicleId = vehicle.id,
                    vehicleTitle = generatedReport.vehicleDisplayTitle,
                    sourceType = session.sourceMetadata.sourceType.code,
                    adapterOrFileLabel = session.sourceMetadata.adapterOrFileLabel,
                    reliabilityLevel = session.sourceMetadata.reliabilityLevel.name,
                    overallUrgency = explanationResult.overallUrgencyLevel.label,
                    confidenceLevel = explanationResult.findings.firstOrNull()?.confidenceLevel?.label ?: "Low",
                    ownerSymptoms = session.ownerSymptoms.orEmpty(),
                    headlineSummary = explanationResult.overallSummary,
                    driveabilityNotice = explanationResult.driveabilityCautionNotice,
                    dtcCodes = session.allDtcs.map { it.code }.distinct(),
                    findingTitles = explanationResult.findings.map { it.findingTitle },
                    telemetrySummary = generatedReport.telemetryRows.map { row ->
                        "${row.pidCode} ${row.label}: ${row.displayValue}"
                    },
                    missingInformation = explanationResult.findings.flatMap { it.missingInformation }.distinct(),
                    mechanicChecklist = generatedReport.mechanicHandoffChecklist,
                    reportPlainText = generatedReport.shareablePlainText,
                    readOnlyVerified = session.sourceMetadata.readOnlyEnforced
                )
                repository.saveObdSession(userId, vehicle.id, record)
                    .onSuccess { id -> savedId = id }
                    .onFailure { err ->
                        saveWarning = "Analyzed locally, but cloud history sync failed: ${err.localizedMessage ?: "Unknown error"}"
                    }
            } else {
                saveWarning = "Session analyzed; register/select a vehicle in BRANDT to save this report to cloud history."
            }

            _state.update {
                it.copy(
                    activeSession = session,
                    activeResult = explanationResult,
                    activeReport = generatedReport,
                    selectedSavedSession = null,
                    isBusy = false,
                    busyStepLabel = null,
                    errorMessage = null,
                    lastSavedSessionId = savedId,
                    saveWarningMessage = saveWarning
                )
            }
            Result.success(explanationResult)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isBusy = false,
                    busyStepLabel = null,
                    errorMessage = e.localizedMessage ?: "Unable to analyze diagnostic session."
                )
            }
            Result.failure(e)
        }
    }

    fun openSavedSession(record: SavedObdSessionRecord) {
        _state.update {
            it.copy(
                selectedSavedSession = record,
                errorMessage = null
            )
        }
    }
}

fun Vehicle.toObdVehicleSnapshot(): ObdVehicleSnapshot = ObdVehicleSnapshot.fromVehicle(this)

fun unregisteredObdVehicleSnapshot(): ObdVehicleSnapshot = ObdVehicleSnapshot(
    vehicleId = "",
    manufacturer = "Unregistered",
    model = "Vehicle",
    modelYear = null,
    trim = null,
    engineType = null,
    engineDisplacement = null,
    transmission = null,
    fuelType = null,
    mileage = null,
    mileageUnit = null,
    market = null
)
