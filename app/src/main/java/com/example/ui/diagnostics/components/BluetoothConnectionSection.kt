package com.example.ui.diagnostics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.obd.BluetoothAdapterUiState
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.BluetoothDeviceEntry
import com.example.data.obd.ObdConnectionStatus
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothConnectionSection(
    bluetoothState: BluetoothAdapterUiState,
    connectionStatus: ObdConnectionStatus,
    onSelectArchitecture: (BluetoothArchitecture) -> Unit,
    onRequestPermissions: () -> Unit,
    onRefreshDevices: () -> Unit,
    onToggleBleDiscovery: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAttemptConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    onEnableBluetooth: () -> Unit = {},
    onToggleShowAllArchitectures: (Boolean) -> Unit = {}
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("bluetooth_connection_section")
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
                        imageVector = if (bluetoothState.isBluetoothEnabled) {
                            Icons.Filled.Bluetooth
                        } else {
                            Icons.Filled.BluetoothDisabled
                        },
                        contentDescription = null,
                        tint = if (bluetoothState.isBluetoothEnabled) Color(0xFF1D4ED8) else Color(0xFF4B5563),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isThai) "ตัวเชื่อมต่อ\nBluetooth OBD-II" else "Bluetooth OBD-II\nAdapter",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                !bluetoothState.isBluetoothSupported ->
                                    if (isThai) "อุปกรณ์นี้ไม่รองรับ Bluetooth" else "Bluetooth not supported on this device"
                                !bluetoothState.isBluetoothEnabled ->
                                    if (isThai) "Bluetooth ปิดอยู่ — เปิดเพื่อค้นหาอัตโนมัติ" else "Bluetooth is off — enable to auto-discover"
                                else ->
                                    if (isThai) {
                                        "พร้อมใช้งาน • ${bluetoothState.architecture.localizedTitle(true)}"
                                    } else {
                                        "Ready • ${bluetoothState.architecture.localizedTitle(false)}"
                                    }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF111827)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onRefreshDevices,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.5.dp, Color(0xFF374151)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("bluetooth_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = if (isThai) "รีเฟรชอุปกรณ์ Bluetooth" else "Refresh Bluetooth devices",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isThai) "รีเฟรช" else "Refresh", maxLines = 1, softWrap = false)
                }
            }

            // Enable Bluetooth directly inside the app when BT radio is OFF
            if (bluetoothState.isBluetoothSupported && !bluetoothState.isBluetoothEnabled) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.5.dp, Color(0xFF2563EB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bluetooth_enable_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isThai) {
                                "เปิด Bluetooth เพื่อเชื่อมในแอปได้ทันที"
                            } else {
                                "Turn On Bluetooth to Auto-Detect Your OBD"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = if (isThai) {
                                "เมื่อเปิด Bluetooth บนโทรศัพท์ ระบบจะแสดงทั้งอุปกรณ์ที่จับคู่ไว้แล้วและอุปกรณ์ OBD-II ใกล้เคียงขึ้นมาให้เลือกเชื่อมต่อทันที"
                            } else {
                                "Once Bluetooth is enabled on your phone, paired and nearby Classic/BLE OBD-II scanners appear here automatically."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1F2937)
                        )
                        Button(
                            onClick = onEnableBluetooth,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1D4ED8),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("bluetooth_enable_button")
                        ) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isThai) "เปิด Bluetooth ตอนนี้" else "Enable Bluetooth Now")
                        }
                    }
                }
            }

            // Adapter overview explanation inside Connect screen for user clarity
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bluetooth_adapter_info_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Bluetooth OBD-II Adapter (Classic SPP / BLE GATT)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    Text(
                        text = if (isThai) {
                            "รองรับทั้งหัวอ่าน Classic SPP (ELM327, OBDLink MX+) และ BLE GATT (Veepeak BLE+, OBDLink CX) • หากเชื่อมหรือจับคู่ในมือถือแล้ว สามารถกดเลือกชื่ออุปกรณ์ด้านล่างเพื่อเชื่อมต่อในแอปและอ่านค่าเรียลไทม์ได้ทันที (ระบบสลับโปรโตคอล Classic/BLE ให้อัตโนมัติหากจำเป็น)"
                        } else {
                            "Supports both Classic SPP (ELM327, OBDLink MX+) and BLE GATT (Veepeak BLE+, OBDLink CX). Paired and discovered scanners appear below with automatic Classic/BLE fallback."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1F2937)
                    )
                }
            }

            // Architecture Switcher: Classic (SPP/RFCOMM) vs BLE (GATT)
            Text(
                text = if (isThai) "รูปแบบโปรโตคอลที่ต้องการเริ่มต้น" else "Preferred Bluetooth Protocol",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Black
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BluetoothArchitecture.entries.forEachIndexed { index, arch ->
                    SegmentedButton(
                        selected = bluetoothState.architecture == arch,
                        onClick = { onSelectArchitecture(arch) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BluetoothArchitecture.entries.size
                        ),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color(0xFFD1D5DB),
                            activeContentColor = Color.Black,
                            inactiveContainerColor = Color(0xFFE5E7EB),
                            inactiveContentColor = Color.Black,
                            activeBorderColor = Color(0xFF1F2937),
                            inactiveBorderColor = Color(0xFF4B5563)
                        ),
                        modifier = Modifier.testTag("bt_arch_${arch.name}")
                    ) {
                        Text(
                            text = if (arch == BluetoothArchitecture.CLASSIC_SPP) "Classic (SPP)" else "BLE (GATT)",
                            color = Color.Black
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = bluetoothState.showAllArchitectures,
                    onClick = { onToggleShowAllArchitectures(true) },
                    label = {
                        Text(if (isThai) "แสดงทุกชนิดที่พบ (แนะนำ)" else "Show All Paired & Nearby")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFFE5E7EB),
                        labelColor = Color.Black,
                        selectedContainerColor = Color(0xFFD1D5DB),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("bt_filter_all")
                )
                FilterChip(
                    selected = !bluetoothState.showAllArchitectures,
                    onClick = { onToggleShowAllArchitectures(false) },
                    label = {
                        Text(
                            if (isThai) {
                                "เฉพาะ ${if (bluetoothState.architecture == BluetoothArchitecture.CLASSIC_SPP) "Classic" else "BLE"}"
                            } else {
                                "Only ${if (bluetoothState.architecture == BluetoothArchitecture.CLASSIC_SPP) "Classic" else "BLE"}"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFFE5E7EB),
                        labelColor = Color.Black,
                        selectedContainerColor = Color(0xFFD1D5DB),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.testTag("bt_filter_arch_only")
                )
            }

            // Runtime permissions check & request
            if (!bluetoothState.hasRequiredPermissions) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE5E7EB),
                    border = BorderStroke(1.5.dp, Color(0xFF374151)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bluetooth_permission_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isThai) {
                                    "จำเป็นต้องอนุญาตสิทธิ์ Bluetooth"
                                } else {
                                    "Bluetooth Permission Required"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.Black
                            )
                        }
                        val permNames = bluetoothState.missingPermissions.joinToString { it.substringAfterLast('.') }
                        Text(
                            text = if (isThai) {
                                "ระบบต้องได้รับสิทธิ์ Bluetooth ($permNames) เพื่อแสดงรายชื่ออุปกรณ์ที่จับคู่ไว้และค้นหาตัวสแกนเนอร์ OBD-II อัตโนมัติ"
                            } else {
                                "Grant Bluetooth permission ($permNames) so BRANDT can auto-detect paired adapters and nearby scanners."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF111827)
                        )
                        Button(
                            onClick = onRequestPermissions,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("bluetooth_grant_permission_button")
                        ) {
                            Text(if (isThai) "อนุญาตสิทธิ์ Bluetooth" else "Grant Bluetooth Permission")
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onToggleBleDiscovery,
                enabled = bluetoothState.hasRequiredPermissions && bluetoothState.isBluetoothEnabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                border = BorderStroke(1.5.dp, Color(0xFF374151)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("bluetooth_ble_scan_button")
            ) {
                if (bluetoothState.isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        bluetoothState.isDiscovering && isThai -> "กำลังสแกน... (แตะเพื่อหยุด)"
                        bluetoothState.isDiscovering -> "Scanning Classic + BLE... (Tap to Stop)"
                        isThai -> "สแกนหาอุปกรณ์ OBD ใกล้เคียง (Classic + BLE)"
                        else -> "Scan Nearby OBD Devices (Classic + BLE)"
                    }
                )
            }

            if (!bluetoothState.statusMessage.isNullOrBlank()) {
                Text(
                    text = bluetoothState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF111827)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFF9CA3AF))

            // Paired / Discovered Devices List
            Text(
                text = if (isThai) {
                    "อุปกรณ์ Bluetooth OBD-II ที่จับคู่ไว้และค้นพบ"
                } else {
                    "Paired & Discovered Bluetooth Devices"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Black
            )

            if (bluetoothState.devices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE5E7EB),
                    border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bluetooth_empty_devices_box")
                ) {
                    Text(
                        text = if (isThai) {
                            "ยังไม่พบตัวสแกนเนอร์ OBD-II กรุณาเปิด Bluetooth เสียบหัวอ่านเข้าพอร์ต OBD ของรถ แล้วกด \"สแกนหาอุปกรณ์ OBD ใกล้เคียง\" หรือ \"รีเฟรช\""
                        } else {
                            "No OBD-II scanners found yet. Turn on Bluetooth, plug your adapter into the car's OBD port, then tap Scan Nearby OBD Devices or Refresh."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bluetoothState.devices.forEach { device ->
                        BluetoothDeviceRow(
                            device = device,
                            isSelected = device.address == bluetoothState.selectedDevice?.address,
                            isThai = isThai,
                            onSelect = { onSelectDevice(device.address) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isConnecting = connectionStatus is ObdConnectionStatus.Connecting &&
                    connectionStatus.method == com.example.data.obd.ObdConnectionMethod.BLUETOOTH
                Button(
                    onClick = onAttemptConnect,
                    enabled = bluetoothState.devices.isNotEmpty() && bluetoothState.hasRequiredPermissions && !isConnecting,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF9CA3AF),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bluetooth_connect_button")
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isConnecting) {
                            if (isThai) "กำลังเชื่อมต่อ..." else "Connecting..."
                        } else {
                            if (isThai) "เชื่อมต่อและเริ่มอ่านค่าเรียลไทม์" else "Verify & Connect Bluetooth"
                        }
                    )
                }

                if (connectionStatus !is ObdConnectionStatus.Disconnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        border = BorderStroke(1.5.dp, Color(0xFF374151)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("bluetooth_disconnect_button")
                    ) {
                        Text(if (isThai) "ตัดการเชื่อมต่อ" else "Disconnect")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BluetoothDeviceRow(
    device: BluetoothDeviceEntry,
    isSelected: Boolean,
    isThai: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFFD1D5DB) else Color(0xFFE5E7EB),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.5.dp,
            color = if (isSelected) Color.Black else Color(0xFF4B5563)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bluetooth_device_row_${device.address}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color.Black,
                    unselectedColor = Color(0xFF374151)
                )
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (device.isObdCandidate) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF065F46)
                        ) {
                            Text(
                                text = "OBD-II",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildString {
                            append(device.address)
                            append(" • ")
                            append(device.localizedArchBadge(isThai))
                            append(" • ")
                            append(
                                if (device.isBonded) {
                                    if (isThai) "จับคู่แล้ว" else "Paired"
                                } else {
                                    if (isThai) "จับคู่ในแอปอัตโนมัติ" else "Auto-pairs on connect"
                                }
                            )
                            device.rssiDbm?.let { append(" • $it dBm") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827)
                    )
                }
            }
        }
    }
}
