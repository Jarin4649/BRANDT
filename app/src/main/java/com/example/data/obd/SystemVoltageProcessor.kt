package com.example.data.obd

import com.example.data.settings.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Verified vehicle electrical architecture type.
 * NEVER inferred or guessed from a single voltage measurement.
 */
enum class ElectricalSystemType(
    val code: String,
    val jsonCode: String,
    val displayLabel: String,
    val displayLabelTh: String
) {
    SYSTEM_12V(
        code = "SYSTEM_12V",
        jsonCode = "12v_system",
        displayLabel = "12V System",
        displayLabelTh = "ระบบไฟ 12V"
    ),
    SYSTEM_24V(
        code = "SYSTEM_24V",
        jsonCode = "24v_system",
        displayLabel = "24V System",
        displayLabelTh = "ระบบไฟ 24V"
    ),
    UNKNOWN(
        code = "UNKNOWN",
        jsonCode = "unknown",
        displayLabel = "Unknown",
        displayLabelTh = "ไม่ทราบ (Unknown)"
    );

    fun localizedLabel(isThai: Boolean): String = if (isThai) displayLabelTh else displayLabel

    companion object {
        fun fromCode(raw: String?): ElectricalSystemType {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return when {
                normalized == "SYSTEM_12V" || normalized == "12V_SYSTEM" || normalized == "12V" -> SYSTEM_12V
                normalized == "SYSTEM_24V" || normalized == "24V_SYSTEM" || normalized == "24V" -> SYSTEM_24V
                else -> UNKNOWN
            }
        }
    }
}

enum class BatteryChemistryType(
    val code: String,
    val labelEn: String,
    val labelTh: String
) {
    STANDARD_LEAD_ACID("STANDARD_LEAD_ACID", "Lead-Acid (Flooded)", "ตะกั่ว-กรดมาตรฐาน"),
    AGM_EFB("AGM_EFB", "AGM / EFB", "AGM / EFB"),
    LFP_LITHIUM("LFP_LITHIUM", "Lithium (LFP)", "ลิเธียม (LFP)"),
    UNSPECIFIED("UNSPECIFIED", "Standard / Unspecified", "ทั่วไป / ไม่ระบุ");

    fun localizedLabel(isThai: Boolean): String = if (isThai) labelTh else labelEn

    companion object {
        fun fromCode(raw: String?): BatteryChemistryType {
            return entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) } ?: UNSPECIFIED
        }
    }
}

enum class ElectricalOperatingCondition(
    val code: String,
    val labelEn: String,
    val labelTh: String
) {
    UNSPECIFIED("UNSPECIFIED", "Auto (Engine/KOEO)", "อัตโนมัติ"),
    KEY_ON_ENGINE_OFF("KEY_ON_ENGINE_OFF", "Engine Off (KOEO)", "เปิดสวิตช์ (ดับเครื่อง)"),
    ENGINE_RUNNING("ENGINE_RUNNING", "Engine Running", "ติดเครื่องยนต์");

    fun localizedLabel(isThai: Boolean): String = if (isThai) labelTh else labelEn
}

enum class SystemVoltageStatus(
    val jsonCode: String,
    val displayLabel: String
) {
    NORMAL("normal", "Normal"),
    LOW("low", "Low"),
    HIGH("high", "High"),
    UNSTABLE("unstable", "Unstable"),
    UNAVAILABLE("unavailable", "Unavailable")
}

enum class SystemVoltageConnectionStatus(
    val jsonCode: String,
    val displayLabel: String
) {
    DISCONNECTED("disconnected", "Disconnected"),
    CONNECTING("connecting", "Connecting"),
    CONNECTED("connected", "Connected"),
    INTERRUPTED("interrupted", "Interrupted"),
    ERROR("error", "Error")
}

