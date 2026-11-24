package com.example.ui.overview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import com.example.data.obd.BatteryChemistryType
import com.example.data.obd.CircularRingStyle
import com.example.data.obd.DiagnosticStatusDetailSummary
import com.example.data.obd.EfficiencyBand
import com.example.data.obd.ElectricalOperatingCondition
import com.example.data.obd.ElectricalSystemType
import com.example.data.obd.LiveDataCategoryId
import com.example.data.obd.LiveMetricGaugeItem
import com.example.data.obd.LiveVehicleCategorySummary
import com.example.data.obd.SystemVoltageErrorState
import com.example.data.obd.SystemVoltageProcessedData
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage

/**
 * Full-screen detail view for any Live Vehicle Data category:
 * - Numeric categories ("Driving & Engine", "Engine Temperature", "System Voltage", "Air Intake & Fuel", "Transmission"):
 *   2-column circular gauge grid with crisp high-contrast blue/red rings.
 * - "Diagnostic Status": Dedicated screen layout matching iPhone 17 - 15
 *   (Confirmed DTC, Pending DTC, Freeze Frame, MIL Status, Readiness Monitor, and AI Explain).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveVehicleDataDetailScreen(
    category: LiveVehicleCategorySummary,
    ecuSourceLabel: String,
    isAdapterConnected: Boolean,
    isRefreshing: Boolean,
    onRefreshEcu: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onBack: () -> Unit,
    diagnosticDetail: DiagnosticStatusDetailSummary = DiagnosticStatusDetailSummary(),
    diagnosticAiState: DiagnosticStatusAiUiState = DiagnosticStatusAiUiState.Idle,
    onExplainDiagnosticStatus: (forceRefresh: Boolean) -> Unit = {},
    systemVoltageData: SystemVoltageProcessedData? = null,
    onUpdateSystemVoltageCriteria: ((ElectricalSystemType?, BatteryChemistryType?, ElectricalOperatingCondition?) -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    val percentColor = categoryBandColor(category.band)

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("live_detail_header_title")
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("live_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isThai) "กลับไปหน้าภาพรวมรถ" else "Back to Car Overview",
                            tint = Color(0xFF111827)
                        )
                    }
                },
                actions = {
                    if (isAdapterConnected) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFD1FAE5)
                        ) {
                            Text(
                                text = "● LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onRefreshEcu,
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("live_detail_refresh_ecu")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = if (isThai) {
                                    "รีเฟรชข้อมูล ECU แบบเรียลไทม์"
                                } else {
                                    "Refresh live ECU data"
                                },
                                tint = Color(0xFF111827)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("live_detail_screen"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (category.categoryId == LiveDataCategoryId.DIAGNOSTIC_STATUS) {
                    DiagnosticStatusDetailContent(
                        detail = diagnosticDetail,
                        ecuSourceLabel = ecuSourceLabel,
                        isThai = isThai,
                        aiState = diagnosticAiState,
                        onExplainDiagnosticStatus = onExplainDiagnosticStatus,
                        onOpenDiagnostics = onOpenDiagnostics
                    )
                } else {
                    // Status summary bar (real ECU source & efficiency band)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF3F6FA),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = ecuSourceLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFF111827)
                                )
                                Text(
                                    text = category.statusNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4B5563)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = percentColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = category.formattedPercent,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = percentColor,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("live_detail_efficiency_badge")
                                )
                            }
                        }
                    }

                    // 2-Column circular gauge grid matching iPhone 17 - 11..14
                    val rows = category.metrics.chunked(2)
                    rows.forEach { rowMetrics ->
                        if (rowMetrics.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Top
                            ) {
                                CircularMetricRingCard(
                                    metric = rowMetrics[0],
                                    modifier = Modifier.weight(1f)
                                )
                                CircularMetricRingCard(
                                    metric = rowMetrics[1],
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularMetricRingCard(
                                    metric = rowMetrics.first(),
                                    modifier = Modifier.fillMaxWidth(0.55f)
                                )
                            }
                        }
                    }

                    if (category.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE && systemVoltageData != null) {
                        SystemVoltageDiagnosticsAndCriteriaSection(
                            data = systemVoltageData,
                            isThai = isThai,
                            onUpdateCriteria = onUpdateSystemVoltageCriteria
                        )
                    }

                    if (!category.hasRealEcuData) {
                        Button(
                            onClick = onOpenDiagnostics,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF111827),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("live_detail_open_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Troubleshoot,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = if (isThai) {
                                    "เปิดหน้าวิเคราะห์ระบบรถ (Diagnostics)"
                                } else {
                                    "Open Vehicle Diagnostics"
                                }
                            )
                        }
                    }

                    Text(
                        text = if (category.categoryId == LiveDataCategoryId.SYSTEM_VOLTAGE) {
                            if (isThai) {
                                "OBD-II Mode 01 PID 42 รายงานแรงดันไฟโมดูลควบคุม (Control Module Voltage) จาก ECU โดยตรง ไม่ใช่การวัดจากขั้วแบตเตอรี่โดยตรง (ค่าที่ไม่รองรับหรือขาดการเชื่อมต่อจะแสดงเป็น N/A)"
                            } else {
                                "OBD-II Mode 01 PID 42 reports ECU Control Module Voltage (not a direct battery-terminal measurement). Unsupported or disconnected values show N/A / Unavailable."
                            }
                        } else if (isThai) {
                            "ข้อมูลนี้ดึงมาจากกล่องควบคุมเครื่องยนต์ (ECU) ของรถคุณโดยตรง (ค่าที่ไม่รองรับหรือยังไม่ได้เชื่อมต่อจะแสดงเป็น --)"
                        } else {
                            "Live telemetry is read directly from your vehicle ECU (unsupported or disconnected sensors display as --)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDC2626),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemVoltageDiagnosticsAndCriteriaSection(
    data: SystemVoltageProcessedData,
    isThai: Boolean,
    onUpdateCriteria: ((ElectricalSystemType?, BatteryChemistryType?, ElectricalOperatingCondition?) -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (data.errorState != SystemVoltageErrorState.NONE || data.isStale) {
            val isStaleWarning = data.isStale || data.errorState == SystemVoltageErrorState.DATA_OUTDATED
            val bannerBg = if (isStaleWarning) Color(0xFFFFFBEB) else Color(0xFFFEF2F2)
            val bannerBorder = if (isStaleWarning) Color(0xFFFCD34D) else Color(0xFFFCA5A5)
            val bannerText = if (isStaleWarning) Color(0xFF92400E) else Color(0xFF991B1B)

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bannerBg,
                border = BorderStroke(1.dp, bannerBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_voltage_error_banner")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = data.errorState.localizedTitle(isThai).ifBlank {
                            if (isThai) "ข้อมูลเก่าเกินกำหนด" else "Data outdated"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = bannerText
                    )
                    val detail = data.errorMessage?.takeIf { it.isNotBlank() }
                        ?: data.missingInformation.firstOrNull()
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = bannerText
                        )
                    }
                }
            }
        }

        if (onUpdateCriteria != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_voltage_criteria_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isThai) {
                            "เกณฑ์ระบบไฟที่ยืนยันสำหรับรถคันนี้"
                        } else {
                            "Verified Vehicle Electrical Configuration"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = if (isThai) {
                            "ระบบไฟ 12V / 24V ต้องอ้างอิงจากข้อมูลรถจริงเท่านั้น จะไม่เดาจากค่าแรงดันไฟเพียงค่าเดียว"
                        } else {
                            "System Type uses verified vehicle configuration only and is never guessed from a single voltage reading."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4B5563)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ElectricalSystemType.entries.forEach { sys ->
                            FilterChip(
                                selected = data.systemType == sys,
                                onClick = { onUpdateCriteria(sys, null, null) },
                                label = { Text(sys.displayLabel, maxLines = 1) },
                                modifier = Modifier.testTag("voltage_criteria_sys_${sys.code.lowercase()}")
                            )
                        }
                    }

                    Text(
                        text = if (isThai) "ชนิดแบตเตอรี่และสภาวะการทำงาน" else "Battery Type & Operating Condition",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF374151)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            BatteryChemistryType.STANDARD_LEAD_ACID,
                            BatteryChemistryType.AGM_EFB,
                            BatteryChemistryType.LFP_LITHIUM,
                            BatteryChemistryType.UNSPECIFIED
                        ).forEach { bat ->
                            FilterChip(
                                selected = data.batteryType == bat,
                                onClick = { onUpdateCriteria(null, bat, null) },
                                label = { Text(bat.localizedLabel(isThai), maxLines = 1) }
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ElectricalOperatingCondition.entries.forEach { cond ->
                            FilterChip(
                                selected = data.operatingCondition == cond,
                                onClick = { onUpdateCriteria(null, null, cond) },
                                label = { Text(cond.localizedLabel(isThai), maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStatusDetailContent(
    detail: DiagnosticStatusDetailSummary,
    ecuSourceLabel: String,
    isThai: Boolean,
    aiState: DiagnosticStatusAiUiState,
    onExplainDiagnosticStatus: (forceRefresh: Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    // Automatically run AI Explain whenever an actual ECU session is loaded
    LaunchedEffect(detail.sessionKey, detail.hasEcuSession) {
        if (detail.hasEcuSession) {
            onExplainDiagnosticStatus(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_status_detail_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top overview wave banner matching iPhone 17 - 15
        DiagnosticWaveBannerCard(
            leftPrimaryText = detail.overallStatusText,
            leftSecondaryText = ecuSourceLabel,
            rightBadgeText = detail.overallBadgeText,
            rightColors = when {
                detail.confirmedCodes.isNotEmpty() -> listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                detail.pendingCodes.isNotEmpty() -> listOf(Color(0xFFF59E0B), Color(0xFFB45309))
                detail.hasEcuSession -> listOf(Color(0xFF2563EB), Color(0xFF1E40AF))
                else -> listOf(Color(0xFF6B8FB5), Color(0xFF47698E))
            },
            modifier = Modifier.testTag("diag_row_overview")
        )

        DiagnosticLabeledWaveRow(
            title = if (isThai) "โค้ดปัญหาที่ยืนยันแล้ว (Confirmed DTC)" else "Confirmed DTC",
            detailText = detail.confirmedDetailText,
            badgeText = detail.confirmedBadgeText,
            badgeColors = if (detail.confirmedCodes.isNotEmpty()) {
                listOf(Color(0xFFDC2626), Color(0xFF991B1B))
            } else {
                listOf(Color(0xFF6B8FB5), Color(0xFF47698E))
            },
            testTag = "diag_row_confirmed_dtc"
        )

        DiagnosticLabeledWaveRow(
            title = if (isThai) "โค้ดปัญหาที่รอตรวจสอบ (Pending DTC)" else "Pending DTC",
            detailText = detail.pendingDetailText,
            badgeText = detail.pendingBadgeText,
            badgeColors = if (detail.pendingCodes.isNotEmpty()) {
                listOf(Color(0xFFD97706), Color(0xFF92400E))
            } else {
                listOf(Color(0xFF6B8FB5), Color(0xFF47698E))
            },
            testTag = "diag_row_pending_dtc"
        )

        DiagnosticLabeledWaveRow(
            title = if (isThai) "ข้อมูลขณะเกิดปัญหา (Freeze Frame)" else "Freeze Frame",
            detailText = detail.freezeFrameDetailText,
            badgeText = detail.freezeFrameBadgeText,
            badgeColors = listOf(Color(0xFF6B8FB5), Color(0xFF47698E)),
            testTag = "diag_row_freeze_frame"
        )

        DiagnosticLabeledWaveRow(
            title = if (isThai) "สถานะไฟเตือนเครื่องยนต์ (MIL Status)" else "MIL Status",
            detailText = detail.milDetailText,
            badgeText = detail.milBadgeText,
            badgeColors = when (detail.milOn) {
                true -> listOf(Color(0xFFDC2626), Color(0xFF991B1B))
                false -> listOf(Color(0xFF16A34A), Color(0xFF166534))
                null -> listOf(Color(0xFF6B8FB5), Color(0xFF47698E))
            },
            testTag = "diag_row_mil_status"
        )

        DiagnosticLabeledWaveRow(
            title = if (isThai) "ความพร้อมระบบตรวจสอบ (Readiness Monitor)" else "Readiness Monitor",
            detailText = detail.readinessDetailText,
            badgeText = detail.readinessBadgeText,
            badgeColors = listOf(Color(0xFF6B8FB5), Color(0xFF47698E)),
            testTag = "diag_row_readiness_monitor"
        )

        // White bordered AI Explain Box at bottom matching iPhone 17 - 15
        DiagnosticAiExplainCard(
            detail = detail,
            isThai = isThai,
            aiState = aiState,
            onExplainClick = { onExplainDiagnosticStatus(true) },
            onOpenDiagnostics = onOpenDiagnostics
        )

        Text(
            text = if (isThai) {
                "ข้อมูลนี้ดึงมาจากกล่องควบคุมเครื่องยนต์ (ECU) ของรถคุณโดยตรง"
            } else {
                "Diagnostic status data is read directly from your vehicle ECU."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFDC2626),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun DiagnosticLabeledWaveRow(
    title: String,
    detailText: String,
    badgeText: String,
    badgeColors: List<Color>,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )
        DiagnosticWaveBannerCard(
            leftPrimaryText = detailText,
            leftSecondaryText = null,
            rightBadgeText = badgeText,
            rightColors = badgeColors
        )
    }
}

@Composable
private fun DiagnosticWaveBannerCard(
    leftPrimaryText: String,
    leftSecondaryText: String?,
    rightBadgeText: String,
    rightColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Curved S-wave slate-blue section on right matching iPhone 17 - 15
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                val w = size.width
                val h = size.height
                val splitX = w * 0.65f
                val wavePath = Path().apply {
                    moveTo(splitX, 0f)
                    cubicTo(
                        splitX - w * 0.045f,
                        h * 0.32f,
                        splitX + w * 0.045f,
                        h * 0.68f,
                        splitX - w * 0.01f,
                        h
                    )
                    lineTo(w, h)
                    lineTo(w, 0f)
                    close()
                }
                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(colors = rightColors)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.64f)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = leftPrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!leftSecondaryText.isNullOrBlank()) {
                        Text(
                            text = leftSecondaryText,
                            fontSize = 11.sp,
                            color = Color(0xFF4B5563),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .padding(start = 8.dp, end = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rightBadgeText,
                        fontSize = if (rightBadgeText.length > 7) 14.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticAiExplainCard(
    detail: DiagnosticStatusDetailSummary,
    isThai: Boolean,
    aiState: DiagnosticStatusAiUiState,
    onExplainClick: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFD1D5DB)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_ai_explain_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isThai) "คำอธิบายจาก AI (AI Explain)" else "AI Explain",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                TextButton(
                    onClick = onExplainClick,
                    enabled = aiState !is DiagnosticStatusAiUiState.Loading,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("diagnostic_ai_explain_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            aiState is DiagnosticStatusAiUiState.Ready && isThai -> "รีเฟรชคำอธิบาย"
                            aiState is DiagnosticStatusAiUiState.Ready -> "Refresh"
                            isThai -> "อธิบายแบบเข้าใจง่าย"
                            else -> "Explain Simply"
                        },
                        color = Color(0xFF1D4ED8),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE5E7EB))

            when (aiState) {
                is DiagnosticStatusAiUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF1D4ED8)
                        )
                        Text(
                            text = if (isThai) {
                                "กำลังแปลรหัสปัญหาจากกล่อง ECU เป็นภาษาเข้าใจง่าย..."
                            } else {
                                "Translating ECU codes into everyday language..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF111827)
                        )
                    }
                }

                is DiagnosticStatusAiUiState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = aiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.testTag("diagnostic_ai_error_text")
                        )
                        OutlinedButton(
                            onClick = onExplainClick,
                            border = BorderStroke(1.dp, Color(0xFF1D4ED8))
                        ) {
                            Text(
                                text = if (isThai) "ลองใหม่อีกครั้ง" else "Retry",
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }
                }

                is DiagnosticStatusAiUiState.Ready -> {
                    val info = aiState.explanation
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diagnostic_ai_ready_content"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = info.headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                        if (info.plainSummary.isNotBlank()) {
                            Text(
                                text = info.plainSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF111827),
                                lineHeight = 22.sp
                            )
                        }
                        if (info.codeBreakdown.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    info.codeBreakdown.forEach { bullet ->
                                        Text(
                                            text = "• $bullet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF111827),
                                            lineHeight = 19.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (info.whatToDoNext.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isThai) {
                                            "สิ่งที่ควรทำต่อไป (แบบเข้าใจง่าย)"
                                        } else {
                                            "What To Do Next"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E40AF)
                                    )
                                    Text(
                                        text = info.whatToDoNext,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF111827),
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }
                    }
                }

                is DiagnosticStatusAiUiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (detail.hasEcuSession) {
                                if (isThai) {
                                    "กดปุ่ม 'อธิบายแบบเข้าใจง่าย' เพื่อให้ AI สรุปความหมายของโค้ดจาก ECU ให้เข้าใจง่าย ไม่ต้องมีพื้นฐานเรื่องรถก็อ่านรู้เรื่อง"
                                } else {
                                    "Tap 'Explain Simply' for a friendly plain-English breakdown of your ECU codes—no mechanic background needed."
                                }
                            } else {
                                if (isThai) {
                                    "เชื่อมต่ออุปกรณ์ OBD-II แล้วสแกนรถของคุณ เพื่อให้ AI ช่วยแปลไฟเตือนและรหัสปัญหาทั้งหมดเป็นภาษาคนทั่วไปที่เข้าใจง่าย"
                                } else {
                                    "Connect your OBD-II scanner and run a read-only scan so AI can translate any warning lights or fault codes into plain everyday terms."
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF111827),
                            lineHeight = 21.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExplainClick,
                                border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isThai) "อธิบายความหมาย" else "Explain Status",
                                    color = Color(0xFF1D4ED8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!detail.hasEcuSession) {
                                Button(
                                    onClick = onOpenDiagnostics,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF111827),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("live_detail_open_diagnostics_button")
                                ) {
                                    Text(
                                        text = if (isThai) "สแกนรถเลย" else "Scan Vehicle",
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CircularMetricRingCard(
    metric: LiveMetricGaugeItem,
    modifier: Modifier = Modifier
) {
    val sweepFraction = metric.ringSweepFraction
    val ringStyle = metric.ringStyle

    Column(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("metric_gauge_${metric.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                // Darker, clearly visible track color so empty/partial circles are unmistakably seen
                val trackColor = Color(0xFFC7D2DF)

                // Background circular track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )

                when (ringStyle) {
                    CircularRingStyle.UNAVAILABLE_EMPTY -> Unit
                    CircularRingStyle.FULL_RED -> {
                        val redBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                        )
                        drawArc(
                            brush = redBrush,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    CircularRingStyle.PROPORTIONAL_BLUE -> {
                        // Deeper, high-contrast royal blue -> navy gradient arc
                        val blueBrush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A))
                        )
                        val sweep = (sweepFraction.coerceIn(0f, 1f)) * 360f
                        if (sweep > 0f) {
                            drawArc(
                                brush = blueBrush,
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                val primaryLen = metric.formattedPrimaryText.length
                val allowTwoLines = metric.textDisplayValue != null && primaryLen >= 9
                Text(
                    text = metric.formattedPrimaryText,
                    fontSize = when {
                        primaryLen >= 11 -> 11.sp
                        primaryLen >= 9 -> 12.sp
                        primaryLen >= 7 -> 13.5.sp
                        primaryLen >= 5 -> 16.sp
                        else -> 20.sp
                    },
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !metric.isAvailable -> Color(0xFF6B7280)
                        ringStyle == CircularRingStyle.FULL_RED -> Color(0xFFDC2626)
                        else -> Color(0xFF111827)
                    },
                    maxLines = if (allowTwoLines) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (metric.formattedUnitText.isNotBlank()) {
                    Text(
                        text = metric.formattedUnitText,
                        fontSize = if (metric.formattedUnitText.length > 5) 10.5.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (metric.isAvailable) Color(0xFF374151) else Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Text(
            text = metric.label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            ),
            color = Color(0xFF111827),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun categoryBandColor(band: EfficiencyBand): Color = when (band) {
    EfficiencyBand.RED -> Color(0xFFDC2626)
    EfficiencyBand.YELLOW -> Color(0xFFD97706)
    EfficiencyBand.GREEN -> Color(0xFF16A34A)
}

fun categoryBandTintColor(band: EfficiencyBand): Color = when (band) {
    EfficiencyBand.RED -> Color(0xFFEF4444).copy(alpha = 0.14f)
    EfficiencyBand.YELLOW -> Color(0xFFF59E0B).copy(alpha = 0.20f)
    EfficiencyBand.GREEN -> Color(0xFF22C55E).copy(alpha = 0.15f)
}
