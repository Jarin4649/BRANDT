package com.example.ui.diagnostics.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcStatus
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionManager
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.diagnostics.DiagnosticsSessionCoordinator
import com.example.ui.diagnostics.DiagnosticsSessionUiState
import com.example.ui.diagnostics.components.DTCListSection
import com.example.ui.diagnostics.components.DiagnosticReportView
import com.example.ui.diagnostics.components.DiagnosticsTopBar
import com.example.ui.diagnostics.components.ReadOnlySafetyNotice
import com.example.ui.theme.BrandtDiagnosticsLightTheme

@Composable
fun DiagnosticsResultsScreen(
    selectedVehicleLabel: String?,
    onNavigateBack: () -> Unit,
    coordinator: DiagnosticsSessionCoordinator? = null,
    connectionManager: ObdConnectionManager? = null,
    onOpenScan: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val resolvedCoordinator = coordinator ?: remember {
        DiagnosticsSessionCoordinator(
            userId = "standalone_preview",
            repository = InMemoryPreviewVehicleDataSource(),
            connectionManager = connectionManager ?: ObdConnectionManager()
        )
    }
    val state by resolvedCoordinator.state.collectAsStateWithLifecycle()
    val liveTelemetryState = connectionManager?.liveTelemetry?.collectAsStateWithLifecycle()?.value
    val strings = appStrings(LocalAppLanguage.current)

    BrandtDiagnosticsLightTheme {
        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                DiagnosticsTopBar(
                    title = "Diagnostic Results",
                    subtitle = selectedVehicleLabel ?: "DTC Evidence & AI Analysis",
                    onNavigateBack = onNavigateBack,
                    backButtonTestTag = "diagnostics_results_screen_back"
                )
            },
            modifier = Modifier.testTag("diagnostics_results_screen")
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
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ResultsContentBody(
                        state = state,
                        liveTelemetry = liveTelemetryState,
                        onOpenScan = onOpenScan,
                        onOpenReport = onOpenReport,
                        onOpenHistory = onOpenHistory
                    )
                    ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultsContentBody(
    state: DiagnosticsSessionUiState,
    liveTelemetry: LiveTelemetryStreamState?,
    onOpenScan: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenHistory: () -> Unit
) {
    when {
        state.isBusy -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        text = state.busyStepLabel ?: "Analyzing diagnostic evidence...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        state.activeResult != null && state.activeSession != null -> {
            val session = state.activeSession
            val result = state.activeResult

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Source: ${session.sourceMetadata.sourceType.displayLabel} • ${session.sourceMetadata.adapterOrFileLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Reliability: ${session.sourceMetadata.reliabilityLevel.displayLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.lastSavedSessionId != null) {
                        Text(
                            text = "Saved to Diagnostic History",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (state.saveWarningMessage != null) {
                        Text(
                            text = state.saveWarningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DTCListSection(
                dtcs = session.allDtcs,
                vehicleSnapshot = session.vehicle
            )

            val liveOverlayReadings = remember(session.livePidReadings, liveTelemetry) {
                val streamSession = liveTelemetry?.takeIf { it.isStreaming }?.latestSession
                if (streamSession == null || streamSession.livePidReadings.isEmpty()) {
                    session.livePidReadings
                } else {
                    val liveMap = streamSession.livePidReadings.associateBy { it.pidCode.uppercase() }
                    val merged = session.livePidReadings.map { existing ->
                        liveMap[existing.pidCode.uppercase()]?.takeIf { it.isAvailable } ?: existing
                    }
                    val existingCodes = session.livePidReadings.map { it.pidCode.uppercase() }.toSet()
                    val extras = streamSession.livePidReadings.filter {
                        it.pidCode.uppercase() !in existingCodes && it.isAvailable
                    }
                    merged + extras
                }
            }

            if (liveOverlayReadings.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("results_telemetry_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Sensor Telemetry Snapshot",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (liveTelemetry?.isStreaming == true) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFD1FAE5)
                                ) {
                                    Text(
                                        text = "● LIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF065F46),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        liveOverlayReadings.forEach { reading ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${reading.pidCode} ${reading.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = reading.formattedValueOrUnavailable(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            DiagnosticReportView(result = result)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenReport,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("results_open_report_button")
                ) {
                    Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mechanic Report")
                }
                OutlinedButton(
                    onClick = onOpenHistory,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("results_open_history_button")
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("History")
                }
            }
        }

        state.selectedSavedSession != null -> {
            val saved = state.selectedSavedSession
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("results_saved_session_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = saved.vehicleTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Urgency: ${saved.overallUrgency} • Confidence: ${saved.confidenceLevel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = saved.headlineSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (saved.driveabilityNotice.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = saved.driveabilityNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (saved.missingInformation.isNotEmpty()) {
                        Text(
                            text = "Missing info: ${saved.missingInformation.joinToString("; ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            DTCListSection(
                dtcs = saved.dtcCodes.map {
                    DiagnosticTroubleCode(
                        code = it,
                        status = DtcStatus.CONFIRMED,
                        timestampEpochMs = saved.createdAt?.toDate()?.time ?: 0L
                    )
                }
            )

            Button(
                onClick = onOpenReport,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("results_open_report_button")
            ) {
                Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Mechanic Report")
            }
        }

        else -> {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("results_empty_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "No diagnostic scan or session loaded yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.errorMessage
                            ?: "Run a read-only OBD-II scan, enter trouble codes manually, or import a diagnostic file to see everyday explanations and evidence checks here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenScan,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("results_go_scan_button")
                    ) {
                        Icon(Icons.Filled.Troubleshoot, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Diagnostic Scan")
                    }
                }
            }
        }
    }
}