enum class SystemVoltageErrorState(
    val code: String,
    val labelEn: String,
    val labelTh: String
) {
    NONE("NONE", "", ""),
    BLUETOOTH_DISCONNECTED(
        "BLUETOOTH_DISCONNECTED",
        "Bluetooth disconnected",
        "Bluetooth ยังไม่เชื่อมต่อ"
    ),
    ADAPTER_NOT_DETECTED(
        "ADAPTER_NOT_DETECTED",
        "Adapter not detected",
        "ตรวจไม่พบอะแดปเตอร์ OBD-II"
    ),
    ECU_COMMUNICATION_FAILED(
        "ECU_COMMUNICATION_FAILED",
        "ECU communication failed",
        "การสื่อสารกับกล่อง ECU ล้มเหลว"
    ),
    PID_UNSUPPORTED(
        "PID_UNSUPPORTED",
        "PID unsupported (Mode 01 PID 42)",
        "รถไม่รองรับข้อมูลแรงดันไฟโมดูลควบคุม (PID 42)"
    ),
    INVALID_RESPONSE(
        "INVALID_RESPONSE",
        "Invalid response from ECU",
        "ได้รับข้อมูลตอบกลับ PID 42 จาก ECU ไม่ถูกต้อง"
    ),
    DATA_UNAVAILABLE(
        "DATA_UNAVAILABLE",
        "Data unavailable",
        "ไม่มีข้อมูลแรงดันไฟระบบ"
    ),
    DATA_OUTDATED(
        "DATA_OUTDATED",
        "Data outdated",
        "ข้อมูลแรงดันไฟเก่าเกินกำหนด"
    ),
    COMMUNICATION_INTERRUPTED(
        "COMMUNICATION_INTERRUPTED",
        "Vehicle communication interrupted",
        "การสื่อสารกับรถยนต์ถูกตัดขาดกะทันหัน"
    );

    fun localizedTitle(isThai: Boolean): String = if (isThai) labelTh else labelEn
}

data class SystemVoltageThresholds(
    val lowVoltageThreshold: Double,
    val highVoltageThreshold: Double,
    val optimalLow: Double,
    val optimalHigh: Double,
    val unstableSpreadThreshold: Double,
    val unstableStepThreshold: Double
)

data class SystemVoltageCriteria(
    val systemType: ElectricalSystemType = ElectricalSystemType.UNKNOWN,
    val batteryType: BatteryChemistryType = BatteryChemistryType.UNSPECIFIED,
    val operatingCondition: ElectricalOperatingCondition = ElectricalOperatingCondition.UNSPECIFIED,
    val staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS,
    val customThresholds: SystemVoltageThresholds? = null
) {
    fun resolveOperatingCondition(engineRpm: Double?): ElectricalOperatingCondition {
        if (operatingCondition != ElectricalOperatingCondition.UNSPECIFIED) {
            return operatingCondition
        }
        return if (engineRpm != null && engineRpm >= 350.0) {
            ElectricalOperatingCondition.ENGINE_RUNNING
        } else {
            ElectricalOperatingCondition.UNSPECIFIED
        }
    }

    fun resolveThresholds(engineRpm: Double? = null): SystemVoltageThresholds? {
        customThresholds?.let { return it }
        if (systemType == ElectricalSystemType.UNKNOWN) return null

        val effectiveCondition = resolveOperatingCondition(engineRpm)
        val is24V = systemType == ElectricalSystemType.SYSTEM_24V
        val scale = if (is24V) 2.0 else 1.0

        val baseLow12 = when (batteryType) {
            BatteryChemistryType.LFP_LITHIUM -> when (effectiveCondition) {
                ElectricalOperatingCondition.ENGINE_RUNNING -> 13.1
                ElectricalOperatingCondition.KEY_ON_ENGINE_OFF -> 12.5
                ElectricalOperatingCondition.UNSPECIFIED -> 12.5
            }
            BatteryChemistryType.AGM_EFB -> when (effectiveCondition) {
                ElectricalOperatingCondition.ENGINE_RUNNING -> 12.8
                ElectricalOperatingCondition.KEY_ON_ENGINE_OFF -> 12.0
                ElectricalOperatingCondition.UNSPECIFIED -> 12.0
            }
            BatteryChemistryType.STANDARD_LEAD_ACID,
            BatteryChemistryType.UNSPECIFIED -> when (effectiveCondition) {
                ElectricalOperatingCondition.ENGINE_RUNNING -> 12.8
                ElectricalOperatingCondition.KEY_ON_ENGINE_OFF -> 11.9
                ElectricalOperatingCondition.UNSPECIFIED -> 12.0
            }
        }

        val baseHigh12 = when (batteryType) {
            BatteryChemistryType.LFP_LITHIUM -> 14.7
            BatteryChemistryType.AGM_EFB -> 15.1
            BatteryChemistryType.STANDARD_LEAD_ACID,
            BatteryChemistryType.UNSPECIFIED -> 15.0
        }

        val optimalLow12 = when (effectiveCondition) {
            ElectricalOperatingCondition.ENGINE_RUNNING -> 13.4
            ElectricalOperatingCondition.KEY_ON_ENGINE_OFF -> 12.2
            ElectricalOperatingCondition.UNSPECIFIED -> 12.4
        }

        return SystemVoltageThresholds(
            lowVoltageThreshold = baseLow12 * scale,
            highVoltageThreshold = baseHigh12 * scale,
            optimalLow = optimalLow12 * scale,
            optimalHigh = (baseHigh12 - 0.2) * scale,
            unstableSpreadThreshold = 1.2 * scale,
            unstableStepThreshold = 0.4 * scale
        )
    }

    companion object {
        const val DEFAULT_STALE_TIMEOUT_MS = 15_000L
    }
}

