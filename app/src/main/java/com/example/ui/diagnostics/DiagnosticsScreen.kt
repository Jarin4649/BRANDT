package com.example.ui.diagnostics

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.obd.ObdConnectionMethod
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.diagnostics.components.ConnectObdCardsSection
import com.example.ui.diagnostics.components.DiagnosticsLiveTelemetryCard
import com.example.ui.diagnostics.components.DiagnosticsReportsHubCard
import com.example.ui.diagnostics.components.DiagnosticsTargetVehicleHeroSection
import com.example.ui.diagnostics.components.DiagnosticsTopBar
import com.example.ui.diagnostics.components.ReadOnlySafetyNotice
import com.example.ui.diagnostics.components.ScanSummaryCard
import com.example.ui.diagnostics.components.WhatIsObdShowcaseSection
import com.example.ui.theme.BrandtDiagnosticsLightTheme

@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onNavigateBack: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenConnect: (ObdConnectionMethod) -> Unit,
    onOpenScan: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenExplain: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val strings = appStrings(LocalAppLanguage.current)

    BrandtDiagnosticsLightTheme {
        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                DiagnosticsTopBar(
                    title = "Vehicle Diagnostics",
                    subtitle = "",
                    onNavigateBack = onNavigateBack,
                    backButtonTestTag = "diagnostics_back_button",
                    showMinimalHeader = true
                )
            },
            modifier = Modifier.testTag("diagnostics_screen")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isLoadingVehicles -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.Black)
                        }
                    }

                    uiState.loadError != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = uiState.loadError.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    else -> {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val isExpanded = maxWidth >= 780.dp
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 980.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Top Hero & Educational Banner matching requested iPhone 17 - 17 (1) layout:
                                // 1. Target Vehicle (Right Side car cutout + year)
                                // 2. What is OBD Bluetooth & OBD Wi-Fi + See Explains + Frame 2 banner
                                if (isExpanded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        DiagnosticsTargetVehicleHeroSection(
                                            vehicles = uiState.vehicles,
                                            selectedVehicle = uiState.selectedVehicle,
                                            onSelectVehicle = viewModel::selectVehicle,
                                            onAddVehicle = onAddVehicle,
                                            modifier = Modifier.weight(1f)
                                        )
                                        WhatIsObdShowcaseSection(
                                            onOpenExplains = onOpenExplain,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    DiagnosticsTargetVehicleHeroSection(
                                        vehicles = uiState.vehicles,
                                        selectedVehicle = uiState.selectedVehicle,
                                        onSelectVehicle = viewModel::selectVehicle,
                                        onAddVehicle = onAddVehicle
                                    )

                                    WhatIsObdShowcaseSection(
                                        onOpenExplains = onOpenExplain
                                    )
                                }

                                // Connect OBD visual cards (Frame 3 Bluetooth & Frame 4 Wi-Fi), followed by Scan & Reports
                                if (isExpanded) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            ConnectObdCardsSection(
                                                connectionSummary = uiState.connectionSummary,
                                                onOpenConnectionSetup = onOpenConnect
                                            )
                                            if (uiState.connectionSummary.isAnyConnected || uiState.liveTelemetry.latestSession != null) {
                                                DiagnosticsLiveTelemetryCard(
                                                    liveTelemetry = uiState.liveTelemetry,
                                                    isConnected = uiState.connectionSummary.isAnyConnected,
                                                    adapterName = uiState.connectionSummary.activeAdapterName,
                                                    onOpenScan = onOpenScan
                                                )
                                            }
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            ScanSummaryCard(
                                                selectedVehicle = uiState.selectedVehicle,
                                                onOpenScan = onOpenScan
                                            )
                                            DiagnosticsReportsHubCard(
                                                savedSessionCount = uiState.savedSessionCount,
                                                recentReportCount = uiState.recentReportCount,
                                                onOpenResults = onOpenResults,
                                                onOpenHistory = onOpenHistory,
                                                onOpenReport = onOpenReport
                                            )
                                        }
                                    }
                                } else {
                                    ConnectObdCardsSection(
                                        connectionSummary = uiState.connectionSummary,
                                        onOpenConnectionSetup = onOpenConnect
                                    )

                                    if (uiState.connectionSummary.isAnyConnected || uiState.liveTelemetry.latestSession != null) {
                                        DiagnosticsLiveTelemetryCard(
                                            liveTelemetry = uiState.liveTelemetry,
                                            isConnected = uiState.connectionSummary.isAnyConnected,
                                            adapterName = uiState.connectionSummary.activeAdapterName,
                                            onOpenScan = onOpenScan
                                        )
                                    }

                                    ScanSummaryCard(
                                        selectedVehicle = uiState.selectedVehicle,
                                        onOpenScan = onOpenScan
                                    )

                                    DiagnosticsReportsHubCard(
                                        savedSessionCount = uiState.savedSessionCount,
                                        recentReportCount = uiState.recentReportCount,
                                        onOpenResults = onOpenResults,
                                        onOpenHistory = onOpenHistory,
                                        onOpenReport = onOpenReport
                                    )
                                }

                                ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
