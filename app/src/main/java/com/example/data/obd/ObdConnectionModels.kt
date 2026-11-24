package com.example.data.obd

import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore

enum class ObdConnectionMethod(val label: String) {
    WIFI("Wi-Fi OBD-II"),
    BLUETOOTH("Bluetooth OBD-II")
}

enum class BluetoothArchitecture(
    private val titleEn: String,
    private val titleTh: String,
    private val detailEn: String,
    private val detailTh: String
) {
    CLASSIC_SPP(
        titleEn = "Bluetooth Classic (SPP / RFCOMM)",
        titleTh = "Bluetooth Classic (SPP / RFCOMM)",
        detailEn = "Standard serial port profile (UUID 00001101) for ELM327 adapters and OBDLink MX/LX. Pair the scanner in Android Bluetooth settings first before connecting.",
        detailTh = "โปรไฟล์พอร์ตอนุกรมมาตรฐาน (UUID 00001101) ใช้กับหัวอ่าน ELM327 ทั่วไป และ OBDLink MX/LX ต้องจับคู่อุปกรณ์ในการตั้งค่า Bluetooth ของเครื่องก่อนเชื่อมต่อ"
    ),
    BLE_GATT(
        titleEn = "Bluetooth Low Energy (BLE / GATT)",
        titleTh = "Bluetooth Low Energy (BLE / GATT)",
        detailEn = "Low-energy GATT communication (e.g., OBDLink CX, Veepeak BLE+). Scans directly inside the app and verifies characteristic UUIDs during handshake.",
        detailTh = "การสื่อสารแบบประหยัดพลังงาน GATT (เช่น OBDLink CX, Veepeak BLE+) โดยระบบจะตรวจสอบ UUID และสื่อสารกับตัวสแกนเนอร์ระหว่างการจับมือเชื่อมต่อ"
    );

    val title: String
        get() = localizedTitle(LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI)

    val technicalDetail: String
        get() = localizedDetail(LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI)

    fun localizedTitle(isThai: Boolean): String = if (isThai) titleTh else titleEn

    fun localizedDetail(isThai: Boolean): String = if (isThai) detailTh else detailEn
}

enum class WifiObdPreset(
    val defaultHost: String,
    val defaultPort: Int,
    private val labelEn: String,
    private val labelTh: String
) {
    STANDARD_ELM327(
        defaultHost = "192.168.0.10",
        defaultPort = 35000,
        labelEn = "Standard ELM327 (192.168.0.10:35000)",
        labelTh = "ELM327 มาตรฐาน (192.168.0.10:35000)"
    ),
    ALT_PORT_23(
        defaultHost = "192.168.0.10",
        defaultPort = 23,
        labelEn = "Alt Port (192.168.0.10:23)",
        labelTh = "พอร์ตสำรอง (192.168.0.10:23)"
    ),
    ALT_SUBNET(
        defaultHost = "192.168.1.10",
        defaultPort = 35000,
        labelEn = "Alt Subnet (192.168.1.10:35000)",
        labelTh = "ซับเน็ต 192.168.1.10:35000"
    ),
    CUSTOM(
        defaultHost = "192.168.0.10",
        defaultPort = 35000,
        labelEn = "Custom IP / Port",
        labelTh = "กำหนด IP / Port เอง"
    );

    val displayLabel: String
        get() = localizedLabel(LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI)

    fun localizedLabel(isThai: Boolean): String = if (isThai) labelTh else labelEn
}

