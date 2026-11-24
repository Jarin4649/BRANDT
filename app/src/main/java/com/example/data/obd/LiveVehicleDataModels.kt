package com.example.data.obd

import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class LiveDataCategoryId(val id: String, val titleEn: String, val titleTh: String) {
    DRIVING_ENGINE("driving_engine", "Driving & Engine", "การขับขี่และเครื่องยนต์"),
    ENGINE_TEMPERATURE("engine_temp", "Engine Temperature", "อุณหภูมิเครื่องยนต์"),
    SYSTEM_VOLTAGE("system_voltage", "System Voltage", "แรงดันไฟระบบ (System Voltage)"),
    AIR_INTAKE_FUEL("air_intake_fuel", "Air Intake & Fuel", "อากาศเข้าและเชื้อเพลิง"),
    TRANSMISSION("transmission", "Transmission", "ระบบส่งกำลัง (เกียร์)"),
    DIAGNOSTIC_STATUS("diagnostic_status", "Diagnostic Status", "สถานะการวิเคราะห์ ECU");

    fun localizedTitle(language: AppLanguage): String =
        if (language == AppLanguage.THAI) titleTh else titleEn
}

enum class EfficiencyBand {
    RED,    // 0-49%
    YELLOW, // 50-69%
    GREEN;  // 70-100%

    companion object {
        fun fromPercent(percent: Int): EfficiencyBand = when (percent.coerceIn(0, 100)) {
            in 0..49 -> RED
            in 50..69 -> YELLOW
            else -> GREEN
        }
    }
}

enum class CircularRingStyle {
    UNAVAILABLE_EMPTY,
    PROPORTIONAL_BLUE, // 0..100 mapped proportionally (e.g. 50 -> half ring)
    FULL_RED           // > 100 filled full ring in red (e.g. speed 140 km/h)
}

data class LiveMetricGaugeItem(
    val id: String,
    val label: String,
    val pidCode: String,
    val rawNumericValue: Double?,
    val formattedPrimaryText: String,
    val formattedUnitText: String,
    val textDisplayValue: String? = null,
    val isAvailable: Boolean,
    val ringStyleOverride: CircularRingStyle? = null,
    val ringSweepOverride: Float? = null
) {
    val ringStyle: CircularRingStyle
        get() {
            ringStyleOverride?.let { return it }
            val v = rawNumericValue ?: return CircularRingStyle.UNAVAILABLE_EMPTY
            return if (v > 100.0) CircularRingStyle.FULL_RED else CircularRingStyle.PROPORTIONAL_BLUE
        }

    /**
     * Sweep angle fraction in [0f, 1f]:
     * - UNAVAILABLE_EMPTY -> 0f
     * - PROPORTIONAL_BLUE -> abs(value).coerceIn(0.0, 100.0) / 100f (e.g. 50 -> 0.5f = 180°)
     * - FULL_RED -> 1f (360° red ring for values > 100)
     */
    val ringSweepFraction: Float
        get() {
            ringSweepOverride?.let { return it.coerceIn(0f, 1f) }
            return when (ringStyle) {
                CircularRingStyle.UNAVAILABLE_EMPTY -> 0f
                CircularRingStyle.FULL_RED -> 1f
                CircularRingStyle.PROPORTIONAL_BLUE -> {
                    val magnitude = abs(rawNumericValue ?: 0.0).coerceIn(0.0, 100.0)
                    (magnitude / 100.0).toFloat()
                }
            }
        }
}

data class LiveVehicleCategorySummary(
    val categoryId: LiveDataCategoryId,
    val title: String,
    val efficiencyPercent: Int,
    val band: EfficiencyBand,
    val hasRealEcuData: Boolean,
    val statusNote: String,
    val metrics: List<LiveMetricGaugeItem>
) {
    val formattedPercent: String
        get() = efficiencyPercent.coerceIn(0, 100).toString().padStart(2, '0') + "%"
}

data class DiagnosticStatusDetailSummary(
    val hasEcuSession: Boolean = false,
    val isAdapterConnected: Boolean = false,
    val sessionKey: String = "disconnected",
    val overallStatusText: String = "ECU Not Connected",
    val overallBadgeText: String = "Offline",
    val confirmedCodes: List<String> = emptyList(),
    val confirmedDetailText: String = "No ECU scan recorded",
    val confirmedBadgeText: String = "--",
    val pendingCodes: List<String> = emptyList(),
    val pendingDetailText: String = "No ECU scan recorded",
    val pendingBadgeText: String = "--",
    val freezeFrameDetailText: String = "No ECU scan recorded",
    val freezeFrameBadgeText: String = "Status",
    val milOn: Boolean? = null,
    val milDetailText: String = "Unavailable until vehicle scan",
    val milBadgeText: String = "ON / OFF",
    val readinessDetailText: String = "Unavailable until vehicle scan",
    val readinessBadgeText: String = "--",
    val incompleteMonitors: List<String> = emptyList()
)

data class LiveVehicleDataSnapshot(
    val isAdapterConnected: Boolean = false,
    val hasActiveEcuSession: Boolean = false,
    val sourceLabel: String = "ECU Not Connected",
    val capturedAtEpochMs: Long? = null,
    val confirmedDtcCount: Int = 0,
    val pendingDtcCount: Int = 0,
    val categories: List<LiveVehicleCategorySummary> = emptyList(),
    val diagnosticStatusDetail: DiagnosticStatusDetailSummary = DiagnosticStatusDetailSummary(),
    val systemVoltageData: SystemVoltageProcessedData? = null
)

object LiveVehicleDataMapper {

