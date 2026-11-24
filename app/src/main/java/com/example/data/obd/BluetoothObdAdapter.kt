package com.example.data.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore

interface BluetoothHardwarePort {
    fun requiredPermissions(): List<String>
    fun missingPermissions(context: Context): List<String>
    fun isBluetoothSupported(context: Context): Boolean
    fun isBluetoothEnabled(context: Context): Boolean
    fun listPairedDevices(context: Context): List<BluetoothDeviceEntry>
    fun startBleScan(
        context: Context,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onScanError: (String) -> Unit
    ): Boolean
    fun stopBleScan(context: Context)

    fun startClassicDiscovery(context: Context): Boolean = false
    fun stopClassicDiscovery(context: Context) {}
    fun requestBond(context: Context, address: String): Boolean = false
    fun registerSystemBluetoothReceiver(
        context: Context,
        onBluetoothStateChanged: (isEnabled: Boolean) -> Unit,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onDiscoveryFinished: () -> Unit,
        onBondStateChanged: (address: String, bondState: Int) -> Unit
    ): (() -> Unit)? = null
}

class AndroidBluetoothHardwarePort : BluetoothHardwarePort {

    private var activeBleCallback: ScanCallback? = null

    private fun isThai(): Boolean =
        LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI

    override fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    override fun missingPermissions(context: Context): List<String> {
        return requiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    override fun isBluetoothSupported(context: Context): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        return hasFeature && bluetoothAdapter(context) != null
    }