enum class ObdProtocolPreference(
    val atCommandCode: String,
    private val nameEn: String,
    private val nameTh: String,
    private val summaryEn: String,
    private val summaryTh: String
) {
    AUTO(
        atCommandCode = "ATSP0",
        nameEn = "Auto Detect (ATSP0)",
        nameTh = "ตรวจจับอัตโนมัติ (ATSP0)",
        summaryEn = "Automatically detects your vehicle ECU bus protocol during connection handshake.",
        summaryTh = "ค้นหาโปรโตคอลบัสของกล่อง ECU รถยนต์อัตโนมัติระหว่างเชื่อมต่อ"
    ),
    CAN_11_500(
        atCommandCode = "ATSP6",
        nameEn = "ISO 15765-4 CAN (11-bit, 500 kbps)",
        nameTh = "ISO 15765-4 CAN (11-bit, 500 kbps)",
        summaryEn = "Standard for most petrol, diesel, and hybrid vehicles from 2008 onward.",
        summaryTh = "ใช้กับรถเบนซิน ดีเซล และไฮบริดส่วนใหญ่ตั้งแต่ปี 2008 ขึ้นไป"
    ),
    CAN_29_500(
        atCommandCode = "ATSP7",
        nameEn = "ISO 15765-4 CAN (29-bit, 500 kbps)",
        nameTh = "ISO 15765-4 CAN (29-bit, 500 kbps)",
        summaryEn = "Used by select Honda, Volvo, FCA models, and commercial vehicles.",
        summaryTh = "ใช้ในรถบางรุ่นของ Honda, Volvo, FCA และรถเพื่อการพาณิชย์"
    ),
    KWP_FAST(
        atCommandCode = "ATSP5",
        nameEn = "ISO 14230-4 KWP2000 (Fast Init)",
        nameTh = "ISO 14230-4 KWP2000 (Fast Init)",
        summaryEn = "Common on European and Asian vehicles from the early 2000s.",
        summaryTh = "พบบ่อยในรถยุโรปและเอเชียช่วงต้นปี 2000"
    ),
    ISO_9141(
        atCommandCode = "ATSP3",
        nameEn = "ISO 9141-2",
        nameTh = "ISO 9141-2",
        summaryEn = "Common on 1996–2004 Asian and European passenger cars.",
        summaryTh = "รถเก๋งเอเชียและยุโรปรุ่นปี 1996–2004"
    ),
    J1850_PWM(
        atCommandCode = "ATSP1",
        nameEn = "SAE J1850 PWM",
        nameTh = "SAE J1850 PWM",
        summaryEn = "Legacy Ford and Mazda diagnostic bus prior to CAN transition.",
        summaryTh = "ระบบบัสวินิจฉัยรุ่นเก่าของ Ford / Mazda ก่อนยุค CAN"
    );

    val displayName: String
        get() = localizedName(LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI)

    val summary: String
        get() = localizedSummary(LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI)

    fun localizedName(isThai: Boolean): String = if (isThai) nameTh else nameEn

    fun localizedSummary(isThai: Boolean): String = if (isThai) summaryTh else summaryEn
}

data class WifiAdapterProfile(
    val name: String,
    val summary: String
)

object WifiObdCatalog {
    fun localizedProfiles(isThai: Boolean): List<WifiAdapterProfile> = if (isThai) {
        listOf(
            WifiAdapterProfile(
                name = "ELM327 Wi-Fi / Vgate iCar Wi-Fi",
                summary = "เชื่อมต่อกับ Wi-Fi ชื่อ WiFi_OBDII หรือ V-LINK โดยทั่วไปใช้ IP 192.168.0.10 พอร์ต 35000"
            ),
            WifiAdapterProfile(
                name = "OBDLink MX Wi-Fi / โปรโตคอล STN",
                summary = "อะแดปเตอร์ Wi-Fi ความเร็วสูง ใช้ TCP 192.168.0.10:35000 หรือ IP ที่ตั้งค่าไว้ในตัวอุปกรณ์"
            ),
            WifiAdapterProfile(
                name = "อะแดปเตอร์พอร์ต Telnet (Port 23)",
                summary = "ตัวสแกนเนอร์ Wi-Fi รุ่นเก่าบางยี่ห้อเปิดพอร์ต TCP 23 หรือ IP 192.168.1.10 พอร์ต 35000"
            )
        )
    } else {
        listOf(
            WifiAdapterProfile(
                name = "ELM327 Wi-Fi / Vgate iCar Wi-Fi",
                summary = "Join Wi-Fi network WiFi_OBDII or V-LINK; typically uses IP 192.168.0.10 on Port 35000."
            ),
            WifiAdapterProfile(
                name = "OBDLink MX Wi-Fi / STN Protocol",
                summary = "High-speed Wi-Fi OBD adapter using TCP 192.168.0.10:35000 or configured custom IP."
            ),
            WifiAdapterProfile(
                name = "Telnet Port Adapters (Port 23)",
                summary = "Some older Wi-Fi scanners listen on TCP Port 23 or subnet 192.168.1.10 Port 35000."
            )
        )
    }
}

