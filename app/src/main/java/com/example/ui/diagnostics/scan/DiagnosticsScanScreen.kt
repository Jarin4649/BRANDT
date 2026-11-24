package com.example.ui.diagnostics.scan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.DtcStatus
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionStatus
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.components.DiagnosticsTopBar
import com.example.ui.diagnostics.components.ReadOnlySafetyNotice
import com.example.ui.theme.BrandtDiagnosticsLightTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScanScreen(
    selectedVehicleLabel: String?,
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsScanViewModel? = null,
    onOpenConnect: () -> Unit = {},
    onNavigateToResults: () -> Unit = {}
) {
    val resolvedViewModel = viewModel ?: remember {
        val mgr = ObdConnectionManager()
        val coord = DiagnosticsSessionCoordinator(
            userId = "standalone_preview",
            repository = InMemoryPreviewVehicleDataSource(),
            connectionManager = mgr
        )
        DiagnosticsScanViewModel(mgr, coord) { null }
    }

    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()
    val managerState by resolvedViewModel.managerState.collectAsStateWithLifecycle()
    val coordinatorState by resolvedViewModel.sessionCoordinatorState.collectAsStateWithLifecycle()
    val liveTelemetry by resolvedViewModel.liveTelemetry.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val strings = appStrings(language)

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            resolvedViewModel.importDiagnosticUri(context, uri)
        }
    }

    BrandtDiagnosticsLightTheme {
        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                DiagnosticsTopBar(
                    title = "Diagnostic Scan",
                    subtitle = "Read-Only ECU, Manual DTC & File Import",
                    onNavigateBack = onNavigateBack,
                    backButtonTestTag = "diagnostics_scan_screen_back"
                )
            },
            modifier = Modifier.testTag("diagnostics_scan_screen")
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!selectedVehicleLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF3F4F6),
                            border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Vehicle",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF111827)
                                )
                                Text(
                                    text = selectedVehicleLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // Mode Switcher: Live OBD-II | Manual DTC | Import File
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ScanInputMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.activeMode == mode,
                                onClick = { resolvedViewModel.selectMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ScanInputMode.entries.size
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = Color(0xFFD1D5DB),
                                    activeContentColor = Color.Black,
                                    inactiveContainerColor = Color(0xFFF3F4F6),
                                    inactiveContentColor = Color.Black,
                                    activeBorderColor = Color(0xFF1F2937),
                                    inactiveBorderColor = Color(0xFF4B5563)
                                ),
                                modifier = Modifier.testTag("scan_mode_${mode.name}")
                            ) {
                                Text(mode.label, maxLines = 1, color = Color.Black)
                            }
                        }
                    }

                    // Shared Owner Symptom Field
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF3F4F6),
                        border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "What are you noticing? (Optional)",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.Black
                            )
                            OutlinedTextField(
                                value = uiState.ownerSymptoms,
                                onValueChange = resolvedViewModel::updateOwnerSymptoms,
                                placeholder = {
                                    Text(
                                        "e.g., Check engine light turned on yesterday; rough idle at stoplights",
                                        color = Color(0xFF4B5563)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Color.Black,
                                    unfocusedBorderColor = Color(0xFF4B5563),
                                    cursorColor = Color.Black
                                ),
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_owner_symptoms_input")
                            )
                        }
                    }

                if (coordinatorState.errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = coordinatorState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                when (uiState.activeMode) {
                    ScanInputMode.LIVE_OBD -> {
                        val isConnected = managerState.connectionStatus is ObdConnectionStatus.Connected &&
                            resolvedViewModel.isLiveAdapterConnected()
                        val connected = managerState.connectionStatus as? ObdConnectionStatus.Connected

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_live_obd_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Live Read-Only OBD-II Scan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isConnected && connected != null) {
                                        "Connected to ${connected.targetLabel} (${connected.protocolLabel}). BRANDT will read Mode 03/07 DTCs, Freeze Frame (02), and supported Mode 01 sensors without altering ECU memory."
                                    } else {
                                        "No verified OBD-II hardware stream is active. Connect a Wi-Fi or Bluetooth adapter in Connection Setup first, or switch to Manual DTC / Import File above."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isConnected && liveTelemetry.latestSession != null) {
                                    val liveSession = liveTelemetry.latestSession!!
                                    val pids = liveSession.livePidReadings.associateBy { it.pidCode.uppercase() }
                                    fun pidText(code: String, unit: String, decimals: Int = 0): String {
                                        val v = pids[code]?.takeIf { it.isAvailable }?.value ?: return "--"
                                        val s = if (decimals == 0) v.roundToInt().toString()
                                        else String.format(Locale.US, "%.${decimals}f", v)
                                        return "$s $unit"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF0FDF4),
                                        border = BorderStroke(1.dp, Color(0xFF15803D)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("scan_live_telemetry_strip")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "● LIVE STREAM • ${liveSession.confirmedDtcs.size} Confirmed / ${liveSession.pendingDtcs.size} Pending DTC",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF065F46)
                                            )
                                            Text(
                                                text = "RPM: ${pidText("010C", "rpm")}  •  Speed: ${pidText("010D", "km/h")}  •  Temp: ${pidText("0105", "°C")}  •  Batt: ${pidText("0142", "V", 1)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF111827)
                                            )
                                        }
                                    }
                                }

                                if (isConnected) {
                                    Button(
                                        onClick = {
                                            resolvedViewModel.runLiveScan(language, onNavigateToResults)
                                        },
                                        enabled = !coordinatorState.isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onSurface,
                                            contentColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("scan_run_live_button")
                                    ) {
                                        if (coordinatorState.isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.surface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(coordinatorState.busyStepLabel ?: "Scanning...")
                                        } else {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Run Read-Only Scan & Analyze")
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onOpenConnect,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("scan_open_connect_button")
                                    ) {
                                        Icon(Icons.Filled.SettingsInputComponent, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Connection Setup")
                                    }
                                }
                            }
                        }
                    }

                    ScanInputMode.MANUAL_DTC -> {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_manual_dtc_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Manual Trouble Code Input",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Enter one or more 5-character OBD-II codes (e.g., P0171, P0300, C0035, U0100) from another scanner or workshop printout.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = uiState.dtcInputText,
                                        onValueChange = resolvedViewModel::updateDtcInputText,
                                        label = { Text("OBD-II Code", color = Color.Black) },
                                        placeholder = { Text("P0171", color = Color(0xFF4B5563)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.Black,
                                            unfocusedTextColor = Color.Black,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                            focusedBorderColor = Color.Black,
                                            unfocusedBorderColor = Color(0xFF4B5563),
                                            cursorColor = Color.Black
                                        ),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("scan_manual_dtc_field")
                                    )
                                    Button(
                                        onClick = resolvedViewModel::addManualDtcCodes,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF111827),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .height(52.dp)
                                            .testTag("scan_add_dtc_button")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add code")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = uiState.dtcStatusSelection == DtcStatus.CONFIRMED,
                                        onClick = { resolvedViewModel.selectDtcStatus(DtcStatus.CONFIRMED) },
                                        label = { Text("Confirmed (Mode 03)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFFE5E7EB),
                                            labelColor = Color.Black,
                                            selectedContainerColor = Color(0xFFD1D5DB),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier.testTag("manual_status_confirmed")
                                    )
                                    FilterChip(
                                        selected = uiState.dtcStatusSelection == DtcStatus.PENDING,
                                        onClick = { resolvedViewModel.selectDtcStatus(DtcStatus.PENDING) },
                                        label = { Text("Pending (Mode 07)") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = Color(0xFFE5E7EB),
                                            labelColor = Color.Black,
                                            selectedContainerColor = Color(0xFFD1D5DB),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier.testTag("manual_status_pending")
                                    )
                                }

                                if (uiState.manualInputError != null) {
                                    Text(
                                        text = uiState.manualInputError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("manual_input_error")
                                    )
                                }

                                if (uiState.manualDtcs.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        uiState.manualDtcs.forEach { chip ->
                                            InputChip(
                                                selected = true,
                                                onClick = { resolvedViewModel.removeManualDtc(chip.code) },
                                                label = {
                                                    Text("${chip.code} (${if (chip.status == DtcStatus.PENDING) "Pending" else "Confirmed"})")
                                                },
                                                colors = InputChipDefaults.inputChipColors(
                                                    selectedContainerColor = Color(0xFFE5E7EB),
                                                    selectedLabelColor = Color.Black,
                                                    selectedTrailingIconColor = Color.Black
                                                ),
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Remove ${chip.code}",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                modifier = Modifier.testTag("manual_dtc_chip_${chip.code}")
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    uiState.manualDtcs.forEach { chip ->
                                        val interp = DtcInterpreter.interpret(
                                            DiagnosticTroubleCode(
                                                code = chip.code,
                                                status = chip.status,
                                                timestampEpochMs = 0L
                                            )
                                        )
                                        Text(
                                            text = "${interp.code}: ${interp.standardDefinition} (${interp.subsystemLabel})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Black
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        resolvedViewModel.analyzeManualEntry(language, onNavigateToResults)
                                    },
                                    enabled = !coordinatorState.isBusy,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("scan_analyze_manual_button")
                                ) {
                                    if (coordinatorState.isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.surface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(coordinatorState.busyStepLabel ?: "Analyzing...")
                                    } else {
                                        Text("Explain Codes & Generate Summary")
                                    }
                                }
                            }
                        }
                    }

                    ScanInputMode.IMPORT_FILE -> {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_import_file_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Import Diagnostic Report or Log",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Supports JSON OBD exports, CSV telemetry logs, or plain-text ELM327 scan outputs. Unlisted sensor PIDs stay marked Unavailable (never zero).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = {
                                        documentPickerLauncher.launch(
                                            arrayOf("application/json", "text/plain", "text/csv", "*/*")
                                        )
                                    },
                                    enabled = !uiState.isImportingFile,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("scan_pick_file_button")
                                ) {
                                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (uiState.isImportingFile) "Reading File..." else "Choose Diagnostic File")
                                }

                                if (uiState.importError != null) {
                                    Text(
                                        text = uiState.importError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.testTag("scan_import_error")
                                    )
                                }

                                val imported = uiState.importedSession
                                if (imported != null) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("scan_imported_preview")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Loaded: ${uiState.importedFileName ?: imported.sourceMetadata.adapterOrFileLabel}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "DTCs: ${imported.allDtcs.size} • Available PIDs: ${imported.livePidReadings.count { it.isAvailable }} • Reliability: ${imported.sourceMetadata.reliabilityLevel.displayLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            resolvedViewModel.analyzeImportedSession(language, onNavigateToResults)
                                        },
                                        enabled = !coordinatorState.isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onSurface,
                                            contentColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("scan_analyze_imported_button")
                                    ) {
                                        if (coordinatorState.isBusy) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.surface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(coordinatorState.busyStepLabel ?: "Analyzing...")
                                        } else {
                                            Text("Analyze Imported File")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)
                Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