data class SystemVoltageProcessedData(
    val section: String = "system_voltage",
    val currentVoltage: Double?,
    val unit: String = "V",
    val systemType: ElectricalSystemType,
    val voltageStatus: SystemVoltageStatus,
    val lastUpdatedEpochMs: Long?,
    val lastUpdatedIsoUtc: String?,
    val dataSource: String = DATA_SOURCE_PID_42,
    val connectionStatus: SystemVoltageConnectionStatus,
    val errorState: SystemVoltageErrorState,
    val errorMessage: String? = null,
    val isStale: Boolean = false,
    val batteryType: BatteryChemistryType = BatteryChemistryType.UNSPECIFIED,
    val operatingCondition: ElectricalOperatingCondition = ElectricalOperatingCondition.UNSPECIFIED,
    val rawHexResponse: String? = null,
    val limitations: List<String> = emptyList(),
    val missingInformation: List<String> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("section", section)
            put("current_voltage", currentVoltage ?: JSONObject.NULL)
            put("unit", unit)
            put("system_type", systemType.jsonCode)
            put("voltage_status", voltageStatus.jsonCode)
            put("last_updated", lastUpdatedIsoUtc ?: JSONObject.NULL)
            put("data_source", dataSource)
            put("connection_status", connectionStatus.jsonCode)
            put("error_state", errorState.code)
            put("error_message", errorMessage ?: JSONObject.NULL)
            put("is_stale", isStale)
            put("battery_type", batteryType.code)
            put("operating_condition", operatingCondition.code)
            put("limitations", JSONArray(limitations))
            put("missing_information", JSONArray(missingInformation))
        }
    }

    fun toMackContextString(language: AppLanguage = AppLanguage.ENGLISH): String {
        val jsonCompact = toJson().toString()
        return buildString {
            appendLine("[BRANDT Structured System Voltage Telemetry ($DATA_SOURCE_PID_42)]")
            appendLine("JSON_PAYLOAD: $jsonCompact")
            appendLine("NOTE: PID 42 reports ECU Control Module Voltage, NOT a direct battery-terminal multimeter measurement.")
            if (errorState != SystemVoltageErrorState.NONE) {
                appendLine("ACTIVE_ERROR_STATE: ${errorState.code} (${errorMessage ?: errorState.localizedTitle(language == AppLanguage.THAI)})")
            }
            if (isStale) {
                appendLine("STALENESS_WARNING: Data is outdated (exceeded freshness window). Do not describe it as current live condition.")
            }
            if (missingInformation.isNotEmpty()) {
                appendLine("MISSING_INFORMATION: ${missingInformation.joinToString("; ")}")
            }
            if (limitations.isNotEmpty()) {
                appendLine("DATA_LIMITATIONS: ${limitations.joinToString("; ")}")
            }
        }.trim()
    }

    companion object {
        const val DATA_SOURCE_PID_42 = "OBD-II PID 42"
    }
}

