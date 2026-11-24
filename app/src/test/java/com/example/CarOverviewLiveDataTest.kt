package com.example

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.ai.DiagnosticStatusAiExplainer
import com.example.data.ai.DiagnosticStatusPlainExplanation
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleDraft
import com.example.data.obd.CircularRingStyle
import com.example.data.obd.DataReliabilityLevel
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcStatus
import com.example.data.obd.EfficiencyBand
import com.example.data.obd.Elm327Parser
import com.example.data.obd.LiveDataCategoryId
import com.example.data.obd.LiveVehicleDataMapper
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdDataSourceType
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.ObdMonitorStatus
import com.example.data.obd.ObdPidReading
import com.example.data.obd.ObdSourceMetadata
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.obd.PidAvailability
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.ui.overview.VehicleOverviewScreen
import com.example.ui.overview.VehicleOverviewUiState
import com.example.ui.overview.VehicleOverviewViewModel
import com.example.ui.theme.MotoraTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CarOverviewLiveDataTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testVehicleSnapshot() = ObdVehicleSnapshot(
        vehicleId = "veh_gtr",
        manufacturer = "Nissan",
        model = "GT-R",
        modelYear = 2024,
        trim = "Premium",
        engineType = "3.8L Twin-Turbo V6",
        engineDisplacement = "3.8L",
        transmission = "6-Speed Dual-Clutch",
        fuelType = "Petrol / Gasoline",
        mileage = 18500L,
        mileageUnit = "km",
        market = "Japan"
    )

    private fun pidReading(code: String, name: String, value: Double?, unit: String): ObdPidReading {
        return ObdPidReading(
            pidCode = code,
            name = name,
            value = value,
            unit = unit,
            availability = if (value != null) PidAvailability.AVAILABLE else PidAvailability.UNSUPPORTED_BY_VEHICLE,
            timestampEpochMs = 1_700_000_000_000L
        )
    }

    @Test
    fun `efficiency bands strictly enforce 0-49 RED, 50-69 YELLOW, and 70-100 GREEN`() {
        assertEquals(EfficiencyBand.RED, EfficiencyBand.fromPercent(0))
        assertEquals(EfficiencyBand.RED, EfficiencyBand.fromPercent(49))
        assertEquals(EfficiencyBand.YELLOW, EfficiencyBand.fromPercent(50))
        assertEquals(EfficiencyBand.YELLOW, EfficiencyBand.fromPercent(69))
        assertEquals(EfficiencyBand.GREEN, EfficiencyBand.fromPercent(70))
        assertEquals(EfficiencyBand.GREEN, EfficiencyBand.fromPercent(100))
    }

    @Test
    fun `circular gauge renders half arc at 50, full RED ring above 100, and empty when disconnected`() {
        val now = 1_700_000_000_000L
        val session = ObdDiagnosticSession(
            sessionId = "ecu_test_1",
            vehicle = testVehicleSnapshot(),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.WIFI_OBD,
                adapterOrFileLabel = "WiFi_OBDII 192.168.0.10",
                protocolLabel = "CAN 11/500",
                connectionMethod = ObdConnectionMethod.WIFI,
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.HIGH_VERIFIED_HARDWARE,
                capturedAtEpochMs = now
            ),
            confirmedDtcs = emptyList(),
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = listOf(
                pidReading("010D", "Vehicle Speed", 140.0, "km/h"),
                pidReading("010C", "Engine Speed (RPM)", 2100.0, "rpm"),
                pidReading("0104", "Calculated Engine Load", 50.0, "%"),
                pidReading("0111", "Throttle Position", 50.0, "%"),
                pidReading("0105", "Engine Coolant Temperature", 88.0, "°C"),
                pidReading("0106", "STFT", 1.5, "%"),
                pidReading("0107", "LTFT", 0.8, "%"),
                pidReading("0142", "Control Module Voltage", 14.1, "V")
            ),
            supportedPids = setOf("010D", "010C", "0104", "0111", "0105", "0106", "0107", "0142"),
            capturedAtEpochMs = now,
            isSimulatedTestData = false
        )

        val mapped = LiveVehicleDataMapper.map(session = session, isAdapterConnected = true, connectedAdapterLabel = "OBDLink EX")
        val driving = mapped.categories.first { it.categoryId == LiveDataCategoryId.DRIVING_ENGINE }

        val speedMetric = driving.metrics.first { it.id == "vehicle_speed" }
        assertEquals(CircularRingStyle.FULL_RED, speedMetric.ringStyle)
        assertEquals(1.0f, speedMetric.ringSweepFraction, 0.001f)

        val calcLoadMetric = driving.metrics.first { it.id == "calculated_engine_load" }
        assertEquals(CircularRingStyle.PROPORTIONAL_BLUE, calcLoadMetric.ringStyle)
        assertEquals(0.5f, calcLoadMetric.ringSweepFraction, 0.001f) // 50% -> half circle

        // Disconnected state never fakes ECU numbers and stays at 00% (RED band)
        val disconnected = LiveVehicleDataMapper.map(session = null, isAdapterConnected = false)
        assertTrue(disconnected.categories.all { it.efficiencyPercent == 0 && it.band == EfficiencyBand.RED })
        val discSpeed = disconnected.categories.first().metrics.first { it.id == "vehicle_speed" }
        assertEquals(CircularRingStyle.UNAVAILABLE_EMPTY, discSpeed.ringStyle)
        assertEquals(0.0f, discSpeed.ringSweepFraction, 0.001f)
    }

    @Test
    fun `elm327 parser decodes extended overview PIDs accurately`() {
        val fuelPressure = Elm327Parser.parsePidResponse("010A", "41 0A 78") // 120 * 3 = 360 kPa
        assertEquals(360.0, fuelPressure.value!!, 0.01)

        val oilTemp = Elm327Parser.parsePidResponse("015C", "41 5C 84") // 132 - 40 = 92 C
        assertEquals(92.0, oilTemp.value!!, 0.01)

        val baro = Elm327Parser.parsePidResponse("0133", "41 33 65") // 101 kPa
        assertEquals(101.0, baro.value!!, 0.01)

        val absLoad = Elm327Parser.parsePidResponse("0143", "41 43 00 FF") // 100%
        assertEquals(100.0, absLoad.value!!, 0.01)
    }

    @Test
    fun `efficiency color bands reflect healthy green vs degraded yellow vs critical red`() {
        val now = 1_700_000_000_000L
        val criticalSession = ObdDiagnosticSession(
            sessionId = "ecu_crit",
            vehicle = testVehicleSnapshot(),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.BLUETOOTH_OBD,
                adapterOrFileLabel = "OBDLink MX+",
                protocolLabel = "CAN",
                connectionMethod = ObdConnectionMethod.BLUETOOTH,
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.HIGH_VERIFIED_HARDWARE,
                capturedAtEpochMs = now
            ),
            confirmedDtcs = listOf(
                DiagnosticTroubleCode("P0115", DtcStatus.CONFIRMED, timestampEpochMs = now),
                DiagnosticTroubleCode("P0171", DtcStatus.CONFIRMED, timestampEpochMs = now)
            ),
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = listOf(
                pidReading("0105", "Engine Coolant Temperature", 115.0, "°C"), // Overheat -55 & P0115 -25 -> 20% RED
                pidReading("0106", "STFT", 12.0, "%"), // -20 & P0171 -25 -> 55% YELLOW
                pidReading("0142", "Voltage", 13.8, "V"),
                pidReading("010D", "Vehicle Speed", 60.0, "km/h") // no misfire DTC -> high efficiency GREEN
            ),
            supportedPids = setOf("0105", "0106", "0142", "010D"),
            capturedAtEpochMs = now,
            isSimulatedTestData = false
        )

        val snap = LiveVehicleDataMapper.map(criticalSession, isAdapterConnected = true)
        assertEquals(EfficiencyBand.RED, snap.categories.first { it.categoryId == LiveDataCategoryId.ENGINE_TEMPERATURE }.band)
        assertEquals(EfficiencyBand.YELLOW, snap.categories.first { it.categoryId == LiveDataCategoryId.AIR_INTAKE_FUEL }.band)
        assertEquals(EfficiencyBand.GREEN, snap.categories.first { it.categoryId == LiveDataCategoryId.DRIVING_ENGINE }.band)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `car overview opens See More Details category gauges and returns cleanly`() {
        val store = InMemoryPreviewVehicleDataSource()
        val id = runBlocking {
            store.createVehicle(
                "user_overview",
                VehicleDraft(
                    manufacturer = "Nissan",
                    model = "GT-R",
                    modelYearInput = "2024",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "3.8L Twin-Turbo V6",
                    transmission = "6-Speed DCT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "12000",
                    mileageUnit = MileageUnit.KM,
                    market = "Japan",
                    anglePhotos = mapOf(VehicleAngle.SIDE_RIGHT to "")
                )
            ).getOrThrow()
        }

        val vm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_overview",
            vehicleId = id,
            autoLoadKnownIssues = false
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onEditVehicle = {}
                )
            }
        }
        runBlocking { vm.uiState.first { it is VehicleOverviewUiState.Ready } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("overview_right_side_cutout_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("overview_vehicle_year").assertTextEquals("2024")
        composeRule.onNodeWithTag("live_category_card_driving_engine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_percent_driving_engine", useUnmergedTree = true)
            .assertTextEquals("00%")
        composeRule.onNodeWithTag("live_category_details_driving_engine", useUnmergedTree = true)
            .assertIsDisplayed()

        // Open Driving & Engine detail ring view
        composeRule.onNodeWithTag("live_category_card_driving_engine").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("live_detail_header_title").assertTextEquals("Driving & Engine")
        composeRule.onNodeWithTag("metric_gauge_vehicle_speed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_gauge_engine_rpm").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_gauge_calculated_engine_load").performScrollTo().assertIsDisplayed()

        // Navigate back to Car Overview
        composeRule.onNodeWithTag("live_detail_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overview_hero_section").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("live_detail_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `pid 0101 parser decodes MIL light and readiness monitors accurately`() {
        // 41 01 82 07 65 01 -> MIL ON (0x82 = 0x80 + 2 DTCs), 3 continuous ready (0x07, no upper incomplete),
        // non-continuous supported: 0x65 (bits 0,2,5,6), incomplete: 0x01 (Catalyst incomplete)
        val status = Elm327Parser.parseMonitorStatusPid01("41 01 82 07 65 01")
        assertTrue(status != null)
        assertEquals(true, status!!.milOn)
        assertEquals(2, status.confirmedDtcCountFromPid01)
        assertEquals(7, status.supportedMonitorsCount)
        assertEquals(6, status.readyMonitorsCount)
        assertEquals(listOf("Catalyst"), status.incompleteMonitorNames)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `diagnostic status detail displays dedicated rows and plain-language AI Explain card`() {
        val store = InMemoryPreviewVehicleDataSource()
        val id = runBlocking {
            store.createVehicle(
                "user_diag_detail",
                VehicleDraft(
                    manufacturer = "Toyota",
                    model = "Corolla",
                    modelYearInput = "2022",
                    trim = "Altis",
                    bodyStyle = BodyStyle.SEDAN,
                    engineType = "1.8L Hybrid",
                    transmission = "e-CVT",
                    fuelType = "Hybrid",
                    mileageInput = "41000",
                    mileageUnit = MileageUnit.KM,
                    market = "Thailand"
                )
            ).getOrThrow()
        }

        val fakeExplainer = object : DiagnosticStatusAiExplainer {
            override suspend fun explainDiagnosticStatusPlainLanguage(
                vehicle: Vehicle,
                status: com.example.data.obd.DiagnosticStatusDetailSummary,
                language: com.example.data.settings.AppLanguage
            ): Result<DiagnosticStatusPlainExplanation> {
                return Result.success(
                    DiagnosticStatusPlainExplanation(
                        headline = "Easy Summary: Your car has no active warning codes.",
                        plainSummary = "All engine check monitors look normal and the Check Engine light is off.",
                        codeBreakdown = listOf("Confirmed & Pending DTC: 0 problems found."),
                        whatToDoNext = "Keep driving normally and follow your regular service schedule."
                    )
                )
            }
        }

        val vm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_diag_detail",
            vehicleId = id,
            autoLoadKnownIssues = false,
            diagnosticAiExplainer = fakeExplainer
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onEditVehicle = {}
                )
            }
        }
        runBlocking { vm.uiState.first { it is VehicleOverviewUiState.Ready } }
        composeRule.waitForIdle()

        // Open Diagnostic Status detail view
        composeRule.onNodeWithTag("live_category_card_diagnostic_status").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("diagnostic_status_detail_view").assertIsDisplayed()
        composeRule.onNodeWithTag("diag_row_confirmed_dtc").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diag_row_pending_dtc").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diag_row_freeze_frame").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diag_row_mil_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diag_row_readiness_monitor").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic_ai_explain_card").performScrollTo().assertIsDisplayed()

        // Tap Explain Simply and verify plain-language AI explanation renders cleanly
        composeRule.onNodeWithTag("diagnostic_ai_explain_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnostic_ai_ready_content").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Easy Summary: Your car has no active warning codes.").assertIsDisplayed()
    }

    @Test
    fun `system voltage sits between Engine Temperature and Air Intake and uses verified system type criteria without guessing`() {
        val now = 1_700_000_000_000L

        fun sessionWithVoltage(
            voltage: Double?,
            dtcs: List<DiagnosticTroubleCode> = emptyList(),
            electricalSystemType: String = "UNKNOWN",
            capturedAtMs: Long = now
        ) = ObdDiagnosticSession(
            sessionId = "volt_test",
            vehicle = testVehicleSnapshot().copy(electricalSystemType = electricalSystemType),
            sourceMetadata = ObdSourceMetadata(
                sourceType = ObdDataSourceType.BLUETOOTH_OBD,
                adapterOrFileLabel = "ELM327 v2.1",
                protocolLabel = "CAN 11/500",
                connectionMethod = ObdConnectionMethod.BLUETOOTH,
                readOnlyEnforced = true,
                reliabilityLevel = DataReliabilityLevel.HIGH_VERIFIED_HARDWARE,
                capturedAtEpochMs = capturedAtMs
            ),
            confirmedDtcs = dtcs,
            pendingDtcs = emptyList(),
            freezeFrames = emptyList(),
            livePidReadings = listOfNotNull(
                voltage?.let {
                    ObdPidReading(
                        pidCode = "0142",
                        name = "Control Module Voltage",
                        value = it,
                        unit = "V",
                        availability = PidAvailability.AVAILABLE,
                        timestampEpochMs = capturedAtMs
                    )
                }
            ),
            supportedPids = setOf("0142"),
            capturedAtEpochMs = capturedAtMs,
            isSimulatedTestData = false
        )

        val criteria12V = com.example.data.obd.SystemVoltageCriteria(
            systemType = com.example.data.obd.ElectricalSystemType.SYSTEM_12V,
            batteryType = com.example.data.obd.BatteryChemistryType.STANDARD_LEAD_ACID
        )
        val criteria24V = com.example.data.obd.SystemVoltageCriteria(
            systemType = com.example.data.obd.ElectricalSystemType.SYSTEM_24V,
            batteryType = com.example.data.obd.BatteryChemistryType.STANDARD_LEAD_ACID
        )

        // 1. Disconnected / No Data -> Unknown, Unavailable, No Data, placed strictly between Engine Temp & Air Intake
        val disconnected = LiveVehicleDataMapper.map(session = null, isAdapterConnected = false, nowMs = now)
        val ids = disconnected.categories.map { it.categoryId }
        val engTempIdx = ids.indexOf(LiveDataCategoryId.ENGINE_TEMPERATURE)
        val sysVoltIdx = ids.indexOf(LiveDataCategoryId.SYSTEM_VOLTAGE)
        val airFuelIdx = ids.indexOf(LiveDataCategoryId.AIR_INTAKE_FUEL)
        assertEquals(engTempIdx + 1, sysVoltIdx)
        assertEquals(sysVoltIdx + 1, airFuelIdx)

        val discVolt = disconnected.categories[sysVoltIdx]
        assertEquals(0, discVolt.efficiencyPercent)
        assertEquals("N/A", discVolt.metrics.first { it.id == "current_voltage" }.formattedPrimaryText)
        assertEquals("Unknown", discVolt.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Unavailable", discVolt.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)
        assertEquals("No Data", discVolt.metrics.first { it.id == "voltage_last_updated" }.formattedPrimaryText)

        // 2. Unverified vehicle MUST NOT guess "12V System" from 14.1V reading -> stays Unknown & Unavailable status
        val unverified14v = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(14.1, electricalSystemType = "UNKNOWN"),
            isAdapterConnected = true,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals("14.1 V", unverified14v.metrics.first { it.id == "current_voltage" }.formattedPrimaryText)
        assertEquals("Unknown", unverified14v.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Unavailable", unverified14v.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)

        // 3. Verified 12V System charging -> 12V System, Normal, formatted hh:mm:ss AM/PM
        val ok12 = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(14.1, electricalSystemType = "SYSTEM_12V"),
            isAdapterConnected = true,
            systemVoltageCriteria = criteria12V,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals(EfficiencyBand.GREEN, ok12.band)
        assertEquals("14.1 V", ok12.metrics.first { it.id == "current_voltage" }.formattedPrimaryText)
        assertEquals("12V System", ok12.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Normal", ok12.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)
        val lastUpdated12 = ok12.metrics.first { it.id == "voltage_last_updated" }
        assertTrue(lastUpdated12.isAvailable)
        assertTrue(lastUpdated12.formattedPrimaryText.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
        assertTrue(lastUpdated12.formattedUnitText == "AM" || lastUpdated12.formattedUnitText == "PM")

        // 4. Verified 24V System -> 24V System, Normal
        val ok24 = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(27.6, electricalSystemType = "SYSTEM_24V"),
            isAdapterConnected = true,
            systemVoltageCriteria = criteria24V,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals("24V System", ok24.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Normal", ok24.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)

        // 5. Low & High for verified 12V System
        val low12 = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(11.3, electricalSystemType = "SYSTEM_12V"),
            isAdapterConnected = true,
            systemVoltageCriteria = criteria12V,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals("12V System", low12.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Low", low12.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)

        val high12 = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(15.7, electricalSystemType = "SYSTEM_12V"),
            isAdapterConnected = true,
            systemVoltageCriteria = criteria12V,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals("12V System", high12.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("High", high12.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)

        // 6. Oscillating 12V readings -> 12V System, Unstable
        val unstable = LiveVehicleDataMapper.map(
            session = sessionWithVoltage(12.2, electricalSystemType = "SYSTEM_12V"),
            isAdapterConnected = true,
            recentVoltageSamples = listOf(14.3, 12.1, 14.5, 12.2),
            systemVoltageCriteria = criteria12V,
            nowMs = now
        ).categories.first { it.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE }
        assertEquals("12V System", unstable.metrics.first { it.id == "system_type" }.formattedPrimaryText)
        assertEquals("Unstable", unstable.metrics.first { it.id == "voltage_status" }.formattedPrimaryText)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `car overview renders System Voltage card and opens 2x2 detail gauges cleanly`() {
        val store = InMemoryPreviewVehicleDataSource()
        val id = runBlocking {
            store.createVehicle(
                "user_volt_ui",
                VehicleDraft(
                    manufacturer = "Nissan",
                    model = "GT-R",
                    modelYearInput = "2024",
                    trim = "Premium",
                    bodyStyle = BodyStyle.COUPE,
                    engineType = "3.8L Twin-Turbo V6",
                    transmission = "6-Speed DCT",
                    fuelType = "Petrol / Gasoline",
                    mileageInput = "12000",
                    mileageUnit = MileageUnit.KM,
                    market = "Japan"
                )
            ).getOrThrow()
        }

        val vm = VehicleOverviewViewModel(
            repository = store,
            currentUserId = "user_volt_ui",
            vehicleId = id,
            autoLoadKnownIssues = false
        )

        composeRule.setContent {
            MotoraTheme {
                VehicleOverviewScreen(
                    viewModel = vm,
                    onNavigateBack = {},
                    onEditVehicle = {}
                )
            }
        }
        runBlocking { vm.uiState.first { it is VehicleOverviewUiState.Ready } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("live_category_card_system_voltage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("live_category_card_system_voltage").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("live_detail_header_title").assertTextEquals("System Voltage")
        composeRule.onNodeWithTag("metric_gauge_current_voltage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_gauge_system_type").performScrollTo()
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("Unknown")))
        composeRule.onNodeWithTag("metric_gauge_voltage_status").performScrollTo()
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("Unavailable")))
        composeRule.onNodeWithTag("metric_gauge_voltage_last_updated").performScrollTo()
            .assertIsDisplayed()
            .assert(hasAnyDescendant(hasText("No Data")))
    }
}