    fun map(
        session: ObdDiagnosticSession?,
        isAdapterConnected: Boolean,
        connectedAdapterLabel: String? = null,
        language: AppLanguage = LanguagePreferenceStore.currentLanguage.value,
        recentVoltageSamples: List<Double> = emptyList(),
        connectionManagerState: ObdConnectionManagerState? = null,
        systemVoltageCriteria: SystemVoltageCriteria = connectionManagerState?.systemVoltageCriteria ?: SystemVoltageCriteria(),
        isCommunicationInterrupted: Boolean = false,
        lastDisconnectReason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): LiveVehicleDataSnapshot {
        val isThai = language == AppLanguage.THAI
        val hasEcuSession = session != null && !session.isSimulatedTestData &&
            session.sourceMetadata.sourceType != ObdDataSourceType.MANUAL_DTC_ENTRY

        val pids = if (hasEcuSession) {
            session!!.livePidReadings.associateBy { it.pidCode.uppercase() }
        } else {
            emptyMap()
        }
        val confirmedDtcs = if (hasEcuSession) session!!.confirmedDtcs else emptyList()
        val pendingDtcs = if (hasEcuSession) session!!.pendingDtcs else emptyList()

        val processedVoltage = SystemVoltageProcessor.processSession(
            session = session,
            connectionManagerState = connectionManagerState,
            isTransportConnected = isAdapterConnected,
            criteria = systemVoltageCriteria,
            recentVoltageSamples = recentVoltageSamples,
            lastDisconnectError = lastDisconnectReason,
            isCommunicationInterrupted = isCommunicationInterrupted,
            nowMs = nowMs
        )

        val drivingCategory = buildDrivingAndEngineCategory(pids, confirmedDtcs, pendingDtcs, hasEcuSession, isThai)
        val temperatureCategory = buildEngineTemperatureCategory(pids, confirmedDtcs, pendingDtcs, hasEcuSession, isThai)
        val systemVoltageCategory = buildSystemVoltageCategory(
            processed = processedVoltage,
            confirmedDtcs = confirmedDtcs,
            pendingDtcs = pendingDtcs,
            hasEcuSession = hasEcuSession,
            isThai = isThai
        )
        val airFuelCategory = buildAirIntakeFuelCategory(pids, confirmedDtcs, pendingDtcs, hasEcuSession, isThai)
        val transmissionCategory = buildTransmissionCategory(pids, confirmedDtcs, pendingDtcs, hasEcuSession, isThai)
        val diagnosticStatusCategory = buildDiagnosticStatusCategory(
            pids = pids,
            confirmedDtcs = confirmedDtcs,
            pendingDtcs = pendingDtcs,
            supportedPidCount = if (hasEcuSession) session!!.supportedPids.size else 0,
            hasEcuSession = hasEcuSession,
            isAdapterConnected = isAdapterConnected,
            isThai = isThai
        )

        val sourceLabel = when {
            isAdapterConnected && !connectedAdapterLabel.isNullOrBlank() ->
                if (isThai) "เชื่อมต่อ ECU แล้ว • $connectedAdapterLabel" else "Connected ECU • $connectedAdapterLabel"
            hasEcuSession -> session!!.sourceMetadata.adapterOrFileLabel
            else ->
                if (isThai) "ยังไม่ได้เชื่อมต่อกล่อง ECU" else "ECU Not Connected"
        }

        val diagnosticDetail = buildDiagnosticStatusDetailSummary(
            session = if (hasEcuSession) session else null,
            hasEcuSession = hasEcuSession,
            isAdapterConnected = isAdapterConnected,
            isThai = isThai
        )

        return LiveVehicleDataSnapshot(
            isAdapterConnected = isAdapterConnected,
            hasActiveEcuSession = hasEcuSession,
            sourceLabel = sourceLabel,
            capturedAtEpochMs = session?.takeIf { hasEcuSession }?.capturedAtEpochMs,
            confirmedDtcCount = confirmedDtcs.size,
            pendingDtcCount = pendingDtcs.size,
            categories = listOf(
                drivingCategory,
                temperatureCategory,
                systemVoltageCategory,
                airFuelCategory,
                transmissionCategory,
                diagnosticStatusCategory
            ),
            diagnosticStatusDetail = diagnosticDetail,
            systemVoltageData = processedVoltage
        )
    }

