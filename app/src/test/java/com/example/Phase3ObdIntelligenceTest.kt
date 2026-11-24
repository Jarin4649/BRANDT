package com.example

import com.example.data.ai.DiagnosticConfidenceLevel
import com.example.data.ai.DiagnosticEvidenceEvaluator
import com.example.data.ai.DiagnosticReportGenerator
import com.example.data.ai.DiagnosticResultSchemaValidator
import com.example.data.ai.DiagnosticSafetyAssessor
import com.example.data.ai.DiagnosticUrgencyLevel
import com.example.data.ai.FirebaseEverydayExplanationEngine
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.DataReliabilityLevel
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.DtcStatus
import com.example.data.obd.DtcSystemCategory
import com.example.data.obd.Elm327Parser
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdDataSourceType
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.ObdProtocolPreference
import com.example.data.obd.ObdSourceMetadata
import com.example.data.obd.ObdTransportStream
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.obd.PidAvailability
import com.example.data.obd.ReadOnlyObdClient
import com.example.data.obd.ReadOnlyObdCommandGuard
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 3 Unit Tests using clearly labeled SIMULATED ELM327/OBD-II responses.
 * These simulated fixtures test parsing, read-only guards, missing-value semantics,
 * DTC interpretation, safety enforcement, evidence auditing, and AI JSON schema validation.
 * They MUST NEVER be presented as real vehicle diagnostic scans.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3ObdIntelligenceTest {

    /**
     * Clearly labeled simulated transport stream strictly for unit tests.
     */
    private class SimulatedTestObdTransport(
        private val simulatedResponses: Map<String, String>,
        override val isConnected: Boolean = true,
        override val adapterLabel: String = "SIMULATED_TEST_ELM327_FIXTURE",
        override val sourceType: ObdDataSourceType = ObdDataSourceType.WIFI_OBD,
        override val method: ObdConnectionMethod = ObdConnectionMethod.WIFI,
        override val bluetoothArchitecture: BluetoothArchitecture? = null
    ) : ObdTransportStream {
        val sentCommands = mutableListOf<String>()

        override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
            sentCommands += command
            val reply = simulatedResponses[command]
                ?: return Result.failure(IllegalStateException("Simulated timeout for $command"))
            return Result.success(reply)
        }

        override suspend fun close() = Unit
    }

    private fun sampleVehicle(completeSpecs: Boolean = true) = ObdVehicleSnapshot(
        vehicleId = "veh_test_1",
        manufacturer = "Toyota",
        model = "Camry",
        modelYear = 2020,
        trim = if (completeSpecs) "SE 2.5L" else null,
        engineType = if (completeSpecs) "2.5L Inline-4 Naturally Aspirated" else null,
        engineDisplacement = if (completeSpecs) "2487 cc" else null,
        transmission = if (completeSpecs) "8-Speed Automatic" else null,
        fuelType = if (completeSpecs) "Petrol / Gasoline" else null,
        mileage = if (completeSpecs) 68_400L else null,
        mileageUnit = "km",
        market = if (completeSpecs) "United States" else null
    )

    @Test
    fun `read-only guard blocks DTC clearing, ECU reset, flashing, and actuator commands`() {
        val forbiddenCommands = listOf("04", "0400", "08", "0801", "1101", "14FFFFFF", "2E1234", "2F0102", "3101FF", "34", "ATPPFF")
        forbiddenCommands.forEach { cmd ->
            assertFalse("Command $cmd must be blocked", ReadOnlyObdCommandGuard.isCommandAllowed(cmd))
        }

        val allowedCommands = listOf("ATI", "ATRV", "ATSP0", "ATDP", "0100", "010C", "020500", "03", "07", "0902")
        allowedCommands.forEach { cmd ->
            assertTrue("Read-only command $cmd should be allowed", ReadOnlyObdCommandGuard.isCommandAllowed(cmd))
        }

        runBlocking {
            val transport = SimulatedTestObdTransport(mapOf("04" to "44"))
            val client = ReadOnlyObdClient()
            val result = client.executeReadOnlyCommand(transport, "04")
            assertTrue(result.isFailure)
            assertTrue("Transport must never receive Mode 04", transport.sentCommands.isEmpty())
        }
    }

    @Test
    fun `elm327 parser handles valid diagnostic responses, supported PID bitmasks, and raw trace preservation`() {
        // 0100 bitmask: PID 0104, 0105, 0106, 0107, 010C, 010D, 010F, 0110 -> bytes: 1C 1B 00 00
        // Bit 4=0x10 (04), 5=0x08 (05), 6=0x04 (06), 7=0x02 (07) -> byte0 = 0x1E
        // Bit 12=0x10 (0C), 13=0x08 (0D), 15=0x02 (0F), 16=0x01 (10) -> byte1 = 0x1B
        val supported = Elm327Parser.parseSupportedPidBitmask("0100", "41 00 1E 1B 00 00\r>")
        assertTrue(supported.containsAll(listOf("0104", "0105", "0106", "0107", "010C", "010D", "010F", "0110")))
        assertFalse(supported.contains("0111"))

        val coolant = Elm327Parser.parsePidResponse("0105", "41 05 82\r>") // 130 - 40 = 90 C
        assertTrue(coolant.isAvailable)
        assertEquals(90.0, coolant.value!!, 0.01)
        assertEquals("°C", coolant.unit)
        assertEquals("41 05 82\r>", coolant.rawHex)

        val rpm = Elm327Parser.parsePidResponse("010C", "41 0C 0B B8\r>") // (11*256 + 184)/4 = 3000/4 = 750 rpm
        assertTrue(rpm.isAvailable)
        assertEquals(750.0, rpm.value!!, 0.01)
        assertEquals("rpm", rpm.unit)

        val maf = Elm327Parser.parsePidResponse("0110", "41 10 01 9A") // (256 + 154)/100 = 4.10 g/s
        assertTrue(maf.isAvailable)
        assertEquals(4.10, maf.value!!, 0.01)
        assertEquals("g/s", maf.unit)

        val dtcResult = Elm327Parser.parseDtcResponse("43 02 01 71 03 00\r>", DtcStatus.CONFIRMED)
        assertNull(dtcResult.errorNote)
        assertEquals(listOf("P0171", "P0300"), dtcResult.dtcs.map { it.code })
    }

    @Test
    fun `invalid responses and unsupported parameters strictly return null value rather than zero`() {
        val noData = Elm327Parser.parsePidResponse("0110", "NO DATA\r>")
        assertFalse(noData.isAvailable)
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, noData.availability)
        assertNull("Unsupported PID must be null, never 0.0", noData.value)

        val questionMark = Elm327Parser.parsePidResponse("0142", "?")
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, questionMark.availability)
        assertNull(questionMark.value)

        val truncated = Elm327Parser.parsePidResponse("010C", "41 0C 0A")
        assertEquals(PidAvailability.MALFORMED_OR_TIMEOUT, truncated.availability)
        assertNull("Malformed/truncated PID must be null, never 0.0", truncated.value)

        val canError = Elm327Parser.parseDtcResponse("CAN ERROR\r>", DtcStatus.CONFIRMED)
        assertTrue(canError.dtcs.isEmpty())
        assertNotNull(canError.errorNote)
    }

    @Test
    fun `read-only client executes full scan with missing sensor data preserved as null`() = runBlocking {
        // Supports 0105, 010C, 010D, 0106, 0107, 0142? Note: omit 0110 & 0111 from bitmask
        // 04=0,05=1,06=1,07=1 -> 0x0E; 0C=1,0D=1,0F=1 -> 0x1A; 10.. -> 0x00; -> 0100: 41 00 0E 1A 00 01 (supports 0120)
        val transport = SimulatedTestObdTransport(
            simulatedResponses = mapOf(
                "ATE0" to "OK",
                "ATL0" to "OK",
                "ATS0" to "OK",
                "ATSP0" to "OK",
                "ATDP" to "AUTO, ISO 15765-4 (CAN 11/500)",
                "0100" to "41 00 0E 1A 00 01",
                "0120" to "41 20 00 00 00 00", // does not support 0142
                "03" to "43 02 01 71 04 20",
                "07" to "47 01 03 00",
                "0105" to "41 05 80", // 88 °C
                "010C" to "41 0C 0C 80", // 800 rpm
                "010D" to "41 0D 00", // valid 0 km/h idle
                "0106" to "41 06 90", // +12.5% STFT
                "0107" to "41 07 96", // +17.1875% LTFT
                "010F" to "41 0F 44", // 28 °C IAT
                "020500" to "42 05 00 81",
                "020C00" to "NO DATA",
                "020D00" to "NO DATA",
                "020400" to "NO DATA",
                "020600" to "NO DATA",
                "020700" to "NO DATA"
            )
        )

        val client = ReadOnlyObdClient()
        val scanResult = client.runReadOnlyDiagnosticScan(
            transport = transport,
            vehicle = sampleVehicle(completeSpecs = true),
            preferredProtocol = ObdProtocolPreference.AUTO,
            ownerSymptoms = "Rough idle when stopped at traffic lights"
        )
        assertTrue(scanResult.isSuccess)
        val session = scanResult.getOrThrow().copy(isSimulatedTestData = true)

        assertEquals(listOf("P0171", "P0420"), session.confirmedDtcs.map { it.code })
        assertEquals(listOf("P0300"), session.pendingDtcs.map { it.code })

        val mafReading = session.livePidReadings.first { it.pidCode == "0110" }
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, mafReading.availability)
        assertNull("Unsupported MAF PID must remain null", mafReading.value)

        val voltageReading = session.livePidReadings.first { it.pidCode == "0142" }
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, voltageReading.availability)
        assertNull(voltageReading.value)

        val coolantReading = session.livePidReadings.first { it.pidCode == "0105" }
        assertTrue(coolantReading.isAvailable)
        assertEquals(88.0, coolantReading.value!!, 0.01)

        assertFalse("Mode 04 must never be queued during scan", transport.sentCommands.contains("04"))
    }

    @Test
    fun `dtc interpreter decodes multiple codes separately from AI and never treats code as proven part failure`() {
        val dtcs = listOf(
            DiagnosticTroubleCode("P0171", DtcStatus.CONFIRMED, timestampEpochMs = 1L),
            DiagnosticTroubleCode("P0300", DtcStatus.PENDING, timestampEpochMs = 1L),
            DiagnosticTroubleCode("P1456", DtcStatus.CONFIRMED, timestampEpochMs = 1L), // OEM-specific
            DiagnosticTroubleCode("X999Z", DtcStatus.UNKNOWN, timestampEpochMs = 1L)
        )

        val interpreted = dtcs.map { DtcInterpreter.interpret(it, sampleVehicle()) }
        assertEquals("System Too Lean (Bank 1)", interpreted[0].standardDefinition)
        assertTrue(interpreted[0].componentFailureDisclaimer.contains("does NOT prove", ignoreCase = true))

        assertEquals(DtcSystemCategory.POWERTRAIN, interpreted[1].systemCategory)
        assertTrue(interpreted[2].isManufacturerSpecificCode)
        assertFalse(interpreted[3].isValidFormat)
    }

    @Test
    fun `uncertain diagnostic findings flag missing information and never report high confidence`() {
        val sparseSession = ObdDiagnosticSession(
            sessionId = "sim_uncertain_1",
            vehicle = sampleVehicle(completeSpecs = false),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.MANUAL_DTC_ENTRY,
                adapterOrFileLabel = "Simulated Manual Entry",
                protocolLabel = null,
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.LOW_MANUAL_OR_UNVERIFIED,
                capturedAtEpochMs = 1_000L
            ),
            confirmedDtcs = listOf(
                DiagnosticTroubleCode("P1604", DtcStatus.CONFIRMED, timestampEpochMs = 1_000L)
            ),
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = emptyList(),
            ownerSymptoms = null,
            capturedAtEpochMs = 1_000L,
            isSimulatedTestData = true
        )

        val interpretations = sparseSession.allDtcs.map { DtcInterpreter.interpret(it, sparseSession.vehicle) }
        val audit = DiagnosticEvidenceEvaluator.evaluate(sparseSession, interpretations)
        val safety = DiagnosticSafetyAssessor.assess(sparseSession, audit)

        assertFalse(audit.hasSufficientInformation)
        assertEquals(DiagnosticConfidenceLevel.LOW, audit.baselineConfidence)
        assertTrue(audit.missingInformation.isNotEmpty())
        assertTrue(audit.reasonsForUncertainty.isNotEmpty())
        assertTrue(
            "Insufficient evidence must prevent a pure low-urgency green/Information rating",
            safety.minimumUrgencyFloor.severityRank >= DiagnosticUrgencyLevel.MONITOR.severityRank
        )
    }

    @Test
    fun `safety-critical findings escalate urgency to Urgent with stop-driving guidance and scan disclaimer`() {
        val criticalSession = ObdDiagnosticSession(
            sessionId = "sim_critical_1",
            vehicle = sampleVehicle(completeSpecs = true),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.BLUETOOTH_OBD,
                adapterOrFileLabel = "Simulated BLE Scanner",
                protocolLabel = "CAN 11/500",
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.HIGH_VERIFIED_HARDWARE,
                capturedAtEpochMs = 2_000L
            ),
            confirmedDtcs = listOf(
                DiagnosticTroubleCode("C0035", DtcStatus.CONFIRMED, timestampEpochMs = 2_000L)
            ),
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = listOf(
                Elm327Parser.parsePidResponse("0105", "41 05 9D", 2_000L) // 157 - 40 = 117 °C overheat!
            ),
            ownerSymptoms = "Brake pedal feels soft and temperature gauge spiked with steam",
            capturedAtEpochMs = 2_000L,
            isSimulatedTestData = true
        )

        val audit = DiagnosticEvidenceEvaluator.evaluate(
            criticalSession,
            criticalSession.allDtcs.map { DtcInterpreter.interpret(it, criticalSession.vehicle) }
        )
        val safety = DiagnosticSafetyAssessor.assess(criticalSession, audit)

        assertTrue(safety.isSafetyCritical)
        assertTrue(safety.requiresImmediateStopWhenSafe)
        assertEquals(DiagnosticUrgencyLevel.URGENT, safety.minimumUrgencyFloor)
        assertTrue(safety.stopDrivingGuidance.any { it.contains("stop", ignoreCase = true) || it.contains("pull over", ignoreCase = true) })
        assertTrue(safety.driveabilityDisclaimer.contains("cannot guarantee", ignoreCase = true))
    }

    @Test
    fun `strict schema validator parses valid Section 8 JSON, rejects invalid or fabricated payloads, and generates report`() {
        val session = ObdDiagnosticSession(
            sessionId = "sim_schema_1",
            vehicle = sampleVehicle(completeSpecs = true),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.WIFI_OBD,
                adapterOrFileLabel = "Simulated Wi-Fi Adapter",
                protocolLabel = "ISO 15765-4 CAN",
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.MODERATE_PARTIAL_DATA,
                capturedAtEpochMs = 3_000L
            ),
            confirmedDtcs = listOf(DiagnosticTroubleCode("P0171", DtcStatus.CONFIRMED, timestampEpochMs = 3_000L)),
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = listOf(
                Elm327Parser.parsePidResponse("0105", "41 05 82", 3_000L),
                Elm327Parser.parsePidResponse("0107", "41 07 96", 3_000L)
            ),
            ownerSymptoms = "Slight hesitation on acceleration",
            capturedAtEpochMs = 3_000L,
            isSimulatedTestData = true
        )

        val validJson = """
            {
              "overall_summary": "Your vehicle recorded a lean air-fuel code (P0171). This means the engine computer noticed more air or less fuel than expected and has been adding extra fuel to compensate.",
              "findings": [
                {
                  "finding_title": "Engine Air-Fuel Balance Running Lean (P0171)",
                  "what_happened": "The engine computer detected too much oxygen in the exhaust compared to fuel injected.",
                  "what_it_means": "Like breathing through an unsealed straw, extra unmetered air may be sneaking into the engine, or fuel delivery may be slightly weak.",
                  "plain_language_explanation": "Your engine computer detected too much oxygen in the exhaust and is compensating by adding extra fuel.",
                  "technical_explanation": "Bank 1 Long Term Fuel Trim (PID 0107) reached +17.19% alongside confirmed P0171, exceeding closed-loop adaptive threshold.",
                  "possible_causes": [
                    "Cracked vacuum hose or intake boot downstream of the MAF sensor",
                    "Dirty airflow sensor",
                    "Fuel delivery pressure or injector restriction"
                  ],
                  "everyday_impact": {
                    "normal_operation": "May cause slight hesitation or rough idle.",
                    "driving_comfort": "Occasional idle vibration while stopped.",
                    "fuel_consumption": "Supported positive fuel trim (+17.2% LTFT) indicates the engine is injecting extra fuel, which can reduce fuel economy.",
                    "vehicle_reliability": "Continuing to drive long-term while lean can stress exhaust components.",
                    "safety_note": "Drive cautiously if power hesitates during merging."
                  },
                  "urgency_level": "Service Recommended",
                  "urgency_reason": "Sustained positive fuel trim (+17.2%) combined with a confirmed P0171 code warrants timely mechanic inspection.",
                  "safe_observations": [
                    "Note whether the Check Engine Light stays steady or ever flashes.",
                    "Listen for any hissing sound near the engine bay only while parked safely with the hood closed or at idle from outside."
                  ],
                  "recommended_next_steps": {
                    "observe_safely": ["Watch if hesitation worsens on hills or AC on."],
                    "record_for_mechanic": ["Record when hesitation occurs (cold start vs warmed up)."],
                    "mechanic_required": ["Perform smoke leak test and measure fuel rail pressure."],
                    "stop_driving_situations": ["Pull over safely if the Check Engine Light begins flashing or the engine stalls."]
                  },
                  "professional_inspection_required": true,
                  "supporting_evidence": {
                    "diagnostic_codes_used": ["P0171"],
                    "sensor_values_used": ["Engine Coolant Temperature [0105]: 90 °C", "Long Term Fuel Trim (Bank 1) [0107]: 17.19 %"],
                    "vehicle_information_used": ["2020 Toyota Camry SE 2.5L"]
                  },
                  "missing_information": ["Freeze Frame snapshot unavailable"],
                  "confidence_level": "Moderate",
                  "reasons_for_uncertainty": ["Requires physical smoke test to isolate leak vs sensor bias"],
                  "knowledge_sources": ["SAE J2012 / ISO 15031-6 Standard DTC Definitions"]
                }
              ]
            }
        """.trimIndent()

        val validOutcome = DiagnosticResultSchemaValidator.parseAndValidateJson(validJson, session)
        assertTrue("Valid Section 8 JSON should pass: ${validOutcome.errors}", validOutcome.isValid)
        assertEquals(1, validOutcome.findings.size)
        assertEquals(DiagnosticUrgencyLevel.SERVICE_RECOMMENDED, validOutcome.findings[0].urgencyLevel)

        // Verify safeguards + report generation
        val audit = DiagnosticEvidenceEvaluator.evaluate(
            session,
            session.allDtcs.map { DtcInterpreter.interpret(it, session.vehicle) }
        )
        val safety = DiagnosticSafetyAssessor.assess(session, audit)
        val engine = FirebaseEverydayExplanationEngine()
        val guarded = engine.applyDeterministicSafeguards(validOutcome.findings, audit, safety)
        val resultObj = com.example.data.ai.EverydayDiagnosticResult(
            sessionId = session.sessionId,
            generatedAtEpochMs = 3_100L,
            overallSummary = validOutcome.overallSummary,
            overallUrgencyLevel = guarded.first().urgencyLevel,
            driveabilityCautionNotice = safety.driveabilityDisclaimer,
            hasSufficientEvidenceForFullAssessment = audit.hasSufficientInformation,
            findings = guarded,
            readOnlyConfirmed = true,
            safetyAssessment = safety
        )
        val report = DiagnosticReportGenerator.generate(session, resultObj)
        assertTrue(report.shareablePlainText.contains("P0171"))
        assertTrue(report.readOnlyVerificationLine.contains("Read-Only Verified"))

        // Fabricated DTC must be rejected
        val fabricatedJson = validJson.replace("\"diagnostic_codes_used\": [\"P0171\"]", "\"diagnostic_codes_used\": [\"P0171\", \"P0999\"]")
        val fabricatedOutcome = DiagnosticResultSchemaValidator.parseAndValidateJson(fabricatedJson, session)
        assertFalse("Fabricated DTC P0999 must fail validation", fabricatedOutcome.isValid)

        // Invalid urgency enum must be rejected
        val badEnumJson = validJson.replace("\"Service Recommended\"", "\"Green Safe\"")
        val badEnumOutcome = DiagnosticResultSchemaValidator.parseAndValidateJson(badEnumJson, session)
        assertFalse("Invalid urgency enum must fail validation", badEnumOutcome.isValid)
    }
}
