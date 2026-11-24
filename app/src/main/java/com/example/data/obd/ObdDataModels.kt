package com.example.data.obd

import com.example.data.model.Vehicle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Standardized Shared OBD-II Data Models (Kotlin equivalent of cross-platform diagnostic schema).
 * Used uniformly across Wi-Fi OBD-II, Bluetooth OBD-II, imported diagnostic files, and manual DTC entries.
 *
 * CRITICAL CONTRACT:
 * Missing or unsupported PID/sensor values MUST be represented as `null` with an explicit
 * [PidAvailability] state — NEVER defaulted to `0.0`.
 */

import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore

enum class ObdDataSourceType(
    val code: String,
    private val labelEn: String,
    private val labelTh: String
) {
    WIFI_OBD("WIFI_OBD", "Wi-Fi OBD-II Adapter", "อะแดปเตอร์ Wi-Fi OBD-II"),
    BLUETOOTH_OBD("BLUETOOTH_OBD", "Bluetooth OBD-II Adapter", "อะแดปเตอร์ Bluetooth OBD-II"),
    IMPORTED_LOG("IMPORTED_LOG", "Imported Diagnostic Log", "ไฟล์บันทึกการวิเคราะห์ที่นำเข้า"),
    MANUAL_DTC_ENTRY("MANUAL_DTC_ENTRY", "Manual DTC Entry", "กรอกรหัสปัญหาด้วยตนเอง");

    val displayLabel: String
        get() = if (LanguagePreferenceStore.currentLanguage.value == AppLanguage.THAI) labelTh else labelEn
}

enum class DataReliabilityLevel(val code: String, val displayLabel: String) {
    HIGH_VERIFIED_HARDWARE("HIGH_VERIFIED_HARDWARE", "Verified Read-Only Adapter Session"),
    MODERATE_PARTIAL_DATA("MODERATE_PARTIAL_DATA", "Partial Telemetry / Missing PIDs"),
    LOW_MANUAL_OR_UNVERIFIED("LOW_MANUAL_OR_UNVERIFIED", "Manual / Unverified Import"),
    UNVERIFIED_INSUFFICIENT("UNVERIFIED_INSUFFICIENT", "Insufficient / Unverified Data")
}

data class ObdSourceMetadata(
    val sourceType: ObdDataSourceType,
    val adapterOrFileLabel: String,
    val protocolLabel: String?,
    val connectionMethod: ObdConnectionMethod? = null,
    val bluetoothArchitecture: BluetoothArchitecture? = null,
    val readOnlyEnforced: Boolean = true,
    val reliabilityLevel: DataReliabilityLevel,
    val reliabilityNotes: List<String> = emptyList(),
    val capturedAtEpochMs: Long
)

data class ObdVehicleSnapshot(
    val vehicleId: String,
    val manufacturer: String,
    val model: String,
    val modelYear: Int?,
    val trim: String?,
    val engineType: String?,
    val engineDisplacement: String?,
    val transmission: String?,
    val fuelType: String?,
    val mileage: Long?,
    val mileageUnit: String?,
    val market: String?,
    val electricalSystemType: String = "UNKNOWN",
    val batteryChemistryType: String = "UNSPECIFIED"
) {
    fun displayTitle(): String {
        val parts = listOfNotNull(
            modelYear?.takeIf { it > 0 }?.toString(),
            manufacturer.takeIf { it.isNotBlank() },
            model.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" ").ifBlank { "Unspecified Vehicle" }
    }

    fun missingSpecificationFields(): List<String> {
        val missing = mutableListOf<String>()
        if (manufacturer.isBlank()) missing += "Manufacturer"
        if (model.isBlank()) missing += "Model"
        if (modelYear == null || modelYear <= 0) missing += "Model Year"
        if (engineType.isNullOrBlank() || engineType.equals("Unknown / Unavailable", ignoreCase = true)) {
            missing += "Engine Type"
        }
        if (fuelType.isNullOrBlank() || fuelType.equals("Unknown / Unavailable", ignoreCase = true)) {
            missing += "Fuel Type"
        }
        if (transmission.isNullOrBlank() || transmission.equals("Unknown / Unavailable", ignoreCase = true)) {
            missing += "Transmission"
        }
        if (mileage == null || mileage < 0L) missing += "Current Mileage"
        if (market.isNullOrBlank()) missing += "Market / Region"
        return missing
    }

    companion object {
        fun fromVehicle(vehicle: Vehicle): ObdVehicleSnapshot {
            return ObdVehicleSnapshot(
                vehicleId = vehicle.id,
                manufacturer = vehicle.manufacturer.trim(),
                model = vehicle.model.trim(),
                modelYear = vehicle.modelYear.takeIf { it > 1885 },
                trim = vehicle.trim.trim().ifBlank { null },
                engineType = vehicle.engineType.trim().ifBlank { null },
                engineDisplacement = vehicle.engineDisplacement.trim().ifBlank { null },
                transmission = vehicle.transmission.trim().ifBlank { null },
                fuelType = vehicle.fuelType.trim().ifBlank { null },
                mileage = vehicle.mileage.takeIf { it >= 0L },
                mileageUnit = vehicle.mileageUnit.trim().ifBlank { null },
                market = vehicle.market.trim().ifBlank { null },
                electricalSystemType = vehicle.electricalSystemType.ifBlank { "UNKNOWN" },
                batteryChemistryType = vehicle.batteryChemistryType.ifBlank { "UNSPECIFIED" }
            )
        }
    }
}