    override fun isBluetoothEnabled(context: Context): Boolean {
        return bluetoothAdapter(context)?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    override fun listPairedDevices(context: Context): List<BluetoothDeviceEntry> {
        if (missingPermissions(context).isNotEmpty()) return emptyList()
        val adapter = bluetoothAdapter(context) ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { device -> device.toEntry(isBonded = true, rssi = null) }
                .sortedBy { it.displayName.lowercase() }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    override fun startBleScan(
        context: Context,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onScanError: (String) -> Unit
    ): Boolean {
        val th = isThai()
        if (missingPermissions(context).isNotEmpty()) {
            onScanError(
                if (th) {
                    "ต้องได้รับสิทธิ์ Bluetooth ก่อนสแกนหาอุปกรณ์ใกล้เคียง"
                } else {
                    "Bluetooth permissions are required before scanning nearby OBD devices."
                }
            )
            return false
        }
        val adapter = bluetoothAdapter(context)
        if (adapter == null || !adapter.isEnabled) {
            onScanError(
                if (th) {
                    "Bluetooth ปิดอยู่หรือไม่พร้อมใช้งานบนอุปกรณ์นี้"
                } else {
                    "Bluetooth is turned off or unavailable on this device."
                }
            )
            return false
        }
        val scanner = adapter.bluetoothLeScanner ?: return false
        stopBleScan(context)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                onDeviceDiscovered(
                    device.toEntry(
                        isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                        rssi = result.rssi
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                onScanError(
                    if (isThai()) {
                        "สแกน BLE ไม่สำเร็จ (รหัสข้อผิดพลาด $errorCode)"
                    } else {
                        "BLE scan failed (error code $errorCode)."
                    }
                )
            }
        }
        activeBleCallback = callback
        return runCatching {
            scanner.startScan(callback)
            true
        }.getOrElse { err ->
            activeBleCallback = null
            onScanError(
                err.localizedMessage ?: if (th) "ไม่สามารถเริ่มสแกน BLE ได้" else "Unable to start BLE scan."
            )
            false
        }
    }

    @SuppressLint("MissingPermission")
    override fun stopBleScan(context: Context) {
        val callback = activeBleCallback ?: return
        activeBleCallback = null
        if (missingPermissions(context).isNotEmpty()) return
        val scanner = bluetoothAdapter(context)?.bluetoothLeScanner ?: return
        runCatching { scanner.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    override fun startClassicDiscovery(context: Context): Boolean {
        if (missingPermissions(context).isNotEmpty()) return false
        val adapter = bluetoothAdapter(context) ?: return false
        if (!adapter.isEnabled) return false
        return runCatching {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override fun stopClassicDiscovery(context: Context) {
        if (missingPermissions(context).isNotEmpty()) return
        val adapter = bluetoothAdapter(context) ?: return
        runCatching {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestBond(context: Context, address: String): Boolean {
        if (missingPermissions(context).isNotEmpty()) return false
        val adapter = bluetoothAdapter(context) ?: return false
        if (!adapter.isEnabled) return false
        return runCatching {
            val device = adapter.getRemoteDevice(address) ?: return@runCatching false
            if (device.bondState == BluetoothDevice.BOND_BONDED) return@runCatching true
            device.createBond()
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override fun registerSystemBluetoothReceiver(
        context: Context,
        onBluetoothStateChanged: (isEnabled: Boolean) -> Unit,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onDiscoveryFinished: () -> Unit,
        onBondStateChanged: (address: String, bondState: Int) -> Unit
    ): (() -> Unit)? {
        val appCtx = context.applicationContext ?: context
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (btState == BluetoothAdapter.STATE_ON) {
                            onBluetoothStateChanged(true)
                        } else if (btState == BluetoothAdapter.STATE_OFF || btState == BluetoothAdapter.STATE_TURNING_OFF) {
                            onBluetoothStateChanged(false)
                        }
                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        if (missingPermissions(appCtx).isNotEmpty()) return
                        val device = extractBluetoothDevice(intent) ?: return
                        val rssi = if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) {
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                                .takeIf { it != Short.MIN_VALUE }?.toInt()
                        } else null
                        onDeviceDiscovered(
                            device.toEntry(
                                isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
                                rssi = rssi
                            )
                        )
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        onDiscoveryFinished()
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = extractBluetoothDevice(intent) ?: return
                        val bondState = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE,
                            BluetoothDevice.BOND_NONE
                        )
                        val addr = device.address.orEmpty()
                        if (addr.isNotBlank()) {
                            onBondStateChanged(addr, bondState)
                        }
                    }
                }
            }
        }
        return runCatching {
            ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            val unregister: () -> Unit = {
                runCatching { appCtx.unregisterReceiver(receiver) }
            }
            unregister
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toEntry(isBonded: Boolean, rssi: Int?): BluetoothDeviceEntry {
        val arch = when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothArchitecture.CLASSIC_SPP
            BluetoothDevice.DEVICE_TYPE_LE -> BluetoothArchitecture.BLE_GATT
            BluetoothDevice.DEVICE_TYPE_DUAL -> null
            else -> null
        }
        val fallbackName = if (isThai()) "อุปกรณ์ไม่ระบุชื่อ ($address)" else "Unnamed Device ($address)"
        return BluetoothDeviceEntry(
            address = address.orEmpty(),
            displayName = name?.takeIf { it.isNotBlank() } ?: fallbackName,
            architecture = arch,
            isBonded = isBonded,
            rssiDbm = rssi
        )
    }
}

class BluetoothObdAdapter(
    private val hardwarePort: BluetoothHardwarePort = AndroidBluetoothHardwarePort()
) {
    private fun isThai(): Boolean =
        LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI

    fun requiredPermissions(): List<String> = hardwarePort.requiredPermissions()

    fun isBluetoothEnabled(context: Context): Boolean =
        hardwarePort.isBluetoothSupported(context) && hardwarePort.isBluetoothEnabled(context)

    fun hasRequiredPermissions(context: Context): Boolean =
        hardwarePort.missingPermissions(context).isEmpty()

    fun inspectState(
        context: Context,
        architecture: BluetoothArchitecture,
        existingDiscovered: List<BluetoothDeviceEntry> = emptyList(),
        showAllArchitectures: Boolean = true
    ): BluetoothAdapterUiState {
        val supported = hardwarePort.isBluetoothSupported(context)
        val enabled = supported && hardwarePort.isBluetoothEnabled(context)
        val missing = hardwarePort.missingPermissions(context)
        val hasPermissions = missing.isEmpty()
        val th = isThai()

        val paired = if (enabled && hasPermissions) {
            hardwarePort.listPairedDevices(context)
        } else {
            emptyList()
        }

        val combined = sortDevicesForSelection(
            devices = (paired + existingDiscovered).distinctBy { it.address },
            preferredArchitecture = architecture,
            showAllArchitectures = showAllArchitectures
        )

        val obdCount = combined.count { it.isObdCandidate }
        val message = when {
            !supported -> if (th) {
                "อุปกรณ์นี้ไม่รองรับ Bluetooth"
            } else {
                "Bluetooth hardware is not supported on this device."
            }
            !hasPermissions -> if (th) {
                "จำเป็นต้องอนุญาตสิทธิ์ Bluetooth เพื่อแสดงอุปกรณ์ที่จับคู่ไว้และค้นหาตัวสแกนเนอร์ OBD-II"
            } else {
                "Bluetooth permission is required to list paired devices and scan for OBD-II adapters."
            }
            !enabled -> if (th) {
                "Bluetooth ปิดอยู่ กดปุ่มเปิด Bluetooth ด้านล่างหรือเปิดในการตั้งค่าของเครื่อง ระบบจะค้นหาอุปกรณ์ OBD ให้อัตโนมัติ"
            } else {
                "Bluetooth is turned off. Tap Enable Bluetooth below or turn it on in system settings — OBD devices will appear automatically."
            }
            combined.isEmpty() -> if (th) {
                "ยังไม่พบตัวสแกนเนอร์ OBD-II กด \"สแกนหาอุปกรณ์ OBD\" หรือจับคู่อุปกรณ์ในการตั้งค่า Bluetooth"
            } else {
                "No OBD-II adapters found yet. Tap \"Scan OBD Devices\" or pair your adapter in Bluetooth settings."
            }
            obdCount > 0 -> if (th) {
                "พบอุปกรณ์ Bluetooth ${combined.size} รายการ (พบตัวสแกน OBD-II ที่แนะนำ $obdCount รายการ)"
            } else {
                "Found ${combined.size} Bluetooth device(s) ($obdCount likely OBD-II adapter(s))."
            }
            else -> if (th) {
                "พบ ${combined.size} อุปกรณ์สำหรับ ${architecture.localizedTitle(true)}"
            } else {
                "Found ${combined.size} device(s) for ${architecture.localizedTitle(false)}."
            }
        }

        return BluetoothAdapterUiState(
            isBluetoothSupported = supported,
            isBluetoothEnabled = enabled,
            hasRequiredPermissions = hasPermissions,
            missingPermissions = missing,
            architecture = architecture,
            showAllArchitectures = showAllArchitectures,
            isDiscovering = false,
            devices = combined,
            selectedDeviceAddress = combined.firstOrNull()?.address,
            statusMessage = message
        )
    }

    fun sortDevicesForSelection(
        devices: List<BluetoothDeviceEntry>,
        preferredArchitecture: BluetoothArchitecture,
        showAllArchitectures: Boolean = true
    ): List<BluetoothDeviceEntry> {
        val filtered = if (showAllArchitectures) {
            devices
        } else {
            devices.filter { entry ->
                entry.architecture == null || entry.architecture == preferredArchitecture
            }
        }
        return filtered.sortedWith(
            compareByDescending<BluetoothDeviceEntry> { it.architecture == preferredArchitecture }
                .thenByDescending { it.isObdCandidate }
                .thenByDescending { it.isBonded }
                .thenByDescending { it.architecture == null }
                .thenBy { it.displayName.lowercase() }
        )
    }

    fun startUnifiedDiscovery(
        context: Context,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onScanError: (String) -> Unit
    ): Boolean {
        var bleError: String? = null
        val bleStarted = hardwarePort.startBleScan(
            context = context,
            onDeviceDiscovered = onDeviceDiscovered,
            onScanError = { bleError = it }
        )
        val classicStarted = hardwarePort.startClassicDiscovery(context)
        if (!bleStarted && !classicStarted && bleError != null) {
            onScanError(bleError!!)
        }
        return bleStarted || classicStarted
    }

    fun startBleDiscovery(
        context: Context,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onScanError: (String) -> Unit
    ): Boolean {
        return startUnifiedDiscovery(context, onDeviceDiscovered, onScanError)
    }

    fun stopDiscovery(context: Context) {
        hardwarePort.stopBleScan(context)
        hardwarePort.stopClassicDiscovery(context)
    }

    fun requestBond(context: Context, address: String): Boolean =
        hardwarePort.requestBond(context, address)

    fun registerSystemBluetoothReceiver(
        context: Context,
        onBluetoothStateChanged: (isEnabled: Boolean) -> Unit,
        onDeviceDiscovered: (BluetoothDeviceEntry) -> Unit,
        onDiscoveryFinished: () -> Unit,
        onBondStateChanged: (address: String, bondState: Int) -> Unit
    ): (() -> Unit)? = hardwarePort.registerSystemBluetoothReceiver(
        context = context,
        onBluetoothStateChanged = onBluetoothStateChanged,
        onDeviceDiscovered = onDeviceDiscovered,
        onDiscoveryFinished = onDiscoveryFinished,
        onBondStateChanged = onBondStateChanged
    )

    fun validateAndPrepareConnection(
        state: BluetoothAdapterUiState,
        protocol: ObdProtocolPreference
    ): ObdConnectionStatus {
        val th = isThai()
        if (!state.isBluetoothSupported) {
            return ObdConnectionStatus.Error(
                if (th) "อุปกรณ์นี้ไม่รองรับ Bluetooth" else "Bluetooth is not supported on this device."
            )
        }
        if (!state.hasRequiredPermissions) {
            return ObdConnectionStatus.PermissionRequired(
                reason = if (th) {
                    "กรุณาอนุญาตสิทธิ์ Bluetooth ก่อนเชื่อมต่อกับ ${state.architecture.localizedTitle(true)}"
                } else {
                    "Grant Bluetooth permissions before connecting via ${state.architecture.localizedTitle(false)}."
                }
            )
        }
        if (!state.isBluetoothEnabled) {
            return ObdConnectionStatus.Error(
                if (th) {
                    "Bluetooth ของเครื่องปิดอยู่ กรุณาเปิด Bluetooth แล้วกดรีเฟรช"
                } else {
                    "Bluetooth is off. Turn on Bluetooth and tap Refresh."
                }
            )
        }
        val selected = state.selectedDevice
            ?: return ObdConnectionStatus.Error(
                if (th) "ยังไม่ได้เลือกอุปกรณ์ Bluetooth OBD-II" else "No Bluetooth OBD-II device selected."
            )

        return ObdConnectionStatus.Error(
            message = if (th) {
                "เลือก '${selected.displayName}' (${state.architecture.localizedTitle(true)}, ${protocol.localizedName(true)}) แล้ว แต่ยังไม่พบการตอบสนองจากตัวสแกนเนอร์จริง"
            } else {
                "Selected '${selected.displayName}' (${state.architecture.localizedTitle(false)}, ${protocol.localizedName(false)}), but no live scanner response was received."
            },
            isHardwarePendingIntegration = true
        )
    }
}