data class BluetoothDeviceEntry(
    val address: String,
    val displayName: String,
    val architecture: BluetoothArchitecture?,
    val isBonded: Boolean,
    val rssiDbm: Int? = null,
    val isObdCandidate: Boolean = ObdAdapterMatcher.isLikelyObdAdapter(displayName)
) {
    fun localizedArchBadge(isThai: Boolean): String = when (architecture) {
        BluetoothArchitecture.CLASSIC_SPP -> "Classic SPP"
        BluetoothArchitecture.BLE_GATT -> "BLE GATT"
        null -> if (isThai) "รองรับอัตโนมัติ" else "Dual / Auto"
    }
}

object ObdAdapterMatcher {
    private val KNOWN_OBD_KEYWORDS = listOf(
        "OBD", "ELM", "V-LINK", "VLINK", "VGATE", "ICAR",
        "VEEPEAK", "KONNWEI", "CARISTA", "OBDLINK", "VIECAR",
        "BAFX", "ANCEL", "THINK", "FOXWELL", "TONWON",
        "NEXAS", "BLUEDRIVER", "STN", "KWP", "CAN", "ECU"
    )

    fun isLikelyObdAdapter(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return KNOWN_OBD_KEYWORDS.any { upper.contains(it) }
    }
}

sealed interface ObdConnectionStatus {
    data object Disconnected : ObdConnectionStatus
    data class PermissionRequired(val reason: String) : ObdConnectionStatus
    data class Connecting(val targetLabel: String, val method: ObdConnectionMethod) : ObdConnectionStatus
    data class Connected(
        val targetLabel: String,
        val method: ObdConnectionMethod,
        val protocolLabel: String
    ) : ObdConnectionStatus
    data class Error(val message: String, val isHardwarePendingIntegration: Boolean = false) : ObdConnectionStatus
}

data class LiveTelemetryStreamState(
    val isStreaming: Boolean = false,
    val lastUpdatedEpochMs: Long? = null,
    val latestSession: ObdDiagnosticSession? = null,
    val pollCount: Int = 0,
    val streamStatusNote: String? = null,
    val recentVoltageSamples: List<Double> = emptyList(),
    val isCommunicationInterrupted: Boolean = false,
    val lastDisconnectReason: String? = null
)

data class WifiAdapterUiState(
    val isWifiSupported: Boolean = true,
    val isWifiEnabled: Boolean = false,
    val isWifiNetworkConnected: Boolean = false,
    val connectedSsidLabel: String? = null,
    val preset: WifiObdPreset = WifiObdPreset.STANDARD_ELM327,
    val hostInput: String = WifiObdPreset.STANDARD_ELM327.defaultHost,
    val portInput: String = WifiObdPreset.STANDARD_ELM327.defaultPort.toString(),
    val statusMessage: String? = null
) {
    val endpointLabel: String
        get() = "${hostInput.trim().ifBlank { "192.168.0.10" }}:${portInput.trim().ifBlank { "35000" }}"
}

data class BluetoothAdapterUiState(
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val architecture: BluetoothArchitecture = BluetoothArchitecture.CLASSIC_SPP,
    val showAllArchitectures: Boolean = true,
    val isDiscovering: Boolean = false,
    val devices: List<BluetoothDeviceEntry> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val statusMessage: String? = null
) {
    val selectedDevice: BluetoothDeviceEntry?
        get() = devices.firstOrNull { it.address == selectedDeviceAddress } ?: devices.firstOrNull()
}

data class ObdConnectionManagerState(
    val activeMethod: ObdConnectionMethod = ObdConnectionMethod.WIFI,
    val protocolPreference: ObdProtocolPreference = ObdProtocolPreference.AUTO,
    val connectionStatus: ObdConnectionStatus = ObdConnectionStatus.Disconnected,
    val wifiState: WifiAdapterUiState = WifiAdapterUiState(),
    val bluetoothState: BluetoothAdapterUiState = BluetoothAdapterUiState(),
    val isReadOnlyEnforced: Boolean = true,
    val systemVoltageCriteria: SystemVoltageCriteria = SystemVoltageCriteria()
) {
    val isConnected: Boolean
        get() = connectionStatus is ObdConnectionStatus.Connected

    val activeAdapterLabel: String?
        get() = (connectionStatus as? ObdConnectionStatus.Connected)?.targetLabel
}