enum class DtcStatus(val code: String, val displayLabel: String) {
    CONFIRMED("CONFIRMED", "Confirmed (Mode 03)"),
    PENDING("PENDING", "Pending (Mode 07)"),
    PERMANENT_READ_ONLY("PERMANENT_READ_ONLY", "Permanent (Mode 0A)"),
    UNKNOWN("UNKNOWN", "Status Unspecified")
}

enum class DtcSystemCategory(val prefixChar: Char, val displayName: String) {
    POWERTRAIN('P', "Powertrain (Engine / Transmission / Emissions)"),
    CHASSIS('C', "Chassis (Brakes / ABS / Steering / Suspension)"),
    BODY('B', "Body (Airbags / Climate / Lighting / Comfort)"),
    NETWORK('U', "Network & ECU Communication"),
    UNKNOWN('?', "Unclassified System");

    companion object {
        fun fromDtcCode(code: String): DtcSystemCategory {
            val first = code.trim().uppercase().firstOrNull() ?: return UNKNOWN
            return entries.firstOrNull { it.prefixChar == first } ?: UNKNOWN
        }
    }
}

data class DiagnosticTroubleCode(
    val code: String,
    val status: DtcStatus,
    val systemCategory: DtcSystemCategory = DtcSystemCategory.fromDtcCode(code),
    val ecuAddress: String? = null,
    val rawHexFrame: String? = null,
    val timestampEpochMs: Long,
    val isValidFormat: Boolean = isValidDtcFormat(code)
) {
    companion object {
        private val DTC_REGEX = Regex("^[PCBU][0-9A-F]{4}$")

        fun normalize(raw: String): String = raw.trim().uppercase()

        fun isValidDtcFormat(code: String): Boolean = DTC_REGEX.matches(normalize(code))
    }
}

enum class PidAvailability(val code: String) {
    AVAILABLE("AVAILABLE"),
    UNSUPPORTED_BY_VEHICLE("UNSUPPORTED_BY_VEHICLE"),
    MALFORMED_OR_TIMEOUT("MALFORMED_OR_TIMEOUT"),
    UNAVAILABLE("UNAVAILABLE")
}

data class ObdPidReading(
    val pidCode: String,
    val name: String,
    /** Numeric sensor value; strictly null when unavailable, unsupported, or invalid (never 0). */
    val value: Double?,
    val unit: String,
    val availability: PidAvailability,
    val rawHex: String? = null,
    val timestampEpochMs: Long,
    val validationNote: String? = null
) {
    val isAvailable: Boolean
        get() = availability == PidAvailability.AVAILABLE && value != null

    fun formattedValueOrUnavailable(): String {
        val numeric = value
        return if (isAvailable && numeric != null) {
            val formatted = if (numeric % 1.0 == 0.0) {
                numeric.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.2f", numeric).trimEnd('0').trimEnd('.')
            }
            if (unit.isNotBlank()) "$formatted $unit" else formatted
        } else {
            "Unavailable (${availability.code})"
        }
    }
}

data class FreezeFrameSnapshot(
    val associatedDtcCode: String?,
    val timestampEpochMs: Long,
    val readings: List<ObdPidReading>,
    val rawHexFrames: List<String> = emptyList()
)

data class ObdMonitorStatus(
    val milOn: Boolean,
    val confirmedDtcCountFromPid01: Int,
    val supportedMonitorsCount: Int,
    val readyMonitorsCount: Int,
    val incompleteMonitorNames: List<String> = emptyList(),
    val rawHex: String? = null
) {
    val incompleteCount: Int
        get() = (supportedMonitorsCount - readyMonitorsCount).coerceAtLeast(0)
}

