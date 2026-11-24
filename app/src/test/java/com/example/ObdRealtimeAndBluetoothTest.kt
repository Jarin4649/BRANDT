package com.example

import android.bluetooth.BluetoothDevice
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
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.BluetoothDeviceEntry
import com.example.data.obd.BluetoothHardwarePort
import com.example.data.obd.BluetoothObdAdapter
import com.example.data.obd.LiveDataCategoryId
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDataSourceType
import com.example.data.obd.ObdTransportConnector
import com.example.data.obd.ObdTransportStream
import com.example.data.obd.WifiHardwarePort
import com.example.data.obd.WifiObdAdapter
import com.example.data.settings.AppLanguage
import com.example.ui.chat.VehicleAiChatViewModel
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.DiagnosticsViewModel
import com.example.ui.diagnostics.scan.DiagnosticsScanViewModel
import com.example.ui.garage.GarageViewModel
import com.example.ui.overview.VehicleOverviewViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ObdRealtimeAndBluetoothTest {

    private class DynamicBluetoothObdStream(
        override val adapterLabel: String = "OBDLink MX+ (AA:BB:CC:11:22:33)",
        override val bluetoothArchitecture: BluetoothArchitecture = BluetoothArchitecture.CLASSIC_SPP
    ) : ObdTransportStream {
        override val sourceType: ObdDataSourceType = ObdDataSourceType.BLUETOOTH_OBD
        override val method: ObdConnectionMethod = ObdConnectionMethod.BLUETOOTH
        private var open = true
        override val isConnected: Boolean get() = open
        val sentCommands = mutableListOf<String>()

        override suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String> {
            sentCommands += command
            val reply = when (command) {
                "ATI" -> "ELM327 v2.3"
                "ATRV" -> "13.9V"
                "ATDP" -> "ISO 15765-4 (CAN 11/500)"
                "ATE0", "ATL0", "ATS0", "ATSP0" -> "OK"
                "0100" -> "41 00 BE 1F A8 13" // supports common PIDs incl. 0104, 0105, 010C, 010D, 0111
                "0120" -> "41 20 80 00 00 01"
                "0140" -> "41 40 44 00 00 00" // 0142 Voltage
                "0101" -> "41 01 00 07 65 00"
                "03" -> "43 01 01 71" // P0171
                "07" -> "NO DATA"
                "0104" -> "41 04 80" // ~50% load
                "0105" -> "41 05 82" // 90 °C
                "010C" -> "41 0C 17 70" // 1500 rpm
                "010D" -> "41 0D 3C" // 60 km/h
                "0111" -> "41 11 40" // ~25% throttle
                "0142" -> "41 42 36 B0" // 14.0 V
                else -> "NO DATA"
            }
            return Result.success(reply)
        }

        override suspend fun close() {
            open = false
        }
    }

    @Test
    fun `bluetooth auto discovery surfaces paired and nearby devices cleanly and toggles on BT state change`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        var btEnabled = false
        var onStateCallback: ((Boolean) -> Unit)? = null
        var onDiscoveredCallback: ((BluetoothDeviceEntry) -> Unit)? = null
        var classicScanStarted = false
        var bleScanStarted = false

        val fakeBtPort = object : BluetoothHardwarePort {
            override fun requiredPermissions() = listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun missingPermissions(context: Context) = emptyList<String>()
            override fun isBluetoothSupported(context: Context) = true
            override fun isBluetoothEnabled(context: Context) = btEnabled
            override fun listPairedDevices(context: Context): List<BluetoothDeviceEntry> {
                return if (!btEnabled) {
                    emptyList()
                } else {
                    listOf(
                        BluetoothDeviceEntry(
                            address = "AA:BB:CC:11:22:33",
                            displayName = "Vgate iCar Pro",
                            architecture = BluetoothArchitecture.CLASSIC_SPP,
                            isBonded = true
                        ),
                        BluetoothDeviceEntry(
                            address = "99:88:77:66:55:44",
                            displayName = "Office Headset",
                            architecture = BluetoothArchitecture.CLASSIC_SPP,
                            isBonded = true
                        )
                    )
                }
            }

            override fun startBleScan(
                context: Context,
                onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
                onScanError: (String) -> Unit
            ): Boolean {
                bleScanStarted = true
                onDeviceDiscovered(
                    BluetoothDeviceEntry(
                        address = "DD:EE:FF:01:02:03",
                        displayName = "Veepeak OBDCheck BLE",
                        architecture = BluetoothArchitecture.BLE_GATT,
                        isBonded = false,
                        rssiDbm = -58
                    )
                )
                return true
            }

            override fun stopBleScan(context: Context) {}

            override fun startClassicDiscovery(context: Context): Boolean {
                classicScanStarted = true
                return true
            }

            override fun stopClassicDiscovery(context: Context) {}

            override fun registerSystemBluetoothReceiver(
                context: Context,
                onBluetoothStateChanged: (Boolean) -> Unit,
                onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
                onDiscoveryFinished: () -> Unit,
                onBondStateChanged: (String, Int) -> Unit
            ): () -> Unit {
                onStateCallback = onBluetoothStateChanged
                onDiscoveredCallback = onDeviceDiscovered
                return {
                    onStateCallback = null
                    onDiscoveredCallback = null
                }
            }
        }

        val manager = ObdConnectionManager(
            bluetoothAdapter = BluetoothObdAdapter(fakeBtPort)
        )

        manager.attachBluetoothObserver(context, autoDiscoverIfEnabled = true)
        assertFalse(manager.state.value.bluetoothState.isBluetoothEnabled)
        assertTrue(manager.state.value.bluetoothState.devices.isEmpty())

        // Simulate user turning Phone Bluetooth ON while app is open
        btEnabled = true
        onStateCallback?.invoke(true)

        val btState = manager.state.value.bluetoothState
        assertTrue(btState.isBluetoothEnabled)
        assertTrue(classicScanStarted || bleScanStarted)
        // Paired Vgate OBD + discovered Veepeak BLE both visible and OBD candidates ranked ahead of non-OBD headset
        assertTrue(btState.devices.any { it.address == "AA:BB:CC:11:22:33" && it.isObdCandidate })
        assertTrue(btState.devices.any { it.address == "DD:EE:FF:01:02:03" && it.isObdCandidate })
        assertFalse(btState.devices.first().address == "99:88:77:66:55:44")

        // Also simulate Classic BR/EDR scan finding another adapter
        onDiscoveredCallback?.invoke(
            BluetoothDeviceEntry(
                address = "12:34:56:78:9A:BC",
                displayName = "OBDII ELM327",
                architecture = BluetoothArchitecture.CLASSIC_SPP,
                isBonded = false,
                rssiDbm = -64
            )
        )
        assertTrue(manager.state.value.bluetoothState.devices.any { it.address == "12:34:56:78:9A:BC" })

        manager.detachBluetoothObserver()
    }

    @Test
    fun `bluetooth obd connection streams real-time ecu data across Diagnostics, Overview, Garage, and MACK without forbidden commands`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val liveStream = DynamicBluetoothObdStream()

        val fakeWifiPort = object : WifiHardwarePort {
            override fun isWifiSupported(context: Context) = true
            override fun isWifiEnabled(context: Context) = false
            override fun isWifiConnected(context: Context) = false
            override fun currentSsidLabel(context: Context): String? = null
        }

        val fakeBtPort = object : BluetoothHardwarePort {
            override fun requiredPermissions() = listOf(android.Manifest.permission.BLUETOOTH_CONNECT)
            override fun missingPermissions(context: Context) = emptyList<String>()
            override fun isBluetoothSupported(context: Context) = true
            override fun isBluetoothEnabled(context: Context) = true
            override fun listPairedDevices(context: Context) = listOf(
                BluetoothDeviceEntry(
                    address = "AA:BB:CC:11:22:33",
                    displayName = "OBDLink MX+",
                    architecture = BluetoothArchitecture.CLASSIC_SPP,
                    isBonded = true
                )
            )

            override fun startBleScan(
                context: Context,
                onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
                onScanError: (String) -> Unit
            ) = true

            override fun stopBleScan(context: Context) {}
        }

        val fakeConnector = object : ObdTransportConnector {
            override suspend fun openWifiTransport(
                context: Context,
                host: String,
                port: Int,
                displayLabel: String
            ): Result<ObdTransportStream> = Result.failure(IllegalStateException("Not used"))

            override suspend fun openBluetoothTransport(
                context: Context,
                device: BluetoothDeviceEntry,
                architecture: BluetoothArchitecture
            ): Result<ObdTransportStream> = Result.success(liveStream)
        }

        val repo = FakeVehicleDataSource(activeAuthenticatedUserId = "owner_bt_live")
        val vehicleId = repo.createVehicle(
            "owner_bt_live",
            VehicleDraft(
                manufacturer = "Toyota",
                model = "GR86",
                modelYearInput = "2023",
                trim = "RZ",
                bodyStyle = BodyStyle.COUPE,
                engineType = "2.4L Boxer-4",
                transmission = "6-Speed Manual",
                fuelType = "Petrol / Gasoline",
                mileageInput = "9500",
                mileageUnit = MileageUnit.KM,
                market = "Japan"
            )
        ).getOrThrow()

        val capturedPrompts = mutableListOf<String>()
        val fakeAiService = object : VehicleMechanicAiService {
            override suspend fun loadCommonIssuesForVehicle(
                vehicle: Vehicle,
                language: AppLanguage
            ): Result<VehicleModelIssuesResult> = Result.success(VehicleModelIssuesResult("OK", emptyList()))

            override suspend fun sendCarMechanicMessage(
                context: Context,
                vehicle: Vehicle,
                history: List<CarChatTurn>,
                userText: String,
                attachment: PendingChatAttachment?,
                language: AppLanguage
            ): Result<CarChatReply> {
                capturedPrompts += userText
                return Result.success(
                    CarChatReply(
                        isCarRelated = true,
                        simpleReply = "Checked your live ECU data.",
                        modelCommonality = "",
                        followUpQuestion = "",
                        suggestedQuickReplies = emptyList(),
                        logSummary = "Live check"
                    )
                )
            }
        }

        val manager = ObdConnectionManager(
            wifiAdapter = WifiObdAdapter(fakeWifiPort),
            bluetoothAdapter = BluetoothObdAdapter(fakeBtPort),
            transportConnector = fakeConnector
        )
        val coordinator = DiagnosticsSessionCoordinator(
            userId = "owner_bt_live",
            repository = repo,
            connectionManager = manager
        )
        val diagVm = DiagnosticsViewModel(repo, "owner_bt_live", vehicleId, manager)
        val scanVm = DiagnosticsScanViewModel(manager, coordinator) { null }
        val garageVm = GarageViewModel(repo, "owner_bt_live", "Owner", manager)
        val overviewVm = VehicleOverviewViewModel(
            repository = repo,
            currentUserId = "owner_bt_live",
            vehicleId = vehicleId,
            aiService = fakeAiService,
            autoLoadKnownIssues = false,
            connectionManager = manager,
            diagnosticsCoordinator = coordinator
        )
        val chatVm = VehicleAiChatViewModel(
            repository = repo,
            currentUserId = "owner_bt_live",
            vehicleId = vehicleId,
            aiService = fakeAiService,
            connectionManager = manager
        )

        manager.selectMethod(ObdConnectionMethod.BLUETOOTH)
        manager.refreshBluetoothState(context)
        val status = manager.connectTransport(context)
        assertTrue(status is ObdConnectionStatus.Connected)

        val overviewCollectJob = launch(Dispatchers.Main) {
            overviewVm.liveVehicleData.collect {}
        }

        suspend fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!predicate() && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25L)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue("Timed out after ${timeoutMs}ms waiting for live ECU condition", predicate())
        }

        // Wait for at least one real-time polling tick to complete automatically
        awaitCondition {
            val streamState = manager.liveTelemetry.value
            streamState.isStreaming && streamState.pollCount >= 1 && streamState.latestSession != null
        }

        // Verify continuous stream reached Diagnostics & Scan & Garage & Overview & MACK
        val latestSession = manager.liveTelemetry.value.latestSession!!
        assertEquals(listOf("P0171"), latestSession.confirmedDtcs.map { it.code })
        assertTrue(latestSession.livePidReadings.any { it.pidCode == "010C" && it.value == 1500.0 })
        assertTrue(latestSession.livePidReadings.any { it.pidCode == "010D" && it.value == 60.0 })

        awaitCondition {
            diagVm.uiState.value.liveTelemetry.isStreaming &&
                diagVm.uiState.value.liveTelemetry.latestSession != null &&
                scanVm.liveTelemetry.value.isStreaming &&
                scanVm.liveTelemetry.value.latestSession != null &&
                garageVm.obdLiveTelemetry.value?.isStreaming == true &&
                garageVm.obdLiveTelemetry.value?.latestSession != null &&
                overviewVm.liveVehicleData.value.isAdapterConnected &&
                overviewVm.liveVehicleData.value.hasActiveEcuSession &&
                chatVm.uiState.value.vehicle != null &&
                chatVm.uiState.value.isObdLiveConnected
        }

        val overviewSnapshot = overviewVm.liveVehicleData.value
        val drivingCat = overviewSnapshot.categories.first { it.categoryId == LiveDataCategoryId.DRIVING_ENGINE }
        val rpmMetric = drivingCat.metrics.first { it.id == "engine_rpm" }
        assertTrue(rpmMetric.isAvailable)
        assertEquals(1500.0, rpmMetric.rawNumericValue!!, 0.1)

        // MACK sees live OBD link & injects ECU telemetry context into AI prompt while keeping user bubble clean
        chatVm.updateInputText("Why is my check engine light on?")
        chatVm.sendMessage(context)
        awaitCondition {
            !chatVm.uiState.value.isSending && chatVm.uiState.value.messages.size >= 2
        }
        assertEquals("Why is my check engine light on?", chatVm.uiState.value.messages.first().text)
        assertTrue(capturedPrompts.single().contains("Live OBD-II ECU Snapshot"))
        assertTrue(capturedPrompts.single().contains("P0171"))

        // Verify withPollingPaused runs exclusive block and resumes streaming cleanly
        val beforeCount = manager.liveTelemetry.value.pollCount
        val exclusiveVal = manager.withPollingPaused { 42 }
        assertEquals(42, exclusiveVal)
        awaitCondition {
            manager.liveTelemetry.value.isStreaming && manager.liveTelemetry.value.pollCount > beforeCount
        }

        // Strict read-only guard: never send Mode 04 clear or Mode 08
        assertTrue(liveStream.sentCommands.none { it.startsWith("04") || it.startsWith("08") })

        overviewCollectJob.cancel()
        manager.disconnectAsync(context)
        delay(50L)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNull(manager.activeTransportStream)
        assertFalse(manager.liveTelemetry.value.isStreaming)
        assertNull(manager.liveTelemetry.value.latestSession)
        assertTrue(manager.state.value.connectionStatus is ObdConnectionStatus.Disconnected)
    }
}
