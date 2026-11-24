package com.example.data.obd

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore

interface WifiHardwarePort {
    fun isWifiSupported(context: Context): Boolean
    fun isWifiEnabled(context: Context): Boolean
    fun isWifiConnected(context: Context): Boolean
    fun currentSsidLabel(context: Context): String?
}

class AndroidWifiHardwarePort : WifiHardwarePort {

    override fun isWifiSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
    }

    @Suppress("DEPRECATION")
    override fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    override fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    override fun currentSsidLabel(context: Context): String? {
        if (!isWifiConnected(context)) return null
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val raw = runCatching { wifiManager?.connectionInfo?.ssid }.getOrNull()?.trim() ?: return null
        if (raw.isBlank() || raw == "<unknown ssid>") {
            return if (LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI) {
                "เครือข่าย Wi-Fi ที่เชื่อมต่ออยู่"
            } else {
                "Connected Wi-Fi Network"
            }
        }
        return raw.removePrefix("\"").removeSuffix("\"")
    }
}

class WifiObdAdapter(
    private val hardwarePort: WifiHardwarePort = AndroidWifiHardwarePort()
) {
    private fun isThai(): Boolean =
        LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI

    fun inspectState(
        context: Context,
        current: WifiAdapterUiState = WifiAdapterUiState()
    ): WifiAdapterUiState {
        val supported = hardwarePort.isWifiSupported(context)
        val enabled = supported && hardwarePort.isWifiEnabled(context)
        val connected = enabled && hardwarePort.isWifiConnected(context)
        val ssid = if (connected) hardwarePort.currentSsidLabel(context) else null
        val th = isThai()

        val message = when {
            !supported -> if (th) {
                "อุปกรณ์นี้ไม่รองรับการเชื่อมต่อ Wi-Fi"
            } else {
                "Wi-Fi hardware is not supported on this device."
            }
            !enabled -> if (th) {
                "Wi-Fi ของเครื่องปิดอยู่ กรุณาเปิด Wi-Fi แล้วเชื่อมต่อเครือข่ายของตัวสแกนเนอร์ OBD-II"
            } else {
                "Wi-Fi is turned off. Turn on Wi-Fi and join your OBD-II scanner hotspot."
            }
            !connected -> if (th) {
                "ยังไม่ได้เชื่อมต่อเครือข่าย Wi-Fi กรุณาเชื่อมต่อ Wi-Fi ของตัวสแกนเนอร์ (เช่น WiFi_OBDII) ก่อนเริ่มตรวจสอบ"
            } else {
                "Not connected to a Wi-Fi network. Join your OBD-II scanner hotspot (e.g., WiFi_OBDII) first."
            }
            ssid != null -> if (th) {
                "เชื่อมต่อเครือข่าย $ssid อยู่ • พร้อมทดสอบสัญญาณกับ ${current.endpointLabel}"
            } else {
                "Joined $ssid • Ready to verify ${current.endpointLabel}"
            }
            else -> if (th) {
                "Wi-Fi พร้อมใช้งาน • พร้อมทดสอบสัญญาณกับ ${current.endpointLabel}"
            } else {
                "Wi-Fi active • Ready to verify ${current.endpointLabel}"
            }
        }

        return current.copy(
            isWifiSupported = supported,
            isWifiEnabled = enabled,
            isWifiNetworkConnected = connected,
            connectedSsidLabel = ssid,
            statusMessage = message
        )
    }

    fun validateEndpoint(hostInput: String, portInput: String): Result<Pair<String, Int>> {
        val th = isThai()
        val host = hostInput.trim()
        if (host.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    if (th) {
                        "กรุณากรอก IP Address ของอะแดปเตอร์ Wi-Fi OBD-II (ค่าเริ่มต้น 192.168.0.10)"
                    } else {
                        "Enter the Wi-Fi OBD-II adapter IP address (default 192.168.0.10)."
                    }
                )
            )
        }
        val port = portInput.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            return Result.failure(
                IllegalArgumentException(
                    if (th) {
                        "หมายเลข Port ต้องอยู่ระหว่าง 1 ถึง 65535 (ค่ามาตรฐานคือ 35000 หรือ 23)"
                    } else {
                        "TCP port must be between 1 and 65535 (commonly 35000 or 23)."
                    }
                )
            )
        }
        return Result.success(host to port)
    }

    fun validateAndPrepareConnection(
        state: WifiAdapterUiState,
        protocol: ObdProtocolPreference
    ): ObdConnectionStatus {
        val th = isThai()
        if (!state.isWifiSupported) {
            return ObdConnectionStatus.Error(
                if (th) "อุปกรณ์นี้ไม่รองรับการเชื่อมต่อ Wi-Fi" else "Wi-Fi is not supported on this device."
            )
        }
        if (!state.isWifiEnabled) {
            return ObdConnectionStatus.Error(
                if (th) {
                    "กรุณาเปิด Wi-Fi ของเครื่องและเชื่อมต่อเครือข่ายของอะแดปเตอร์ OBD-II ก่อน"
                } else {
                    "Turn on Wi-Fi and join your OBD-II scanner hotspot before connecting."
                }
            )
        }
        if (!state.isWifiNetworkConnected) {
            return ObdConnectionStatus.Error(
                if (th) {
                    "ยังไม่ได้เชื่อมต่อเครือข่าย Wi-Fi ของตัวสแกนเนอร์ OBD-II กรุณาเชื่อมต่อในตั้งค่า Wi-Fi ของเครื่องก่อน"
                } else {
                    "Phone is not connected to the OBD-II scanner Wi-Fi hotspot. Join it in Android Wi-Fi settings first."
                }
            )
        }
        val endpoint = validateEndpoint(state.hostInput, state.portInput).getOrElse { err ->
            return ObdConnectionStatus.Error(
                err.localizedMessage ?: if (th) "ข้อมูล IP หรือ Port ไม่ถูกต้อง" else "Invalid IP address or port."
            )
        }
        return ObdConnectionStatus.Error(
            message = if (th) {
                "ตั้งค่าปลายทาง ${endpoint.first}:${endpoint.second} (${protocol.localizedName(true)}) เรียบร้อย ยังไม่พบการตอบสนองจากตัวสแกนเนอร์จริง"
            } else {
                "Configured target ${endpoint.first}:${endpoint.second} (${protocol.localizedName(false)}), but no live scanner response was received."
            },
            isHardwarePendingIntegration = true
        )
    }
}