/**
 * Structured Data Processing Layer between raw OBD-II communication (Mode 01 PID 42)
 * and BRANDT UI / MACK diagnostic intelligence.
 *
 * Rules enforced:
 * - Strictly interprets Mode 01 PID 42 (Control Module Voltage: (A * 256 + B) / 1000).
 * - Never fabricates voltage readings or fills placeholder values when unavailable/error.
 * - Never guesses System Type (12V System vs 24V System) from a single voltage reading;
 *   relies strictly on verified vehicle/user configuration.
 * - Tracks timestamps, staleness (DATA_OUTDATED), connection interruptions, and error states.
 */
object SystemVoltageProcessor {

    fun processSession(
        session: ObdDiagnosticSession?,
        connectionManagerState: ObdConnectionManagerState?,
        isTransportConnected: Boolean,
        criteria: SystemVoltageCriteria = SystemVoltageCriteria(),
        recentVoltageSamples: List<Double> = emptyList(),
        lastDisconnectError: String? = null,
        isCommunicationInterrupted: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): SystemVoltageProcessedData {
        val effectiveCriteria = mergeVehicleConfigIntoCriteria(
            baseCriteria = criteria,
            vehicle = session?.vehicle
        )

        val hasRealEcuSession = session != null &&
            !session.isSimulatedTestData &&
            session.sourceMetadata.sourceType != ObdDataSourceType.MANUAL_DTC_ENTRY

        val resolvedConnection = resolveConnectionStatus(
            connectionManagerState = connectionManagerState,
            isTransportConnected = isTransportConnected,
            isCommunicationInterrupted = isCommunicationInterrupted,
            hasEcuSession = hasRealEcuSession
        )

        val pids = if (hasRealEcuSession) {
            session!!.livePidReadings.associateBy { it.pidCode.uppercase() }
        } else {
            emptyMap()
        }
        val pid42 = pids["0142"]
        val rpm = pids["010C"]?.takeIf { it.isAvailable }?.value
        val dtcCodes = if (hasRealEcuSession) {
            (session!!.confirmedDtcs + session.pendingDtcs).map { it.code.uppercase() }
        } else {
            emptyList()
        }

        val sessionSupportsPid42 = hasRealEcuSession &&
            (session!!.supportedPids.isEmpty() || "0142" in session.supportedPids) &&
            "0142" !in session.unsupportedCommands

        return evaluate(
            pid42Reading = pid42,
            pid42SupportedByBitmask = sessionSupportsPid42,
            hasEcuSession = hasRealEcuSession,
            connectionStatus = resolvedConnection,
            connectionManagerState = connectionManagerState,
            lastDisconnectError = lastDisconnectError,
            isCommunicationInterrupted = isCommunicationInterrupted,
            criteria = effectiveCriteria,
            engineRpm = rpm,
            dtcCodes = dtcCodes,
            recentVoltageSamples = recentVoltageSamples,
            sessionCapturedAtMs = if (hasRealEcuSession) session?.capturedAtEpochMs else null,
            nowMs = nowMs
        )
    }

