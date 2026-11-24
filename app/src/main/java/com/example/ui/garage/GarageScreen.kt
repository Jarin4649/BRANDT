package com.example.ui.garage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.catalog.DemoVehicleCatalog
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionManagerState
import com.example.data.obd.ObdConnectionStatus
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import kotlin.math.roundToInt
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.components.VehicleBackgroundRemover
import com.example.ui.theme.BrandtAuthInk
import com.example.ui.theme.BrandtEmptyCanvas
import com.example.ui.theme.BrandtEmptyShadow
import com.example.ui.theme.BrandtMintAction
import com.example.ui.theme.BrandtWordmarkFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import java.util.Locale

@Composable
fun GarageScreen(
    viewModel: GarageViewModel,
    ownerDisplayName: String,
    isPreviewMode: Boolean = false,
    onAddVehicle: () -> Unit,
    onOpenVehicleOverview: (String) -> Unit,
    onEditVehicle: (String) -> Unit,
    onOpenAiChat: (String) -> Unit = {},
    onOpenIssueLog: (String) -> Unit = {},
    onOpenDiagnostics: (String?) -> Unit = {},
    onSignOut: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPreviewId by viewModel.selectedPreviewId.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDeleteVehicle.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val obdConnectionState by viewModel.obdConnectionState.collectAsStateWithLifecycle()
    val obdLiveTelemetry by viewModel.obdLiveTelemetry.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeActionMessage()
        }
    }

    val strings = appStrings(LocalAppLanguage.current)

    if (pendingDelete != null) {
        val target = pendingDelete!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            modifier = Modifier.testTag("delete_confirmation_dialog"),
            title = {
                Text(
                    text = strings.garageRemoveTitle,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${target.modelYear} ${target.manufacturer} ${target.model} (${target.trim}) from your BRANDT garage? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteVehicle() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(strings.garageRemoveConfirm)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissDeleteConfirmation() },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text(strings.garageCancel)
                }
            }
        )
    }

    val isGarageEmpty = (uiState as? GarageUiState.Ready)?.vehicles?.isEmpty() == true

    Scaffold(
        containerColor = if (isGarageEmpty) BrandtEmptyCanvas else Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isGarageEmpty) {
                    GarageTopHeader(
                        ownerDisplayName = ownerDisplayName,
                        vehicleCount = (uiState as? GarageUiState.Ready)?.vehicles?.size ?: 0,
                        onOpenDiagnostics = { onOpenDiagnostics(selectedPreviewId) },
                        onSignOut = onSignOut
                    )
                }
                if (isPreviewMode) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("preview_mode_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PREVIEW MODE • Temporary in-memory session (not synced)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onSignOut,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("exit_preview_button")
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is GarageUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF1D6EC7))
                    }
                }

                is GarageUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.widthIn(max = 500.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }

                is GarageUiState.Ready -> {
                    if (state.vehicles.isEmpty()) {
                        EmptyGarageStage(
                            onAddVehicle = onAddVehicle,
                            onOpenDiagnostics = { onOpenDiagnostics(null) },
                            onSignOut = onSignOut,
                            hasTopBanner = isPreviewMode,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val isExpandedLayout = maxWidth >= 720.dp
                            val primaryOrFirstId = (state.vehicles.firstOrNull { it.primary } ?: state.vehicles.firstOrNull())?.id
                            if (isExpandedLayout) {
                                ExpandedGarageLayout(
                                    vehicles = state.vehicles,
                                    isMutating = isMutating,
                                    obdConnectionState = obdConnectionState,
                                    obdLiveTelemetry = obdLiveTelemetry,
                                    onOpenDiagnostics = { onOpenDiagnostics(primaryOrFirstId) },
                                    onAddVehicle = onAddVehicle,
                                    onOpenOverview = onOpenVehicleOverview,
                                    onEditVehicle = onEditVehicle,
                                    onSetPrimary = { viewModel.setPrimaryVehicle(it) },
                                    onRequestDelete = { viewModel.requestDeleteVehicle(it) }
                                )
                            } else {
                                CompactGarageLayout(
                                    vehicles = state.vehicles,
                                    isMutating = isMutating,
                                    obdConnectionState = obdConnectionState,
                                    obdLiveTelemetry = obdLiveTelemetry,
                                    onOpenDiagnostics = { onOpenDiagnostics(primaryOrFirstId) },
                                    onAddVehicle = onAddVehicle,
                                    onOpenOverview = onOpenVehicleOverview,
                                    onEditVehicle = onEditVehicle,
                                    onSetPrimary = { viewModel.setPrimaryVehicle(it) },
                                    onRequestDelete = { viewModel.requestDeleteVehicle(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageTopHeader(
    ownerDisplayName: String,
    vehicleCount: Int,
    onOpenDiagnostics: () -> Unit,
    onSignOut: () -> Unit
) {
    val strings = appStrings(LocalAppLanguage.current)
    Surface(
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF111827),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BRANDT",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF111827)
                )
                Text(
                    text = if (ownerDisplayName.isNotBlank()) "${strings.garageSubtitlePrefix} • $ownerDisplayName" else strings.garageSubtitleStandalone,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFF3F4F6),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Text(
                    text = if (vehicleCount == 1) "1 CAR" else "$vehicleCount CARS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1F2937),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            IconButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nav_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Troubleshoot,
                    contentDescription = strings.navDiagnostics,
                    tint = Color(0xFF1D6EC7)
                )
            }

            LanguageSettingsActionButton(tint = Color(0xFF374151))

            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = strings.garageSignOut,
                    tint = Color(0xFF4B5563)
                )
            }
        }
    }
}

@Composable
private fun CompactGarageLayout(
    vehicles: List<Vehicle>,
    isMutating: Boolean,
    obdConnectionState: ObdConnectionManagerState?,
    obdLiveTelemetry: LiveTelemetryStreamState?,
    onOpenDiagnostics: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenOverview: (String) -> Unit,
    onEditVehicle: (String) -> Unit,
    onSetPrimary: (Vehicle) -> Unit,
    onRequestDelete: (Vehicle) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("garage_compact_list"),
        contentPadding = PaddingValues(bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "garage_hero_banner") {
            GarageHeroShutterBanner(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (obdConnectionState?.isConnected == true || obdLiveTelemetry?.isStreaming == true) {
            item(key = "garage_live_obd_strip") {
                GarageLiveObdStripCard(
                    connectionState = obdConnectionState,
                    liveTelemetry = obdLiveTelemetry,
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
        }

        items(vehicles, key = { it.id }) { vehicle ->
            BrandtGarageShowcaseCard(
                vehicle = vehicle,
                isMutating = isMutating,
                onOpenOverview = { onOpenOverview(vehicle.id) },
                onEdit = { onEditVehicle(vehicle.id) },
                onSetPrimary = { onSetPrimary(vehicle) },
                onDelete = { onRequestDelete(vehicle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        item(key = "add_new_car_slot") {
            AddNewCarSlotCard(
                onAddVehicle = onAddVehicle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ExpandedGarageLayout(
    vehicles: List<Vehicle>,
    isMutating: Boolean,
    obdConnectionState: ObdConnectionManagerState?,
    obdLiveTelemetry: LiveTelemetryStreamState?,
    onOpenDiagnostics: () -> Unit,
    onAddVehicle: () -> Unit,
    onOpenOverview: (String) -> Unit,
    onEditVehicle: (String) -> Unit,
    onSetPrimary: (Vehicle) -> Unit,
    onRequestDelete: (Vehicle) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("garage_expanded_layout"),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.95f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GarageHeroShutterBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
            )

            if (obdConnectionState?.isConnected == true || obdLiveTelemetry?.isStreaming == true) {
                GarageLiveObdStripCard(
                    connectionState = obdConnectionState,
                    liveTelemetry = obdLiveTelemetry,
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AddNewCarSlotCard(
                onAddVehicle = onAddVehicle,
                modifier = Modifier.fillMaxWidth()
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1.2f),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(vehicles, key = { it.id }) { vehicle ->
                BrandtGarageShowcaseCard(
                    vehicle = vehicle,
                    isMutating = isMutating,
                    onOpenOverview = { onOpenOverview(vehicle.id) },
                    onEdit = { onEditVehicle(vehicle.id) },
                    onSetPrimary = { onSetPrimary(vehicle) },
                    onDelete = { onRequestDelete(vehicle) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GarageLiveObdStripCard(
    connectionState: ObdConnectionManagerState?,
    liveTelemetry: LiveTelemetryStreamState?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    val session = liveTelemetry?.latestSession
    val readings = remember(session) {
        session?.livePidReadings?.associateBy { it.pidCode.uppercase() }.orEmpty()
    }
    fun valOrDash(code: String, unit: String, decimals: Int = 0): String {
        val v = readings[code]?.takeIf { it.isAvailable }?.value ?: return "--"
        val n = if (decimals == 0) v.roundToInt().toString() else String.format(Locale.US, "%.${decimals}f", v)
        return "$n $unit"
    }
    val connectedLabel = (connectionState?.connectionStatus as? ObdConnectionStatus.Connected)?.targetLabel
        ?: (if (isThai) "OBD-II เชื่อมต่อแล้ว" else "OBD-II Connected")
    val dtcTotal = (session?.confirmedDtcs?.size ?: 0) + (session?.pendingDtcs?.size ?: 0)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF0FDF4),
        border = BorderStroke(1.2.dp, Color(0xFF15803D)),
        modifier = modifier.testTag("garage_live_obd_strip")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Troubleshoot,
                        contentDescription = null,
                        tint = Color(0xFF065F46),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = connectedLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF065F46),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (dtcTotal > 0) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)
                ) {
                    Text(
                        text = if (dtcTotal > 0) {
                            if (isThai) "● LIVE • $dtcTotal DTC" else "● LIVE • $dtcTotal DTC"
                        } else {
                            "● LIVE"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (dtcTotal > 0) Color(0xFF991B1B) else Color(0xFF065F46),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = if (session != null) {
                    val speedLabel = if (isThai) "ความเร็ว" else "Speed"
                    val rpmLabel = if (isThai) "รอบ" else "RPM"
                    val coolantLabel = if (isThai) "หม้อน้ำ" else "Coolant"
                    "$rpmLabel: ${valOrDash("010C", "rpm")}  •  $speedLabel: ${valOrDash("010D", "km/h")}  •  $coolantLabel: ${valOrDash("0105", "°C")}  •  ${valOrDash("0142", "V", 1)}"
                } else {
                    if (isThai) "เชื่อมต่อสำเร็จ • กำลังอ่านข้อมูล ECU สดแบบเรียลไทม์..." else "Connected • Reading live ECU telemetry stream..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GarageHeroShutterBanner(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.img_garage_hero_banner),
        contentDescription = "YOUR BRANDT GARAGE CARS",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .height(225.dp)
            .testTag("garage_hero_banner")
    )
}

/**
 * Vehicle card matching iPhone 17 - 25:
 * - Soft frosted white/silver card surface
 * - Top: Manufacturer + Model • Year, and body style/type underneath
 * - Background watermark: uppercase country where the car was manufactured ("America" -> "USA")
 * - Bottom-right cutout: User's Left Side photo with background removed cleanly
 * - Bottom-left CTA: Blue gradient "View Car" pill button
 */
@Composable
private fun BrandtGarageShowcaseCard(
    vehicle: Vehicle,
    isMutating: Boolean,
    onOpenOverview: () -> Unit,
    onEdit: () -> Unit,
    onSetPrimary: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = appStrings(LocalAppLanguage.current)

    val countryWatermark = remember(vehicle.market, vehicle.manufacturer) {
        resolveGarageCountryWatermark(vehicle)
    }

    val rawLeftSideUri = remember(vehicle.id, vehicle.photoUri, vehicle.anglePhotos) {
        val angles = vehicle.resolvedAnglePhotos()
        angles[VehicleAngle.SIDE_LEFT]?.takeIf { it.isNotBlank() }
            ?: vehicle.photoUri.takeIf { it.isNotBlank() }
            ?: angles[VehicleAngle.SIDE_RIGHT]?.takeIf { it.isNotBlank() }
            ?: angles[VehicleAngle.FRONT]?.takeIf { it.isNotBlank() }
            ?: angles[VehicleAngle.REAR]?.takeIf { it.isNotBlank() }
            ?: ""
    }

    var cutoutCarUri by remember(rawLeftSideUri) { mutableStateOf(rawLeftSideUri) }
    LaunchedEffect(rawLeftSideUri) {
        cutoutCarUri = if (rawLeftSideUri.isBlank()) {
            ""
        } else {
            VehicleBackgroundRemover.extractVehicleCutoutUri(context, rawLeftSideUri)
        }
    }

    val carDisplayTitle = remember(vehicle.manufacturer, vehicle.model) {
        val mfg = vehicle.manufacturer.trim()
        val mdl = vehicle.model.trim()
        when {
            mfg.isBlank() -> mdl
            mdl.startsWith(mfg, ignoreCase = true) -> mdl
            else -> "$mfg $mdl"
        }.ifBlank { "Vehicle" }
    }

    val typeSubtitle = remember(vehicle.bodyStyle, vehicle.trim) {
        val bodyName = vehicle.resolvedBodyStyle().displayName
        val trimClean = vehicle.trim.trim()
        if (trimClean.isNotBlank() && !trimClean.equals("Unspecified", ignoreCase = true)) {
            "$bodyName • $trimClean"
        } else {
            bodyName
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7F8FA),
        shadowElevation = 4.dp,
        border = BorderStroke(
            width = if (vehicle.primary) 1.5.dp else 1.dp,
            color = if (vehicle.primary) Color(0xFF93C5FD) else Color(0xFFE5E7EB)
        ),
        modifier = modifier
            .height(206.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenOverview)
            .testTag("vehicle_card_${vehicle.id}")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF3F5F8)
                        )
                    )
                )
        ) {
            val cardWidth = maxWidth
            val halfCarBoxWidth = (cardWidth * 1.04f).coerceAtLeast(280.dp)
            val halfCarOffsetX = halfCarBoxWidth * 0.44f

            // Bold vertical sky-blue gradient country watermark matching JAPAN reference
            val countryWatermarkBrush = remember {
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF79AEE3).copy(alpha = 0.86f),
                        0.55f to Color(0xFFA6C8EA).copy(alpha = 0.65f),
                        1.0f to Color(0xFFE1EDF8).copy(alpha = 0.25f)
                    )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(top = 6.dp, start = 48.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = countryWatermark,
                    style = TextStyle(
                        brush = countryWatermarkBrush,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 74.sp,
                        lineHeight = 76.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-2).sp
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-2).dp)
                        .testTag("vehicle_country_watermark_${vehicle.id}")
                )
            }

            // Cutout Left Side vehicle photo (or studio hatchback cutout) cropped cleanly to the front half
            if (cutoutCarUri.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(halfCarBoxWidth)
                        .height(148.dp)
                        .offset(x = halfCarOffsetX, y = 4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AsyncImage(
                        model = cutoutCarUri,
                        contentDescription = "$carDisplayTitle Left Side",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vehicle_card_photo_${vehicle.id}")
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .width(328.dp)
                        .height(146.dp)
                        .offset(x = 158.dp, y = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_garage_default_car),
                        contentDescription = "$carDisplayTitle Front Half",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vehicle_card_default_photo_${vehicle.id}")
                    )
                }
            }

            // Top identity + quiet management icons
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 14.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = carDisplayTitle,
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4B5563))
                            )
                            Text(
                                text = vehicle.modelYear.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }

                    // Compact management action icons (48.dp touch targets)
                    if (!vehicle.primary) {
                        IconButton(
                            onClick = onSetPrimary,
                            enabled = !isMutating,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("set_primary_${vehicle.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.StarBorder,
                                contentDescription = "Set ${vehicle.model} as primary",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Primary vehicle",
                                tint = Color(0xFF1D6EC7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_vehicle_${vehicle.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${vehicle.model}",
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        enabled = !isMutating,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_vehicle_${vehicle.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Remove ${vehicle.model}",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Text(
                    text = typeSubtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 96.dp)
                )
            }

            // Bottom-left blue gradient "View Car" CTA pill matching iPhone 17 - 25
            Surface(
                onClick = onOpenOverview,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 18.dp)
                    .heightIn(min = 48.dp)
                    .widthIn(min = 136.dp)
                    .testTag("card_overview_${vehicle.id}")
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF1D6EC7),
                                    Color(0xFF9EC5EA)
                                )
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.garageViewVehicle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Bottom "Add New Cars" empty vehicle slot card using the exact uploaded iPhone 17 - 24 asset.
 * Tapping navigates straight into the add-vehicle registration flow.
 */
@Composable
private fun AddNewCarSlotCard(
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings(LocalAppLanguage.current)
    Surface(
        onClick = onAddVehicle,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF7F8FA),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .testTag("add_vehicle_button")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_garage_add_card),
            contentDescription = strings.garageAddVehicle,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

internal fun resolveGarageCountryWatermark(vehicle: Vehicle): String {
    val marketRaw = vehicle.market.trim()
    val manufacturerOrigin = DemoVehicleCatalog.manufacturers
        .firstOrNull { it.name.equals(vehicle.manufacturer.trim(), ignoreCase = true) }
        ?.countryOfOrigin
        .orEmpty()

    val candidate = when {
        marketRaw.isBlank() || marketRaw.equals("Other / Global", ignoreCase = true) -> manufacturerOrigin
        else -> marketRaw
    }

    return normalizeCountryWatermark(candidate)
}

internal fun normalizeCountryWatermark(rawCountry: String): String {
    val clean = rawCountry.trim()
    if (clean.isBlank()) return "GLOBAL"
    val lower = clean.lowercase(Locale.US)

    return when {
        lower == "us" ||
            lower == "u.s." ||
            lower == "usa" ||
            lower == "u.s.a." ||
            lower.contains("america") ||
            lower.contains("united states") ||
            lower.contains("สหรัฐ") -> "USA"

        lower == "uk" ||
            lower.contains("united kingdom") ||
            lower.contains("great britain") ||
            lower.contains("england") ||
            lower.contains("สหราชอาณาจักร") ||
            lower.contains("อังกฤษ") -> "UK"

        lower.contains("japan") || lower.contains("ญี่ปุ่น") -> "JAPAN"
        lower.contains("germany") || lower.contains("เยอรม") -> "GERMANY"
        lower.contains("sweden") || lower.contains("สวีเดน") -> "SWEDEN"
        lower.contains("thailand") || lower.contains("ไทย") -> "THAILAND"
        lower.contains("korea") || lower.contains("เกาหลี") -> "KOREA"
        lower.contains("italy") || lower.contains("อิตาลี") -> "ITALY"
        lower.contains("france") || lower.contains("ฝรั่งเศส") -> "FRANCE"
        lower.contains("china") || lower.contains("จีน") -> "CHINA"
        else -> clean.substringBefore('/').trim().uppercase(Locale.US).ifBlank { "GLOBAL" }
    }
}

@Composable
private fun EmptyGarageStage(
    onAddVehicle: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSignOut: () -> Unit,
    hasTopBanner: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = appStrings(LocalAppLanguage.current)
    val isThai = strings.language == AppLanguage.THAI
    val density = LocalDensity.current
    var headlineHeightPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .background(BrandtEmptyCanvas)
    ) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val isWide = viewportWidth >= 700.dp

        val visibleCarHeight = if (isWide) {
            (viewportHeight * 0.78f).coerceAtLeast(360.dp)
        } else {
            (viewportHeight * 0.59f).coerceIn(260.dp, 560.dp)
        }
        val carHeight = visibleCarHeight / if (isWide) 0.86f else 0.78f
        val carWidth = carHeight * (458f / 895f)
        val carTranslateX = if (isWide) 16.dp else carWidth * 0.25f
        val carTranslateY = carHeight - visibleCarHeight

        // Soft studio ambient ground shadow beneath left flank of the vehicle
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = carWidth * 1.18f, height = carHeight * 0.88f)
                .graphicsLayer {
                    translationX = (carTranslateX - 38.dp).toPx()
                    translationY = carTranslateY.toPx()
                }
        ) {
            drawOval(
                brush = Brush.radialGradient(
                    0.0f to BrandtEmptyShadow,
                    0.55f to BrandtEmptyShadow.copy(alpha = 0.16f),
                    1.0f to Color.Transparent,
                    center = Offset(size.width * 0.44f, size.height * 0.56f),
                    radius = size.maxDimension * 0.52f
                )
            )
        }

        // Top-down transparent silver sport coupe positioned flush to bottom-right
        Image(
            painter = painterResource(id = R.drawable.hero_empty_garage_car),
            contentDescription = "BRANDT Top-Down Coupe",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = carWidth, height = carHeight)
                .graphicsLayer {
                    translationX = carTranslateX.toPx()
                    translationY = carTranslateY.toPx()
                }
                .testTag("empty_garage_car_image")
        )

        // Subtle minimal top-right controls (diagnostics, language settings & sign out) preserving editorial whitespace
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(if (hasTopBanner) Modifier else Modifier.statusBarsPadding())
                .padding(top = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("empty_nav_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Troubleshoot,
                    contentDescription = strings.navDiagnostics,
                    tint = BrandtAuthInk.copy(alpha = 0.72f)
                )
            }
            LanguageSettingsActionButton()
            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = strings.garageSignOut,
                    tint = BrandtAuthInk.copy(alpha = 0.72f)
                )
            }
        }

        // Top-left editorial headline
        val headlineFontSize = (viewportWidth.value / if (isThai) 9.8f else 11.4f)
            .coerceIn(27f, 46f)
            .sp
        val headlineTopPadding = if (hasTopBanner) 24.dp else 54.dp

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(if (hasTopBanner) Modifier else Modifier.statusBarsPadding())
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    top = headlineTopPadding
                )
                .onSizeChanged { size -> headlineHeightPx = size.height },
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = strings.emptyGarageHeadline,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = if (isThai) SpaceGroteskFontFamily else BrandtWordmarkFontFamily,
                    fontWeight = if (isThai) FontWeight.ExtraBold else FontWeight.Normal,
                    fontSize = headlineFontSize,
                    lineHeight = headlineFontSize * if (isThai) 1.18f else 1.08f,
                    letterSpacing = 0.5.sp
                ),
                color = BrandtAuthInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_garage_headline")
            )

            if (isWide) {
                Spacer(modifier = Modifier.height(32.dp))
                EmptyGarageMintCtaButton(
                    label = strings.emptyGarageCta,
                    onClick = onAddVehicle,
                    modifier = Modifier.width(320.dp)
                )
            }
        }

        if (!isWide) {
            // Symmetrical horizontally centered CTA anchored cleanly onto the car's bumper horizon line
            // while guaranteeing minimum 24.dp clearance below the multi-line headline.
            val ctaHeight = 56.dp
            val headlineReservedTop = with(density) { headlineHeightPx.toDp() } + headlineTopPadding + 48.dp
            val maxSafeBottom = (viewportHeight - headlineReservedTop - ctaHeight).coerceAtLeast(28.dp)
            val bumperAlignedBottom = (visibleCarHeight - (ctaHeight / 2)).coerceIn(28.dp, maxSafeBottom)
            val symmetricCtaWidth = (viewportWidth - 76.dp).coerceIn(240.dp, 324.dp)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bumperAlignedBottom),
                contentAlignment = Alignment.Center
            ) {
                EmptyGarageMintCtaButton(
                    label = strings.emptyGarageCta,
                    onClick = onAddVehicle,
                    modifier = Modifier.width(symmetricCtaWidth)
                )
            }
        }
    }
}

@Composable
private fun EmptyGarageMintCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandtMintAction,
            contentColor = BrandtAuthInk
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = modifier
            .height(56.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = BrandtMintAction.copy(alpha = 0.45f),
                spotColor = BrandtMintAction.copy(alpha = 0.45f)
            )
            .testTag("empty_add_vehicle_button")
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = BrandtAuthInk,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp
            ),
            color = BrandtAuthInk
        )
    }
}
