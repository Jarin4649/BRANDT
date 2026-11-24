package com.example.data.obd

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Phase 4: Manual DTC Entry & Diagnostic File Import (JSON, CSV, ELM327/OBD text logs).
 * All imported data flows through the shared [ObdDiagnosticSession] model with honest
 * [DataReliabilityLevel] metadata and strictly null values for missing PIDs.
 */
object DiagnosticFileImporter {

    private val DTC_PATTERN = Regex("\\b([PCBU][0-9A-F]{4})\\b", RegexOption.IGNORE_CASE)
    private val PENDING_LINE_HINT = Regex("\\bpending|mode\\s*0?7|\\b47\\b", RegexOption.IGNORE_CASE)

    fun importFromUri(
        context: Context,
        uri: Uri,
        fileDisplayName: String,
        vehicle: ObdVehicleSnapshot,
        ownerSymptoms: String? = null
    ): Result<ObdDiagnosticSession> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size > 1_500_000) {
                    return Result.failure(
                        IllegalArgumentException("Diagnostic file exceeds 1.5 MB limit.")
                    )
                }
                bytes.toString(Charsets.UTF_8)
            } ?: return Result.failure(
                IllegalStateException("Unable to open the selected diagnostic file.")
            )
            importFromText(
                rawText = content,
                fileDisplayName = fileDisplayName,
                vehicle = vehicle,
                ownerSymptoms = ownerSymptoms
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importFromText(
        rawText: String,
        fileDisplayName: String,
        vehicle: ObdVehicleSnapshot,
        ownerSymptoms: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ObdDiagnosticSession> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("The selected diagnostic file is empty."))
        }

        if (trimmed.startsWith("{")) {
            val jsonAttempt = parseJsonDiagnosticFile(trimmed, fileDisplayName, vehicle, ownerSymptoms, nowMs)
            if (jsonAttempt.isSuccess) return jsonAttempt
        }

        return parsePlainTextOrCsvLog(trimmed, fileDisplayName, vehicle, ownerSymptoms, nowMs)
    }

    fun buildManualSession(
        vehicle: ObdVehicleSnapshot,
        manualDtcs: List<Pair<String, DtcStatus>>,
        ownerSymptoms: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ObdDiagnosticSession> {
        val normalized = manualDtcs.map { (raw, status) ->
            DiagnosticTroubleCode(
                code = DiagnosticTroubleCode.normalize(raw),
                status = status,
                rawHexFrame = null,
                timestampEpochMs = nowMs
            )
        }.filter { it.code.isNotBlank() }

        val invalid = normalized.filterNot { it.isValidFormat }
        if (invalid.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid OBD-II code format: ${invalid.joinToString(", ") { it.code }}. Expected 5 characters like P0171 or C0035."
                )
            )
        }

        val cleanSymptoms = ownerSymptoms?.trim()?.takeIf { it.isNotBlank() }
        if (normalized.isEmpty() && cleanSymptoms == null) {
            return Result.failure(
                IllegalArgumentException("Enter at least one DTC code or describe the vehicle symptoms before analyzing.")
            )
        }

        val confirmed = normalized.filter { it.status != DtcStatus.PENDING }
        val pending = normalized.filter { it.status == DtcStatus.PENDING }

        return Result.success(
            ObdDiagnosticSession(
                sessionId = "manual_${UUID.randomUUID()}",
                vehicle = vehicle,
                sourceMetadata = ObdSourceMetadata(
                    sourceType = ObdDataSourceType.MANUAL_DTC_ENTRY,
                    adapterOrFileLabel = "Owner Manual Entry",
                    protocolLabel = null,
                    readOnlyEnforced = true,
                    reliabilityLevel = DataReliabilityLevel.LOW_MANUAL_OR_UNVERIFIED,
                    reliabilityNotes = listOf(
                        "Entered manually without direct ECU live PID telemetry; sensor values remain unavailable until scanned."
                    ),
                    capturedAtEpochMs = nowMs
                ),
                confirmedDtcs = confirmed,
                pendingDtcs = pending,
                freezeFrames = emptyList(),
                livePidReadings = emptyList(),
                ownerSymptoms = cleanSymptoms,
                capturedAtEpochMs = nowMs,
                isSimulatedTestData = false
            )
        )
    }

    private fun parseJsonDiagnosticFile(
        jsonText: String,
        fileDisplayName: String,
        vehicle: ObdVehicleSnapshot,
        ownerSymptoms: String?,
        nowMs: Long
    ): Result<ObdDiagnosticSession> {
        return try {
            val root = JSONObject(jsonText)
            val confirmed = mutableListOf<DiagnosticTroubleCode>()
            val pending = mutableListOf<DiagnosticTroubleCode>()
            val livePids = mutableListOf<ObdPidReading>()

            fun parseDtcArray(arr: JSONArray?, defaultStatus: DtcStatus) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val elem = arr.opt(i)
                    when (elem) {
                        is JSONObject -> {
                            val code = DiagnosticTroubleCode.normalize(elem.optString("code", ""))
                            val statusStr = elem.optString("status", defaultStatus.code)
                            val status = DtcStatus.entries.firstOrNull {
                                it.code.equals(statusStr, ignoreCase = true)
                            } ?: defaultStatus
                            if (code.isNotBlank()) {
                                val dtc = DiagnosticTroubleCode(
                                    code = code,
                                    status = status,
                                    rawHexFrame = elem.optString("raw_hex_frame", "").ifBlank { null },
                                    timestampEpochMs = nowMs
                                )
                                if (status == DtcStatus.PENDING) pending += dtc else confirmed += dtc
                            }
                        }
                        is String -> {
                            val code = DiagnosticTroubleCode.normalize(elem)
                            if (code.isNotBlank()) {
                                val dtc = DiagnosticTroubleCode(code = code, status = defaultStatus, timestampEpochMs = nowMs)
                                if (defaultStatus == DtcStatus.PENDING) pending += dtc else confirmed += dtc
                            }
                        }
                    }
                }
            }

            parseDtcArray(root.optJSONArray("confirmed_dtcs"), DtcStatus.CONFIRMED)
            parseDtcArray(root.optJSONArray("pending_dtcs"), DtcStatus.PENDING)
            parseDtcArray(root.optJSONArray("dtcs"), DtcStatus.CONFIRMED)

            val pidsArr = root.optJSONArray("live_pids") ?: root.optJSONArray("pids")
            if (pidsArr != null) {
                for (i in 0 until pidsArr.length()) {
                    val item = pidsArr.optJSONObject(i) ?: continue
                    val pidCode = item.optString("pid_code", item.optString("pid", "")).trim().uppercase()
                    val name = item.optString("name", "OBD PID $pidCode").trim()
                    val unit = item.optString("unit", "").trim()
                    val rawHex = item.optString("raw_hex", "").trim().ifBlank { null }
                    val rawVal = if (item.has("value") && !item.isNull("value")) {
                        item.optDouble("value").takeUnless { it.isNaN() }
                    } else null

                    val availStr = item.optString("availability", "")
                    val availability = PidAvailability.entries.firstOrNull {
                        it.code.equals(availStr, ignoreCase = true)
                    } ?: if (rawVal != null) PidAvailability.AVAILABLE else PidAvailability.UNAVAILABLE

                    livePids += ObdPidReading(
                        pidCode = pidCode.ifBlank { "UNKNOWN" },
                        name = name,
                        value = if (availability == PidAvailability.AVAILABLE) rawVal else null,
                        unit = unit,
                        availability = if (rawVal == null && availability == PidAvailability.AVAILABLE) {
                            PidAvailability.UNAVAILABLE
                        } else {
                            availability
                        },
                        rawHex = rawHex,
                        timestampEpochMs = nowMs
                    )
                }
            }

            val fileSymptoms = root.optString("owner_symptoms", "").trim().takeIf { it.isNotBlank() }
            val mergedSymptoms = listOfNotNull(fileSymptoms, ownerSymptoms?.trim()?.takeIf { it.isNotBlank() })
                .distinct()
                .joinToString(" | ")
                .takeIf { it.isNotBlank() }

            if (confirmed.isEmpty() && pending.isEmpty() && livePids.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("JSON diagnostic file '$fileDisplayName' contained no recognized DTCs or PID readings.")
                )
            }

            val availablePidCount = livePids.count { it.isAvailable }
            Result.success(
                ObdDiagnosticSession(
                    sessionId = "import_${UUID.randomUUID()}",
                    vehicle = vehicle,
                    sourceMetadata = ObdSourceMetadata(
                        sourceType = ObdDataSourceType.IMPORTED_LOG,
                        adapterOrFileLabel = fileDisplayName,
                        protocolLabel = root.optJSONObject("source_metadata")?.optString("protocol_label", "")?.ifBlank { null },
                        readOnlyEnforced = true,
                        reliabilityLevel = if (availablePidCount > 0) {
                            DataReliabilityLevel.MODERATE_PARTIAL_DATA
                        } else {
                            DataReliabilityLevel.LOW_MANUAL_OR_UNVERIFIED
                        },
                        reliabilityNotes = listOf("Imported from JSON diagnostic file ($fileDisplayName)."),
                        capturedAtEpochMs = nowMs
                    ),
                    confirmedDtcs = confirmed.distinctBy { it.code },
                    pendingDtcs = pending.distinctBy { it.code },
                    freezeFrames = emptyList(),
                    livePidReadings = livePids,
                    ownerSymptoms = mergedSymptoms,
                    capturedAtEpochMs = nowMs,
                    isSimulatedTestData = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlainTextOrCsvLog(
        rawText: String,
        fileDisplayName: String,
        vehicle: ObdVehicleSnapshot,
        ownerSymptoms: String?,
        nowMs: Long
    ): Result<ObdDiagnosticSession> {
        val confirmed = mutableListOf<DiagnosticTroubleCode>()
        val pending = mutableListOf<DiagnosticTroubleCode>()
        val parsedPids = mutableListOf<ObdPidReading>()
        val traces = mutableListOf<ObdRawTraceEntry>()

        rawText.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            // 1. Check raw Mode 03/07 ELM hex responses inside text logs
            val upper = line.uppercase()
            if (upper.startsWith("43 ") || upper.startsWith("43")) {
                val res = Elm327Parser.parseDtcResponse(line, DtcStatus.CONFIRMED, nowMs)
                confirmed += res.dtcs
                traces += ObdRawTraceEntry("IMPORT_03", line, nowMs, res.dtcs.isNotEmpty())
            } else if (upper.startsWith("47 ") || upper.startsWith("47")) {
                val res = Elm327Parser.parseDtcResponse(line, DtcStatus.PENDING, nowMs)
                pending += res.dtcs
                traces += ObdRawTraceEntry("IMPORT_07", line, nowMs, res.dtcs.isNotEmpty())
            } else {
                // 2. Extract explicit DTC tokens (P0171, C0035, etc.)
                val isPendingLine = PENDING_LINE_HINT.containsMatchIn(line)
                DTC_PATTERN.findAll(line).forEach { match ->
                    val code = match.groupValues[1].uppercase()
                    val dtc = DiagnosticTroubleCode(
                        code = code,
                        status = if (isPendingLine) DtcStatus.PENDING else DtcStatus.CONFIRMED,
                        rawHexFrame = line.take(120),
                        timestampEpochMs = nowMs
                    )
                    if (isPendingLine) pending += dtc else confirmed += dtc
                }
            }

            // 3. Extract raw Mode 01 hex PID responses (e.g. `41 05 82` or `0105: 41 05 82`)
            val hexPidMatch = Regex("41\\s*([0-9A-F]{2})(?:\\s+[0-9A-F]{2})+").find(upper)
            if (hexPidMatch != null) {
                val pidCmd = "01" + hexPidMatch.groupValues[1]
                val reading = Elm327Parser.parsePidResponse(pidCmd, hexPidMatch.value, nowMs)
                if (reading.isAvailable) parsedPids += reading
            }

            // 4. Extract human-readable CSV/log telemetry rows cleanly
            parseNamedTelemetryLine(line, nowMs)?.let { parsedPids += it }
        }

        val uniqueConfirmed = confirmed.distinctBy { it.code }
        val uniquePending = pending.filterNot { p -> uniqueConfirmed.any { it.code == p.code } }.distinctBy { it.code }
        val uniquePids = parsedPids.distinctBy { it.pidCode }

        if (uniqueConfirmed.isEmpty() && uniquePending.isEmpty() && uniquePids.isEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "No valid OBD-II trouble codes or readable sensor telemetry found in '$fileDisplayName'."
                )
            )
        }

        return Result.success(
            ObdDiagnosticSession(
                sessionId = "import_${UUID.randomUUID()}",
                vehicle = vehicle,
                sourceMetadata = ObdSourceMetadata(
                    sourceType = ObdDataSourceType.IMPORTED_LOG,
                    adapterOrFileLabel = fileDisplayName,
                    protocolLabel = null,
                    readOnlyEnforced = true,
                    reliabilityLevel = if (uniquePids.any { it.isAvailable }) {
                        DataReliabilityLevel.MODERATE_PARTIAL_DATA
                    } else {
                        DataReliabilityLevel.LOW_MANUAL_OR_UNVERIFIED
                    },
                    reliabilityNotes = listOf(
                        "Imported from '$fileDisplayName'. Unlisted sensor PIDs remain marked unavailable."
                    ),
                    capturedAtEpochMs = nowMs
                ),
                confirmedDtcs = uniqueConfirmed,
                pendingDtcs = uniquePending,
                freezeFrames = emptyList(),
                livePidReadings = uniquePids,
                rawTraces = traces,
                ownerSymptoms = ownerSymptoms?.trim()?.takeIf { it.isNotBlank() },
                capturedAtEpochMs = nowMs,
                isSimulatedTestData = false
            )
        )
    }

    private fun parseNamedTelemetryLine(line: String, nowMs: Long): ObdPidReading? {
        val numberMatch = Regex("(-?\\d+(?:\\.\\d+)?)").find(line) ?: return null
        val parsedNumber = numberMatch.groupValues[1].toDoubleOrNull() ?: return null
        val lower = line.lowercase()

        return when {
            lower.contains("coolant") || lower.contains("0105") ->
                if (parsedNumber in -40.0..215.0) ObdPidReading("0105", "Engine Coolant Temperature", parsedNumber, "°C", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            lower.contains("rpm") || lower.contains("engine speed") || lower.contains("010c") ->
                if (parsedNumber in 0.0..16383.75) ObdPidReading("010C", "Engine Speed (RPM)", parsedNumber, "rpm", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            lower.contains("ltft") || lower.contains("long term fuel") || lower.contains("0107") ->
                if (parsedNumber in -100.0..99.22) ObdPidReading("0107", "Long Term Fuel Trim (Bank 1)", parsedNumber, "%", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            lower.contains("stft") || lower.contains("short term fuel") || lower.contains("0106") ->
                if (parsedNumber in -100.0..99.22) ObdPidReading("0106", "Short Term Fuel Trim (Bank 1)", parsedNumber, "%", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            lower.contains("maf") || lower.contains("mass air") || lower.contains("0110") ->
                if (parsedNumber in 0.0..655.35) ObdPidReading("0110", "Mass Air Flow (MAF) Rate", parsedNumber, "g/s", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            lower.contains("voltage") || lower.contains("battery") || lower.contains("0142") ->
                if (parsedNumber in 0.0..65.5) ObdPidReading("0142", "Control Module Voltage", parsedNumber, "V", PidAvailability.AVAILABLE, line.take(80), nowMs) else null
            else -> null
        }
    }
}
