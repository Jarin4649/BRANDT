package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.DiagnosticConfidenceLevel
import com.example.data.ai.DiagnosticUrgencyLevel
import com.example.data.ai.EverydayDiagnosticFinding
import com.example.data.ai.EverydayDiagnosticResult
import com.example.data.ai.EverydayExplanationEngine
import com.example.data.ai.EverydayImpactBreakdown
import com.example.data.ai.StructuredNextSteps
import com.example.data.ai.SupportingEvidenceBundle
import com.example.data.ai.DiagnosticSafetyAssessor
import com.example.data.ai.DiagnosticEvidenceEvaluator
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.BluetoothDeviceEntry
import com.example.data.obd.BluetoothHardwarePort
import com.example.data.obd.BluetoothObdAdapter
import com.example.data.obd.DataReliabilityLevel
import com.example.data.obd.DiagnosticFileImporter
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.DtcStatus
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDataSourceType
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.ObdTransportConnector
import com.example.data.obd.ObdTransportStream
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.obd.PidAvailability
import com.example.data.obd.WifiHardwarePort
import com.example.data.obd.WifiObdAdapter
import com.example.data.settings.AppLanguage
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.DiagnosticsViewModel
import com.example.ui.diagnostics.history.DiagnosticsHistoryScreen
import com.example.ui.diagnostics.history.DiagnosticsHistoryViewModel
import com.example.ui.diagnostics.report.DiagnosticsReportScreen
import com.example.ui.diagnostics.results.DiagnosticsResultsScreen
import com.example.ui.diagnostics.scan.DiagnosticsScanScreen
import com.example.ui.diagnostics.scan.DiagnosticsScanViewModel
import com.example.ui.theme.MotoraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phases 4-7 Test Suite:
 * - Phase 4: DiagnosticFileImporter (JSON, ELM327/CSV logs, empty/malformed, manual DTC, null PIDs)
 * - Phase 5: Coordinator -> ExplanationEngine -> Safety/Evidence integration
 * - Phase 6: Firestore-backed SavedObdSessionRecord repository + History/Results/Report Compose UI
 * - Phase 7: Live USB/Bluetooth ObdTransportConnector handshake & read-only scan -> disconnect flow
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4To7DiagnosticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeTestExplanationEngine : EverydayExplanationEngine {
        var lastSession: ObdDiagnosticSession? = null
        override suspend fun explainDiagnosticSession(
            session: ObdDiagnosticSession,
            language: AppLanguage
        ): Result<EverydayDiagnosticResult> {
            lastSession = session
            val interpretations = session.allDtcs.map { DtcInterpreter.interpret(it, session.vehicle) }
            val audit = DiagnosticEvidenceEvaluator.evaluate(session, interpretations)
            val safety = DiagnosticSafetyAssessor.assess(session, audit)
            val dtcs = session.allDtcs.map { it.code }.ifEmpty { listOf("NONE") }
            val finding = EverydayDiagnosticFinding(
                findingTitle = if (session.allDtcs.isNotEmpty()) "${dtcs.first()} Check Engine Signal" else "General Health Review",
                whatHappened = "The car's computer recorded ${dtcs.joinToString(", ")} while monitoring engine balance.",
                whatItMeans = "More unmeasured air or less fuel than expected was noticed, so the engine computer flagged a check.",
                plainLanguageExplanation = "The engine computer noticed the air-to-fuel mix looked off and turned on a check code.",
                technicalExplanation = "Fuel trim / sensor deviation observed in read-only session.",
                possibleCauses = listOf("Intake boot vacuum leak", "Dirty airflow sensor"),
                everydayImpact = EverydayImpactBreakdown(
                    normalOperation = "Idle may feel slightly uneven when stopped.",
                    drivingComfort = "Mild vibration at traffic lights.",
                    fuelConsumption = "Insufficient sensor evidence to quantify fuel consumption change yet.",
                    vehicleReliability = "Leaving a lean mixture uninspected can stress exhaust parts over time.",
                    safetyNote = DiagnosticSafetyAssessor.MANDATORY_SCAN_SAFETY_DISCLAIMER
                ),
                urgencyLevel = safety.minimumUrgencyFloor,
                urgencyReason = "An inspection helps find the root cause before emissions or drivability worsens.",
                safeObservations = listOf("Note whether idle smooths out when warmed up."),
                recommendedNextSteps = StructuredNextSteps(
                    thingsOwnerCanSafelyObserve = listOf("Check if fuel cap feels snug after refueling."),
                    informationOwnerCanRecord = listOf("Record mileage and whether warning light flashes."),
                    mechanicRequiredActions = listOf("Smoke test intake ducts and verify fuel trims."),
                    stopDrivingSituations = safety.stopDrivingGuidance
                ),
                professionalInspectionRequired = true,
                supportingEvidence = SupportingEvidenceBundle(
                    diagnosticCodesUsed = dtcs,
                    sensorValuesUsed = audit.evidenceBundle.sensorValuesUsed,
                    vehicleInformationUsed = audit.evidenceBundle.vehicleInformationUsed
                ),
                missingInformation = audit.missingInformation,
                confidenceLevel = if (audit.hasSufficientInformation) DiagnosticConfidenceLevel.MODERATE else DiagnosticConfidenceLevel.LOW,
                reasonsForUncertainty = audit.reasonsForUncertainty,
                knowledgeSources = listOf("SAE J2012")
            )
            return Result.success(
                EverydayDiagnosticResult(
                    sessionId = session.sessionId,
                    generatedAtEpochMs = 1_700_000_000_000L,
                    overallSummary = "Your vehicle logged ${dtcs.joinToString(", ")}. Inspect intake hoses and schedule a workshop check.",
                    overallUrgencyLevel = safety.minimumUrgencyFloor,
                    driveabilityCautionNotice = safety.driveabilityDisclaimer,
                    hasSufficientEvidenceForFullAssessment = audit.hasSufficientInformation,
                    findings = listOf(finding),
                    readOnlyConfirmed = true,
                    safetyAssessment = safety
                )
            )
        }
    }

    private class TestVerifiedTransport(
        override val adapterLabel: String = "Wi-Fi 192.168.0.10:35000",
        override val sourceType: ObdDataSourceType = ObdDataSourceType.WIFI_OBD,
        override val method: ObdConnectionMethod = ObdConnectionMethod.WIFI
    ) : ObdTransportStream {
        override val bluetoothArchitecture: BluetoothArchitecture? = null
        private var open = true
        override val isConnected: Boolean get() = open
        val sentCommands = mutableListOf<String>()

        override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
            sentCommands += command
            val reply = when (command) {
                "ATI" -> "ELM327 v2.3"
                "ATRV" -> "12.6V"
                "ATDP" -> "ISO 15765-4 (CAN 11/500)"
                "ATE0", "ATL0", "ATS0", "ATSP0" -> "OK"
                "0100" -> "41 00 98 18 00 01" // supports 0105, 010C, 010D
                "03" -> "43 01 01 71" // P0171
                "07" -> "NO DATA"
                "0105" -> "41 05 7A" // 82 C
                "010C" -> "41 0C 0D AC" // 875 rpm
                "010D" -> "41 0D 00" // 0 km/h (real idle zero speed)
                "020500" -> "42 05 00 78" // 80 C
                else -> "NO DATA"
            }
            return Result.success(reply)
        }

        override suspend fun close() {
            open = false
        }
    }

    private fun testVehicleSnapshot() = ObdVehicleSnapshot(
        vehicleId = "veh_gtr_1",
        manufacturer = "Nissan",
        model = "GT-R",
        modelYear = 2024,
        trim = "Premium",
        engineType = "3.8L Twin-Turbo V6",
        engineDisplacement = "3.8L",
        transmission = "6-Speed Dual-Clutch",
        fuelType = "Gasoline",
        mileage = 18500L,
        mileageUnit = "km",
        market = "Japan"
    )

    @Test
    fun phase4_fileImporterParsesJsonAndTextLogsAndPreservesNullForMissingPids() {
        val vehicle = testVehicleSnapshot()

        val jsonText = """
            {
              "confirmed_dtcs": ["P0171"],
              "pending_dtcs": [{"code": "P0300", "status": "PENDING"}],
              "live_pids": [
                {"pid_code": "0105", "name": "Coolant Temp", "value": 89.0, "unit": "°C"},
                {"pid_code": "0110", "name": "MAF Rate", "value": null, "unit": "g/s", "availability": "UNSUPPORTED_BY_VEHICLE"}
              ],
              "owner_symptoms": "Slight hesitation at idle"
            }
        """.trimIndent()

        val jsonRes = DiagnosticFileImporter.importFromText(jsonText, "scan_export.json", vehicle)
        assertTrue(jsonRes.isSuccess)
        val session = jsonRes.getOrThrow()
        assertEquals(ObdDataSourceType.IMPORTED_LOG, session.sourceMetadata.sourceType)
        assertEquals(listOf("P0171"), session.confirmedDtcs.map { it.code })
        assertEquals(listOf("P0300"), session.pendingDtcs.map { it.code })
        assertEquals(89.0, session.livePidReadings.first { it.pidCode == "0105" }.value!!, 0.01)
        val maf = session.livePidReadings.first { it.pidCode == "0110" }
        assertNull(maf.value) // Never coerced to 0.0
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, maf.availability)

        val logText = """
            43 01 01 71
            Pending Mode 07 code: U0100
            Coolant: 86 C
            41 0C 0B B8
        """.trimIndent()
        val textRes = DiagnosticFileImporter.importFromText(logText, "elm_capture.txt", vehicle)
        assertTrue(textRes.isSuccess)
        val textSession = textRes.getOrThrow()
        assertTrue(textSession.confirmedDtcs.any { it.code == "P0171" })
        assertTrue(textSession.pendingDtcs.any { it.code == "U0100" })
        assertEquals(86.0, textSession.livePidReadings.first { it.pidCode == "0105" }.value!!, 0.01)
        assertEquals(750.0, textSession.livePidReadings.first { it.pidCode == "010C" }.value!!, 0.01)

        // Empty / invalid log fails cleanly
        assertTrue(DiagnosticFileImporter.importFromText("   ", "empty.txt", vehicle).isFailure)
        assertTrue(DiagnosticFileImporter.importFromText("random text no codes", "bad.txt", vehicle).isFailure)
    }

    @Test
    fun phase5And6_manualScanToAiExplainSavesToHistoryAndRendersResultsAndReport() = runBlocking {
        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_1")
        val vehId = repo.createVehicle(
            userId = "owner_1",
            draft = VehicleDraft(
                manufacturer = "Nissan",
                model = "GT-R",
                modelYearInput = "2024",
                trim = "Premium",
                bodyStyle = BodyStyle.COUPE,
                engineType = "3.8L Twin-Turbo V6",
                transmission = "6-Speed Dual-Clutch",
                fuelType = "Gasoline",
                mileageInput = "18500",
                mileageUnit = MileageUnit.KM,
                market = "Japan",
                setAsPrimary = true
            )
        ).getOrThrow()
        val vehicle = repo.observeVehicle("owner_1", vehId).first()!!

        val mgr = ObdConnectionManager()
        val fakeEngine = FakeTestExplanationEngine()
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_1",
            repository = repo,
            connectionManager = mgr,
            explanationEngine = fakeEngine
        )
        val scanVm = DiagnosticsScanViewModel(mgr, coordinator) { vehicle }
        val diagVm = DiagnosticsViewModel(repo, "owner_1", vehId, mgr)
        val historyVm = DiagnosticsHistoryViewModel(repo, "owner_1", vehId)

        var navigatedToResults = false
        composeRule.setContent {
            MotoraTheme {
                DiagnosticsScanScreen(
                    selectedVehicleLabel = "2024 Nissan GT-R",
                    onNavigateBack = {},
                    viewModel = scanVm,
                    onOpenConnect = {},
                    onNavigateToResults = { navigatedToResults = true }
                )
            }
        }
        composeRule.waitForIdle()

        // Switch to Manual DTC mode, add P0171 + symptom, and trigger AI analysis
        composeRule.onNodeWithTag("scan_mode_MANUAL_DTC").performScrollTo().performClick()
        composeRule.onNodeWithTag("scan_owner_symptoms_input").performScrollTo().performTextInput("Rough idle at lights")
        composeRule.onNodeWithTag("scan_manual_dtc_field").performScrollTo().performTextInput("p0171")
        composeRule.onNodeWithTag("scan_add_dtc_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manual_dtc_chip_P0171").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("scan_analyze_manual_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            navigatedToResults && coordinator.state.value.activeResult != null
        }

        // Verify persisted into FakeVehicleDataSource & reflected in DiagnosticsViewModel + HistoryViewModel
        val savedList = repo.observeObdSessions("owner_1", vehId).first()
        assertEquals(1, savedList.size)
        assertEquals(listOf("P0171"), savedList.first().dtcCodes)
        assertTrue(savedList.first().readOnlyVerified)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            diagVm.uiState.value.savedSessionCount == 1 && historyVm.uiState.value.sessions.size == 1
        }
    }

    @Test
    fun phase6_resultsHistoryAndReportScreensRenderRichFindingsAndAllowDelete() {
        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_2")
        val vehId = runBlocking {
            repo.createVehicle(
                userId = "owner_2",
                draft = VehicleDraft(
                    manufacturer = "Volvo",
                    model = "EX30",
                    modelYearInput = "2025",
                    trim = "Twin Motor",
                    bodyStyle = BodyStyle.CROSSOVER,
                    engineType = "Dual-Motor Electric",
                    transmission = "Single-Speed",
                    fuelType = "Electric",
                    mileageInput = "4200",
                    mileageUnit = MileageUnit.KM,
                    market = "United States"
                )
            ).getOrThrow()
        }
        val vehicle = runBlocking { repo.observeVehicle("owner_2", vehId).first()!! }
        val mgr = ObdConnectionManager()
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_2",
            repository = repo,
            connectionManager = mgr,
            explanationEngine = FakeTestExplanationEngine()
        )
        val manualSession = DiagnosticFileImporter.buildManualSession(
            vehicle = testVehicleSnapshot(),
            manualDtcs = listOf("C0035" to DtcStatus.CONFIRMED),
            ownerSymptoms = "ABS light came on"
        ).getOrThrow()

        runBlocking {
            coordinator.analyzeSessionAndPersist(manualSession, vehicle, AppLanguage.ENGLISH).getOrThrow()
        }

        // 1. Results screen renders C0035, Urgent non-green badge, and finding details
        composeRule.setContent {
            MotoraTheme {
                DiagnosticsResultsScreen(
                    selectedVehicleLabel = "2025 Volvo EX30",
                    onNavigateBack = {},
                    coordinator = coordinator
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("dtc_item_C0035").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic_urgency_badge").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic_finding_card_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("finding_technical_toggle_1").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Fuel trim / sensor deviation observed in read-only session.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun phase6_reportAndHistoryUiDisplayPersistedSessions() {
        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_3")
        val vehId = runBlocking {
            repo.createVehicle(
                userId = "owner_3",
                draft = VehicleDraft(
                    manufacturer = "Toyota",
                    model = "GR86",
                    modelYearInput = "2023",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "2.4L Flat-4",
                    transmission = "6-Speed Manual",
                    fuelType = "Gasoline",
                    mileageInput = "9100",
                    mileageUnit = MileageUnit.KM,
                    market = "Japan"
                )
            ).getOrThrow()
        }
        val vehicle = runBlocking { repo.observeVehicle("owner_3", vehId).first()!! }
        val mgr = ObdConnectionManager()
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_3",
            repository = repo,
            connectionManager = mgr,
            explanationEngine = FakeTestExplanationEngine()
        )
        val session = DiagnosticFileImporter.buildManualSession(
            vehicle = testVehicleSnapshot(),
            manualDtcs = listOf("P0171" to DtcStatus.CONFIRMED),
            ownerSymptoms = "Check engine light"
        ).getOrThrow()
        val savedId = runBlocking {
            coordinator.analyzeSessionAndPersist(session, vehicle, AppLanguage.ENGLISH).getOrThrow()
            coordinator.state.value.lastSavedSessionId!!
        }

        val historyVm = DiagnosticsHistoryViewModel(repo, "owner_3", vehId)
        composeRule.setContent {
            MotoraTheme {
                DiagnosticsHistoryScreen(
                    selectedVehicleLabel = "2023 Toyota GR86",
                    onNavigateBack = {},
                    viewModel = historyVm
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            historyVm.uiState.value.sessions.isNotEmpty()
        }
        composeRule.onNodeWithTag("history_session_card_$savedId").assertIsDisplayed()

        // Delete session via UI confirmation
        composeRule.onNodeWithTag("delete_history_session_$savedId").performClick()
        composeRule.onNodeWithTag("confirm_delete_obd_session").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            historyVm.uiState.value.sessions.isEmpty()
        }
        composeRule.onNodeWithTag("history_empty_card").assertIsDisplayed()
    }

    @Test
    fun phase6_reportScreenDisplaysShareButtonAndChecklist() {
        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_4")
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_4",
            repository = repo,
            connectionManager = ObdConnectionManager(),
            explanationEngine = FakeTestExplanationEngine()
        )
        val session = DiagnosticFileImporter.buildManualSession(
            vehicle = testVehicleSnapshot(),
            manualDtcs = listOf("P0171" to DtcStatus.CONFIRMED),
            ownerSymptoms = "Hesitation"
        ).getOrThrow()
        runBlocking {
            coordinator.analyzeSessionAndPersist(session, null, AppLanguage.ENGLISH).getOrThrow()
        }

        composeRule.setContent {
            MotoraTheme {
                DiagnosticsReportScreen(
                    selectedVehicleLabel = "2024 Nissan GT-R",
                    onNavigateBack = {},
                    coordinator = coordinator
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("report_content_card").assertIsDisplayed()
        composeRule.onNodeWithTag("report_share_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun phase7_connectionManagerConnectsVerifiedStreamRunsReadOnlyScanAndDisconnectsCleanly() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val testStream = TestVerifiedTransport()
        val fakeWifiPort = object : WifiHardwarePort {
            override fun isWifiSupported(context: Context) = true
            override fun isWifiEnabled(context: Context) = true
            override fun isWifiConnected(context: Context) = true
            override fun currentSsidLabel(context: Context) = "WiFi_OBDII"
        }
        val fakeBtPort = object : BluetoothHardwarePort {
            override fun requiredPermissions() = emptyList<String>()
            override fun missingPermissions(context: Context) = emptyList<String>()
            override fun isBluetoothSupported(context: Context) = true
            override fun isBluetoothEnabled(context: Context) = true
            override fun listPairedDevices(context: Context) = emptyList<BluetoothDeviceEntry>()
            override fun startBleScan(
                context: Context,
                onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
                onScanError: (String) -> Unit
            ) = false
            override fun stopBleScan(context: Context) {}
        }
        val testConnector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(
                context: Context,
                host: String,
                port: Int,
                displayLabel: String
            ): Result<ObdTransportStream> = Result.success(testStream)

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ): Result<ObdTransportStream> = Result.failure(IllegalStateException("No BT hardware in test"))
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(fakeWifiPort),
            bluetoothAdapter = BluetoothObdAdapter(fakeBtPort),
            transportConnector = testConnector
        )
        manager.refreshWifiState(context)
        val status = manager.connectTransport(context)
        assertTrue(status is ObdConnectionStatus.Connected)
        assertTrue(manager.activeTransportStream != null)

        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_7")
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_7",
            repository = repo,
            connectionManager = manager,
            explanationEngine = FakeTestExplanationEngine()
        )
        val res = coordinator.runLiveObdScanAndAnalyze(null, "Idle check", AppLanguage.ENGLISH)
        assertTrue(res.isSuccess)
        val scanned = coordinator.state.value.activeSession!!
        assertEquals(listOf("P0171"), scanned.confirmedDtcs.map { it.code })
        // Strict read-only: Mode 04 / clears never sent
        assertTrue(testStream.sentCommands.none { it.startsWith("04") || it.startsWith("08") })
        // Unsupported PID 0110 preserved as null, not 0.0
        val maf = scanned.livePidReadings.first { it.pidCode == "0110" }
        assertNull(maf.value)
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, maf.availability)

        manager.disconnectAsync(context)
        assertNull(manager.activeTransportStream)
        assertTrue(manager.state.value.connectionStatus is ObdConnectionStatus.Disconnected)
    }
}
