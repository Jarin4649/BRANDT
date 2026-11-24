package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.CarChatReply
import com.example.data.ai.CarChatTurn
import com.example.data.ai.PendingChatAttachment
import com.example.data.ai.VehicleMechanicAiService
import com.example.data.ai.VehicleModelIssuesResult
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.Vehicle
import com.example.data.model.VehicleDraft
import com.example.data.obd.BatteryChemistryType
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.BluetoothDeviceEntry
import com.example.data.obd.BluetoothHardwarePort
import com.example.data.obd.BluetoothObdAdapter
import com.example.data.obd.ElectricalOperatingCondition
import com.example.data.obd.ElectricalSystemType
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDataSourceType
import com.example.data.obd.ObdTransportConnector
import com.example.data.obd.ObdTransportStream
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.obd.PidAvailability
import com.example.data.obd.ReadOnlyObdClient
import com.example.data.obd.SystemVoltageConnectionStatus
import com.example.data.obd.SystemVoltageCriteria
import com.example.data.obd.SystemVoltageErrorState
import com.example.data.obd.SystemVoltageProcessor
import com.example.data.obd.SystemVoltageStatus
import com.example.data.obd.WifiHardwarePort
import com.example.data.obd.WifiObdAdapter
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.ui.chat.VehicleAiChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemVoltagePhase8Test {

    private fun testVehicleSnapshot(
        electricalSystemType: String = "SYSTEM_12V",
        batteryType: String = "STANDARD_LEAD_ACID"
    ) = ObdVehicleSnapshot(
        vehicleId = "veh_phase8",
        manufacturer = "Toyota",
        model = "Camry",
        modelYear = 2023,
        trim = "Hybrid",
        engineType = "2.5L I4",
        engineDisplacement = "2.5L",
        transmission = "e-CVT",
        fuelType = "Hybrid",
        mileage = 22000L,
        mileageUnit = "km",
        market = "Thailand",
        electricalSystemType = electricalSystemType,
        batteryChemistryType = batteryType
    )

    private fun createBtPort(
        btEnabled: Boolean = true,
        devices: List<BluetoothDeviceEntry> = emptyList()
    ): BluetoothHardwarePort = object : BluetoothHardwarePort {
        override fun requiredPermissions() = listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun missingPermissions(context: Context) = emptyList<String>()
        override fun isBluetoothSupported(context: Context) = true
        override fun isBluetoothEnabled(context: Context) = btEnabled
        override fun listPairedDevices(context: Context) = devices
        override fun startBleScan(
            context: Context,
            onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
            onScanError: (String) -> Unit
        ) = false
        override fun stopBleScan(context: Context) = Unit
        override fun startClassicDiscovery(context: Context) = false
        override fun stopClassicDiscovery(context: Context) = Unit
        override fun registerSystemBluetoothReceiver(
            context: Context,
            onBluetoothStateChanged: (Boolean) -> Unit,
            onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
            onDiscoveryFinished: () -> Unit,
            onBondStateChanged: (String, Int) -> Unit
        ): () -> Unit = {}
    }

    private fun createWifiPort(): WifiHardwarePort = object : WifiHardwarePort {
        override fun isWifiSupported(context: Context) = true
        override fun isWifiEnabled(context: Context) = true
        override fun isWifiConnected(context: Context) = true
        override fun currentSsidLabel(context: Context) = "WiFi_OBDII"
    }

    private class FakeObdStream(
        override val adapterLabel: String = "OBDLink MX+ (11:22:33:44:55:66)",
        private val responseMap: (String) -> Result<String>
    ) : ObdTransportStream {
        override val sourceType = ObdDataSourceType.BLUETOOTH_OBD
        override val method = ObdConnectionMethod.BLUETOOTH
        override val bluetoothArchitecture = BluetoothArchitecture.CLASSIC_SPP

        @Volatile
        var open = true
        override val isConnected: Boolean get() = open

        override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
            return if (!open) {
                Result.failure(IllegalStateException("Stream disconnected"))
            } else {
                responseMap(command)
            }
        }

        override suspend fun close() {
            open = false
        }
    }

    @Test
    fun `1_bluetooth adapter connects cleanly and prevents duplicate concurrent connections`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val device = BluetoothDeviceEntry(
            address = "11:22:33:44:55:66",
            displayName = "OBDLink MX+",
            architecture = BluetoothArchitecture.CLASSIC_SPP,
            isBonded = true
        )
        val openCalls = AtomicInteger(0)
        val stream = FakeObdStream { cmd ->
            when (cmd) {
                "ATI" -> Result.success("ELM327 v2.3")
                "ATRV" -> Result.success("14.0V")
                "ATDP" -> Result.success("ISO 15765-4 CAN")
                "ATE0", "ATL0", "ATS0", "ATSP0" -> Result.success("OK")
                "0100" -> Result.success("41 00 80 00 00 01")
                "0140" -> Result.success("41 40 40 00 00 00") // 0142
                "0142" -> Result.success("41 42 36 B0") // 14.000V
                else -> Result.success("NO DATA")
            }
        }

        val connector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(context: Context, host: String, port: Int, displayLabel: String) =
                Result.failure<ObdTransportStream>(IllegalStateException("Unused"))

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ): Result<ObdTransportStream> {
                openCalls.incrementAndGet()
                return Result.success(stream)
            }
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(createWifiPort()),
            bluetoothAdapter = BluetoothObdAdapter(createBtPort(btEnabled = true, devices = listOf(device))),
            transportConnector = connector
        )
        manager.selectMethod(ObdConnectionMethod.BLUETOOTH)
        manager.refreshBluetoothState(context)

        val firstStatus = manager.connectTransport(context)
        assertTrue(firstStatus is ObdConnectionStatus.Connected)
        assertEquals(1, openCalls.get())

        // Duplicate connect attempt while connected to same adapter should reuse existing session
        val secondStatus = manager.connectTransport(context)
        assertTrue(secondStatus is ObdConnectionStatus.Connected)
        assertEquals(1, openCalls.get())

        manager.disconnectAsync(context)
    }

    @Test
    fun `2_bluetooth adapter not detected maps cleanly to ADAPTER_NOT_DETECTED and Unavailable`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val connector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(context: Context, host: String, port: Int, displayLabel: String) =
                Result.failure<ObdTransportStream>(IllegalStateException("Unused"))

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ) = Result.failure<ObdTransportStream>(IllegalStateException("Adapter not detected"))
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(createWifiPort()),
            bluetoothAdapter = BluetoothObdAdapter(createBtPort(btEnabled = true, devices = emptyList())),
            transportConnector = connector
        )
        manager.selectMethod(ObdConnectionMethod.BLUETOOTH)
        val status = manager.connectTransport(context)
        assertTrue(status is ObdConnectionStatus.Error)

        val processed = manager.currentSystemVoltageData(nowMs = 1_700_000_000_000L)
        assertNull(processed.currentVoltage)
        assertEquals(SystemVoltageStatus.UNAVAILABLE, processed.voltageStatus)
        assertEquals(SystemVoltageErrorState.ADAPTER_NOT_DETECTED, processed.errorState)
        assertEquals("unavailable", processed.toJson().getString("voltage_status"))
    }

    @Test
    fun `3_valid Mode 01 PID 42 hex response computes exact voltage and structured Phase 5 JSON`() {
        val now = 1_700_000_000_000L
        // 41 42 36 B0 -> (0x36 * 256 + 0xB0) / 1000 = (54*256 + 176)/1000 = 14000 / 1000 = 14.0V
        val criteria = SystemVoltageCriteria(
            systemType = ElectricalSystemType.SYSTEM_12V,
            batteryType = BatteryChemistryType.AGM_EFB,
            operatingCondition = ElectricalOperatingCondition.ENGINE_RUNNING
        )
        val processed = SystemVoltageProcessor.processRawPid42(
            rawResponse = "41 42 36 B0",
            connectionStatus = SystemVoltageConnectionStatus.CONNECTED,
            criteria = criteria,
            readingTimestampMs = now,
            nowMs = now
        )

        assertEquals(14.0, processed.currentVoltage!!, 0.001)
        assertEquals(ElectricalSystemType.SYSTEM_12V, processed.systemType)
        assertEquals(SystemVoltageStatus.NORMAL, processed.voltageStatus)
        assertEquals(SystemVoltageErrorState.NONE, processed.errorState)
        assertFalse(processed.isStale)

        val json = processed.toJson()
        assertEquals("system_voltage", json.getString("section"))
        assertEquals(14.0, json.getDouble("current_voltage"), 0.001)
        assertEquals("V", json.getString("unit"))
        assertEquals("12v_system", json.getString("system_type"))
        assertEquals("normal", json.getString("voltage_status"))
        assertNotNull(json.optString("last_updated").takeIf { it.isNotBlank() && it != "null" })
        assertEquals("OBD-II PID 42", json.getString("data_source"))
        assertEquals("connected", json.getString("connection_status"))
    }

    @Test
    fun `4_unsupported PID 42 is NOT masked by ATRV and surfaces PID_UNSUPPORTED`() = runBlocking {
        val client = ReadOnlyObdClient()
        // Vehicle ECU supports 010C only and returns NO DATA for 0142, even though adapter responds to ATRV!
        val stream = FakeObdStream { cmd ->
            when (cmd) {
                "ATI" -> Result.success("ELM327 v2.1")
                "ATRV" -> Result.success("13.8V") // MUST NOT substitute into PID 42!
                "ATDP" -> Result.success("ISO 15765-4 CAN")
                "ATE0", "ATL0", "ATS0", "ATSP0" -> Result.success("OK")
                "0100" -> Result.success("41 00 00 10 00 00") // Only 010C
                "010C" -> Result.success("41 0C 0F A0")
                "03", "07" -> Result.success("NO DATA")
                "0142" -> Result.success("NO DATA")
                else -> Result.success("NO DATA")
            }
        }

        val session = client.runReadOnlyDiagnosticScan(
            transport = stream,
            vehicle = testVehicleSnapshot(),
            requestedPids = listOf("010C", "0142"),
            nowMs = 1_700_000_000_000L
        ).getOrThrow()

        val pid42 = session.livePidReadings.first { it.pidCode == "0142" }
        assertNull(pid42.value)
        assertEquals(PidAvailability.UNSUPPORTED_BY_VEHICLE, pid42.availability)

        val processed = SystemVoltageProcessor.processSession(
            session = session,
            connectionManagerState = null,
            isTransportConnected = true,
            criteria = SystemVoltageCriteria(systemType = ElectricalSystemType.SYSTEM_12V),
            nowMs = 1_700_000_000_000L
        )
        assertNull(processed.currentVoltage)
        assertEquals(SystemVoltageStatus.UNAVAILABLE, processed.voltageStatus)
        assertEquals(SystemVoltageErrorState.PID_UNSUPPORTED, processed.errorState)
    }

    @Test
    fun `5_invalid or truncated PID 42 response returns INVALID_RESPONSE without fabricated voltage`() {
        val now = 1_700_000_000_000L
        val invalidTruncated = SystemVoltageProcessor.processRawPid42(
            rawResponse = "41 42 36",
            connectionStatus = SystemVoltageConnectionStatus.CONNECTED,
            criteria = SystemVoltageCriteria(systemType = ElectricalSystemType.SYSTEM_12V),
            nowMs = now
        )
        assertNull(invalidTruncated.currentVoltage)
        assertEquals(SystemVoltageStatus.UNAVAILABLE, invalidTruncated.voltageStatus)
        assertEquals(SystemVoltageErrorState.INVALID_RESPONSE, invalidTruncated.errorState)
        assertTrue(invalidTruncated.toJson().isNull("current_voltage"))

        val errorLiteral = SystemVoltageProcessor.processRawPid42(
            rawResponse = "BUS INIT: ...ERROR",
            connectionStatus = SystemVoltageConnectionStatus.CONNECTED,
            criteria = SystemVoltageCriteria(systemType = ElectricalSystemType.SYSTEM_12V),
            nowMs = now
        )
        assertNull(errorLiteral.currentVoltage)
        assertEquals(SystemVoltageErrorState.INVALID_RESPONSE, errorLiteral.errorState)
    }

    @Test
    fun `6_unexpected Bluetooth disconnection during polling sets COMMUNICATION_INTERRUPTED`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val device = BluetoothDeviceEntry(
            address = "AA:BB:CC:DD:EE:FF",
            displayName = "Veepeak BLE",
            architecture = BluetoothArchitecture.CLASSIC_SPP,
            isBonded = true
        )
        val stream = FakeObdStream { cmd ->
            when (cmd) {
                "ATI" -> Result.success("ELM327 v2.3")
                "ATRV" -> Result.success("14.1V")
                "ATDP" -> Result.success("ISO 15765-4 CAN")
                "ATE0", "ATL0", "ATS0", "ATSP0" -> Result.success("OK")
                "0100" -> Result.success("41 00 80 00 00 01")
                "0140" -> Result.success("41 40 40 00 00 00")
                "0142" -> Result.success("41 42 36 B0")
                else -> Result.success("NO DATA")
            }
        }
        val connector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(context: Context, host: String, port: Int, displayLabel: String) =
                Result.failure<ObdTransportStream>(IllegalStateException("Unused"))

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ) = Result.success<ObdTransportStream>(stream)
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(createWifiPort()),
            bluetoothAdapter = BluetoothObdAdapter(createBtPort(btEnabled = true, devices = listOf(device))),
            transportConnector = connector
        )
        manager.selectMethod(ObdConnectionMethod.BLUETOOTH)
        manager.refreshBluetoothState(context)
        assertTrue(manager.connectTransport(context) is ObdConnectionStatus.Connected)

        // Wait for at least one live telemetry cycle to populate
        manager.liveTelemetry.first { it.pollCount >= 1 }

        // Simulate abrupt physical BT loss
        stream.open = false
        val droppedTelemetry = manager.liveTelemetry.first { !it.isStreaming && it.isCommunicationInterrupted }
        assertTrue(droppedTelemetry.isCommunicationInterrupted)

        val processed = manager.currentSystemVoltageData()
        assertEquals(SystemVoltageConnectionStatus.INTERRUPTED, processed.connectionStatus)
        assertEquals(SystemVoltageErrorState.COMMUNICATION_INTERRUPTED, processed.errorState)
        assertEquals(SystemVoltageStatus.UNAVAILABLE, processed.voltageStatus)
        assertNull(processed.currentVoltage)
    }

    @Test
    fun `7_outdated timestamp triggers DATA_OUTDATED and marks status Unavailable`() {
        val capturedAt = 1_700_000_000_000L
        val now = capturedAt + 25_000L // > 15s default threshold

        val processed = SystemVoltageProcessor.processRawPid42(
            rawResponse = "41 42 36 B0", // 14.0V
            connectionStatus = SystemVoltageConnectionStatus.CONNECTED,
            criteria = SystemVoltageCriteria(
                systemType = ElectricalSystemType.SYSTEM_12V,
                staleTimeoutMs = 15_000L
            ),
            readingTimestampMs = capturedAt,
            nowMs = now
        )

        assertTrue(processed.isStale)
        assertEquals(SystemVoltageErrorState.DATA_OUTDATED, processed.errorState)
        assertEquals(SystemVoltageStatus.UNAVAILABLE, processed.voltageStatus)
        assertEquals(capturedAt, processed.lastUpdatedEpochMs)
        assertTrue(processed.toJson().getBoolean("is_stale"))
    }

    @Test
    fun `8_MACK receives structured System Voltage JSON for both valid PID 42 and unavailable states`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = InMemoryPreviewVehicleDataSource()
        val vehicleId = store.createVehicle(
            "user_mack_volt",
            VehicleDraft(
                manufacturer = "Toyota",
                model = "Camry",
                modelYearInput = "2023",
                trim = "Hybrid",
                bodyStyle = BodyStyle.SEDAN,
                engineType = "2.5L Hybrid",
                transmission = "e-CVT",
                fuelType = "Hybrid",
                mileageInput = "22000",
                mileageUnit = MileageUnit.KM,
                market = "Thailand",
                electricalSystemType = "SYSTEM_12V",
                batteryChemistryType = "AGM_EFB"
            )
        ).getOrThrow()

        val device = BluetoothDeviceEntry(
            address = "11:22:33:44:55:66",
            displayName = "OBDLink MX+",
            architecture = BluetoothArchitecture.CLASSIC_SPP,
            isBonded = true
        )
        val stream = FakeObdStream { cmd ->
            when (cmd) {
                "ATI" -> Result.success("ELM327 v2.3")
                "ATRV" -> Result.success("14.0V")
                "ATDP" -> Result.success("ISO 15765-4 CAN")
                "ATE0", "ATL0", "ATS0", "ATSP0" -> Result.success("OK")
                "0100" -> Result.success("41 00 80 00 00 01")
                "0120" -> Result.success("41 20 80 00 00 01")
                "0140" -> Result.success("41 40 40 00 00 00")
                "0142" -> Result.success("41 42 36 B0") // 14.0V
                else -> Result.success("NO DATA")
            }
        }
        val connector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(context: Context, host: String, port: Int, displayLabel: String) =
                Result.failure<ObdTransportStream>(IllegalStateException("Unused"))

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ) = Result.success<ObdTransportStream>(stream)
        }
        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(createWifiPort()),
            bluetoothAdapter = BluetoothObdAdapter(createBtPort(btEnabled = true, devices = listOf(device))),
            transportConnector = connector
        )

        val dummyAi = object : VehicleMechanicAiService {
            override suspend fun loadCommonIssuesForVehicle(vehicle: Vehicle, language: AppLanguage): Result<VehicleModelIssuesResult> =
                Result.failure(UnsupportedOperationException())

            override suspend fun sendCarMechanicMessage(
                context: Context,
                vehicle: Vehicle,
                history: List<CarChatTurn>,
                userText: String,
                attachment: PendingChatAttachment?,
                language: AppLanguage
            ): Result<CarChatReply> = Result.success(
                CarChatReply(true, "OK", "", "", emptyList(), "OK")
            )
        }

        val chatVm = VehicleAiChatViewModel(
            repository = store,
            currentUserId = "user_mack_volt",
            vehicleId = vehicleId,
            aiService = dummyAi,
            connectionManager = manager
        )
        // Wait for vehicle load
        var waitCycles = 0
        while (chatVm.uiState.value.vehicle == null && waitCycles < 50) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            delay(20L)
            waitCycles++
        }
        assertNotNull(chatVm.uiState.value.vehicle)

        // Scenario A: Disconnected / unavailable -> MACK still receives structured JSON explaining unavailable status + limitations
        val disconnectedPrompt = chatVm.buildPromptWithLiveObdContext("Check my voltage")
        assertTrue(disconnectedPrompt.contains("JSON_PAYLOAD:"))
        assertTrue(disconnectedPrompt.contains("\"voltage_status\":\"unavailable\""))
        assertTrue(disconnectedPrompt.contains("\"connection_status\":\"disconnected\""))
        assertTrue(disconnectedPrompt.contains("Control Module Voltage"))

        // Scenario B: Connected with valid PID 42 -> MACK receives live 14.0V JSON + 12v_system + OBDI PID 42
        manager.selectMethod(ObdConnectionMethod.BLUETOOTH)
        manager.refreshBluetoothState(context)
        assertTrue(manager.connectTransport(context) is ObdConnectionStatus.Connected)
        val liveState = manager.liveTelemetry.first { it.latestSession != null }
        val connectedNow = liveState.latestSession!!.capturedAtEpochMs

        val connectedPrompt = chatVm.buildPromptWithLiveObdContext("Check my voltage", nowMs = connectedNow)
        val payloadLine = connectedPrompt.lines().first { it.startsWith("JSON_PAYLOAD:") }
        val payloadJson = JSONObject(payloadLine.removePrefix("JSON_PAYLOAD:").trim())
        assertEquals("system_voltage", payloadJson.getString("section"))
        assertEquals(14.0, payloadJson.getDouble("current_voltage"), 0.001)
        assertEquals("12v_system", payloadJson.getString("system_type"))
        assertEquals("normal", payloadJson.getString("voltage_status"))
        assertEquals("OBD-II PID 42", payloadJson.getString("data_source"))
        assertEquals("connected", payloadJson.getString("connection_status"))

        manager.disconnectAsync(context)
    }
}
