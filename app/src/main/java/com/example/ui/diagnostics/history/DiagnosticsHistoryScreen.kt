package com.example.ui.diagnostics.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SavedObdSessionRecord
import com.example.data.repository.InMemoryPreviewVehicleDataSource
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.diagnostics.components.DiagnosticsTopBar
import com.example.ui.diagnostics.components.ReadOnlySafetyNotice
import com.example.ui.theme.BrandtDiagnosticsLightTheme
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsHistoryScreen(
    selectedVehicleLabel: String?,
    onNavigateBack: () -> Unit,
    viewModel: DiagnosticsHistoryViewModel? = null,
    onOpenScan: () -> Unit = {},
    onOpenSavedSessionInResults: (SavedObdSessionRecord) -> Unit = {},
    onOpenSavedSessionInReport: (SavedObdSessionRecord) -> Unit = {}
) {
    val resolvedViewModel = viewModel ?: remember {
        DiagnosticsHistoryViewModel(
            repository = InMemoryPreviewVehicleDataSource(),
            userId = "standalone_preview",
            vehicleId = null
        )
    }
    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()
    val strings = appStrings(LocalAppLanguage.current)
    var pendingDelete by remember { mutableStateOf<SavedObdSessionRecord?>(null) }

    BrandtDiagnosticsLightTheme {
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = Color.White,
                titleContentColor = Color.Black,
                textContentColor = Color(0xFF111827),
                title = { Text("Delete Diagnostic Session?") },
                text = { Text("Remove this saved OBD-II session (${target.adapterOrFileLabel}) from your vehicle's history?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            resolvedViewModel.deleteSession(target.id)
                            pendingDelete = null
                        },
                        modifier = Modifier.testTag("confirm_delete_obd_session")
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = Color.Black)
                    }
                }
            )
        }

        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                DiagnosticsTopBar(
                    title = "Diagnostic History",
                    subtitle = selectedVehicleLabel ?: "Saved Read-Only Sessions",
                    onNavigateBack = onNavigateBack,
                    backButtonTestTag = "diagnostics_history_screen_back"
                )
            },
            modifier = Modifier.testTag("diagnostics_history_screen")
        ) { innerPadding ->
        Box(
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
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.errorMessage != null -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    uiState.sessions.isEmpty() -> {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_empty_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "No saved diagnostic sessions yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Run a read-only OBD-II scan, analyze manual trouble codes, or import a diagnostic report to build your vehicle's timeline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenScan,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("history_go_scan_button")
                                ) {
                                    Text("Open Diagnostic Scan")
                                }
                            }
                        }
                    }

                    else -> {
                        uiState.sessions.forEach { session ->
                            val dateLabel = remember(session.createdAt) {
                                val ts = session.createdAt?.toDate()
                                if (ts != null) {
                                    SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.US).format(ts)
                                } else {
                                    "Syncing..."
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("history_session_card_${session.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.adapterOrFileLabel,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${session.vehicleTitle} • $dateLabel",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { pendingDelete = session },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("delete_history_session_${session.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Delete diagnostic session",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = "Urgency: ${session.overallUrgency}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Text(
                                                text = "Confidence: ${session.confidenceLevel}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    if (session.dtcCodes.isNotEmpty()) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            session.dtcCodes.forEach { code ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = code,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = session.headlineSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onOpenSavedSessionInResults(session) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("history_open_results_${session.id}")
                                        ) {
                                            Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Summary")
                                        }
                                        OutlinedButton(
                                            onClick = { onOpenSavedSessionInReport(session) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("history_open_report_${session.id}")
                                        ) {
                                            Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Report")
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