    fun processRawPid42(
        rawResponse: String?,
        connectionStatus: SystemVoltageConnectionStatus,
        criteria: SystemVoltageCriteria = SystemVoltageCriteria(),
        explicitError: SystemVoltageErrorState? = null,
        explicitErrorMessage: String? = null,
        recentVoltageSamples: List<Double> = emptyList(),
        engineRpm: Double? = null,
        dtcCodes: List<String> = emptyList(),
        readingTimestampMs: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): SystemVoltageProcessedData {
        val ts = readingTimestampMs ?: nowMs
        val parsed = if (rawResponse != null) {
            Elm327Parser.parsePidResponse("0142", rawResponse, ts)
        } else {
            null
        }
        val pidUnsupported = parsed?.availability == PidAvailability.UNSUPPORTED_BY_VEHICLE ||
            explicitError == SystemVoltageErrorState.PID_UNSUPPORTED
        val isInterrupted = explicitError == SystemVoltageErrorState.COMMUNICATION_INTERRUPTED ||
            connectionStatus == SystemVoltageConnectionStatus.INTERRUPTED

        return evaluate(
            pid42Reading = parsed,
            pid42SupportedByBitmask = !pidUnsupported,
            hasEcuSession = connectionStatus == SystemVoltageConnectionStatus.CONNECTED || parsed != null,
            connectionStatus = connectionStatus,
            connectionManagerState = null,
            lastDisconnectError = explicitErrorMessage,
            isCommunicationInterrupted = isInterrupted,
            explicitErrorOverride = explicitError,
            criteria = criteria,
            engineRpm = engineRpm,
            dtcCodes = dtcCodes,
            recentVoltageSamples = recentVoltageSamples,
            sessionCapturedAtMs = readingTimestampMs ?: parsed?.takeIf { it.isAvailable }?.timestampEpochMs,
            nowMs = nowMs
        )
    }

