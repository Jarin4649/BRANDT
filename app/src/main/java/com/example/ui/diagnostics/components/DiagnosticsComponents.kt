package com.example.ui.diagnostics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionMethod
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.components.VehicleBackgroundRemover
import com.example.ui.components.VehicleStudioSilhouette
import com.example.ui.diagnostics.DiagnosticsConnectionSummary
import java.util.Locale
import kotlin.math.roundToInt

private val CrispBorderColor = Color(0xFF4B5563)
private val CrispButtonBorderColor = Color(0xFF374151)

@Composable
fun DiagnosticsTopBar(
    title: String,
    subtitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    backButtonTestTag: String = "diagnostics_back_button",
    showMinimalHeader: Boolean = false
) {
    val strings = appStrings(LocalAppLanguage.current)
    Surface(
        color = Color.White,
        contentColor = Color.Black,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(backButtonTestTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.chatBackDesc,
                        tint = Color.Black
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (showMinimalHeader) FontWeight.Medium else FontWeight.SemiBold,
                            color = if (showMinimalHeader) Color(0xFF4B5563) else Color.Black
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!showMinimalHeader && subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF111827),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!showMinimalHeader) {
                    ReadOnlyPillBadge(label = strings.diagnosticsReadOnlyBadge)
                }

                LanguageSettingsActionButton()
            }
            HorizontalDivider(color = Color(0xFF9CA3AF))
        }
    }
}

@Composable
fun ReadOnlyPillBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFE5E7EB),
        border = BorderStroke(1.5.dp, CrispBorderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black
            )
        }
    }
}

@Composable
fun ReadOnlySafetyNotice(
    noteText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFE5E7EB),
        border = BorderStroke(1.5.dp, CrispBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_readonly_notice")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp)
            )
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827)
            )
        }
    }
}

/**
 * Target Vehicle showcase matching the clean visual structure requested for Vehicle Diagnostics:
 * Title ("Target Vehicle"), vehicle name + type, background-removed Right Side cutout in gray stage, and Year.
 */
@Composable
fun DiagnosticsTargetVehicleHeroSection(
    vehicles: List<Vehicle>,
    selectedVehicle: Vehicle?,
    onSelectVehicle: (String) -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val isThai = language == AppLanguage.THAI
    val strings = appStrings(language)
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_vehicle_selector_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.diagnosticsVehicleSectionTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            if (selectedVehicle != null) {
                OutlinedButton(
                    onClick = onAddVehicle,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.5.dp, CrispButtonBorderColor),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.diagnosticsRegisterVehicleCta,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (vehicles.isEmpty() || selectedVehicle == null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF3F4F6),
                border = BorderStroke(1.5.dp, CrispBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.diagnosticsNoVehiclesTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black
                    )
                    Text(
                        text = strings.diagnosticsNoVehiclesBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827)
                    )
                    OutlinedButton(
                        onClick = onAddVehicle,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        border = BorderStroke(1.5.dp, CrispButtonBorderColor),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("diagnostics_add_vehicle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.diagnosticsRegisterVehicleCta)
                    }
                }
            }
        } else {
            val resolvedAngles = remember(selectedVehicle.anglePhotos) { selectedVehicle.resolvedAnglePhotos() }
            val rawHeroPhotoUri = remember(selectedVehicle.photoUri, resolvedAngles) {
                resolvedAngles[VehicleAngle.SIDE_RIGHT]
                    ?.takeIf { it.isNotBlank() }
                    ?: selectedVehicle.photoUri.takeIf { it.isNotBlank() }
                    ?: resolvedAngles.values.firstOrNull { it.isNotBlank() }
                    ?: ""
            }
            var cutoutUri by remember(rawHeroPhotoUri) { mutableStateOf(rawHeroPhotoUri) }
            LaunchedEffect(rawHeroPhotoUri) {
                cutoutUri = if (rawHeroPhotoUri.isBlank()) {
                    ""
                } else {
                    VehicleBackgroundRemover.extractVehicleCutoutUri(context, rawHeroPhotoUri)
                }
            }

            val vehicleName = "${selectedVehicle.modelYear} ${selectedVehicle.manufacturer} ${selectedVehicle.model}"
            val typeSubtitle = remember(selectedVehicle.trim, selectedVehicle.bodyStyle) {
                val trimPart = selectedVehicle.trim.takeIf { it.isNotBlank() && it != UNKNOWN_SPEC_LABEL }
                val stylePart = selectedVehicle.resolvedBodyStyle().displayName
                listOfNotNull(stylePart, trimPart).distinct().joinToString(" • ")
            }

            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = vehicleName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.testTag("diagnostics_selected_vehicle_title")
                )
                Text(
                    text = typeSubtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563)
                )
            }

            if (vehicles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    vehicles.forEach { item ->
                        val isSelected = item.id == selectedVehicle.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectVehicle(item.id) },
                            label = { Text("${item.modelYear} ${item.model}") },
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = CrispBorderColor,
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
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("diagnostics_vehicle_chip_${item.id}")
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .testTag("diagnostics_vehicle_cutout_stage"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.48f
                        ),
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(size.width, size.height * 0.7f)
                    )
                }

                if (cutoutUri.isNotBlank()) {
                    AsyncImage(
                        model = cutoutUri,
                        contentDescription = "$vehicleName Right Side Cutout",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                } else {
                    VehicleStudioSilhouette(
                        bodyStyle = selectedVehicle.resolvedBodyStyle(),
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(105.dp),
                        bodyTint = Color(0xFF4B5563)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (isThai) {
                        "ปีรถ ${selectedVehicle.modelYear}"
                    } else {
                        "Year ${selectedVehicle.modelYear}"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF4B5563),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Educational showcase matching the requested layout:
 * "What is OBD Bluetooth & OBD Wi-Fi" + blue underlined "See Explains" link + OBD dongles banner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatIsObdShowcaseSection(
    onOpenExplains: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("what_is_obd_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = "What is OBD Bluetooth\n& OBD Wi-Fi",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = if (isThai) "ดูคำอธิบาย" else "See Explains",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2563EB),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable(onClick = onOpenExplains)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("diagnostics_see_explains_button")
            )
        }

        ObdAdaptersOverviewBanner(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenExplains)
        )
    }
}

