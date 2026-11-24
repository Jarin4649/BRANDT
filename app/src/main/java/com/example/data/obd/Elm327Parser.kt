package com.example.data.obd

/**
 * ELM327-compatible OBD-II response parser.
 *
 * Design guarantees:
 * - Preserves original raw response strings for debugging and traceability.
 * - Identifies unsupported commands (`NO DATA`, `?`, unsupported PID bitmask) vs malformed frames.
 * - Validates physical value ranges and units per SAE J1979.
 * - Unavailable/invalid/unsupported PID values are ALWAYS returned as `null`, never `0.0`.
 */
object Elm327Parser {

    data class DtcParseResult(
        val dtcs: List<DiagnosticTroubleCode>,
        val rawResponse: String,
        val isUnsupportedCommand: Boolean = false,
        val errorNote: String? = null
    )

    data class PidDefinition(
        val pidCommand: String,
        val name: String,
        val unit: String,
        val expectedDataBytes: Int,
        val minValid: Double,
        val maxValid: Double,
        val decode: (List<Int>) -> Double?
    )

    private val pidCatalog: Map<String, PidDefinition> = listOf(
        PidDefinition(
            pidCommand = "0104",
            name = "Calculated Engine Load",
            unit = "%",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 100.0,
            decode = { bytes -> (bytes[0] * 100.0) / 255.0 }
        ),
        PidDefinition(
            pidCommand = "0105",
            name = "Engine Coolant Temperature",
            unit = "°C",
            expectedDataBytes = 1,
            minValid = -40.0,
            maxValid = 215.0,
            decode = { bytes -> (bytes[0] - 40).toDouble() }
        ),
        PidDefinition(
            pidCommand = "0106",
            name = "Short Term Fuel Trim (Bank 1)",
            unit = "%",
            expectedDataBytes = 1,
            minValid = -100.0,
            maxValid = 99.22,
            decode = { bytes -> ((bytes[0] - 128) * 100.0) / 128.0 }
        ),
        PidDefinition(
            pidCommand = "0107",
            name = "Long Term Fuel Trim (Bank 1)",
            unit = "%",
            expectedDataBytes = 1,
            minValid = -100.0,
            maxValid = 99.22,
            decode = { bytes -> ((bytes[0] - 128) * 100.0) / 128.0 }
        ),
        PidDefinition(
            pidCommand = "0108",
            name = "Short Term Fuel Trim (Bank 2)",
            unit = "%",
            expectedDataBytes = 1,
            minValid = -100.0,
            maxValid = 99.22,
            decode = { bytes -> ((bytes[0] - 128) * 100.0) / 128.0 }
        ),
        PidDefinition(
            pidCommand = "0109",
            name = "Long Term Fuel Trim (Bank 2)",
            unit = "%",
            expectedDataBytes = 1,
            minValid = -100.0,
            maxValid = 99.22,
            decode = { bytes -> ((bytes[0] - 128) * 100.0) / 128.0 }
        ),
        PidDefinition(
            pidCommand = "010A",
            name = "Fuel Pressure",
            unit = "kPa",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 765.0,
            decode = { bytes -> (bytes[0] * 3).toDouble() }
        ),
        PidDefinition(
            pidCommand = "010B",
            name = "Intake Manifold Absolute Pressure",
            unit = "kPa",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 255.0,
            decode = { bytes -> bytes[0].toDouble() }
        ),
        PidDefinition(
            pidCommand = "010C",
            name = "Engine Speed (RPM)",
            unit = "rpm",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 16383.75,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]) / 4.0 }
        ),
        PidDefinition(
            pidCommand = "010D",
            name = "Vehicle Speed",
            unit = "km/h",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 255.0,
            decode = { bytes -> bytes[0].toDouble() }
        ),
        PidDefinition(
            pidCommand = "010F",
            name = "Intake Air Temperature",
            unit = "°C",
            expectedDataBytes = 1,
            minValid = -40.0,
            maxValid = 215.0,
            decode = { bytes -> (bytes[0] - 40).toDouble() }
        ),
        PidDefinition(
            pidCommand = "0110",
            name = "Mass Air Flow (MAF) Rate",
            unit = "g/s",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 655.35,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]) / 100.0 }
        ),
        PidDefinition(
            pidCommand = "0111",
            name = "Throttle Position",
            unit = "%",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 100.0,
            decode = { bytes -> (bytes[0] * 100.0) / 255.0 }
        ),
        PidDefinition(
            pidCommand = "0114",
            name = "O2 Sensor 1 Voltage (Bank 1)",
            unit = "V",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 1.275,
            decode = { bytes -> bytes[0] / 200.0 }
        ),
        PidDefinition(
            pidCommand = "0115",
            name = "O2 Sensor 2 Voltage (Bank 1)",
            unit = "V",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 1.275,
            decode = { bytes -> bytes[0] / 200.0 }
        ),
        PidDefinition(
            pidCommand = "0123",
            name = "Fuel Rail Gauge Pressure",
            unit = "kPa",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 655350.0,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]) * 10.0 }
        ),
        PidDefinition(
            pidCommand = "0133",
            name = "Absolute Barometric Pressure",
            unit = "kPa",
            expectedDataBytes = 1,
            minValid = 0.0,
            maxValid = 255.0,
            decode = { bytes -> bytes[0].toDouble() }
        ),
        PidDefinition(
            pidCommand = "013C",
            name = "Catalyst / Exhaust Gas Temperature",
            unit = "°C",
            expectedDataBytes = 2,
            minValid = -40.0,
            maxValid = 6513.5,
            decode = { bytes -> (((bytes[0] * 256) + bytes[1]) / 10.0) - 40.0 }
        ),
        PidDefinition(
            pidCommand = "0142",
            name = "Control Module Voltage",
            unit = "V",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 65.535,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]) / 1000.0 }
        ),
        PidDefinition(
            pidCommand = "0143",
            name = "Absolute Load Value",
            unit = "%",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 25700.0,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]) * (100.0 / 255.0) }
        ),
        PidDefinition(
            pidCommand = "015C",
            name = "Engine Oil Temperature",
            unit = "°C",
            expectedDataBytes = 1,
            minValid = -40.0,
            maxValid = 210.0,
            decode = { bytes -> (bytes[0] - 40).toDouble() }
        ),
        PidDefinition(
            pidCommand = "0162",
            name = "Actual Engine Percent Torque",
            unit = "%",
            expectedDataBytes = 1,
            minValid = -125.0,
            maxValid = 125.0,
            decode = { bytes -> (bytes[0] - 125).toDouble() }
        ),
        PidDefinition(
            pidCommand = "0163",
            name = "Engine Reference Torque",
            unit = "Nm",
            expectedDataBytes = 2,
            minValid = 0.0,
            maxValid = 65535.0,
            decode = { bytes -> ((bytes[0] * 256) + bytes[1]).toDouble() }
        ),
        PidDefinition(
            pidCommand = "01A4",
            name = "Transmission Actual Gear",
            unit = "gear",
            expectedDataBytes = 4,
            minValid = 0.0,
            maxValid = 16.0,
            decode = { bytes ->
                if ((bytes[0] and 0x02) != 0) ((bytes[2] * 256) + bytes[3]) / 1000.0 else null
            }
        )
    ).associateBy { it.pidCommand }

    private val unsupportedOrErrorTokens = listOf(
        "NO DATA",
        "UNABLE TO CONNECT",
        "BUS INIT",
        "CAN ERROR",
        "ERROR",
        "STOPPED",
        "BUFFER FULL",
        "FB ERROR",
        "DATA ERROR",
        "?"
    )

    fun knownPidCatalog(): Map<String, PidDefinition> = pidCatalog

    /**
     * Parses Mode 01 supported PID bitmask commands (`0100`, `0120`, `0140`, etc.).
     * Returns set of normalized PID commands e.g. `0105`, `010C`.
     */
    fun parseSupportedPidBitmask(baseCommand: String, rawResponse: String): Set<String> {
        val cleanCmd = normalizeCommand(baseCommand)
        if (!cleanCmd.startsWith("01") || cleanCmd.length != 4) return emptySet()
        val baseOffset = cleanCmd.substring(2, 4).toIntOrNull(16) ?: return emptySet()

        if (isUnsupportedOrErrorResponse(rawResponse)) return emptySet()

        val bytes = extractHexBytes(rawResponse, expectedCommandEcho = cleanCmd)
        val headerIndex = findSequenceIndex(bytes, 0x41, baseOffset)
        if (headerIndex < 0 || headerIndex + 5 >= bytes.size) return emptySet()

        val maskBytes = bytes.subList(headerIndex + 2, headerIndex + 6)
        val supported = linkedSetOf<String>()
        maskBytes.forEachIndexed { byteIdx, byteVal ->
            for (bit in 7 downTo 0) {
                if ((byteVal and (1 shl bit)) != 0) {
                    val pidNum = baseOffset + (byteIdx * 8) + (8 - bit)
                    supported += "01" + pidNum.toString(16).uppercase().padStart(2, '0')
                }
            }
        }
        return supported
    }

    /**
     * Parses SAE J1979 Mode 01 PID 01 (`0101` -> `41 01 A B C D`) into [ObdMonitorStatus]:
     * - Byte A bit 7: MIL (Check Engine Light) ON/OFF; bits 6..0: confirmed DTC count
     * - Byte B bits 0..2 / 4..6: Continuous monitors (Misfire, Fuel System, Comprehensive Component)
     * - Bytes C & D: Non-continuous Readiness Monitors (Catalyst, EVAP, O2 Sensor, EGR/VVT, etc.)
     * Returns null when unsupported (`NO DATA`) or malformed.
     */
    fun parseMonitorStatusPid01(rawResponse: String): ObdMonitorStatus? {
        val cleaned = cleanRawText(rawResponse)
        if (cleaned.isBlank() || isUnsupportedOrErrorResponse(cleaned)) return null

        val bytes = extractHexBytes(cleaned, expectedCommandEcho = "0101")
        val headerIdx = findSequenceIndex(bytes, 0x41, 0x01)
        if (headerIdx < 0 || headerIdx + 5 >= bytes.size) return null

        val byteA = bytes[headerIdx + 2]
        val byteB = bytes[headerIdx + 3]
        val byteC = bytes[headerIdx + 4]
        val byteD = bytes[headerIdx + 5]

        val milOn = (byteA and 0x80) != 0
        val dtcCount = byteA and 0x7F
        val isCompressionIgnition = (byteB and 0x08) != 0

        var supportedCount = 0
        var readyCount = 0
        val incompleteNames = mutableListOf<String>()

        // Continuous monitors in Byte B (bits 0..2 supported, bits 4..6 incomplete)
        val continuousMonitors = listOf(
            Triple(0x01, 0x10, "Misfire"),
            Triple(0x02, 0x20, "Fuel System"),
            Triple(0x04, 0x40, "Comprehensive Components")
        )
        for ((supMask, incMask, label) in continuousMonitors) {
            if ((byteB and supMask) != 0) {
                supportedCount++
                if ((byteB and incMask) == 0) {
                    readyCount++
                } else {
                    incompleteNames += label
                }
            }
        }

        // Non-continuous monitors in Bytes C (supported) & D (incomplete)
        val nonContinuousMonitors = if (isCompressionIgnition) {
            listOf(
                0 to "NMHC Catalyst",
                1 to "NOx/SCR Aftertreatment",
                3 to "Boost Pressure",
                5 to "Exhaust Gas Sensor",
                6 to "PM Filter",
                7 to "EGR / VVT System"
            )
        } else {
            listOf(
                0 to "Catalyst",
                1 to "Heated Catalyst",
                2 to "Evaporative System",
                3 to "Secondary Air System",
                4 to "A/C Refrigerant",
                5 to "Oxygen Sensor",
                6 to "Oxygen Sensor Heater",
                7 to "EGR / VVT System"
            )
        }

        for ((bitIdx, label) in nonContinuousMonitors) {
            val mask = 1 shl bitIdx
            if ((byteC and mask) != 0) {
                supportedCount++
                if ((byteD and mask) == 0) {
                    readyCount++
                } else {
                    incompleteNames += label
                }
            }
        }

        return ObdMonitorStatus(
            milOn = milOn,
            confirmedDtcCountFromPid01 = dtcCount,
            supportedMonitorsCount = supportedCount,
            readyMonitorsCount = readyCount,
            incompleteMonitorNames = incompleteNames,
            rawHex = rawResponse
        )
    }

    /**
     * Parses Mode 03 (Confirmed) or Mode 07 (Pending) DTC responses into [DiagnosticTroubleCode].
     * Handles CAN multi-DTC frames (e.g. `43 02 01 71 03 00`), legacy frames (`43 01 71 03 00`),
     * and multi-line ECU responses cleanly.
     */
    fun parseDtcResponse(
        rawResponse: String,
        status: DtcStatus,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): DtcParseResult {
        val normalizedRaw = cleanRawText(rawResponse)
        if (normalizedRaw.isBlank()) {
            return DtcParseResult(
                dtcs = emptyList(),
                rawResponse = rawResponse,
                errorNote = "Empty response received from adapter."
            )
        }
        if (isUnsupportedOrErrorResponse(normalizedRaw)) {
            val isNoData = normalizedRaw.contains("NO DATA", ignoreCase = true)
            return DtcParseResult(
                dtcs = emptyList(),
                rawResponse = rawResponse,
                isUnsupportedCommand = !isNoData && normalizedRaw.contains("?"),
                errorNote = if (isNoData) null else "Adapter reported: $normalizedRaw"
            )
        }

        val expectedHeaderByte = when (status) {
            DtcStatus.CONFIRMED -> 0x43
            DtcStatus.PENDING -> 0x47
            DtcStatus.PERMANENT_READ_ONLY -> 0x4A
            DtcStatus.UNKNOWN -> 0x43
        }
        val expectedEcho = when (status) {
            DtcStatus.CONFIRMED -> "03"
            DtcStatus.PENDING -> "07"
            DtcStatus.PERMANENT_READ_ONLY -> "0A"
            DtcStatus.UNKNOWN -> "03"
        }

        val parsedDtcs = mutableListOf<DiagnosticTroubleCode>()
        val lines = normalizedRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
        var foundValidHeader = false

        for (line in lines) {
            val bytes = extractHexBytes(line, expectedCommandEcho = expectedEcho)
            val idx = bytes.indexOf(expectedHeaderByte)
            if (idx < 0) continue
            foundValidHeader = true

            val payload = bytes.drop(idx + 1)
            if (payload.isEmpty()) continue

            // Under ISO 15765-4 CAN, byte[0] after 43/47 can be DTC count when payload length is odd.
            val dtcBytes = if (payload.size >= 3 && payload.size % 2 == 1 && payload[0] <= (payload.size - 1) / 2) {
                payload.drop(1)
            } else {
                payload
            }

            if (dtcBytes.size % 2 != 0) {
                return DtcParseResult(
                    dtcs = parsedDtcs,
                    rawResponse = rawResponse,
                    errorNote = "Malformed or truncated DTC byte stream in '$line'."
                )
            }

            var i = 0
            while (i + 1 < dtcBytes.size) {
                val high = dtcBytes[i]
                val low = dtcBytes[i + 1]
                if (high != 0 || low != 0) {
                    val code = decodeDtcPair(high, low)
                    parsedDtcs += DiagnosticTroubleCode(
                        code = code,
                        status = status,
                        rawHexFrame = line,
                        timestampEpochMs = timestampEpochMs
                    )
                }
                i += 2
            }
        }

        return if (!foundValidHeader) {
            DtcParseResult(
                dtcs = emptyList(),
                rawResponse = rawResponse,
                errorNote = "Response did not contain expected OBD DTC header (0x${expectedHeaderByte.toString(16).uppercase()})."
            )
        } else {
            DtcParseResult(
                dtcs = parsedDtcs.distinctBy { it.code },
                rawResponse = rawResponse,
                isUnsupportedCommand = false,
                errorNote = null
            )
        }
    }

    /**
     * Parses a Mode 01 live PID or Mode 02 Freeze Frame PID response.
     * Any unsupported command, timeout/error, truncated payload, or out-of-range reading returns
     * `value = null` with an accurate [PidAvailability].
     */
    fun parsePidResponse(
        command: String,
        rawResponse: String,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): ObdPidReading {
        val cleanCmd = normalizeCommand(command)
        val pidDef = pidCatalog[cleanCmd]
            ?: pidCatalog["01" + cleanCmd.drop(2)]
        val pidName = pidDef?.name ?: "OBD PID $cleanCmd"
        val pidUnit = pidDef?.unit.orEmpty()

        val cleaned = cleanRawText(rawResponse)
        if (cleaned.isBlank()) {
            return ObdPidReading(
                pidCode = cleanCmd,
                name = pidName,
                value = null,
                unit = pidUnit,
                availability = PidAvailability.MALFORMED_OR_TIMEOUT,
                rawHex = rawResponse,
                timestampEpochMs = timestampEpochMs,
                validationNote = "Empty or timed-out response."
            )
        }

        if (isUnsupportedOrErrorResponse(cleaned)) {
            val unsupported = cleaned.contains("NO DATA", ignoreCase = true) || cleaned.contains("?")
            return ObdPidReading(
                pidCode = cleanCmd,
                name = pidName,
                value = null,
                unit = pidUnit,
                availability = if (unsupported) {
                    PidAvailability.UNSUPPORTED_BY_VEHICLE
                } else {
                    PidAvailability.MALFORMED_OR_TIMEOUT
                },
                rawHex = rawResponse,
                timestampEpochMs = timestampEpochMs,
                validationNote = "Adapter reported '$cleaned'."
            )
        }

        if (pidDef == null || cleanCmd.length !in listOf(4, 6)) {
            return ObdPidReading(
                pidCode = cleanCmd,
                name = pidName,
                value = null,
                unit = pidUnit,
                availability = PidAvailability.UNSUPPORTED_BY_VEHICLE,
                rawHex = rawResponse,
                timestampEpochMs = timestampEpochMs,
                validationNote = "PID $cleanCmd is outside supported read-only decoder catalog."
            )
        }

        val modeByte = cleanCmd.substring(0, 2).toIntOrNull(16) ?: return malformedReading(
            cleanCmd, pidName, pidUnit, rawResponse, timestampEpochMs, "Invalid mode byte."
        )
        val pidByte = cleanCmd.substring(2, 4).toIntOrNull(16) ?: return malformedReading(
            cleanCmd, pidName, pidUnit, rawResponse, timestampEpochMs, "Invalid PID byte."
        )
        val expectedResponseMode = modeByte + 0x40
        val expectedFramePrefixBytes = if (modeByte == 0x02) 3 else 2

        val bytes = extractHexBytes(cleaned, expectedCommandEcho = cleanCmd)
        val headerIdx = findSequenceIndex(bytes, expectedResponseMode, pidByte)
        if (headerIdx < 0) {
            return malformedReading(
                cleanCmd,
                pidName,
                pidUnit,
                rawResponse,
                timestampEpochMs,
                "Missing expected response header ${expectedResponseMode.toString(16).uppercase()} ${pidByte.toString(16).uppercase()}."
            )
        }

        val dataBytes = bytes.drop(headerIdx + expectedFramePrefixBytes)
        if (dataBytes.size < pidDef.expectedDataBytes) {
            return malformedReading(
                cleanCmd,
                pidName,
                pidUnit,
                rawResponse,
                timestampEpochMs,
                "Incomplete PID payload: expected ${pidDef.expectedDataBytes} byte(s), got ${dataBytes.size}."
            )
        }

        val decoded = pidDef.decode(dataBytes)
        if (decoded == null || decoded.isNaN() || decoded < pidDef.minValid || decoded > pidDef.maxValid) {
            return malformedReading(
                cleanCmd,
                pidName,
                pidUnit,
                rawResponse,
                timestampEpochMs,
                "Decoded value ($decoded $pidUnit) outside valid range [${pidDef.minValid}..${pidDef.maxValid}]."
            )
        }

        return ObdPidReading(
            pidCode = cleanCmd,
            name = pidDef.name,
            value = decoded,
            unit = pidDef.unit,
            availability = PidAvailability.AVAILABLE,
            rawHex = rawResponse,
            timestampEpochMs = timestampEpochMs,
            validationNote = null
        )
    }

    private fun malformedReading(
        pidCode: String,
        name: String,
        unit: String,
        rawResponse: String,
        timestampEpochMs: Long,
        reason: String
    ): ObdPidReading = ObdPidReading(
        pidCode = pidCode,
        name = name,
        value = null,
        unit = unit,
        availability = PidAvailability.MALFORMED_OR_TIMEOUT,
        rawHex = rawResponse,
        timestampEpochMs = timestampEpochMs,
        validationNote = reason
    )

    private fun decodeDtcPair(highByte: Int, lowByte: Int): String {
        val systemChar = when ((highByte ushr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val d1 = (highByte ushr 4) and 0x03
        val d2 = highByte and 0x0F
        val d3 = (lowByte ushr 4) and 0x0F
        val d4 = lowByte and 0x0F
        return buildString {
            append(systemChar)
            append(d1)
            append(d2.toString(16).uppercase())
            append(d3.toString(16).uppercase())
            append(d4.toString(16).uppercase())
        }
    }

    private fun normalizeCommand(command: String): String {
        return command.replace("\\s+".toRegex(), "").uppercase()
    }

    private fun cleanRawText(raw: String): String {
        return raw.replace("\r", "\n")
            .replace(">", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("SEARCHING...", ignoreCase = true) }
            .joinToString("\n")
    }

    private fun isUnsupportedOrErrorResponse(text: String): Boolean {
        val upper = text.uppercase()
        return unsupportedOrErrorTokens.any { token ->
            if (token == "?") upper.trim() == "?" || upper.lines().any { it.trim() == "?" }
            else upper.contains(token)
        }
    }

    private fun extractHexBytes(line: String, expectedCommandEcho: String): List<Int> {
        val normalizedLine = line.uppercase().trim()
        val compact = normalizedLine.replace(" ", "")
        val stripped = if (compact.startsWith(expectedCommandEcho) &&
            !compact.startsWith("41") &&
            !compact.startsWith("42") &&
            !compact.startsWith("43") &&
            !compact.startsWith("47") &&
            !compact.startsWith("4A")
        ) {
            compact.removePrefix(expectedCommandEcho)
        } else {
            normalizedLine
        }

        val tokens = if (stripped.contains(" ")) {
            stripped.split("\\s+".toRegex()).filter { it.isNotBlank() }
        } else {
            stripped.chunked(2)
        }

        val result = mutableListOf<Int>()
        for (token in tokens) {
            if (token.length == 2 && token.all { it in '0'..'9' || it in 'A'..'F' }) {
                result += token.toInt(16)
            } else if (token.length > 2 && token.all { it in '0'..'9' || it in 'A'..'F' }) {
                token.chunked(2).forEach { pair ->
                    if (pair.length == 2) result += pair.toInt(16)
                }
            }
        }
        return result
    }

    private fun findSequenceIndex(bytes: List<Int>, first: Int, second: Int): Int {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == first && bytes[i + 1] == second) return i
        }
        return -1
    }
}