    private fun evaluate(
        pid42Reading: ObdPidReading?,
        pid42SupportedByBitmask: Boolean,
        hasEcuSession: Boolean,
        connectionStatus: SystemVoltageConnectionStatus,
        connectionManagerState: ObdConnectionManagerState?,
        lastDisconnectError: String?,
        isCommunicationInterrupted: Boolean,
        explicitErrorOverride: SystemVoltageErrorState? = null,
        criteria: SystemVoltageCriteria,
        engineRpm: Double?,
        dtcCodes: List<String>,
        recentVoltageSamples: List<Double>,
        sessionCapturedAtMs: Long?,
        nowMs: Long
    ): SystemVoltageProcessedData {
        val standardLimitation = "OBD-II Mode 01 PID 42 reports ECU Control Module Voltage only; it is not a direct battery-terminal or load-test measurement."
        val limitations = mutableListOf(standardLimitation)
        val missingInfo = mutableListOf<String>()

        if (criteria.systemType == ElectricalSystemType.UNKNOWN) {
            missingInfo += "Verified vehicle electrical architecture (12V System vs 24V System) is not configured"
            limitations += "Voltage thresholds cannot be evaluated without a verified 12V or 24V system configuration."
        }
        if (criteria.batteryType == BatteryChemistryType.UNSPECIFIED) {
            missingInfo += "Battery chemistry type is unspecified (using general default limits)"
        }
        val effectiveCondition = criteria.resolveOperatingCondition(engineRpm)
        if (effectiveCondition == ElectricalOperatingCondition.UNSPECIFIED) {
            missingInfo += "Operating condition / engine RPM context is unverified"
        }

        // Handle explicit or connection-level failure states before reading numeric value
        if (explicitErrorOverride == SystemVoltageErrorState.ADAPTER_NOT_DETECTED) {
            missingInfo += "No OBD-II adapter detected"
            return SystemVoltageProcessedData(
                currentVoltage = null,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = null,
                lastUpdatedIsoUtc = null,
                connectionStatus = SystemVoltageConnectionStatus.DISCONNECTED,
                errorState = SystemVoltageErrorState.ADAPTER_NOT_DETECTED,
                errorMessage = lastDisconnectError ?: SystemVoltageErrorState.ADAPTER_NOT_DETECTED.labelEn,
                isStale = false,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        if (isCommunicationInterrupted || explicitErrorOverride == SystemVoltageErrorState.COMMUNICATION_INTERRUPTED) {
            missingInfo += "Vehicle OBD-II communication was interrupted"
            val staleTs = pid42Reading?.timestampEpochMs ?: sessionCapturedAtMs
            return SystemVoltageProcessedData(
                currentVoltage = null,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = staleTs,
                lastUpdatedIsoUtc = staleTs?.let(::formatIsoUtc),
                connectionStatus = SystemVoltageConnectionStatus.INTERRUPTED,
                errorState = SystemVoltageErrorState.COMMUNICATION_INTERRUPTED,
                errorMessage = lastDisconnectError ?: SystemVoltageErrorState.COMMUNICATION_INTERRUPTED.labelEn,
                isStale = staleTs != null,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        if (connectionStatus == SystemVoltageConnectionStatus.DISCONNECTED && !hasEcuSession) {
            val adapterNotDetected = detectAdapterNotDetected(connectionManagerState)
            val errState = if (adapterNotDetected) {
                SystemVoltageErrorState.ADAPTER_NOT_DETECTED
            } else {
                SystemVoltageErrorState.BLUETOOTH_DISCONNECTED
            }
            missingInfo += if (adapterNotDetected) {
                "No supported OBD-II Bluetooth adapter detected"
            } else {
                "Bluetooth / OBD-II adapter is disconnected"
            }
            return SystemVoltageProcessedData(
                currentVoltage = null,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = null,
                lastUpdatedIsoUtc = null,
                connectionStatus = SystemVoltageConnectionStatus.DISCONNECTED,
                errorState = errState,
                errorMessage = lastDisconnectError ?: errState.labelEn,
                isStale = false,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        if (connectionStatus == SystemVoltageConnectionStatus.ERROR && !hasEcuSession) {
            val adapterNotDetected = detectAdapterNotDetected(connectionManagerState, lastDisconnectError)
            val errState = when {
                explicitErrorOverride != null && explicitErrorOverride != SystemVoltageErrorState.NONE -> explicitErrorOverride
                adapterNotDetected -> SystemVoltageErrorState.ADAPTER_NOT_DETECTED
                else -> SystemVoltageErrorState.ECU_COMMUNICATION_FAILED
            }
            missingInfo += errState.labelEn
            return SystemVoltageProcessedData(
                currentVoltage = null,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = null,
                lastUpdatedIsoUtc = null,
                connectionStatus = SystemVoltageConnectionStatus.ERROR,
                errorState = errState,
                errorMessage = lastDisconnectError ?: errState.labelEn,
                isStale = false,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        // Connected or has ECU session: inspect PID 42 reading strictly
        val availableVoltage = pid42Reading?.takeIf { it.isAvailable }?.value
        val readingTs = pid42Reading?.timestampEpochMs?.takeIf { it > 0L } ?: sessionCapturedAtMs

        if (availableVoltage == null) {
            val errState = when {
                explicitErrorOverride != null && explicitErrorOverride != SystemVoltageErrorState.NONE -> explicitErrorOverride
                pid42Reading?.availability == PidAvailability.UNSUPPORTED_BY_VEHICLE || !pid42SupportedByBitmask ->
                    SystemVoltageErrorState.PID_UNSUPPORTED
                pid42Reading?.availability == PidAvailability.MALFORMED_OR_TIMEOUT ->
                    SystemVoltageErrorState.INVALID_RESPONSE
                !hasEcuSession -> SystemVoltageErrorState.DATA_UNAVAILABLE
                else -> SystemVoltageErrorState.DATA_UNAVAILABLE
            }
            missingInfo += when (errState) {
                SystemVoltageErrorState.PID_UNSUPPORTED -> "Vehicle ECU does not support Mode 01 PID 42"
                SystemVoltageErrorState.INVALID_RESPONSE -> "PID 42 response was malformed or timed out"
                else -> "Control Module Voltage (PID 42) reading unavailable"
            }
            return SystemVoltageProcessedData(
                currentVoltage = null,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = null,
                lastUpdatedIsoUtc = null,
                connectionStatus = connectionStatus,
                errorState = errState,
                errorMessage = pid42Reading?.validationNote ?: errState.labelEn,
                isStale = false,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                rawHexResponse = pid42Reading?.rawHex,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        // Check staleness
        val isStale = readingTs != null &&
            criteria.staleTimeoutMs > 0L &&
            (nowMs - readingTs) > criteria.staleTimeoutMs

        if (isStale) {
            val ageSec = ((nowMs - readingTs!!) / 1000L).coerceAtLeast(1L)
            limitations += "Last valid PID 42 reading is ${ageSec}s old (exceeded ${criteria.staleTimeoutMs / 1000L}s freshness limit)."
            missingInfo += "Fresh Control Module Voltage reading (current reading is outdated)"
            return SystemVoltageProcessedData(
                currentVoltage = availableVoltage,
                systemType = criteria.systemType,
                voltageStatus = SystemVoltageStatus.UNAVAILABLE,
                lastUpdatedEpochMs = readingTs,
                lastUpdatedIsoUtc = formatIsoUtc(readingTs),
                connectionStatus = connectionStatus,
                errorState = SystemVoltageErrorState.DATA_OUTDATED,
                errorMessage = "Data outdated (${ageSec}s old)",
                isStale = true,
                batteryType = criteria.batteryType,
                operatingCondition = effectiveCondition,
                rawHexResponse = pid42Reading?.rawHex,
                limitations = limitations.distinct(),
                missingInformation = missingInfo.distinct()
            )
        }

        // Valid, fresh PID 42 value -> evaluate status strictly against verified configuration & criteria
        val thresholds = criteria.resolveThresholds(engineRpm)
        val hasVoltageMalfunctionDtc = "P0560" in dtcCodes
        val hasLowVoltageDtc = "P0562" in dtcCodes
        val hasHighVoltageDtc = "P0563" in dtcCodes

        val status = when {
            criteria.systemType == ElectricalSystemType.UNKNOWN -> {
                // Phase 4 rule: Never guess system type from one voltage reading; status requires verified criteria
                SystemVoltageStatus.UNAVAILABLE
            }
            thresholds != null && (
                hasVoltageMalfunctionDtc ||
                    detectVoltageOscillation(
                        samples = recentVoltageSamples,
                        currentVoltage = availableVoltage,
                        thresholds = thresholds
                    )
                ) -> SystemVoltageStatus.UNSTABLE
            thresholds != null && (availableVoltage < thresholds.lowVoltageThreshold || hasLowVoltageDtc) ->
                SystemVoltageStatus.LOW
            thresholds != null && (availableVoltage > thresholds.highVoltageThreshold || hasHighVoltageDtc) ->
                SystemVoltageStatus.HIGH
            else -> SystemVoltageStatus.NORMAL
        }

        return SystemVoltageProcessedData(
            currentVoltage = availableVoltage,
            systemType = criteria.systemType,
            voltageStatus = status,
            lastUpdatedEpochMs = readingTs,
            lastUpdatedIsoUtc = readingTs?.let(::formatIsoUtc),
            connectionStatus = connectionStatus,
            errorState = SystemVoltageErrorState.NONE,
            errorMessage = null,
            isStale = false,
            batteryType = criteria.batteryType,
            operatingCondition = effectiveCondition,
            rawHexResponse = pid42Reading?.rawHex,
            limitations = limitations.distinct(),
            missingInformation = missingInfo.distinct()
        )
    }

    private fun mergeVehicleConfigIntoCriteria(
        baseCriteria: SystemVoltageCriteria,
        vehicle: ObdVehicleSnapshot?
    ): SystemVoltageCriteria {
        if (vehicle == null) return baseCriteria
        val vehicleSysType = ElectricalSystemType.fromCode(vehicle.electricalSystemType)
        val vehicleBattery = BatteryChemistryType.fromCode(vehicle.batteryChemistryType)
        return baseCriteria.copy(
            systemType = if (baseCriteria.systemType != ElectricalSystemType.UNKNOWN) {
                baseCriteria.systemType
            } else {
                vehicleSysType
            },
            batteryType = if (baseCriteria.batteryType != BatteryChemistryType.UNSPECIFIED) {
                baseCriteria.batteryType
            } else {
                vehicleBattery
            }
        )
    }

    private fun resolveConnectionStatus(
        connectionManagerState: ObdConnectionManagerState?,
        isTransportConnected: Boolean,
        isCommunicationInterrupted: Boolean,
        hasEcuSession: Boolean
    ): SystemVoltageConnectionStatus {
        if (isCommunicationInterrupted) return SystemVoltageConnectionStatus.INTERRUPTED
        if (isTransportConnected) return SystemVoltageConnectionStatus.CONNECTED
        val mgrStatus = connectionManagerState?.connectionStatus
        return when (mgrStatus) {
            is ObdConnectionStatus.Connected ->
                if (isTransportConnected) SystemVoltageConnectionStatus.CONNECTED else SystemVoltageConnectionStatus.INTERRUPTED
            is ObdConnectionStatus.Connecting -> SystemVoltageConnectionStatus.CONNECTING
            is ObdConnectionStatus.Error,
            is ObdConnectionStatus.PermissionRequired -> SystemVoltageConnectionStatus.ERROR
            is ObdConnectionStatus.Disconnected ->
                if (hasEcuSession) SystemVoltageConnectionStatus.DISCONNECTED else SystemVoltageConnectionStatus.DISCONNECTED
            null ->
                if (hasEcuSession) SystemVoltageConnectionStatus.CONNECTED else SystemVoltageConnectionStatus.DISCONNECTED
        }
    }

    private fun detectAdapterNotDetected(
        connectionManagerState: ObdConnectionManagerState?,
        errorText: String? = null
    ): Boolean {
        val text = (errorText ?: (connectionManagerState?.connectionStatus as? ObdConnectionStatus.Error)?.message).orEmpty()
        if (text.contains("No Bluetooth OBD-II device", ignoreCase = true) ||
            text.contains("Adapter not detected", ignoreCase = true) ||
            text.contains("ยังไม่ได้เลือกอุปกรณ์", ignoreCase = true) ||
            text.contains("ตรวจไม่พบอะแดปเตอร์", ignoreCase = true) ||
            text.contains("No compatible", ignoreCase = true)
        ) {
            return true
        }
        val bt = connectionManagerState?.bluetoothState ?: return false
        return connectionManagerState.activeMethod == ObdConnectionMethod.BLUETOOTH &&
            bt.isBluetoothEnabled &&
            bt.hasRequiredPermissions &&
            !bt.isDiscovering &&
            bt.devices.isEmpty()
    }

    private fun detectVoltageOscillation(
        samples: List<Double>,
        currentVoltage: Double?,
        thresholds: SystemVoltageThresholds
    ): Boolean {
        if (currentVoltage == null) return false
        val series = if (samples.lastOrNull() == currentVoltage) samples else (samples + currentVoltage)
        if (series.size < 3) return false
        val spread = (series.maxOrNull() ?: currentVoltage) - (series.minOrNull() ?: currentVoltage)
        if (spread < thresholds.unstableSpreadThreshold) return false

        var sawPositiveJump = false
        var sawNegativeJump = false
        for (i in 1 until series.size) {
            val delta = series[i] - series[i - 1]
            if (delta >= thresholds.unstableStepThreshold) sawPositiveJump = true
            if (delta <= -thresholds.unstableStepThreshold) sawNegativeJump = true
        }
        return sawPositiveJump && sawNegativeJump
    }

    private fun formatIsoUtc(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))
    }
}