/**
 * Reusable OBD-II dual-adapter showcase banner (uploaded Frame 2 PNG).
 */
@Composable
fun ObdAdaptersOverviewBanner(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.img_obd_adapters_banner),
        contentDescription = "OBD Bluetooth & OBD Wi-Fi Adapters",
        contentScale = ContentScale.FillWidth,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
    )
}

/**
 * Connect OBD visual cards using uploaded Frame 3 (Bluetooth) & Frame 4 (Wi-Fi) PNG assets,
 * routing users directly into Bluetooth or Wi-Fi connection setup.
 */
@Composable
fun ConnectObdCardsSection(
    connectionSummary: DiagnosticsConnectionSummary,
    onOpenConnectionSetup: (ObdConnectionMethod) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_connection_status_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isThai) "เชื่อมต่อ OBD" else "Connect OBD",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (connectionSummary.isAnyConnected) Color(0xFFD1FAE5) else Color(0xFFF3F4F6),
                border = BorderStroke(
                    width = 1.2.dp,
                    color = if (connectionSummary.isAnyConnected) Color(0xFF047857) else CrispBorderColor
                )
            ) {
                Text(
                    text = if (connectionSummary.isAnyConnected) {
                        val prefix = if (isThai) "เชื่อมต่อแล้ว" else "Connected"
                        "$prefix • ${connectionSummary.activeAdapterName.orEmpty()}"
                    } else {
                        if (isThai) "ยังไม่เชื่อมต่อ" else "Disconnected"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = if (connectionSummary.isAnyConnected) Color(0xFF065F46) else Color(0xFF1F2937),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("diagnostics_connection_status_text")
                )
            }
        }

        // Frame 3: OBD-II Bluetooth Connect Card
        ObdConnectBannerCard(
            title = "Bluetooth",
            ctaLabel = if (isThai) "เชื่อมต่อ Bluetooth" else "Connect Bluetooth",
            drawableRes = R.drawable.img_connect_obd_bluetooth,
            onClick = { onOpenConnectionSetup(ObdConnectionMethod.BLUETOOTH) },
            modifier = Modifier.testTag("diagnostics_bluetooth_option_row")
        )

        // Frame 4: OBD-II Wi-Fi Connect Card
        ObdConnectBannerCard(
            title = "Wi-Fi",
            ctaLabel = if (isThai) "เชื่อมต่อ Wi-Fi" else "Connect Wi-Fi",
            drawableRes = R.drawable.img_connect_obd_wifi,
            onClick = { onOpenConnectionSetup(ObdConnectionMethod.WIFI) },
            modifier = Modifier.testTag("diagnostics_wifi_option_row")
        )

        val liveDetail = connectionSummary.statusDetailMessage
        if (connectionSummary.isAnyConnected || !liveDetail.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3F4F6),
                border = BorderStroke(1.2.dp, CrispBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = liveDetail ?: if (isThai) {
                        "เชื่อมต่อพร้อมใช้งาน: ${connectionSummary.activeAdapterName.orEmpty()}"
                    } else {
                        "Connected & Ready: ${connectionSummary.activeAdapterName.orEmpty()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF111827),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun DiagnosticsLiveTelemetryCard(
    liveTelemetry: LiveTelemetryStreamState,
    isConnected: Boolean,
    adapterName: String?,
    onOpenScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    val session = liveTelemetry.latestSession
    val readingsByPid = remember(session) {
        session?.livePidReadings?.associateBy { it.pidCode.uppercase() }.orEmpty()
    }
    fun formatPid(pid: String, suffix: String, decimals: Int = 0): String {
        val reading = readingsByPid[pid]
        val value = reading?.takeIf { it.isAvailable }?.value ?: return "--"
        val numberStr = if (decimals == 0) {
            value.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${decimals}f", value)
        }
        return "$numberStr $suffix"
    }

    val confirmedCount = session?.confirmedDtcs?.size ?: 0
    val pendingCount = session?.pendingDtcs?.size ?: 0

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (liveTelemetry.isStreaming) Color(0xFF047857) else CrispBorderColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_live_telemetry_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isThai) "ค่า ECU สดจากรถ (Real-Time)" else "Live ECU Telemetry Stream",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                    val subtitleText = liveTelemetry.streamStatusNote
                        ?: adapterName?.let {
                            if (isThai) "อะแดปเตอร์: $it" else "Adapter: $it"
                        }
                        ?: if (isThai) "กำลังรับข้อมูลจากรถ" else "Receiving vehicle data"
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF374151),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (liveTelemetry.isStreaming || isConnected) Color(0xFFD1FAE5) else Color(0xFFE5E7EB),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (liveTelemetry.isStreaming || isConnected) Color(0xFF047857) else CrispBorderColor
                    )
                ) {
                    Text(
                        text = if (liveTelemetry.isStreaming) "● LIVE" else if (isConnected) "READY" else "LAST",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (liveTelemetry.isStreaming || isConnected) Color(0xFF065F46) else Color(0xFF374151),
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("diagnostics_live_badge")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveMiniStatTile(
                    label = if (isThai) "รอบเครื่อง" else "RPM",
                    value = formatPid("010C", "rpm"),
                    modifier = Modifier.weight(1f)
                )
                LiveMiniStatTile(
                    label = if (isThai) "ความเร็ว" else "Speed",
                    value = formatPid("010D", if (isThai) "กม./ชม." else "km/h"),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveMiniStatTile(
                    label = if (isThai) "น้ำหล่อเย็น" else "Coolant",
                    value = formatPid("0105", "°C"),
                    modifier = Modifier.weight(1f)
                )
                LiveMiniStatTile(
                    label = if (isThai) "แบตเตอรี่" else "Battery",
                    value = formatPid("0142", "V", decimals = 1),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalDtcs = confirmedCount + pendingCount
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (totalDtcs > 0) Color(0xFFFEE2E2) else Color(0xFFE5E7EB),
                    border = BorderStroke(
                        1.dp,
                        if (totalDtcs > 0) Color(0xFFDC2626) else Color(0xFF9CA3AF)
                    )
                ) {
                    Text(
                        text = if (session == null) {
                            if (isThai) "กำลังอ่านสถานะ DTC..." else "Checking DTC status..."
                        } else if (totalDtcs > 0) {
                            if (isThai) "พบโค้ดปัญหา $confirmedCount ยืนยัน / $pendingCount รอตรวจ"
                            else "$confirmedCount Confirmed • $pendingCount Pending DTC"
                        } else {
                            if (isThai) "ไม่พบโค้ดปัญหา (0 DTC)" else "No Active DTCs (0)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (totalDtcs > 0) Color(0xFF991B1B) else Color(0xFF1F2937),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Text(
                    text = if (isThai) "สแกนเชิงลึก →" else "Full Scan →",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier
                        .clickable(onClick = onOpenScan)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveMiniStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4B5563),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ObdConnectBannerCard(
    title: String,
    ctaLabel: String,
    drawableRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = "$title - $ctaLabel",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ScanSummaryCard(
    selectedVehicle: Vehicle?,
    onOpenScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val isThai = language == AppLanguage.THAI
    val strings = appStrings(language)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.5.dp, CrispBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_scan_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Troubleshoot,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.diagnosticsScanSectionTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black
                )
            }

            if (selectedVehicle != null) {
                val targetPrefix = if (isThai) "รถเป้าหมาย" else "Target vehicle"
                Text(
                    text = "$targetPrefix: ${selectedVehicle.modelYear} ${selectedVehicle.manufacturer} ${selectedVehicle.model}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black
                )
            }

            Text(
                text = strings.diagnosticsScanReadySummary,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827)
            )

            Button(
                onClick = onOpenScan,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("diagnostics_open_scan_button")
            ) {
                Text(strings.diagnosticsOpenScanCta)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DiagnosticsReportsHubCard(
    savedSessionCount: Int,
    recentReportCount: Int,
    onOpenResults: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    val isThai = language == AppLanguage.THAI
    val strings = appStrings(language)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.5.dp, CrispBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_history_reports_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.diagnosticsHistorySectionTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black
                )
            }

            Text(
                text = if (savedSessionCount == 0 && recentReportCount == 0) {
                    strings.diagnosticsEmptyHistoryLabel
                } else {
                    if (isThai) {
                        "บันทึกแล้ว $savedSessionCount เซสชัน • $recentReportCount รายงาน"
                    } else {
                        "$savedSessionCount saved session(s) • $recentReportCount report(s)"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubpageNavRow(
                    icon = Icons.Filled.Science,
                    title = strings.diagnosticsOpenResultsCta,
                    subtitle = if (isThai) {
                        "สรุปโค้ดปัญหา หลักฐานเซ็นเซอร์ และความน่าเชื่อถือ"
                    } else {
                        "DTC breakdown, sensor evidence, and confidence summary"
                    },
                    onClick = onOpenResults,
                    testTag = "diagnostics_nav_results_row"
                )
                SubpageNavRow(
                    icon = Icons.Filled.History,
                    title = strings.diagnosticsOpenHistoryCta,
                    subtitle = if (isThai) {
                        "ประวัติการสแกน OBD-II และการกรอกรหัสย้อนหลัง"
                    } else {
                        "Past OBD-II scans and logged diagnostic sessions"
                    },
                    onClick = onOpenHistory,
                    testTag = "diagnostics_nav_history_row"
                )
                SubpageNavRow(
                    icon = Icons.Filled.Assignment,
                    title = strings.diagnosticsOpenReportCta,
                    subtitle = if (isThai) {
                        "สรุปรายงานเป็นระเบียบพร้อมส่งต่อให้ช่างตรวจเช็ก"
                    } else {
                        "Structured inspection report ready for your mechanic"
                    },
                    onClick = onOpenReport,
                    testTag = "diagnostics_nav_report_row"
                )
            }
        }
    }
}

@Composable
private fun SubpageNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFE5E7EB),
        border = BorderStroke(1.5.dp, CrispBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF111827)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun DiagnosticsSubpageScaffold(
    title: String,
    subtitle: String,
    routeBadge: String,
    phaseNote: String,
    responsibilities: List<String>,
    selectedVehicleLabel: String?,
    onNavigateBack: () -> Unit,
    screenTestTag: String
) {
    val strings = appStrings(LocalAppLanguage.current)
    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            DiagnosticsTopBar(
                title = title,
                subtitle = subtitle,
                onNavigateBack = onNavigateBack,
                backButtonTestTag = "${screenTestTag}_back"
            )
        },
        modifier = Modifier.testTag(screenTestTag)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF3F4F6),
                    border = BorderStroke(1.5.dp, CrispBorderColor),
                    modifier = Modifier.fillMaxWidth()
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
                            Text(
                                text = routeBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black
                            )
                            if (!selectedVehicleLabel.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color(0xFFE5E7EB),
                                    border = BorderStroke(1.2.dp, CrispBorderColor)
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

                        Text(
                            text = phaseNote,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black
                        )

                        HorizontalDivider(color = Color(0xFF9CA3AF))

                        responsibilities.forEach { item ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Black
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF111827)
                                )
                            }
                        }
                    }
                }

                ReadOnlySafetyNotice(noteText = strings.diagnosticsReadOnlyPolicyNote)

                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                    border = BorderStroke(1.5.dp, CrispButtonBorderColor),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Back to ${strings.diagnosticsTitle}")
                }
            }
        }
    }
}
