package com.example.data.obd

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hardware transport factory abstraction enabling clean separation between
 * Android Wi-Fi TCP / Bluetooth drivers and connection state orchestration.
 */
interface ObdTransportConnector {
    suspend fun openWifiTransport(
        context: Context,
        host: String,
        port: Int,
        displayLabel: String
    ): Result<ObdTransportStream>

    suspend fun openBluetoothTransport(
        context: Context,
        device: BluetoothDeviceEntry,
        architecture: BluetoothArchitecture
    ): Result<ObdTransportStream>
}

object DefaultObdTransportConnector : ObdTransportConnector {
    override suspend fun openWifiTransport(
        context: Context,
        host: String,
        port: Int,
        displayLabel: String
    ): Result<ObdTransportStream> {
        return WifiObdTransport.openAndHandshake(
            context = context,
            host = host,
            port = port,
            displayLabel = displayLabel
        )
    }

    override suspend fun openBluetoothTransport(
        context: Context,
        device: BluetoothDeviceEntry,
        architecture: BluetoothArchitecture
    ): Result<ObdTransportStream> {
        val primaryArch = device.architecture ?: architecture
        val primaryResult = connectSingleArchitecture(context, device, primaryArch)
        if (primaryResult.isSuccess) return primaryResult

        val primaryErr = primaryResult.exceptionOrNull()
        if (primaryErr is SecurityException) return primaryResult

        // Many real-world paired OBD adapters (e.g., Vgate, ELM327, Konnwei Dual-Mode clones)
        // report CLASSIC or UNKNOWN in OS metadata even when communicating over BLE GATT (or vice versa).
        val secondaryArch = if (primaryArch == BluetoothArchitecture.CLASSIC_SPP) {
            BluetoothArchitecture.BLE_GATT
        } else {
            BluetoothArchitecture.CLASSIC_SPP
        }
        val shouldTryFallback = primaryArch != secondaryArch &&
            (device.architecture == null ||
                primaryArch == BluetoothArchitecture.CLASSIC_SPP ||
                device.isBonded)

        if (shouldTryFallback) {
            delay(200L)
            val fallbackResult = connectSingleArchitecture(context, device, secondaryArch)
            if (fallbackResult.isSuccess) return fallbackResult
        }

        return primaryResult
    }

    private suspend fun connectSingleArchitecture(
        context: Context,
        device: BluetoothDeviceEntry,
        architecture: BluetoothArchitecture
    ): Result<ObdTransportStream> {
        return when (architecture) {
            BluetoothArchitecture.CLASSIC_SPP -> ClassicSppObdTransport.connectAndHandshake(
                context = context,
                deviceAddress = device.address,
                displayName = device.displayName
            )
            BluetoothArchitecture.BLE_GATT -> BleGattObdTransport.connectAndHandshake(
                context = context,
                deviceAddress = device.address,
                displayName = device.displayName
            )
        }
    }
}

/**
 * Unified OBD Connection Manager shared across Vehicle Diagnostics, Car Overview, Garage, and MACK.
 * Coordinates Wi-Fi and Bluetooth adapter auto-discovery, in-app pairing, smart Classic/BLE fallback,
 * real transport handshakes, connection status, and continuous read-only live telemetry streaming.
 */