data class ObdRawTraceEntry(
    val command: String,
    val rawResponse: String,
    val timestampEpochMs: Long,
    val parsedSuccessfully: Boolean,
    val note: String? = null
)

data class ObdDiagnosticSession(
    val sessionId: String,
    val vehicle: ObdVehicleSnapshot,
    val sourceMetadata: ObdSourceMetadata,
    val confirmedDtcs: List<DiagnosticTroubleCode>,
    val pendingDtcs: List<DiagnosticTroubleCode>,
    val freezeFrames: List<FreezeFrameSnapshot>,
    val livePidReadings: List<ObdPidReading>,
    val supportedPids: Set<String> = emptySet(),
    val unsupportedCommands: List<String> = emptyList(),
    val rawTraces: List<ObdRawTraceEntry> = emptyList(),
    val ownerSymptoms: String? = null,
    val capturedAtEpochMs: Long,
    /** Must be true only in automated unit tests; never shown as a real vehicle scan. */
    val isSimulatedTestData: Boolean = false,
    val monitorStatus: ObdMonitorStatus? = null
) {
    val allDtcs: List<DiagnosticTroubleCode>
        get() = (confirmedDtcs + pendingDtcs).distinctBy { "${it.code}:${it.status.code}" }

    val availableReadings: List<ObdPidReading>
        get() = livePidReadings.filter { it.isAvailable }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("session_id", sessionId)
            put("captured_at_epoch_ms", capturedAtEpochMs)
            put("is_simulated_test_data", isSimulatedTestData)
            put("owner_symptoms", ownerSymptoms ?: JSONObject.NULL)
            put("vehicle", JSONObject().apply {
                put("vehicle_id", vehicle.vehicleId)
                put("manufacturer", vehicle.manufacturer)
                put("model", vehicle.model)
                put("model_year", vehicle.modelYear ?: JSONObject.NULL)
                put("trim", vehicle.trim ?: JSONObject.NULL)
                put("engine_type", vehicle.engineType ?: JSONObject.NULL)
                put("fuel_type", vehicle.fuelType ?: JSONObject.NULL)
                put("transmission", vehicle.transmission ?: JSONObject.NULL)
                put("mileage", vehicle.mileage ?: JSONObject.NULL)
                put("mileage_unit", vehicle.mileageUnit ?: JSONObject.NULL)
                put("market", vehicle.market ?: JSONObject.NULL)
            })
            put("source_metadata", JSONObject().apply {
                put("source_type", sourceMetadata.sourceType.code)
                put("adapter_or_file_label", sourceMetadata.adapterOrFileLabel)
                put("protocol_label", sourceMetadata.protocolLabel ?: JSONObject.NULL)
                put("read_only_enforced", sourceMetadata.readOnlyEnforced)
                put("reliability_level", sourceMetadata.reliabilityLevel.code)
                put("captured_at_epoch_ms", sourceMetadata.capturedAtEpochMs)
            })
            put("confirmed_dtcs", JSONArray(confirmedDtcs.map { dtc ->
                JSONObject().apply {
                    put("code", dtc.code)
                    put("status", dtc.status.code)
                    put("category", dtc.systemCategory.name)
                    put("raw_hex_frame", dtc.rawHexFrame ?: JSONObject.NULL)
                    put("timestamp_epoch_ms", dtc.timestampEpochMs)
                }
            }))
            put("pending_dtcs", JSONArray(pendingDtcs.map { dtc ->
                JSONObject().apply {
                    put("code", dtc.code)
                    put("status", dtc.status.code)
                    put("category", dtc.systemCategory.name)
                    put("raw_hex_frame", dtc.rawHexFrame ?: JSONObject.NULL)
                    put("timestamp_epoch_ms", dtc.timestampEpochMs)
                }
            }))
            put("live_pids", JSONArray(livePidReadings.map { pid ->
                JSONObject().apply {
                    put("pid_code", pid.pidCode)
                    put("name", pid.name)
                    put("value", pid.value ?: JSONObject.NULL)
                    put("unit", pid.unit)
                    put("availability", pid.availability.code)
                    put("raw_hex", pid.rawHex ?: JSONObject.NULL)
                    put("timestamp_epoch_ms", pid.timestampEpochMs)
                }
            }))
        }
    }
}
