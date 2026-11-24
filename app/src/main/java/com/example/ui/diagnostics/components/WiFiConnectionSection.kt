package com.example.ui.diagnostics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.WifiAdapterUiState
import com.example.data.obd.WifiObdPreset
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage

@Composable
fun WiFiConnectionSection(
    wifiState: WifiAdapterUiState,
    connectionStatus: ObdConnectionStatus,
    onRefreshWifi: () -> Unit,
    onSelectPreset: (WifiObdPreset) -> Unit,
    onUpdateHost: (String) -> Unit,
    onUpdatePort: (String) -> Unit,
    onAttemptConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("wifi_connection_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isThai) "ตัวเชื่อมต่อ\nWi-Fi OBD-II" else "Wi-Fi OBD-II\nAdapter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                !wifiState.isWifiSupported ->
                                    if (isThai) "อุปกรณ์นี้ไม่รองรับ Wi-Fi" else "Wi-Fi not supported on this device"
                                !wifiState.isWifiEnabled ->
                                    if (isThai) {
                                        "Wi-Fi ปิดอยู่ • กรุณาเปิด Wi-Fi ก่อนเชื่อมต่อ"
                                    } else {
                                        "Wi-Fi is off • Enable Wi-Fi first"
                                    }
                                wifiState.isWifiNetworkConnected ->
                                    if (isThai) {
                                        "เชื่อมต่ออยู่: ${wifiState.connectedSsidLabel ?: "Wi-Fi"}"
                                    } else {
                                        "Joined: ${wifiState.connectedSsidLabel ?: "Wi-Fi"}"
                                    }
                                else ->
                                    if (isThai) {
                                        "เปิด Wi-Fi แล้ว • รอเชื่อมเครือข่ายสแกนเนอร์"
                                    } else {
                                        "Wi-Fi on • Join scanner hotspot"
                                    }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF111827)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRefreshWifi,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.5.dp, Color(0xFF374151)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wifi_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = if (isThai) "รีเฟรชสถานะ Wi-Fi" else "Refresh Wi-Fi status",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isThai) "รีเฟรช" else "Refresh", maxLines = 1, softWrap = false)
                }
            }

            if (!wifiState.statusMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE5E7EB),
                    border = BorderStroke(1.2.dp, Color(0xFF4B5563))
                ) {
                    Text(
                        text = wifiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            // Adapter overview explanation inside Connect screen for user clarity
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wifi_adapter_info_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Wi-Fi OBD-II Adapter (Wireless ELM327 TCP)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    Text(
                        text = if (isThai) {
                            "อะแดปเตอร์เชื่อมต่อไร้สายแบบปล่อยสัญญาณ Wi-Fi Hotspot ในตัว (ค่ามาตรฐาน 192.168.0.10:35000) รับ-ส่งข้อมูลจากกล่อง ECU ได้รวดเร็วและเสถียร รองรับทั้ง Android และ iOS โดยไม่ต้องจับคู่รหัสผ่าน Bluetooth (หมายเหตุ: ขณะเชื่อมต่อ Wi-Fi ของหัวอ่าน อินเทอร์เน็ตบ้าน/Wi-Fi อื่นจะหยุดใช้ชั่วคราว)"
                        } else {
                            "Wireless OBD-II scanner that creates its own local Wi-Fi hotspot (typically 192.168.0.10:35000). Delivers fast, stable ECU communication across Android & iOS with no Bluetooth pairing PIN needed. (Note: home/office Wi-Fi internet pauses while joined to the scanner hotspot.)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1F2937)
                    )
                }
            }

            // Guidance box for connecting phone to OBD-II Wi-Fi hotspot
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFE5E7EB),
                border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isThai) "ขั้นตอนเชื่อมต่อสแกนเนอร์ Wi-Fi" else "How to Connect Wi-Fi Scanner",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black
                    )
                    Text(
                        text = if (isThai) {
                            "1. เสียบหัวอ่านเข้าช่อง OBD-II ใต้พวงมาลัยและบิดกุญแจไปตำแหน่ง ON\n2. เปิดตั้งค่า Wi-Fi ในมือถือ แล้วเชื่อมชื่อสัญญาณของหัวอ่าน (เช่น WiFi_OBDII, V-LINK)\n3. เลือกรูปแบบ IP/Port ด้านล่างแล้วกดตรวจสอบและเชื่อมต่อ"
                        } else {
                            "1. Plug the scanner into the OBD-II port under the steering wheel and turn ignition ON.\n2. Open Android Wi-Fi settings and join the scanner network (e.g., WiFi_OBDII, V-LINK).\n3. Select the IP / Port preset below and tap Verify & Connect."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827)
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFF9CA3AF))

            Text(
                text = if (isThai) "ค่ามาตรฐาน IP / Port ของสแกนเนอร์" else "Scanner IP / Port Presets",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Black
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WifiObdPreset.entries.forEach { preset ->
                    val selected = wifiState.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset.localizedLabel(isThai)) },
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color(0xFF4B5563),
                            selectedBorderColor = Color.Black,
                            borderWidth = 1.5.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFFE5E7EB),
                            labelColor = Color.Black,
                            selectedContainerColor = Color(0xFFD1D5DB),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("wifi_preset_${preset.name}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = wifiState.hostInput,
                    onValueChange = onUpdateHost,
                    label = { Text("IP Address", color = Color.Black) },
                    placeholder = { Text("192.168.0.10", color = Color(0xFF4B5563)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color(0xFF4B5563),
                        cursorColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1.6f)
                        .testTag("wifi_host_input")
                )

                OutlinedTextField(
                    value = wifiState.portInput,
                    onValueChange = onUpdatePort,
                    label = { Text("Port", color = Color.Black) },
                    placeholder = { Text("35000", color = Color(0xFF4B5563)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.Black,
                        unfocusedBorderColor = Color(0xFF4B5563),
                        cursorColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wifi_port_input")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAttemptConnect,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("wifi_connect_button")
                ) {
                    Text(if (isThai) "ตรวจสอบและเชื่อมต่อ Wi-Fi" else "Verify & Connect Wi-Fi")
                }

                if (connectionStatus !is ObdConnectionStatus.Disconnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        border = BorderStroke(1.5.dp, Color(0xFF374151)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wifi_disconnect_button")
                    ) {
                        Text(if (isThai) "ตัดการเชื่อมต่อ" else "Disconnect")
                    }
                }
            }
        }
    }
}