class ObdConnectionManager(
    private val wifiAdapter: WifiObdAdapter = WifiObdAdapter(),
    private val bluetoothAdapter: BluetoothObdAdapter = BluetoothObdAdapter(),
    private val transportConnector: ObdTransportConnector = DefaultObdTransportConnector,
    private val obdClient: ReadOnlyObdClient = ReadOnlyObdClient()
) {
    private val _state = MutableStateFlow(ObdConnectionManagerState())
    val state: StateFlow<ObdConnectionManagerState> = _state.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(LiveTelemetryStreamState())
    val liveTelemetry: StateFlow<LiveTelemetryStreamState> = _liveTelemetry.asStateFlow()

    private fun isThai(): Boolean =
        LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI

    private val discoveredDevices = mutableListOf<BluetoothDeviceEntry>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var activeTransport: ObdTransportStream? = null

    @Volatile
    private var pollingJob: Job? = null

    @Volatile
    private var targetVehicleSnapshot: ObdVehicleSnapshot = defaultVehicleSnapshot()

    @Volatile
    private var unregisterBtReceiver: (() -> Unit)? = null

    @Volatile
    private var observedAppContext: Context? = null

    @Volatile
    private var observerRefCount: Int = 0

    @Volatile
    private var pendingBondAddress: String? = null

    @Volatile
    private var pendingBondDeferred: CompletableDeferred<Boolean>? = null

    private val connectMutex = Mutex()

    private var cachedSupportedPids: Set<String> = emptySet()

    val activeTransportStream: ObdTransportStream?
        get() = activeTransport?.takeIf { it.isConnected }

    fun updateTargetVehicleSnapshot(snapshot: ObdVehicleSnapshot) {
        targetVehicleSnapshot = snapshot
        val vehicleSysType = ElectricalSystemType.fromCode(snapshot.electricalSystemType)
        val vehicleBattery = BatteryChemistryType.fromCode(snapshot.batteryChemistryType)
        _state.update { current ->
            current.copy(
                systemVoltageCriteria = current.systemVoltageCriteria.copy(
                    systemType = if (vehicleSysType != ElectricalSystemType.UNKNOWN) {
                        vehicleSysType
                    } else {
                        current.systemVoltageCriteria.systemType
                    },
                    batteryType = if (vehicleBattery != BatteryChemistryType.UNSPECIFIED) {
                        vehicleBattery
                    } else {
                        current.systemVoltageCriteria.batteryType
                    }
                )
            )
        }
    }

    fun updateSystemVoltageCriteria(criteria: SystemVoltageCriteria) {
        _state.update { it.copy(systemVoltageCriteria = criteria) }
    }

    fun currentSystemVoltageData(nowMs: Long = System.currentTimeMillis()): SystemVoltageProcessedData {
        val connState = _state.value
        val telemetry = _liveTelemetry.value
        val isTransportConnected = connState.isConnected && activeTransportStream?.isConnected == true
        return SystemVoltageProcessor.processSession(
            session = telemetry.latestSession,
            connectionManagerState = connState,
            isTransportConnected = isTransportConnected,
            criteria = connState.systemVoltageCriteria,
            recentVoltageSamples = telemetry.recentVoltageSamples,
            lastDisconnectError = telemetry.lastDisconnectReason
                ?: (connState.connectionStatus as? ObdConnectionStatus.Error)?.message,
            isCommunicationInterrupted = telemetry.isCommunicationInterrupted,
            nowMs = nowMs
        )
    }

    fun attachBluetoothObserver(context: Context, autoDiscoverIfEnabled: Boolean = true) {
        val appCtx = context.applicationContext ?: context
        synchronized(this) {
            observerRefCount++
            observedAppContext = appCtx
            if (unregisterBtReceiver == null) {
                unregisterBtReceiver = bluetoothAdapter.registerSystemBluetoothReceiver(
                    context = appCtx,
                    onBluetoothStateChanged = { enabled ->
                        handleSystemBluetoothToggle(appCtx, enabled)
                    },
                    onDeviceDiscovered = { entry ->
                        upsertDiscoveredDevice(entry)
                    },
                    onDiscoveryFinished = {
                        _state.update { current ->
                            current.copy(
                                bluetoothState = current.bluetoothState.copy(isDiscovering = false)
                            )
                        }
                    },
                    onBondStateChanged = { address, bondState ->
                        handleBondStateChanged(appCtx, address, bondState)
                    }
                )
            }
        }
        refreshBluetoothState(appCtx)
        if (autoDiscoverIfEnabled &&
            bluetoothAdapter.isBluetoothEnabled(appCtx) &&
            bluetoothAdapter.hasRequiredPermissions(appCtx) &&
            !_state.value.isConnected &&
            !_state.value.bluetoothState.isDiscovering
        ) {
            startBleDiscovery(appCtx)
        }
    }

    fun detachBluetoothObserver() {
        val toStopCtx: Context?
        val toUnregister: (() -> Unit)?
        synchronized(this) {
            observerRefCount = (observerRefCount - 1).coerceAtLeast(0)
            if (observerRefCount == 0) {
                toUnregister = unregisterBtReceiver
                unregisterBtReceiver = null
                toStopCtx = observedAppContext
            } else {
                toUnregister = null
                toStopCtx = null
            }
        }
        toUnregister?.invoke()
        if (toStopCtx != null) {
            stopBleDiscovery(toStopCtx)
        }
    }

    private fun handleSystemBluetoothToggle(appCtx: Context, enabled: Boolean) {
        if (!enabled) {
            bluetoothAdapter.stopDiscovery(appCtx)
            if (_state.value.activeMethod == ObdConnectionMethod.BLUETOOTH ||
                (_state.value.connectionStatus as? ObdConnectionStatus.Connected)?.method == ObdConnectionMethod.BLUETOOTH
            ) {
                disconnect(appCtx)
            }
            discoveredDevices.clear()
            refreshBluetoothState(appCtx)
        } else {
            refreshBluetoothState(appCtx)
            if (bluetoothAdapter.hasRequiredPermissions(appCtx)) {
                startBleDiscovery(appCtx)
            }
        }
    }

    private fun handleBondStateChanged(appCtx: Context, address: String, bondState: Int) {
        if (address.equals(pendingBondAddress, ignoreCase = true)) {
            when (bondState) {
                BluetoothDevice.BOND_BONDED -> pendingBondDeferred?.complete(true)
                BluetoothDevice.BOND_NONE -> pendingBondDeferred?.complete(false)
            }
        }
        synchronized(discoveredDevices) {
            val idx = discoveredDevices.indexOfFirst { it.address.equals(address, ignoreCase = true) }
            if (idx >= 0) {
                discoveredDevices[idx] = discoveredDevices[idx].copy(
                    isBonded = bondState == BluetoothDevice.BOND_BONDED
                )
            }
        }
        refreshBluetoothState(appCtx)
    }

    private fun upsertDiscoveredDevice(entry: BluetoothDeviceEntry) {
        synchronized(discoveredDevices) {
            val idx = discoveredDevices.indexOfFirst { it.address.equals(entry.address, ignoreCase = true) }
            if (idx >= 0) {
                val existing = discoveredDevices[idx]
                discoveredDevices[idx] = existing.copy(
                    displayName = if (entry.displayName.startsWith("Unnamed", ignoreCase = true) ||
                        entry.displayName.startsWith("อุปกรณ์ไม่ระบุชื่อ")
                    ) existing.displayName else entry.displayName,
                    architecture = entry.architecture ?: existing.architecture,
                    isBonded = entry.isBonded || existing.isBonded,
                    rssiDbm = entry.rssiDbm ?: existing.rssiDbm,
                    isObdCandidate = entry.isObdCandidate || existing.isObdCandidate
                )
            } else {
                discoveredDevices.add(entry)
            }
        }
        _state.update { current ->
            val sorted = bluetoothAdapter.sortDevicesForSelection(
                devices = (current.bluetoothState.devices + discoveredDevices).distinctBy { it.address },
                preferredArchitecture = current.bluetoothState.architecture,
                showAllArchitectures = current.bluetoothState.showAllArchitectures
            )
            val th = isThai()
            val obdCount = sorted.count { it.isObdCandidate }
            val msg = when {
                th && obdCount > 0 -> "พบอุปกรณ์ Bluetooth ${sorted.size} รายการ (พบตัวสแกน OBD-II ที่แนะนำ $obdCount รายการ)"
                th -> "ค้นพบอุปกรณ์ Bluetooth แล้ว ${sorted.size} รายการ"
                obdCount > 0 -> "Discovered ${sorted.size} Bluetooth device(s) ($obdCount OBD-II adapter(s))."
                else -> "Discovered ${sorted.size} Bluetooth device(s)."
            }
            val nextSelected = current.bluetoothState.selectedDeviceAddress
                ?.takeIf { addr -> sorted.any { it.address == addr } }
                ?: sorted.firstOrNull { it.isObdCandidate }?.address
                ?: sorted.firstOrNull()?.address
            current.copy(
                bluetoothState = current.bluetoothState.copy(
                    devices = sorted,
                    selectedDeviceAddress = nextSelected,
                    statusMessage = msg
                )
            )
        }
    }

    fun selectMethod(method: ObdConnectionMethod) {
        val currentConnected = (_state.value.connectionStatus as? ObdConnectionStatus.Connected)
        if (currentConnected != null && currentConnected.method == method) {
            _state.update { it.copy(activeMethod = method) }
            return
        }
        closeActiveTransportAsync()
        _state.update { current ->
            current.copy(
                activeMethod = method,
                connectionStatus = ObdConnectionStatus.Disconnected
            )
        }
    }

    fun selectProtocol(protocol: ObdProtocolPreference) {
        _state.update { it.copy(protocolPreference = protocol) }
    }

    fun selectWifiPreset(preset: WifiObdPreset) {
        closeActiveTransportAsync()
        _state.update { current ->
            val updatedWifi = if (preset == WifiObdPreset.CUSTOM) {
                current.wifiState.copy(preset = preset)
            } else {
                current.wifiState.copy(
                    preset = preset,
                    hostInput = preset.defaultHost,
                    portInput = preset.defaultPort.toString()
                )
            }
            current.copy(
                wifiState = updatedWifi,
                connectionStatus = ObdConnectionStatus.Disconnected
            )
        }
    }

    fun updateWifiHost(host: String) {
        _state.update { current ->
            current.copy(
                wifiState = current.wifiState.copy(
                    hostInput = host,
                    preset = WifiObdPreset.CUSTOM
                )
            )
        }
    }

    fun updateWifiPort(port: String) {
        val digitsOnly = port.filter { it.isDigit() }.take(5)
        _state.update { current ->
            current.copy(
                wifiState = current.wifiState.copy(
                    portInput = digitsOnly,
                    preset = WifiObdPreset.CUSTOM
                )
            )
        }
    }

    fun refreshWifiState(context: Context) {
        _state.update { current ->
            current.copy(
                wifiState = wifiAdapter.inspectState(context, current.wifiState)
            )
        }
    }

    fun selectBluetoothArchitecture(context: Context, architecture: BluetoothArchitecture) {
        val showAll = _state.value.bluetoothState.showAllArchitectures
        val wasConnected = _state.value.connectionStatus is ObdConnectionStatus.Connected
        if (!wasConnected) {
            closeActiveTransportAsync()
        }
        bluetoothAdapter.stopDiscovery(context)
        val snapshotList = synchronized(discoveredDevices) { discoveredDevices.toList() }
        val refreshed = bluetoothAdapter.inspectState(
            context = context,
            architecture = architecture,
            existingDiscovered = snapshotList,
            showAllArchitectures = showAll
        )
        _state.update { current ->
            current.copy(
                bluetoothState = refreshed,
                connectionStatus = if (wasConnected) current.connectionStatus else ObdConnectionStatus.Disconnected
            )
        }
    }

    fun setShowAllBluetoothArchitectures(context: Context, showAll: Boolean) {
        val arch = _state.value.bluetoothState.architecture
        val snapshotList = synchronized(discoveredDevices) { discoveredDevices.toList() }
        val refreshed = bluetoothAdapter.inspectState(
            context = context,
            architecture = arch,
            existingDiscovered = snapshotList,
            showAllArchitectures = showAll
        )
        _state.update { current ->
            val keepAddress = current.bluetoothState.selectedDeviceAddress
                ?.takeIf { addr -> refreshed.devices.any { it.address == addr } }
                ?: refreshed.selectedDeviceAddress
            current.copy(
                bluetoothState = refreshed.copy(
                    selectedDeviceAddress = keepAddress,
                    isDiscovering = current.bluetoothState.isDiscovering
                )
            )
        }
    }

    fun requiredBluetoothPermissions(): List<String> = bluetoothAdapter.requiredPermissions()

    fun refreshBluetoothState(context: Context) {
        val currentBt = _state.value.bluetoothState
        val snapshotList = synchronized(discoveredDevices) { discoveredDevices.toList() }
        val refreshed = bluetoothAdapter.inspectState(
            context = context,
            architecture = currentBt.architecture,
            existingDiscovered = snapshotList,
            showAllArchitectures = currentBt.showAllArchitectures
        )
        _state.update { current ->
            val keepAddress = current.bluetoothState.selectedDeviceAddress
                ?.takeIf { addr -> refreshed.devices.any { it.address == addr } }
                ?: refreshed.selectedDeviceAddress
            current.copy(
                bluetoothState = refreshed.copy(
                    selectedDeviceAddress = keepAddress,
                    isDiscovering = current.bluetoothState.isDiscovering
                )
            )
        }
    }

    fun startBleDiscovery(context: Context) {
        val appCtx = context.applicationContext ?: context
        val currentArch = _state.value.bluetoothState.architecture
        val showAll = _state.value.bluetoothState.showAllArchitectures
        val baseState = bluetoothAdapter.inspectState(
            context = appCtx,
            architecture = currentArch,
            existingDiscovered = synchronized(discoveredDevices) { discoveredDevices.toList() },
            showAllArchitectures = showAll
        )
        val th = isThai()
        if (!baseState.hasRequiredPermissions) {
            _state.update {
                it.copy(
                    bluetoothState = baseState,
                    connectionStatus = ObdConnectionStatus.PermissionRequired(
                        if (th) {
                            "กรุณาอนุญาตสิทธิ์ Bluetooth เพื่อค้นหาตัวสแกนเนอร์ OBD-II ใกล้เคียง"
                        } else {
                            "Grant Bluetooth permissions to scan for nearby OBD-II adapters."
                        }
                    )
                )
            }
            return
        }
        if (!baseState.isBluetoothEnabled) {
            _state.update { it.copy(bluetoothState = baseState) }
            return
        }
        val scanningMsg = if (th) {
            "กำลังสแกนหาตัวสแกนเนอร์ OBD-II (Classic + BLE)..."
        } else {
            "Scanning for nearby OBD-II scanners (Classic + BLE)..."
        }
        _state.update { current ->
            current.copy(
                bluetoothState = baseState.copy(
                    isDiscovering = true,
                    selectedDeviceAddress = current.bluetoothState.selectedDeviceAddress
                        ?.takeIf { addr -> baseState.devices.any { it.address == addr } }
                        ?: baseState.selectedDeviceAddress,
                    statusMessage = scanningMsg
                )
            )
        }
        val started = bluetoothAdapter.startUnifiedDiscovery(
            context = appCtx,
            onDeviceDiscovered = { entry ->
                upsertDiscoveredDevice(entry)
            },
            onScanError = { errorMsg ->
                _state.update { current ->
                    current.copy(
                        bluetoothState = current.bluetoothState.copy(
                            isDiscovering = false,
                            statusMessage = errorMsg
                        ),
                        connectionStatus = ObdConnectionStatus.Error(errorMsg)
                    )
                }
            }
        )
        if (!started) {
            _state.update { current ->
                current.copy(
                    bluetoothState = current.bluetoothState.copy(
                        isDiscovering = false,
                        statusMessage = if (current.bluetoothState.devices.isNotEmpty()) {
                            current.bluetoothState.statusMessage
                        } else {
                            baseState.statusMessage
                        }
                    )
                )
            }
        }
    }

    fun stopBleDiscovery(context: Context) {
        bluetoothAdapter.stopDiscovery(context)
        _state.update { current ->
            current.copy(
                bluetoothState = current.bluetoothState.copy(isDiscovering = false)
            )
        }
    }

    fun selectBluetoothDevice(address: String) {
        val currentConnected = (_state.value.connectionStatus as? ObdConnectionStatus.Connected)
        if (currentConnected != null &&
            _state.value.bluetoothState.selectedDeviceAddress != address
        ) {
            closeActiveTransportAsync()
        }
        _state.update { current ->
            val matched = current.bluetoothState.devices.firstOrNull { it.address == address }
            val adjustedArch = matched?.architecture ?: current.bluetoothState.architecture
            current.copy(
                bluetoothState = current.bluetoothState.copy(
                    selectedDeviceAddress = address,
                    architecture = adjustedArch
                ),
                connectionStatus = if (current.bluetoothState.selectedDeviceAddress == address && current.isConnected) {
                    current.connectionStatus
                } else {
                    ObdConnectionStatus.Disconnected
                }
            )
        }
    }

    suspend fun connectTransport(context: Context): ObdConnectionStatus = connectMutex.withLock {
        val current = _state.value
        val activeStream = activeTransport
        val existingConnected = current.connectionStatus as? ObdConnectionStatus.Connected
        if (existingConnected != null && activeStream != null && activeStream.isConnected) {
            val sameTarget = when (current.activeMethod) {
                ObdConnectionMethod.WIFI ->
                    existingConnected.method == ObdConnectionMethod.WIFI &&
                        existingConnected.targetLabel == "Wi-Fi ${current.wifiState.endpointLabel}"
                ObdConnectionMethod.BLUETOOTH ->
                    existingConnected.method == ObdConnectionMethod.BLUETOOTH &&
                        current.bluetoothState.selectedDevice?.displayName == existingConnected.targetLabel
            }
            if (sameTarget) {
                return@withLock existingConnected
            }
        }

        closeActiveTransportSuspending()
        val freshState = _state.value
        return@withLock when (freshState.activeMethod) {
            ObdConnectionMethod.WIFI -> connectWifiInternal(context, freshState)
            ObdConnectionMethod.BLUETOOTH -> connectBluetoothInternal(context, freshState)
        }
    }

    private suspend fun connectWifiInternal(
        context: Context,
        current: ObdConnectionManagerState
    ): ObdConnectionStatus {
        val inspectedWifi = wifiAdapter.inspectState(context, current.wifiState)
        _state.update { it.copy(wifiState = inspectedWifi) }
        val th = isThai()

        if (!inspectedWifi.isWifiSupported) {
            val status = ObdConnectionStatus.Error(
                if (th) "อุปกรณ์นี้ไม่รองรับการเชื่อมต่อ Wi-Fi" else "Wi-Fi is not supported on this device."
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }
        if (!inspectedWifi.isWifiEnabled) {
            val status = ObdConnectionStatus.Error(
                if (th) {
                    "Wi-Fi ของเครื่องปิดอยู่ กรุณาเปิด Wi-Fi แล้วเชื่อมต่อเครือข่ายของตัวสแกนเนอร์ OBD-II"
                } else {
                    "Wi-Fi is turned off. Enable Wi-Fi and join your OBD-II scanner hotspot."
                }
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }
        if (!inspectedWifi.isWifiNetworkConnected) {
            val status = ObdConnectionStatus.Error(
                if (th) {
                    "ยังไม่ได้เชื่อมต่อเครือข่าย Wi-Fi ของตัวสแกนเนอร์ OBD-II กรุณาเลือก Wi-Fi ของตัวสแกนเนอร์ในการตั้งค่าก่อน"
                } else {
                    "Not joined to an OBD-II Wi-Fi hotspot. Connect to your scanner Wi-Fi network in Android settings first."
                }
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        val endpoint = wifiAdapter.validateEndpoint(inspectedWifi.hostInput, inspectedWifi.portInput).getOrElse { err ->
            val fallback = if (th) "ข้อมูล IP หรือ Port ไม่ถูกต้อง" else "Invalid IP address or port."
            val status = ObdConnectionStatus.Error(err.localizedMessage ?: fallback)
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        val targetLabel = "Wi-Fi ${endpoint.first}:${endpoint.second}"
        _state.update {
            it.copy(
                connectionStatus = ObdConnectionStatus.Connecting(
                    targetLabel = targetLabel,
                    method = ObdConnectionMethod.WIFI
                )
            )
        }

        val streamResult = try {
            transportConnector.openWifiTransport(
                context = context,
                host = endpoint.first,
                port = endpoint.second,
                displayLabel = targetLabel
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        return finalizeHandshake(
            targetLabel = targetLabel,
            method = ObdConnectionMethod.WIFI,
            protocolPreference = current.protocolPreference,
            streamResult = streamResult
        )
    }

    private suspend fun connectBluetoothInternal(
        context: Context,
        current: ObdConnectionManagerState
    ): ObdConnectionStatus {
        refreshBluetoothState(context)
        val btState = _state.value.bluetoothState
        val th = isThai()
        if (!btState.isBluetoothSupported) {
            val status = ObdConnectionStatus.Error(
                if (th) "อุปกรณ์นี้ไม่รองรับ Bluetooth" else "Bluetooth is not supported on this device."
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }
        if (!btState.hasRequiredPermissions) {
            val status = ObdConnectionStatus.PermissionRequired(
                if (th) {
                    "กรุณาอนุญาตสิทธิ์ Bluetooth ก่อนเชื่อมต่อกับ ${btState.architecture.localizedTitle(true)}"
                } else {
                    "Grant Bluetooth permissions before connecting via ${btState.architecture.localizedTitle(false)}."
                }
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }
        if (!btState.isBluetoothEnabled) {
            val status = ObdConnectionStatus.Error(
                if (th) {
                    "Bluetooth ของเครื่องปิดอยู่ กรุณาเปิด Bluetooth แล้วกดรีเฟรช"
                } else {
                    "Bluetooth is off. Turn on Bluetooth and tap Refresh."
                }
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }
        var selected = btState.selectedDevice
        if (selected == null) {
            val status = ObdConnectionStatus.Error(
                if (th) {
                    "ตรวจไม่พบอะแดปเตอร์ Bluetooth OBD-II (ยังไม่ได้เลือกอุปกรณ์)"
                } else {
                    "Adapter not detected: No Bluetooth OBD-II device selected."
                }
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        bluetoothAdapter.stopDiscovery(context)

        // Attempt in-app Bluetooth pairing if the selected Classic/Dual adapter is not yet bonded
        if (!selected.isBonded && selected.architecture != BluetoothArchitecture.BLE_GATT) {
            val bondDeferred = CompletableDeferred<Boolean>()
            pendingBondAddress = selected.address
            pendingBondDeferred = bondDeferred
            val requested = bluetoothAdapter.requestBond(context, selected.address)
            if (requested) {
                _state.update {
                    it.copy(
                        bluetoothState = it.bluetoothState.copy(isDiscovering = false),
                        connectionStatus = ObdConnectionStatus.Connecting(
                            targetLabel = if (th) {
                                "กำลังจับคู่ ${selected!!.displayName}..."
                            } else {
                                "Pairing ${selected!!.displayName}..."
                            },
                            method = ObdConnectionMethod.BLUETOOTH
                        )
                    )
                }
                withTimeoutOrNull(18_000L) {
                    bondDeferred.await()
                }
                refreshBluetoothState(context)
                selected = _state.value.bluetoothState.devices
                    .firstOrNull { it.address.equals(selected!!.address, ignoreCase = true) }
                    ?: selected
            }
            pendingBondAddress = null
            pendingBondDeferred = null
        }

        val resolvedSelected = selected!!
        _state.update {
            it.copy(
                bluetoothState = it.bluetoothState.copy(isDiscovering = false),
                connectionStatus = ObdConnectionStatus.Connecting(
                    targetLabel = resolvedSelected.displayName,
                    method = ObdConnectionMethod.BLUETOOTH
                )
            )
        }

        val streamResult = try {
            transportConnector.openBluetoothTransport(
                context = context,
                device = resolvedSelected,
                architecture = btState.architecture
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        return finalizeHandshake(
            targetLabel = resolvedSelected.displayName,
            method = ObdConnectionMethod.BLUETOOTH,
            protocolPreference = current.protocolPreference,
            streamResult = streamResult
        )
    }

    private suspend fun finalizeHandshake(
        targetLabel: String,
        method: ObdConnectionMethod,
        protocolPreference: ObdProtocolPreference,
        streamResult: Result<ObdTransportStream>
    ): ObdConnectionStatus {
        val th = isThai()
        val stream = streamResult.getOrElse { err ->
            val fallback = if (th) {
                "ไม่สามารถเชื่อมต่อกับตัวสแกนเนอร์ OBD-II ($targetLabel) ได้"
            } else {
                "Unable to complete handshake with OBD-II scanner ($targetLabel)."
            }
            val message = err.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
            val status = if (err is SecurityException) {
                ObdConnectionStatus.PermissionRequired(message)
            } else {
                ObdConnectionStatus.Error(message = message, isHardwarePendingIntegration = false)
            }
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        val compat = obdClient.checkDeviceCompatibility(stream, protocolPreference).getOrElse { err ->
            runCatching { stream.close() }
            val fallback = if (th) {
                "ตรวจสอบความเข้ากันได้ของ OBD-II ไม่สำเร็จ"
            } else {
                "Failed to verify OBD-II adapter compatibility."
            }
            val status = ObdConnectionStatus.Error(
                message = err.localizedMessage ?: fallback,
                isHardwarePendingIntegration = false
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        if (compat.adapterFirmwareLabel == null) {
            runCatching { stream.close() }
            val fallback = if (th) {
                "พอร์ตที่เชื่อมต่อ '$targetLabel' ไม่ยืนยันเฟิร์มแวร์มาตรฐาน ELM327"
            } else {
                "Connected target '$targetLabel' did not confirm standard ELM327 firmware."
            }
            val status = ObdConnectionStatus.Error(
                message = compat.diagnosticNotes.firstOrNull() ?: fallback,
                isHardwarePendingIntegration = false
            )
            _state.update { it.copy(connectionStatus = status) }
            return status
        }

        activeTransport = stream
        cachedSupportedPids = emptySet()
        val protoSummary = buildString {
            append(compat.protocolLabel ?: protocolPreference.localizedName(th))
            append(" • ${compat.adapterFirmwareLabel}")
            compat.batteryVoltageVolts?.let { v -> append(" • %.1fV".format(v)) }
        }
        val connected = ObdConnectionStatus.Connected(
            targetLabel = targetLabel,
            method = method,
            protocolLabel = protoSummary
        )
        val readyStatusMessage = if (th) {
            "ยืนยันเฟิร์มแวร์ ${compat.adapterFirmwareLabel} แล้ว • รองรับ ${compat.supportedPidCount} PIDs พื้นฐาน"
        } else {
            "Verified ${compat.adapterFirmwareLabel} firmware • ${compat.supportedPidCount} standard PIDs supported"
        }
        _state.update { current ->
            current.copy(
                connectionStatus = connected,
                wifiState = if (method == ObdConnectionMethod.WIFI) {
                    current.wifiState.copy(statusMessage = readyStatusMessage)
                } else current.wifiState,
                bluetoothState = if (method == ObdConnectionMethod.BLUETOOTH) {
                    current.bluetoothState.copy(
                        architecture = stream.bluetoothArchitecture ?: current.bluetoothState.architecture,
                        statusMessage = readyStatusMessage
                    )
                } else current.bluetoothState
            )
        }

        startLiveTelemetryPolling(stream)
        return connected
    }

    private fun startLiveTelemetryPolling(stream: ObdTransportStream) {
        val oldJob = pollingJob
        pollingJob = cleanupScope.launch {
            oldJob?.cancelAndJoin()
            var cycle = 0
            var consecutiveFailures = 0
            // Small pause lets explicit post-connect callers (or UI transitions) settle first
            delay(150L)
            while (isActive && activeTransport === stream && stream.isConnected) {
                val previous = _liveTelemetry.value.latestSession
                val pref = _state.value.protocolPreference
                val tickResult = try {
                    obdClient.pollLiveTelemetryTick(
                        transport = stream,
                        vehicle = targetVehicleSnapshot,
                        preferredProtocol = pref,
                        cachedSupportedPids = cachedSupportedPids,
                        pollCycleIndex = cycle,
                        previousSession = previous
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }

                tickResult.fold(
                    onSuccess = { session ->
                        consecutiveFailures = 0
                        if (session.supportedPids.isNotEmpty()) {
                            cachedSupportedPids = session.supportedPids
                        }
                        val nextCount = _liveTelemetry.value.pollCount + 1
                        val statusNote = if (isThai()) {
                            "กำลังส่งข้อมูลสดจาก ECU (${session.availableReadings.size} ค่า)"
                        } else {
                            "Streaming live ECU telemetry (${session.availableReadings.size} values)"
                        }
                        val nextVoltages = appendVoltageSample(_liveTelemetry.value.recentVoltageSamples, session)
                        _liveTelemetry.value = LiveTelemetryStreamState(
                            isStreaming = true,
                            lastUpdatedEpochMs = session.capturedAtEpochMs,
                            latestSession = session,
                            pollCount = nextCount,
                            streamStatusNote = statusNote,
                            recentVoltageSamples = nextVoltages,
                            isCommunicationInterrupted = false,
                            lastDisconnectReason = null
                        )
                    },
                    onFailure = { err ->
                        consecutiveFailures++
                        if (!stream.isConnected || consecutiveFailures >= 3) {
                            handleUnexpectedTransportDrop(
                                err.localizedMessage ?: if (isThai()) {
                                    "การเชื่อมต่อกับตัวสแกนเนอร์ OBD-II ขาดหาย"
                                } else {
                                    "OBD-II scanner connection was lost."
                                }
                            )
                            return@launch
                        }
                    }
                )
                cycle++
                delay(1_000L)
            }
            if (!stream.isConnected && activeTransport === stream) {
                handleUnexpectedTransportDrop(
                    if (isThai()) "ตัวสแกนเนอร์ OBD-II ตัดการเชื่อมต่อ" else "OBD-II scanner disconnected."
                )
            }
        }
    }

    private fun handleUnexpectedTransportDrop(reason: String) {
        val old = activeTransport
        activeTransport = null
        pollingJob = null
        cachedSupportedPids = emptySet()
        _liveTelemetry.update {
            it.copy(
                isStreaming = false,
                streamStatusNote = reason,
                isCommunicationInterrupted = true,
                lastDisconnectReason = reason
            )
        }
        _state.update {
            it.copy(
                connectionStatus = ObdConnectionStatus.Error(reason, isHardwarePendingIntegration = false)
            )
        }
        if (old != null) {
            cleanupScope.launch { runCatching { old.close() } }
        }
    }

    /**
     * Temporarily pauses background live polling so an exclusive full read-only scan
     * can run on the transport stream without command contention, then resumes streaming cleanly.
     */
    suspend fun <T> withPollingPaused(block: suspend () -> T): T {
        val active = activeTransportStream
        val priorJob = pollingJob
        pollingJob = null
        priorJob?.cancelAndJoin()
        _liveTelemetry.update { it.copy(isStreaming = false) }
        return try {
            block()
        } finally {
            val currentStream = activeTransportStream
            if (currentStream != null && currentStream.isConnected && (active == null || currentStream === active)) {
                startLiveTelemetryPolling(currentStream)
            }
        }
    }

    fun publishLatestSession(session: ObdDiagnosticSession) {
        if (session.sourceMetadata.sourceType == ObdDataSourceType.MANUAL_DTC_ENTRY || session.isSimulatedTestData) {
            return
        }
        if (session.supportedPids.isNotEmpty()) {
            cachedSupportedPids = session.supportedPids
        }
        _liveTelemetry.update { current ->
            current.copy(
                isStreaming = activeTransportStream != null,
                lastUpdatedEpochMs = session.capturedAtEpochMs,
                latestSession = session,
                pollCount = current.pollCount + 1,
                recentVoltageSamples = appendVoltageSample(current.recentVoltageSamples, session)
            )
        }
    }

    private fun appendVoltageSample(
        existing: List<Double>,
        session: ObdDiagnosticSession
    ): List<Double> {
        val v = session.livePidReadings
            .firstOrNull { it.pidCode.equals("0142", ignoreCase = true) && it.isAvailable }
            ?.value
            ?: return existing
        return (existing + v).takeLast(8)
    }

    fun attemptConnect() {
        val current = _state.value
        val resultStatus = when (current.activeMethod) {
            ObdConnectionMethod.WIFI -> wifiAdapter.validateAndPrepareConnection(
                state = current.wifiState,
                protocol = current.protocolPreference
            )
            ObdConnectionMethod.BLUETOOTH -> bluetoothAdapter.validateAndPrepareConnection(
                state = current.bluetoothState,
                protocol = current.protocolPreference
            )
        }
        _state.update { it.copy(connectionStatus = resultStatus) }
    }

    suspend fun disconnectAsync(context: Context? = null) {
        if (context != null) {
            bluetoothAdapter.stopDiscovery(context)
        }
        closeActiveTransportSuspending()
        _state.update { current ->
            current.copy(connectionStatus = ObdConnectionStatus.Disconnected)
        }
    }

    fun disconnect(context: Context? = null) {
        if (context != null) {
            bluetoothAdapter.stopDiscovery(context)
        }
        closeActiveTransportAsync()
        _state.update { current ->
            current.copy(connectionStatus = ObdConnectionStatus.Disconnected)
        }
    }

    private suspend fun closeActiveTransportSuspending() {
        val job = pollingJob
        pollingJob = null
        job?.cancelAndJoin()
        cachedSupportedPids = emptySet()
        _liveTelemetry.value = LiveTelemetryStreamState()
        val old = activeTransport
        activeTransport = null
        if (old != null) {
            runCatching { old.close() }
        }
    }

    private fun closeActiveTransportAsync() {
        val job = pollingJob
        pollingJob = null
        cachedSupportedPids = emptySet()
        _liveTelemetry.value = LiveTelemetryStreamState()
        val old = activeTransport
        activeTransport = null
        cleanupScope.launch {
            runCatching { job?.cancelAndJoin() }
            if (old != null) {
                runCatching { old.close() }
            }
        }
    }

    private fun defaultVehicleSnapshot() = ObdVehicleSnapshot(
        vehicleId = "",
        manufacturer = "Vehicle",
        model = "",
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
}