    private fun buildDiagnosticStatusDetailSummary(
        session: ObdDiagnosticSession?,
        hasEcuSession: Boolean,
        isAdapterConnected: Boolean,
        isThai: Boolean
    ): DiagnosticStatusDetailSummary {
        if (!hasEcuSession || session == null) {
            return DiagnosticStatusDetailSummary(
                hasEcuSession = false,
                isAdapterConnected = isAdapterConnected,
                sessionKey = if (isAdapterConnected) "connected_no_scan" else "disconnected",
                overallStatusText = when {
                    isAdapterConnected && isThai -> "เชื่อมต่ออะแดปเตอร์แล้ว • พร้อมสแกน"
                    isAdapterConnected -> "Adapter Connected • Ready to Scan"
                    isThai -> "ยังไม่ได้เชื่อมต่อกล่อง ECU"
                    else -> "ECU Not Connected"
                },
                overallBadgeText = when {
                    isAdapterConnected && isThai -> "พร้อม"
                    isAdapterConnected -> "Ready"
                    isThai -> "ออฟไลน์"
                    else -> "Offline"
                },
                confirmedDetailText = if (isThai) "ยังไม่มีการสแกนจาก ECU" else "No ECU scan recorded",
                pendingDetailText = if (isThai) "ยังไม่มีการสแกนจาก ECU" else "No ECU scan recorded",
                freezeFrameDetailText = if (isThai) "ยังไม่มีการสแกนจาก ECU" else "No ECU scan recorded",
                freezeFrameBadgeText = if (isThai) "สถานะ" else "Status",
                milDetailText = if (isThai) "ยังไม่พบข้อมูลจนกว่าจะสแกนรถ" else "Unavailable until vehicle scan",
                readinessDetailText = if (isThai) "ยังไม่พบข้อมูลจนกว่าจะสแกนรถ" else "Unavailable until vehicle scan"
            )
        }

        val confirmedCodes = session.confirmedDtcs.map { it.code }
        val pendingCodes = session.pendingDtcs.map { it.code }

        val overallStatusText = when {
            confirmedCodes.isNotEmpty() -> if (isThai) {
                "พบรหัสปัญหาที่ยืนยันแล้ว ${confirmedCodes.size} รายการ"
            } else {
                "${confirmedCodes.size} Confirmed Fault Code(s) Found"
            }
            pendingCodes.isNotEmpty() -> if (isThai) {
                "พบรหัสปัญหาที่รอตรวจสอบ ${pendingCodes.size} รายการ"
            } else {
                "${pendingCodes.size} Pending Fault Code(s) Found"
            }
            else -> if (isThai) "ไม่พบรหัสปัญหาในระบบ" else "No Trouble Codes Detected"
        }
        val overallBadgeText = when {
            confirmedCodes.isNotEmpty() -> if (isThai) "แจ้งเตือน" else "Alert"
            pendingCodes.isNotEmpty() -> if (isThai) "เฝ้าระวัง" else "Watch"
            else -> if (isThai) "ปกติ" else "Normal"
        }

        val confirmedText = if (confirmedCodes.isEmpty()) {
            if (isThai) "ไม่พบโค้ดปัญหาที่ยืนยันแล้ว" else "No confirmed trouble codes"
        } else {
            confirmedCodes.joinToString(", ")
        }
        val confirmedBadge = if (isThai) "${confirmedCodes.size} โค้ด" else "${confirmedCodes.size} DTC"

        val pendingText = if (pendingCodes.isEmpty()) {
            if (isThai) "ไม่พบโค้ดปัญหาที่รอตรวจสอบ" else "No pending trouble codes"
        } else {
            pendingCodes.joinToString(", ")
        }
        val pendingBadge = if (isThai) "${pendingCodes.size} โค้ด" else "${pendingCodes.size} DTC"

        val firstFreeze = session.freezeFrames.firstOrNull()
        val freezeText = if (firstFreeze == null || firstFreeze.readings.isEmpty()) {
            if (confirmedCodes.isEmpty()) {
                if (isThai) "ไม่มีข้อมูลค้าง (ระบบปกติ)" else "No freeze frame captured (System normal)"
            } else {
                if (isThai) "ECU ไม่ได้ส่งข้อมูลขณะเกิดปัญหา (Freeze Frame)" else "ECU did not report freeze frame snapshot"
            }
        } else {
            val metricsPreview = firstFreeze.readings.take(3).joinToString(" • ") {
                "${it.name}: ${it.formattedValueOrUnavailable()}"
            }
            if (!firstFreeze.associatedDtcCode.isNullOrBlank()) {
                "${firstFreeze.associatedDtcCode}: $metricsPreview"
            } else {
                metricsPreview
            }
        }
        val freezeBadge = if (firstFreeze != null && firstFreeze.readings.isNotEmpty()) {
            if (isThai) "บันทึกแล้ว" else "Captured"
        } else if (confirmedCodes.isEmpty()) {
            if (isThai) "ปกติ" else "Normal"
        } else {
            if (isThai) "ไม่มี" else "None"
        }

        val monitor = session.monitorStatus
        val milOn = monitor?.milOn
        val milText = when (milOn) {
            true -> if (isThai) {
                "ไฟเตือนเครื่องยนต์ติดสว่าง (บันทึก ${monitor.confirmedDtcCountFromPid01} โค้ด)"
            } else {
                "Check Engine light is ON (${monitor.confirmedDtcCountFromPid01} stored code(s))"
            }
            false -> if (isThai) "ไฟเตือนเครื่องยนต์ดับปกติ" else "Check Engine light is OFF"
            null -> if (isThai) "ไม่พบสถานะไฟเตือน (ECU ไม่ส่งค่า PID 0101)" else "MIL status unavailable (PID 0101 not reported)"
        }
        val milBadge = when (milOn) {
            true -> if (isThai) "เปิด (ON)" else "ON"
            false -> if (isThai) "ปิด (OFF)" else "OFF"
            null -> "--"
        }

        val readinessText = when {
            monitor == null -> if (isThai) "ไม่สามารถอ่านสถานะความพร้อมระบบ (Readiness) ได้" else "Readiness monitors unavailable"
            monitor.supportedMonitorsCount == 0 -> if (isThai) "รถไม่รายงานตัวตรวจจับความพร้อม (Readiness Monitor)" else "Vehicle does not report readiness monitors"
            monitor.incompleteCount == 0 -> if (isThai) {
                "ระบบตรวจสอบพร้อมครบ ${monitor.readyMonitorsCount}/${monitor.supportedMonitorsCount} รายการ"
            } else {
                "All ${monitor.readyMonitorsCount}/${monitor.supportedMonitorsCount} monitors ready"
            }
            else -> if (isThai) {
                "พร้อม ${monitor.readyMonitorsCount}/${monitor.supportedMonitorsCount} รายการ • ยังไม่ครบ: ${
                    monitor.incompleteMonitorNames.take(2).joinToString(", ").ifBlank { "${monitor.incompleteCount}" }
                }"
            } else {
                "Ready ${monitor.readyMonitorsCount}/${monitor.supportedMonitorsCount} • Incomplete: ${
                    monitor.incompleteMonitorNames.take(2).joinToString(", ").ifBlank { "${monitor.incompleteCount}" }
                }"
            }
        }
        val readinessBadge = when {
            monitor == null -> "--"
            monitor.supportedMonitorsCount == 0 -> "N/A"
            monitor.incompleteCount == 0 -> if (isThai) "พร้อม" else "Ready"
            else -> "${monitor.readyMonitorsCount}/${monitor.supportedMonitorsCount}"
        }

        return DiagnosticStatusDetailSummary(
            hasEcuSession = true,
            isAdapterConnected = isAdapterConnected,
            sessionKey = "${session.sessionId}_${session.capturedAtEpochMs}",
            overallStatusText = overallStatusText,
            overallBadgeText = overallBadgeText,
            confirmedCodes = confirmedCodes,
            confirmedDetailText = confirmedText,
            confirmedBadgeText = confirmedBadge,
            pendingCodes = pendingCodes,
            pendingDetailText = pendingText,
            pendingBadgeText = pendingBadge,
            freezeFrameDetailText = freezeText,
            freezeFrameBadgeText = freezeBadge,
            milOn = milOn,
            milDetailText = milText,
            milBadgeText = milBadge,
            readinessDetailText = readinessText,
            readinessBadgeText = readinessBadge,
            incompleteMonitors = monitor?.incompleteMonitorNames.orEmpty()
        )
    }

    private fun buildDrivingAndEngineCategory(
        pids: Map<String, ObdPidReading>,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        hasEcuSession: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val speed = availableValue(pids["010D"])
        val rpm = availableValue(pids["010C"])
        val absLoad = availableValue(pids["0143"])
        val throttle = availableValue(pids["0111"])
        val calcLoad = availableValue(pids["0104"])
        val engineLoadDisplay = absLoad ?: calcLoad

        val torquePercent = availableValue(pids["0162"])
        val refTorqueNm = availableValue(pids["0163"])
        val computedTorqueNm = when {
            refTorqueNm != null && torquePercent != null -> refTorqueNm * (torquePercent / 100.0)
            refTorqueNm != null -> refTorqueNm
            else -> null
        }

        val metrics = listOf(
            numericMetric(
                id = "vehicle_speed",
                label = if (isThai) "ความเร็วรถ" else "Vehicle Speed",
                pidCode = "010D",
                value = speed,
                unit = if (isThai) "กม./ชม." else "km/h",
                decimals = 0
            ),
            numericMetric(
                id = "engine_rpm",
                label = if (isThai) "รอบเครื่องยนต์" else "Engine RPM",
                pidCode = "010C",
                value = rpm,
                unit = if (isThai) "รอบ/นาที" else "RPM",
                decimals = 0,
                useGrouping = true
            ),
            numericMetric(
                id = "engine_load",
                label = if (isThai) "ภาระเครื่องยนต์" else "Engine Load",
                pidCode = if (absLoad != null) "0143" else "0104",
                value = engineLoadDisplay,
                unit = "%",
                decimals = 0,
                unitInline = true
            ),
            numericMetric(
                id = "throttle_position",
                label = if (isThai) "ตำแหน่งลิ้นปีกผีเสื้อ" else "Throttle Position",
                pidCode = "0111",
                value = throttle,
                unit = "%",
                decimals = 0,
                unitInline = true
            ),
            if (computedTorqueNm != null) {
                numericMetric(
                    id = "engine_torque",
                    label = if (isThai) "แรงบิดเครื่องยนต์" else "Engine Torque",
                    pidCode = "0162/0163",
                    value = computedTorqueNm,
                    unit = "Nm",
                    decimals = 0
                )
            } else {
                numericMetric(
                    id = "engine_torque",
                    label = if (isThai) "แรงบิดเครื่องยนต์" else "Engine Torque",
                    pidCode = "0162",
                    value = torquePercent,
                    unit = if (torquePercent != null) "%" else "Nm",
                    decimals = 0,
                    unitInline = torquePercent != null
                )
            },
            numericMetric(
                id = "calculated_engine_load",
                label = if (isThai) "ภาระเครื่องยนต์ที่คำนวณได้" else "Calculated Engine Load",
                pidCode = "0104",
                value = calcLoad,
                unit = "%",
                decimals = 0,
                unitInline = true
            )
        )

        val anyAvailable = metrics.any { it.isAvailable }
        val efficiency = if (!hasEcuSession || !anyAvailable) {
            0
        } else {
            var score = 100
            val relevantConfirmed = confirmedDtcs.count {
                it.code.startsWith("P03") || it.code.startsWith("P05") ||
                    it.code.startsWith("P06") || it.code.startsWith("P21")
            }
            val relevantPending = pendingDtcs.count {
                it.code.startsWith("P03") || it.code.startsWith("P05") ||
                    it.code.startsWith("P06") || it.code.startsWith("P21")
            }
            score -= relevantConfirmed * 30
            score -= relevantPending * 15
            if (rpm != null && rpm > 5500.0) score -= 20
            if (calcLoad != null && calcLoad > 90.0) score -= 15
            score.coerceIn(0, 100)
        }

        val catId = LiveDataCategoryId.DRIVING_ENGINE
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession && anyAvailable,
            statusNote = if (hasEcuSession && anyAvailable) {
                if (isThai) {
                    "อ่านค่าจาก ECU แล้ว ${metrics.count { it.isAvailable }}/${metrics.size} รายการ"
                } else {
                    "ECU reported ${metrics.count { it.isAvailable }}/${metrics.size} PIDs"
                }
            } else {
                if (isThai) "รอรับข้อมูลจากกล่อง ECU" else "Waiting for ECU telemetry"
            },
            metrics = metrics
        )
    }

    private fun buildEngineTemperatureCategory(
        pids: Map<String, ObdPidReading>,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        hasEcuSession: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val coolant = availableValue(pids["0105"])
        val intake = availableValue(pids["010F"])
        val oilTemp = availableValue(pids["015C"])
        val exhaustTemp = availableValue(pids["013C"])
        val transTemp = availableValue(pids["01A4_TEMP"])

        val metrics = listOf(
            numericMetric("coolant_temp", if (isThai) "อุณหภูมิน้ำหล่อเย็น" else "Coolant Temperature", "0105", coolant, "°C", decimals = 0, unitInline = true),
            numericMetric("intake_air_temp", if (isThai) "อุณหภูมิอากาศไอดี" else "Intake Air Temperature", "010F", intake, "°C", decimals = 0, unitInline = true),
            numericMetric("engine_oil_temp", if (isThai) "อุณหภูมิน้ำมันเครื่อง" else "Engine Oil Temperature", "015C", oilTemp, "°C", decimals = 0, unitInline = true),
            numericMetric("exhaust_gas_temp", if (isThai) "อุณหภูมิไอเสีย" else "Exhaust Gas Temperature", "013C", exhaustTemp, "°C", decimals = 0, unitInline = true),
            numericMetric("engine_trans_temp", if (isThai) "อุณหภูมิน้ำมันเกียร์" else "Transmission Temperature", "01A4", transTemp, "°C", decimals = 0, unitInline = true)
        )

        val anyAvailable = metrics.any { it.isAvailable }
        val efficiency = if (!hasEcuSession || !anyAvailable) {
            0
        } else {
            var score = 100
            if (coolant != null) {
                score -= when {
                    coolant > 112.0 -> 55
                    coolant > 105.0 -> 30
                    coolant < 50.0 -> 20
                    coolant < 72.0 -> 10
                    else -> 0
                }
            }
            if (oilTemp != null && oilTemp > 125.0) score -= 25
            if (intake != null && intake > 65.0) score -= 10
            val tempDtcs = (confirmedDtcs + pendingDtcs).count {
                it.code.startsWith("P011") || it.code.startsWith("P012")
            }
            score -= tempDtcs * 25
            score.coerceIn(0, 100)
        }

        val catId = LiveDataCategoryId.ENGINE_TEMPERATURE
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession && anyAvailable,
            statusNote = if (hasEcuSession && anyAvailable) {
                if (isThai) {
                    "เซ็นเซอร์ความร้อนพร้อม ${metrics.count { it.isAvailable }}/${metrics.size} จุด"
                } else {
                    "Thermal sensors online: ${metrics.count { it.isAvailable }}/${metrics.size}"
                }
            } else {
                if (isThai) "รอรับข้อมูลจากกล่อง ECU" else "Waiting for ECU telemetry"
            },
            metrics = metrics
        )
    }

    private fun buildSystemVoltageCategory(
        processed: SystemVoltageProcessedData,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        hasEcuSession: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val voltage = processed.currentVoltage
        val systemType = processed.systemType
        val voltageStatus = processed.voltageStatus
        val allDtcs = (confirmedDtcs + pendingDtcs).map { it.code.uppercase() }

        val currentVoltageMetric = if (voltage != null && !processed.isStale) {
            val sweep = when (systemType) {
                ElectricalSystemType.SYSTEM_12V -> (voltage / 16.0).coerceIn(0.15, 1.0).toFloat()
                ElectricalSystemType.SYSTEM_24V -> (voltage / 32.0).coerceIn(0.15, 1.0).toFloat()
                ElectricalSystemType.UNKNOWN -> 0.5f
            }
            val ringStyle = when (voltageStatus) {
                SystemVoltageStatus.NORMAL -> CircularRingStyle.PROPORTIONAL_BLUE
                SystemVoltageStatus.LOW,
                SystemVoltageStatus.HIGH,
                SystemVoltageStatus.UNSTABLE -> CircularRingStyle.FULL_RED
                SystemVoltageStatus.UNAVAILABLE -> CircularRingStyle.PROPORTIONAL_BLUE
            }
            LiveMetricGaugeItem(
                id = "current_voltage",
                label = if (isThai) "แรงดันไฟปัจจุบัน" else "Current Voltage",
                pidCode = "0142",
                rawNumericValue = voltage,
                formattedPrimaryText = String.format(Locale.US, "%.1f V", voltage),
                formattedUnitText = "",
                isAvailable = true,
                ringStyleOverride = ringStyle,
                ringSweepOverride = sweep
            )
        } else {
            LiveMetricGaugeItem(
                id = "current_voltage",
                label = if (isThai) "แรงดันไฟปัจจุบัน" else "Current Voltage",
                pidCode = "0142",
                rawNumericValue = null,
                formattedPrimaryText = "N/A",
                formattedUnitText = if (processed.isStale) {
                    if (isThai) "ข้อมูลเก่า" else "Outdated"
                } else {
                    "V"
                },
                isAvailable = false,
                ringStyleOverride = CircularRingStyle.UNAVAILABLE_EMPTY,
                ringSweepOverride = 0f
            )
        }

        val systemTypeKnown = systemType != ElectricalSystemType.UNKNOWN
        val systemTypeMetric = LiveMetricGaugeItem(
            id = "system_type",
            label = if (isThai) "ประเภทระบบไฟ" else "System Type",
            pidCode = "0142",
            rawNumericValue = when (systemType) {
                ElectricalSystemType.SYSTEM_12V -> 12.0
                ElectricalSystemType.SYSTEM_24V -> 24.0
                ElectricalSystemType.UNKNOWN -> null
            },
            formattedPrimaryText = systemType.displayLabel,
            formattedUnitText = "",
            textDisplayValue = systemType.displayLabel,
            isAvailable = systemTypeKnown,
            ringStyleOverride = if (systemTypeKnown) {
                CircularRingStyle.PROPORTIONAL_BLUE
            } else {
                CircularRingStyle.UNAVAILABLE_EMPTY
            },
            ringSweepOverride = if (systemTypeKnown) 1.0f else 0f
        )

        val statusKnown = voltageStatus != SystemVoltageStatus.UNAVAILABLE
        val voltageStatusMetric = LiveMetricGaugeItem(
            id = "voltage_status",
            label = if (isThai) "สถานะแรงดันไฟ" else "Voltage Status",
            pidCode = "0142",
            rawNumericValue = if (statusKnown) voltage else null,
            formattedPrimaryText = voltageStatus.displayLabel,
            formattedUnitText = "",
            textDisplayValue = voltageStatus.displayLabel,
            isAvailable = statusKnown,
            ringStyleOverride = when (voltageStatus) {
                SystemVoltageStatus.UNAVAILABLE -> CircularRingStyle.UNAVAILABLE_EMPTY
                SystemVoltageStatus.NORMAL -> CircularRingStyle.PROPORTIONAL_BLUE
                else -> CircularRingStyle.FULL_RED
            },
            ringSweepOverride = if (statusKnown) 1.0f else 0f
        )

        val lastUpdatedMs = processed.lastUpdatedEpochMs
        val lastUpdatedMetric = if (lastUpdatedMs != null && (voltage != null || processed.isStale)) {
            val timeStr = SimpleDateFormat("hh:mm:ss", Locale.US).format(Date(lastUpdatedMs))
            val amPmStr = SimpleDateFormat("a", Locale.US).format(Date(lastUpdatedMs))
            LiveMetricGaugeItem(
                id = "voltage_last_updated",
                label = if (isThai) "อัปเดตล่าสุด" else "Last Updated",
                pidCode = "0142",
                rawNumericValue = if (processed.isStale) null else 100.0,
                formattedPrimaryText = timeStr,
                formattedUnitText = if (processed.isStale) {
                    if (isThai) "$amPmStr • เก่า" else "$amPmStr • Stale"
                } else {
                    amPmStr
                },
                textDisplayValue = "$timeStr $amPmStr",
                isAvailable = !processed.isStale,
                ringStyleOverride = if (processed.isStale) {
                    CircularRingStyle.FULL_RED
                } else {
                    CircularRingStyle.PROPORTIONAL_BLUE
                },
                ringSweepOverride = 1.0f
            )
        } else {
            LiveMetricGaugeItem(
                id = "voltage_last_updated",
                label = if (isThai) "อัปเดตล่าสุด" else "Last Updated",
                pidCode = "0142",
                rawNumericValue = null,
                formattedPrimaryText = "No Data",
                formattedUnitText = "",
                textDisplayValue = "No Data",
                isAvailable = false,
                ringStyleOverride = CircularRingStyle.UNAVAILABLE_EMPTY,
                ringSweepOverride = 0f
            )
        }

        val metrics = listOf(
            currentVoltageMetric,
            systemTypeMetric,
            voltageStatusMetric,
            lastUpdatedMetric
        )

        val efficiency = if (voltage == null || !hasEcuSession || processed.isStale) {
            0
        } else {
            var score = when (voltageStatus) {
                SystemVoltageStatus.NORMAL -> {
                    val isOptimal = if (systemType == ElectricalSystemType.SYSTEM_24V) {
                        voltage in 24.8..29.4
                    } else {
                        voltage in 12.4..14.8
                    }
                    if (isOptimal) 96 else 78
                }
                SystemVoltageStatus.LOW -> {
                    val deeplyLow = if (systemType == ElectricalSystemType.SYSTEM_24V) voltage < 23.4 else voltage < 11.7
                    if (deeplyLow) 35 else 55
                }
                SystemVoltageStatus.HIGH -> 30
                SystemVoltageStatus.UNSTABLE -> 40
                SystemVoltageStatus.UNAVAILABLE -> 50
            }
            val voltageDtcCount = allDtcs.count { it.startsWith("P056") }
            score -= voltageDtcCount * 25
            score.coerceIn(0, 100)
        }

        val statusNote = when {
            processed.errorState != SystemVoltageErrorState.NONE ->
                processed.errorState.localizedTitle(isThai)
            voltage != null && hasEcuSession -> if (isThai) {
                "${systemType.displayLabel} • ${String.format(Locale.US, "%.1f V", voltage)} • ${voltageStatus.displayLabel}"
            } else {
                "${systemType.displayLabel} • ${String.format(Locale.US, "%.1f V", voltage)} • Status: ${voltageStatus.displayLabel}"
            }
            else -> if (isThai) "รอรับข้อมูลแรงดันไฟจากกล่อง ECU" else "Waiting for ECU voltage telemetry"
        }

        val catId = LiveDataCategoryId.SYSTEM_VOLTAGE
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession && voltage != null && !processed.isStale,
            statusNote = statusNote,
            metrics = metrics
        )
    }

    private fun buildAirIntakeFuelCategory(
        pids: Map<String, ObdPidReading>,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        hasEcuSession: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val maf = availableValue(pids["0110"])
        val map = availableValue(pids["010B"])
        val baro = availableValue(pids["0133"])
        val boost = if (map != null && baro != null) (map - baro).coerceAtLeast(0.0) else null
        val stft = availableValue(pids["0106"])
        val ltft = availableValue(pids["0107"])
        val o2 = availableValue(pids["0114"])
        val fuelPressure = availableValue(pids["010A"]) ?: availableValue(pids["0123"])

        val metrics = listOf(
            numericMetric("maf", if (isThai) "ปริมาณอากาศเข้า (MAF)" else "MAF Airflow", "0110", maf, "g/s", decimals = 1),
            numericMetric("map", if (isThai) "แรงดันท่อร่วมไอดี (MAP)" else "MAP Pressure", "010B", map, "kPa", decimals = 0),
            numericMetric("boost_pressure", if (isThai) "แรงดันเทอร์โบ (บูสต์)" else "Boost Pressure", "010B/0133", boost, "kPa", decimals = 0),
            numericMetric("stft", if (isThai) "ชดเชยน้ำมันระยะสั้น" else "Short-term Fuel Trim", "0106", stft, "%", decimals = 1, unitInline = true, signed = true),
            numericMetric("ltft", if (isThai) "ชดเชยน้ำมันระยะยาว" else "Long-term Fuel Trim", "0107", ltft, "%", decimals = 1, unitInline = true, signed = true),
            numericMetric("oxygen_sensor", if (isThai) "เซ็นเซอร์ออกซิเจน" else "Oxygen Sensor", "0114", o2, "V", decimals = 2, unitInline = true),
            numericMetric("fuel_pressure", if (isThai) "แรงดันน้ำมันเชื้อเพลิง" else "Fuel Pressure", "010A", fuelPressure, "kPa", decimals = 0)
        )

        val anyAvailable = metrics.any { it.isAvailable }
        val efficiency = if (!hasEcuSession || !anyAvailable) {
            0
        } else {
            var score = 100
            if (stft != null) {
                val dev = abs(stft)
                score -= when {
                    dev > 15.0 -> 35
                    dev > 10.0 -> 20
                    dev > 5.0 -> 8
                    else -> 0
                }
            }
            if (ltft != null) {
                val dev = abs(ltft)
                score -= when {
                    dev > 15.0 -> 40
                    dev > 10.0 -> 22
                    dev > 5.0 -> 10
                    else -> 0
                }
            }
            val mixtureDtcs = (confirmedDtcs + pendingDtcs).count {
                it.code.startsWith("P010") || it.code.startsWith("P013") ||
                    it.code.startsWith("P014") || it.code.startsWith("P017")
            }
            score -= mixtureDtcs * 25
            score.coerceIn(0, 100)
        }

        val catId = LiveDataCategoryId.AIR_INTAKE_FUEL
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession && anyAvailable,
            statusNote = if (hasEcuSession && anyAvailable) {
                if (isThai) {
                    "อ่านค่าอากาศและเชื้อเพลิง ${metrics.count { it.isAvailable }}/${metrics.size} รายการ"
                } else {
                    "Air & fuel mixture channels: ${metrics.count { it.isAvailable }}/${metrics.size}"
                }
            } else {
                if (isThai) "รอรับข้อมูลจากกล่อง ECU" else "Waiting for ECU telemetry"
            },
            metrics = metrics
        )
    }

    private fun buildTransmissionCategory(
        pids: Map<String, ObdPidReading>,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        hasEcuSession: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val gearValue = availableValue(pids["01A4"])
        val transTemp = availableValue(pids["01A4_TEMP"])
        val speedValue = availableValue(pids["010D"])
        val rpmValue = availableValue(pids["010C"])

        val gearLabel = if (isThai) "เกียร์ปัจจุบัน" else "Current Gear"
        val tempLabel = if (isThai) "อุณหภูมิน้ำมันเกียร์" else "Transmission Temperature"
        val speedLabel = if (isThai) "ความเร็วระบบเกียร์" else "Transmission Speed"
        val tccLabel = if (isThai) "สถานะทอร์กคอนเวอร์เตอร์" else "Torque Converter Status"

        val metrics = listOf(
            if (gearValue != null) {
                val roundedGear = gearValue.roundToInt().coerceAtLeast(0)
                val label = if (roundedGear == 0) "P / N" else "D / $roundedGear"
                LiveMetricGaugeItem(
                    id = "current_gear",
                    label = gearLabel,
                    pidCode = "01A4",
                    rawNumericValue = (roundedGear * 12.0).coerceIn(0.0, 100.0),
                    formattedPrimaryText = label,
                    formattedUnitText = "",
                    textDisplayValue = label,
                    isAvailable = true
                )
            } else {
                unavailableMetric("current_gear", gearLabel, "01A4", "")
            },
            numericMetric("trans_temp", tempLabel, "01A4", transTemp, "°C", decimals = 0, unitInline = true),
            if (gearValue != null && rpmValue != null) {
                numericMetric("trans_speed", speedLabel, "010C", rpmValue, if (isThai) "รอบ/นาที" else "RPM", decimals = 0, useGrouping = true)
            } else if (gearValue != null && speedValue != null) {
                numericMetric("trans_speed", speedLabel, "010D", speedValue, if (isThai) "กม./ชม." else "km/h", decimals = 0)
            } else {
                unavailableMetric("trans_speed", speedLabel, "01A4", if (isThai) "รอบ/นาที" else "RPM")
            },
            unavailableMetric("torque_converter", tccLabel, "01B4", "")
        )

        val tcuMetricsAvailable = metrics.any { it.isAvailable }
        val tcuDtcs = (confirmedDtcs + pendingDtcs).count {
            it.code.startsWith("P07") || it.code.startsWith("P08") || it.code.startsWith("P09")
        }
        val efficiency = if (!hasEcuSession || (!tcuMetricsAvailable && tcuDtcs == 0)) {
            0
        } else {
            (100 - (tcuDtcs * 35)).coerceIn(0, 100)
        }

        val catId = LiveDataCategoryId.TRANSMISSION
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession && tcuMetricsAvailable,
            statusNote = if (hasEcuSession && tcuMetricsAvailable) {
                if (isThai) {
                    "อ่านค่ากล่องเกียร์ ${metrics.count { it.isAvailable }}/${metrics.size} รายการ"
                } else {
                    "Transmission signals active: ${metrics.count { it.isAvailable }}/${metrics.size}"
                }
            } else if (hasEcuSession) {
                if (isThai) "รุ่นรถนี้ไม่ส่งค่าเกียร์ผ่าน OBD-II Mode 01 มาตรฐาน" else "Transmission PIDs not reported over standard OBD-II Mode 01"
            } else {
                if (isThai) "รอรับข้อมูลจากกล่อง ECU" else "Waiting for ECU telemetry"
            },
            metrics = metrics
        )
    }

    private fun buildDiagnosticStatusCategory(
        pids: Map<String, ObdPidReading>,
        confirmedDtcs: List<DiagnosticTroubleCode>,
        pendingDtcs: List<DiagnosticTroubleCode>,
        supportedPidCount: Int,
        hasEcuSession: Boolean,
        isAdapterConnected: Boolean,
        isThai: Boolean
    ): LiveVehicleCategorySummary {
        val voltage = availableValue(pids["0142"])
        val codeUnit = if (isThai) "โค้ด" else "DTC"
        val pidUnit = if (isThai) "รายการ" else "PIDs"
        val metrics = listOf(
            if (hasEcuSession) {
                numericMetric(
                    id = "confirmed_dtcs",
                    label = if (isThai) "รหัสปัญหาที่ยืนยันแล้ว" else "Confirmed Fault Codes",
                    pidCode = "Mode 03",
                    value = confirmedDtcs.size.toDouble(),
                    unit = codeUnit,
                    decimals = 0
                )
            } else {
                unavailableMetric("confirmed_dtcs", if (isThai) "รหัสปัญหาที่ยืนยันแล้ว" else "Confirmed Fault Codes", "Mode 03", codeUnit)
            },
            if (hasEcuSession) {
                numericMetric(
                    id = "pending_dtcs",
                    label = if (isThai) "รหัสปัญหาที่รอตรวจสอบ" else "Pending Fault Codes",
                    pidCode = "Mode 07",
                    value = pendingDtcs.size.toDouble(),
                    unit = codeUnit,
                    decimals = 0
                )
            } else {
                unavailableMetric("pending_dtcs", if (isThai) "รหัสปัญหาที่รอตรวจสอบ" else "Pending Fault Codes", "Mode 07", codeUnit)
            },
            numericMetric(
                id = "battery_voltage",
                label = if (isThai) "แรงดันไฟโมดูลควบคุม (ECU)" else "Control Module Voltage",
                pidCode = "0142",
                value = voltage,
                unit = "V",
                decimals = 1,
                unitInline = true
            ),
            if (hasEcuSession && supportedPidCount > 0) {
                numericMetric(
                    id = "supported_pids",
                    label = if (isThai) "จำนวน PID ที่รองรับ" else "Supported ECU PIDs",
                    pidCode = "0100",
                    value = supportedPidCount.toDouble(),
                    unit = pidUnit,
                    decimals = 0
                )
            } else {
                unavailableMetric("supported_pids", if (isThai) "จำนวน PID ที่รองรับ" else "Supported ECU PIDs", "0100", pidUnit)
            }
        )

        val efficiency = if (!hasEcuSession) {
            0
        } else {
            var score = 100
            score -= confirmedDtcs.size * 35
            score -= pendingDtcs.size * 15
            if (voltage != null && voltage < 11.8) score -= 25
            score.coerceIn(0, 100)
        }

        val note = when {
            hasEcuSession && confirmedDtcs.isEmpty() && pendingDtcs.isEmpty() ->
                if (isThai) "ไม่พบรหัสปัญหา • ตรวจสอบ ECU แบบอ่านอย่างเดียวแล้ว" else "No fault codes • ECU verified (Read-Only)"
            hasEcuSession ->
                if (isThai) "ยืนยันแล้ว ${confirmedDtcs.size} โค้ด, รอตรวจสอบ ${pendingDtcs.size} โค้ด" else "${confirmedDtcs.size} confirmed, ${pendingDtcs.size} pending fault(s)"
            isAdapterConnected ->
                if (isThai) "อะแดปเตอร์พร้อมแล้ว • กดอัปเดตข้อมูล ECU" else "Adapter ready • Tap Update ECU to read live status"
            else ->
                if (isThai) "เชื่อมต่ออะแดปเตอร์ OBD-II ที่หน้าวิเคราะห์ระบบรถ" else "Connect OBD-II adapter in Vehicle Diagnostics"
        }

        val catId = LiveDataCategoryId.DIAGNOSTIC_STATUS
        return LiveVehicleCategorySummary(
            categoryId = catId,
            title = if (isThai) catId.titleTh else catId.titleEn,
            efficiencyPercent = efficiency,
            band = EfficiencyBand.fromPercent(efficiency),
            hasRealEcuData = hasEcuSession,
            statusNote = note,
            metrics = metrics
        )
    }

    private fun availableValue(reading: ObdPidReading?): Double? {
        return if (reading != null && reading.isAvailable) reading.value else null
    }

    private fun unavailableMetric(
        id: String,
        label: String,
        pidCode: String,
        unit: String
    ): LiveMetricGaugeItem {
        return LiveMetricGaugeItem(
            id = id,
            label = label,
            pidCode = pidCode,
            rawNumericValue = null,
            formattedPrimaryText = "--",
            formattedUnitText = unit,
            isAvailable = false
        )
    }

    private fun numericMetric(
        id: String,
        label: String,
        pidCode: String,
        value: Double?,
        unit: String,
        decimals: Int,
        unitInline: Boolean = false,
        useGrouping: Boolean = false,
        signed: Boolean = false
    ): LiveMetricGaugeItem {
        if (value == null) {
            return unavailableMetric(id, label, pidCode, unit)
        }
        val numText = when {
            decimals == 0 && useGrouping -> {
                val formatted = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToInt())
                if (signed && value >= 0.0) "+$formatted" else formatted
            }
            decimals == 0 -> {
                val rounded = value.roundToInt()
                if (signed && rounded >= 0) "+$rounded" else rounded.toString()
            }
            else -> {
                val fmt = String.format(Locale.US, "%.${decimals}f", value)
                if (signed && value >= 0.0) "+$fmt" else fmt
            }
        }
        return if (unitInline && unit.isNotBlank()) {
            val combined = if (unit == "%" || unit == "°C") "$numText$unit" else "$numText $unit"
            LiveMetricGaugeItem(
                id = id,
                label = label,
                pidCode = pidCode,
                rawNumericValue = value,
                formattedPrimaryText = combined,
                formattedUnitText = "",
                isAvailable = true
            )
        } else {
            LiveMetricGaugeItem(
                id = id,
                label = label,
                pidCode = pidCode,
                rawNumericValue = value,
                formattedPrimaryText = numText,
                formattedUnitText = unit,
                isAvailable = true
            )
        }
    }
}
