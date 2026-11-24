package com.example.ui.diagnostics.connect

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdConnectionStatus
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.diagnostics.components.BluetoothConnectionSection
import com.example.ui.diagnostics.components.CompatibilityInfoCard
import com.example.ui.diagnostics.components.DiagnosticsTopBar
import com.example.ui.diagnostics.components.ReadOnlySafetyNotice
import com.example.ui.diagnostics.components.WiFiConnectionSection
import com.example.ui.theme.BrandtDiagnosticsLightTheme

@Composable
fun DiagnosticsConnectScreen(
    viewModel: DiagnosticsConnectViewModel,
    selectedVehicleLabel: String?,
    onNavigateBack: () -> Unit,
    initialMethod: ObdConnectionMethod? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val isThai = language == AppLanguage.THAI
    val strings = appStrings(language)

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.attachBluetoothObserver(context)
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.attachBluetoothObserver(context)
    }

    LaunchedEffect(initialMethod) {
        if (initialMethod != null) {
            viewModel.selectMethod(initialMethod)
        }
    }

    LaunchedEffect(initialMethod, language) {
        viewModel.refreshAll(context)
    }

    DisposableEffect(context) {
        viewModel.attachBluetoothObserver(context)
        onDispose {
            viewModel.stopDiscovery(context)
            viewModel.detachBluetoothObserver()
        }
    }

    BrandtDiagnosticsLightTheme {
        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                DiagnosticsTopBar(
                    title = if (isThai) "ตั้งค่าการเชื่อมต่อ" else "Connection Setup",
                    subtitle = if (isThai) {
                        "ตั้งค่า Wi-Fi และ Bluetooth OBD-II"
                    } else {
                        "Configure Wi-Fi & Bluetooth OBD-II"
                    },
                    onNavigateBack = onNavigateBack,
                    backButtonTestTag = "diagnostics_connect_screen_back"
                )
            },
            modifier = Modifier.testTag("diagnostics_connect_screen")
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                val isExpanded = maxWidth >= 820.dp
                Column(
                    modifier = Modifier
                        .widthIn(max = 980.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ConnectionSummaryBanner(
                        status = state.connectionStatus,
                        selectedVehicleLabel = selectedVehicleLabel,
                        isThai = isThai,
                        liveStatusNote = liveTelemetry.streamStatusNote?.takeIf { liveTelemetry.isStreaming },
                        onResetErrorOrDisconnect = { viewModel.disconnect(context) }
                    )

                    // Primary transport selector (Wi-Fi OBD-II | Bluetooth OBD-II)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ObdConnectionMethod.entries.forEachIndexed { index, method ->
                            SegmentedButton(
                                selected = state.activeMethod == method,
                                onClick = { viewModel.selectMethod(method) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ObdConnectionMethod.entries.size
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = Color(0xFFD1D5DB),
                                    activeContentColor = Color.Black,
                                    inactiveContainerColor = Color(0xFFF3F4F6),
                                    inactiveContentColor = Color.Black,
                                    activeBorderColor = Color(0xFF1F2937),
                                    inactiveBorderColor = Color(0xFF4B5563)
                                ),
                                modifier = Modifier.testTag("connect_method_${method.name}")
                            ) {
                                Text(method.label, color = Color.Black)
                            }
                        }
                    }

                    val enableBluetoothAction: () -> Unit = {
                        runCatching {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    }

                    if (isExpanded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(modifier = Modifier.weight(1.1f)) {
                                ActiveTransportPanel(
                                    state = state,
                                    viewModel = viewModel,
                                    onRequestBluetoothPermissions = {
                                        bluetoothPermissionLauncher.launch(
                                            viewModel.requiredBluetoothPermissions().toTypedArray()
                                        )
                                    },
                                    onEnableBluetooth = enableBluetoothAction
                                )
                            }
                            Column(
                                modifier = Modifier.weight(0.9f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CompatibilityInfoCard(
                                    selectedProtocol = state.protocolPreference,
                                    onSelectProtocol = viewModel::selectProtocol
                                )
                                ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)
                            }
                        }
                    } else {
                        ActiveTransportPanel(
                            state = state,
                            viewModel = viewModel,
                            onRequestBluetoothPermissions = {
                                bluetoothPermissionLauncher.launch(
                                    viewModel.requiredBluetoothPermissions().toTypedArray()
                                )
                            },
                            onEnableBluetooth = enableBluetoothAction
                        )

                        CompatibilityInfoCard(
                            selectedProtocol = state.protocolPreference,
                            onSelectProtocol = viewModel::selectProtocol
                        )

                        ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveTransportPanel(
    state: com.example.data.obd.ObdConnectionManagerState,
    viewModel: DiagnosticsConnectViewModel,
    onRequestBluetoothPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit
) {
    val context = LocalContext.current
    when (state.activeMethod) {
        ObdConnectionMethod.WIFI -> WiFiConnectionSection(
            wifiState = state.wifiState,
            connectionStatus = state.connectionStatus,
            onRefreshWifi = { viewModel.refreshWifi(context) },
            onSelectPreset = viewModel::selectWifiPreset,
            onUpdateHost = viewModel::updateWifiHost,
            onUpdatePort = viewModel::updateWifiPort,
            onAttemptConnect = { viewModel.attemptConnect(context) },
            onDisconnect = { viewModel.disconnect(context) }
        )

        ObdConnectionMethod.BLUETOOTH -> BluetoothConnectionSection(
            bluetoothState = state.bluetoothState,
            connectionStatus = state.connectionStatus,
            onSelectArchitecture = { arch -> viewModel.selectBluetoothArchitecture(context, arch) },
            onRequestPermissions = onRequestBluetoothPermissions,
            onRefreshDevices = { viewModel.refreshBluetooth(context) },
            onToggleBleDiscovery = { viewModel.toggleBleDiscovery(context) },
            onSelectDevice = viewModel::selectBluetoothDevice,
            onAttemptConnect = { viewModel.attemptConnect(context) },
            onDisconnect = { viewModel.disconnect(context) },
            onEnableBluetooth = onEnableBluetooth,
            onToggleShowAllArchitectures = { showAll ->
                viewModel.setShowAllBluetoothArchitectures(context, showAll)
            }
        )
    }
}

@Composable
private fun ConnectionSummaryBanner(
    status: ObdConnectionStatus,
    selectedVehicleLabel: String?,
    isThai: Boolean,
    liveStatusNote: String? = null,
    onResetErrorOrDisconnect: () -> Unit
) {
    val isError = status is ObdConnectionStatus.Error || status is ObdConnectionStatus.PermissionRequired
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isError) Color(0xFFDC2626) else Color(0xFF4B5563)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_status_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (status) {
                        is ObdConnectionStatus.Disconnected ->
                            if (isThai) "สถานะ: ยังไม่ได้เชื่อมต่อ" else "Status: Disconnected"
                        is ObdConnectionStatus.PermissionRequired ->
                            if (isThai) "จำเป็นต้องอนุญาตสิทธิ์" else "Permission Required"
                        is ObdConnectionStatus.Connecting ->
                            if (isThai) {
                                "กำลังเชื่อมต่อ (${status.method.label})..."
                            } else {
                                "Connecting (${status.method.label})..."
                            }
                        is ObdConnectionStatus.Connected ->
                            if (isThai) {
                                "เชื่อมต่อแล้ว: ${status.targetLabel}"
                            } else {
                                "Connected: ${status.targetLabel}"
                            }
                        is ObdConnectionStatus.Error ->
                            if (isThai) "การเชื่อมต่อยังไม่พร้อมใช้งาน" else "Connection Not Ready"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )

                if (!selectedVehicleLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFE5E7EB),
                        border = BorderStroke(1.2.dp, Color(0xFF4B5563))
                    ) {
                        Text(
                            text = selectedVehicleLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            val detailText = when (status) {
                is ObdConnectionStatus.Disconnected ->
                    if (isThai) {
                        "เลือกการเชื่อมต่อแบบ Wi-Fi หรือ Bluetooth ด้านล่างเพื่อตั้งค่าและตรวจสอบอุปกรณ์สแกนเนอร์"
                    } else {
                        "Select Wi-Fi or Bluetooth below to configure and verify your OBD-II scanner."
                    }
                is ObdConnectionStatus.PermissionRequired -> status.reason
                is ObdConnectionStatus.Connecting ->
                    if (isThai) {
                        "กำลังทดสอบการสื่อสารแบบอ่านอย่างเดียวกับ ${status.targetLabel}..."
                    } else {
                        "Verifying read-only communication with ${status.targetLabel}..."
                    }
                is ObdConnectionStatus.Connected ->
                    if (isThai) {
                        "ยืนยันโปรโตคอลแล้ว: ${status.protocolLabel}"
                    } else {
                        "Protocol verified: ${status.protocolLabel}"
                    }
                is ObdConnectionStatus.Error -> status.message
            }

            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827),
                modifier = Modifier.testTag("connect_status_detail")
            )

            if (status is ObdConnectionStatus.Connected && !liveStatusNote.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFD1FAE5),
                    border = BorderStroke(1.dp, Color(0xFF047857)),
                    modifier = Modifier.testTag("connect_live_status_note")
                ) {
                    Text(
                        text = "● $liveStatusNote",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF065F46),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (isError || status is ObdConnectionStatus.Connected) {
                OutlinedButton(
                    onClick = onResetErrorOrDisconnect,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.5.dp, Color(0xFF374151)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (status is ObdConnectionStatus.Connected) {
                            if (isThai) "ตัดการเชื่อมต่อ" else "Disconnect"
                        } else {
                            if (isThai) "ล้างสถานะ" else "Clear Status"
                        }
                    )
                }
            }
        }
    }
}
