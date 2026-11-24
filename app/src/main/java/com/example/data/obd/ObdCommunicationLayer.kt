package com.example.data.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Transport-agnostic OBD-II stream contract.
 * USB-to-serial and Bluetooth SPP/BLE implementations reside separately in Phase 7 drivers
 * and inject an [ObdTransportStream] here without coupling transport logic to OBD parsing.
 */
interface ObdTransportStream {
    val adapterLabel: String
    val sourceType: ObdDataSourceType
    val method: ObdConnectionMethod?
    val bluetoothArchitecture: BluetoothArchitecture?
    val isConnected: Boolean

    suspend fun exchangeCommand(command: String, timeoutMs: Long): Result<String>
    suspend fun close()
}

data class AdapterCompatibilityReport(
    val isCompatible: Boolean,
    val adapterFirmwareLabel: String?,
    val batteryVoltageVolts: Double?,
    val protocolLabel: String?,
    val supportedPidCount: Int,
    val diagnosticNotes: List<String>
)

/**
 * Enforces BRANDT's strict READ-ONLY diagnostic policy.
 * Rejects DTC clearing (Mode 04), actuator/test control (Mode 08), ECU resets (UDS 0x11),
 * programming/writing (0x2E, 0x2F, 0x31, 0x34-0x37), and unrecognized non-read commands.
 */
object ReadOnlyObdCommandGuard {

    private val allowedAtCommands = setOf(
        "ATE0", "ATL0", "ATS0", "ATH0", "ATH1",
        "ATI", "ATRV", "ATDP", "ATDPN",
        "ATSP0", "ATSP1", "ATSP2", "ATSP3", "ATSP4", "ATSP5",
        "ATSP6", "ATSP7", "ATSP8", "ATSP9"
    )

    private val allowedObdReadModes = setOf("01", "02", "03", "07", "09", "0A")

    fun normalize(command: String): String = command.replace("\\s+".toRegex(), "").uppercase()

    fun isCommandAllowed(rawCommand: String): Boolean {
        val cmd = normalize(rawCommand)
        if (cmd.isEmpty()) return false
        if (cmd.startsWith("AT")) {
            return cmd in allowedAtCommands
        }
        if (cmd.length < 2 || !cmd.all { it in '0'..'9' || it in 'A'..'F' }) {
            return false
        }
        val mode = cmd.substring(0, 2)
        if (mode !in allowedObdReadModes) return false
        return when (mode) {
            "03", "07", "0A" -> cmd.length == 2
            "01", "09" -> cmd.length == 4
            "02" -> cmd.length == 6
            else -> false
        }
    }

    fun requireReadOnly(rawCommand: String): Result<String> {
        val cmd = normalize(rawCommand)
        if (!isCommandAllowed(cmd)) {
            return Result.failure(
                SecurityException(
                    "Blocked non-read-only or unverified command '$cmd'. BRANDT permits read-only diagnostics only (never clears DTCs, resets ECUs, flashes, or triggers actuators)."
                )
            )
        }
        return Result.success(cmd)
    }
}

/**
 * Shared Read-Only OBD Communication Layer.
 * Coordinates AT initialization, adapter compatibility probes, timeout management,
 * invalid/empty response handling, Mode 01/02/03/07 queries, and session assembly.
 */
class ReadOnlyObdClient(
    private val defaultCommandTimeoutMs: Long = 3_500L
) {
    companion object {
        val DEFAULT_LIVE_PIDS = listOf(
            "0105", // Coolant Temp
            "010C", // RPM
            "010D", // Speed
            "0104", // Engine Load
            "0106", // STFT Bank 1
            "0107", // LTFT Bank 1
            "010F", // Intake Air Temp
            "0110", // MAF
            "0111", // Throttle Position
            "0142"  // Control Module Voltage
        )

        val OVERVIEW_TELEMETRY_PIDS = listOf(
            "010D", // Vehicle Speed
            "010C", // Engine RPM
            "0143", // Absolute Load Value
            "0111", // Throttle Position
            "0162", // Actual Engine Torque %
            "0163", // Engine Reference Torque Nm
            "0104", // Calculated Engine Load
            "0105", // Coolant Temperature
            "010F", // Intake Air Temperature
            "015C", // Engine Oil Temperature
            "013C", // Catalyst / Exhaust Gas Temperature
            "0110", // MAF
            "010B", // MAP
            "0133", // Barometric Pressure
            "0106", // Short-term Fuel Trim
            "0107", // Long-term Fuel Trim
            "0114", // Oxygen Sensor
            "010A", // Fuel Pressure
            "0123", // Fuel Rail Gauge Pressure
            "01A4", // Transmission Actual Gear
            "0142"  // Control Module Voltage
        )
    }

    suspend fun executeReadOnlyCommand(
        transport: ObdTransportStream,
        command: String,
        timeoutMs: Long = defaultCommandTimeoutMs
    ): Result<String> {
        val allowed = ReadOnlyObdCommandGuard.requireReadOnly(command)
            .getOrElse { return Result.failure(it) }

        if (!transport.isConnected) {
            return Result.failure(IllegalStateException("OBD-II transport is disconnected."))
        }

        return try {
            withTimeout(timeoutMs) {
                transport.exchangeCommand(allowed, timeoutMs)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Command '$allowed' timed out after ${timeoutMs}ms."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkDeviceCompatibility(
        transport: ObdTransportStream,
        preferredProtocol: ObdProtocolPreference = ObdProtocolPreference.AUTO
    ): Result<AdapterCompatibilityReport> {
        if (!transport.isConnected) {
            return Result.failure(IllegalStateException("Connect an OBD-II adapter before checking compatibility."))
        }

        val notes = mutableListOf<String>()
        executeReadOnlyCommand(transport, "ATE0")
        executeReadOnlyCommand(transport, "ATL0")
        executeReadOnlyCommand(transport, "ATS0")
        executeReadOnlyCommand(transport, preferredProtocol.atCommandCode)

        val atiResult = executeReadOnlyCommand(transport, "ATI")
        val firmwareLabel = atiResult.getOrNull()?.trim()?.takeIf { it.isNotBlank() && !it.contains("?") }
        if (firmwareLabel == null) {
            notes += "Adapter did not identify ELM327-compatible firmware via ATI."
        }

        val voltageRaw = executeReadOnlyCommand(transport, "ATRV").getOrNull().orEmpty()
        val parsedVoltage = Regex("(\\d+(?:\\.\\d+)?)").find(voltageRaw)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        val protocolRaw = executeReadOnlyCommand(transport, "ATDP").getOrNull()?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("?") && !it.contains("ERROR", ignoreCase = true) }

        val pid00Raw = executeReadOnlyCommand(transport, "0100").getOrNull().orEmpty()
        val supported = Elm327Parser.parseSupportedPidBitmask("0100", pid00Raw)
        if (supported.isEmpty()) {
            notes += "Vehicle ECU did not confirm Mode 01 supported PIDs (0100) yet. Check ignition ON (KOEO/KOER) or bus protocol."
        }

        val compatible = firmwareLabel != null && supported.isNotEmpty()
        return Result.success(
            AdapterCompatibilityReport(
                isCompatible = compatible,
                adapterFirmwareLabel = firmwareLabel,
                batteryVoltageVolts = parsedVoltage,
                protocolLabel = protocolRaw,
                supportedPidCount = supported.size,
                diagnosticNotes = notes
            )
        )
    }

    suspend fun runReadOnlyDiagnosticScan(
        transport: ObdTransportStream,
        vehicle: ObdVehicleSnapshot,
        preferredProtocol: ObdProtocolPreference = ObdProtocolPreference.AUTO,
        requestedPids: List<String> = DEFAULT_LIVE_PIDS,
        ownerSymptoms: String? = null,
        sessionId: String = "scan_${UUID.randomUUID()}",
        nowMs: Long = System.currentTimeMillis()
    ): Result<ObdDiagnosticSession> {
        if (!transport.isConnected) {
            return Result.failure(IllegalStateException("Cannot run scan: OBD-II transport is not connected."))
        }

        val traces = mutableListOf<ObdRawTraceEntry>()
        val unsupportedCommands = mutableListOf<String>()
        val reliabilityNotes = mutableListOf<String>()

        // Safe read-only adapter setup
        listOf("ATE0", "ATL0", "ATS0", preferredProtocol.atCommandCode).forEach { cmd ->
            val resp = executeReadOnlyCommand(transport, cmd)
            traces += ObdRawTraceEntry(
                command = cmd,
                rawResponse = resp.getOrElse { "ERROR: ${it.message}" },
                timestampEpochMs = nowMs,
                parsedSuccessfully = resp.isSuccess
            )
        }

        val detectedProtocol = executeReadOnlyCommand(transport, "ATDP").getOrNull()?.trim()
            ?.takeIf { it.isNotBlank() && !it.contains("?") }
            ?: preferredProtocol.displayName

        // Discover Mode 01 supported PID bitmasks (0100..01A0)
        val supportedPids = linkedSetOf<String>()
        val pid00Resp = executeReadOnlyCommand(transport, "0100")
        pid00Resp.onSuccess { raw ->
            val set00 = Elm327Parser.parseSupportedPidBitmask("0100", raw)
            supportedPids += set00
            traces += ObdRawTraceEntry("0100", raw, nowMs, set00.isNotEmpty())
        }.onFailure { err ->
            traces += ObdRawTraceEntry("0100", err.message ?: "Failure", nowMs, false)
            reliabilityNotes += "Mode 01 supported PID discovery (0100) failed: ${err.message}"
        }

        listOf("0120", "0140", "0160", "0180", "01A0").forEach { nextMask ->
            if (nextMask in supportedPids) {
                executeReadOnlyCommand(transport, nextMask).onSuccess { raw ->
                    val setNext = Elm327Parser.parseSupportedPidBitmask(nextMask, raw)
                    supportedPids += setNext
                    traces += ObdRawTraceEntry(nextMask, raw, nowMs, setNext.isNotEmpty())
                }
            }
        }

        // Mode 01 PID 01: MIL (Check Engine Light) & Readiness Monitors
        val monitorStatus: ObdMonitorStatus? = if (supportedPids.isEmpty() || "0101" in supportedPids) {
            executeReadOnlyCommand(transport, "0101").fold(
                onSuccess = { raw ->
                    val parsed = Elm327Parser.parseMonitorStatusPid01(raw)
                    traces += ObdRawTraceEntry("0101", raw, nowMs, parsed != null)
                    parsed
                },
                onFailure = { err ->
                    traces += ObdRawTraceEntry("0101", err.message ?: "Failure", nowMs, false)
                    null
                }
            )
        } else {
            null
        }

        // Mode 03: Confirmed DTCs
        val confirmedResult = executeReadOnlyCommand(transport, "03")
        val confirmedDtcs = confirmedResult.fold(
            onSuccess = { raw ->
                val parsed = Elm327Parser.parseDtcResponse(raw, DtcStatus.CONFIRMED, nowMs)
                traces += ObdRawTraceEntry("03", raw, nowMs, parsed.errorNote == null, parsed.errorNote)
                if (parsed.errorNote != null) reliabilityNotes += parsed.errorNote
                parsed.dtcs
            },
            onFailure = { err ->
                traces += ObdRawTraceEntry("03", err.message ?: "Failure", nowMs, false)
                reliabilityNotes += "Confirmed DTC read (Mode 03) failed: ${err.message}"
                emptyList()
            }
        )

        // Mode 07: Pending DTCs
        val pendingResult = executeReadOnlyCommand(transport, "07")
        val pendingDtcs = pendingResult.fold(
            onSuccess = { raw ->
                val parsed = Elm327Parser.parseDtcResponse(raw, DtcStatus.PENDING, nowMs)
                traces += ObdRawTraceEntry("07", raw, nowMs, parsed.errorNote == null, parsed.errorNote)
                if (parsed.isUnsupportedCommand) unsupportedCommands += "07"
                if (parsed.errorNote != null) reliabilityNotes += parsed.errorNote
                parsed.dtcs
            },
            onFailure = { err ->
                traces += ObdRawTraceEntry("07", err.message ?: "Failure", nowMs, false)
                reliabilityNotes += "Pending DTC read (Mode 07) failed: ${err.message}"
                emptyList()
            }
        )

        // Mode 01: Live PIDs (never fabricating 0 for unsupported PIDs)
        val liveReadings = mutableListOf<ObdPidReading>()
        val catalog = Elm327Parser.knownPidCatalog()
        for (pidCmd in requestedPids.map { ReadOnlyObdCommandGuard.normalize(it) }.distinct()) {
            val pidDef = catalog[pidCmd]
            if (supportedPids.isNotEmpty() && pidCmd !in supportedPids) {
                unsupportedCommands += pidCmd
                liveReadings += ObdPidReading(
                    pidCode = pidCmd,
                    name = pidDef?.name ?: "OBD PID $pidCmd",
                    value = null,
                    unit = pidDef?.unit.orEmpty(),
                    availability = PidAvailability.UNSUPPORTED_BY_VEHICLE,
                    rawHex = null,
                    timestampEpochMs = nowMs,
                    validationNote = "Not reported in ECU Mode 01 supported PID bitmask."
                )
                continue
            }

            val pidResp = executeReadOnlyCommand(transport, pidCmd)
            val reading = pidResp.fold(
                onSuccess = { raw ->
                    val parsed = Elm327Parser.parsePidResponse(pidCmd, raw, nowMs)
                    traces += ObdRawTraceEntry(pidCmd, raw, nowMs, parsed.isAvailable, parsed.validationNote)
                    if (parsed.availability == PidAvailability.UNSUPPORTED_BY_VEHICLE) {
                        unsupportedCommands += pidCmd
                    }
                    parsed
                },
                onFailure = { err ->
                    traces += ObdRawTraceEntry(pidCmd, err.message ?: "Timeout/Error", nowMs, false)
                    ObdPidReading(
                        pidCode = pidCmd,
                        name = pidDef?.name ?: "OBD PID $pidCmd",
                        value = null,
                        unit = pidDef?.unit.orEmpty(),
                        availability = PidAvailability.MALFORMED_OR_TIMEOUT,
                        rawHex = null,
                        timestampEpochMs = nowMs,
                        validationNote = err.message
                    )
                }
            )
            liveReadings += reading
        }

        // Note: Mode 01 PID 42 strictly represents ECU-reported Control Module Voltage.
        // We never substitute adapter pin ATRV into PID 42 so unsupported/failed ECU responses stay explicit.

        // Mode 02: Freeze Frame (frame 00) if confirmed DTCs exist
        val freezeFrames = mutableListOf<FreezeFrameSnapshot>()
        if (confirmedDtcs.isNotEmpty()) {
            val ffReadings = mutableListOf<ObdPidReading>()
            val ffRaw = mutableListOf<String>()
            listOf("020500", "020C00", "020D00", "020400", "020600", "020700").forEach { ffCmd ->
                executeReadOnlyCommand(transport, ffCmd).onSuccess { raw ->
                    val parsed = Elm327Parser.parsePidResponse(ffCmd, raw, nowMs)
                    if (parsed.isAvailable) {
                        ffReadings += parsed
                        ffRaw += raw
                    }
                    traces += ObdRawTraceEntry(ffCmd, raw, nowMs, parsed.isAvailable, parsed.validationNote)
                }
            }
            if (ffReadings.isNotEmpty()) {
                freezeFrames += FreezeFrameSnapshot(
                    associatedDtcCode = confirmedDtcs.firstOrNull()?.code,
                    timestampEpochMs = nowMs,
                    readings = ffReadings,
                    rawHexFrames = ffRaw
                )
            } else {
                reliabilityNotes += "No freeze frame telemetry was returned by the ECU."
            }
        }

        val availableCount = liveReadings.count { it.isAvailable }
        val reliabilityLevel = when {
            availableCount >= 4 && confirmedResult.isSuccess -> DataReliabilityLevel.HIGH_VERIFIED_HARDWARE
            confirmedResult.isSuccess || availableCount > 0 -> DataReliabilityLevel.MODERATE_PARTIAL_DATA
            else -> DataReliabilityLevel.UNVERIFIED_INSUFFICIENT
        }

        return Result.success(
            ObdDiagnosticSession(
                sessionId = sessionId,
                vehicle = vehicle,
                sourceMetadata = ObdSourceMetadata(
                    sourceType = transport.sourceType,
                    adapterOrFileLabel = transport.adapterLabel,
                    protocolLabel = detectedProtocol,
                    connectionMethod = transport.method,
                    bluetoothArchitecture = transport.bluetoothArchitecture,
                    readOnlyEnforced = true,
                    reliabilityLevel = reliabilityLevel,
                    reliabilityNotes = reliabilityNotes.distinct(),
                    capturedAtEpochMs = nowMs
                ),
                confirmedDtcs = confirmedDtcs,
                pendingDtcs = pendingDtcs,
                freezeFrames = freezeFrames,
                livePidReadings = liveReadings,
                supportedPids = supportedPids,
                unsupportedCommands = unsupportedCommands.distinct(),
                rawTraces = traces,
                ownerSymptoms = ownerSymptoms?.trim()?.takeIf { it.isNotBlank() },
                capturedAtEpochMs = nowMs,
                isSimulatedTestData = false,
                monitorStatus = monitorStatus
            )
        )
    }

    /**
     * Lightweight continuous read-only polling tick used by [ObdConnectionManager] while connected.
     * - Cycle 0 (or missing supported PIDs): discovers supported PIDs, protocol, DTCs (03/07), MIL/Readiness (0101), and overview PIDs.
     * - Fast cycles: streams real-time Mode 01 sensor PIDs every ~1s without unnecessary AT resets.
     * - Every 10th cycle: refreshes Mode 0101 / 03 / 07 fault status in the background.
     */
    suspend fun pollLiveTelemetryTick(
        transport: ObdTransportStream,
        vehicle: ObdVehicleSnapshot,
        preferredProtocol: ObdProtocolPreference = ObdProtocolPreference.AUTO,
        cachedSupportedPids: Set<String> = emptySet(),
        pollCycleIndex: Int = 0,
        previousSession: ObdDiagnosticSession? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Result<ObdDiagnosticSession> {
        if (!transport.isConnected) {
            return Result.failure(IllegalStateException("OBD-II transport disconnected."))
        }

        val isInitialOrPeriodicSync = pollCycleIndex == 0 || previousSession == null || cachedSupportedPids.isEmpty()
        val shouldRefreshDtcs = isInitialOrPeriodicSync || (pollCycleIndex % 10 == 0)

        var supportedPids = cachedSupportedPids.toMutableSet()
        var detectedProtocol = previousSession?.sourceMetadata?.protocolLabel
        val traces = mutableListOf<ObdRawTraceEntry>()

        if (isInitialOrPeriodicSync) {
            listOf("ATE0", "ATL0", "ATS0", preferredProtocol.atCommandCode).forEach { cmd ->
                executeReadOnlyCommand(transport, cmd, timeoutMs = 2_000L)
            }
            detectedProtocol = executeReadOnlyCommand(transport, "ATDP", timeoutMs = 2_000L)
                .getOrNull()?.trim()?.takeIf { it.isNotBlank() && !it.contains("?") }
                ?: preferredProtocol.displayName

            val pid00Resp = executeReadOnlyCommand(transport, "0100", timeoutMs = 2_800L)
            pid00Resp.onSuccess { raw ->
                val set00 = Elm327Parser.parseSupportedPidBitmask("0100", raw)
                supportedPids.addAll(set00)
                traces += ObdRawTraceEntry("0100", raw, nowMs, set00.isNotEmpty())
            }
            listOf("0120", "0140", "0160", "0180", "01A0").forEach { nextMask ->
                if (nextMask in supportedPids) {
                    executeReadOnlyCommand(transport, nextMask, timeoutMs = 2_500L).onSuccess { raw ->
                        val setNext = Elm327Parser.parseSupportedPidBitmask(nextMask, raw)
                        supportedPids.addAll(setNext)
                        traces += ObdRawTraceEntry(nextMask, raw, nowMs, setNext.isNotEmpty())
                    }
                }
            }
        }

        var monitorStatus = previousSession?.monitorStatus
        var confirmedDtcs = previousSession?.confirmedDtcs.orEmpty()
        var pendingDtcs = previousSession?.pendingDtcs.orEmpty()
        var confirmedReadSucceeded = previousSession != null

        if (shouldRefreshDtcs) {
            if (supportedPids.isEmpty() || "0101" in supportedPids) {
                executeReadOnlyCommand(transport, "0101", timeoutMs = 2_500L).onSuccess { raw ->
                    monitorStatus = Elm327Parser.parseMonitorStatusPid01(raw)
                    traces += ObdRawTraceEntry("0101", raw, nowMs, monitorStatus != null)
                }
            }
            val mode03 = executeReadOnlyCommand(transport, "03", timeoutMs = 2_800L)
            mode03.onSuccess { raw ->
                confirmedReadSucceeded = true
                val parsed = Elm327Parser.parseDtcResponse(raw, DtcStatus.CONFIRMED, nowMs)
                confirmedDtcs = parsed.dtcs
                traces += ObdRawTraceEntry("03", raw, nowMs, parsed.errorNote == null, parsed.errorNote)
            }
            val mode07 = executeReadOnlyCommand(transport, "07", timeoutMs = 2_500L)
            mode07.onSuccess { raw ->
                val parsed = Elm327Parser.parseDtcResponse(raw, DtcStatus.PENDING, nowMs)
                pendingDtcs = parsed.dtcs
                traces += ObdRawTraceEntry("07", raw, nowMs, parsed.errorNote == null, parsed.errorNote)
            }
        }

        val fastCorePids = listOf(
            "010C", // RPM
            "010D", // Speed
            "0104", // Engine Load
            "0111", // Throttle
            "0105", // Coolant Temp
            "010F", // Intake Air Temp
            "0110", // MAF
            "010B", // MAP
            "0106", // STFT
            "0107", // LTFT
            "0142", // Control Module Voltage
            "0143", // Absolute Load
            "015C"  // Engine Oil Temp
        )

        val candidatePids = when {
            isInitialOrPeriodicSync && supportedPids.isNotEmpty() ->
                OVERVIEW_TELEMETRY_PIDS.filter { it in supportedPids }
            supportedPids.isNotEmpty() ->
                fastCorePids.filter { it in supportedPids }
            else ->
                fastCorePids.take(6)
        }

        val mergedReadings = linkedMapOf<String, ObdPidReading>()
        previousSession?.livePidReadings?.forEach { prev ->
            mergedReadings[prev.pidCode.uppercase()] = prev
        }
        if (isInitialOrPeriodicSync && supportedPids.isNotEmpty()) {
            val catalog = Elm327Parser.knownPidCatalog()
            OVERVIEW_TELEMETRY_PIDS.filterNot { it in supportedPids }.forEach { unsupportedPid ->
                val def = catalog[unsupportedPid]
                mergedReadings[unsupportedPid] = ObdPidReading(
                    pidCode = unsupportedPid,
                    name = def?.name ?: "OBD PID $unsupportedPid",
                    value = null,
                    unit = def?.unit.orEmpty(),
                    availability = PidAvailability.UNSUPPORTED_BY_VEHICLE,
                    rawHex = null,
                    timestampEpochMs = nowMs,
                    validationNote = "Not reported in ECU Mode 01 supported PID bitmask."
                )
            }
        }

        var transportSuccessCount = 0
        var transportFailureCount = 0
        var lastTransportError: Throwable? = null

        val catalog = Elm327Parser.knownPidCatalog()
        for (pidCmd in candidatePids) {
            if (!transport.isConnected) {
                return Result.failure(IllegalStateException("OBD-II transport disconnected during polling."))
            }
            val resp = executeReadOnlyCommand(transport, pidCmd, timeoutMs = 1_800L)
            resp.onSuccess { raw ->
                transportSuccessCount++
                val parsed = Elm327Parser.parsePidResponse(pidCmd, raw, nowMs)
                mergedReadings[pidCmd] = parsed
                traces += ObdRawTraceEntry(pidCmd, raw, nowMs, parsed.isAvailable, parsed.validationNote)
            }.onFailure { err ->
                transportFailureCount++
                lastTransportError = err
                val pidDef = catalog[pidCmd]
                mergedReadings[pidCmd] = ObdPidReading(
                    pidCode = pidCmd,
                    name = pidDef?.name ?: "OBD PID $pidCmd",
                    value = null,
                    unit = pidDef?.unit.orEmpty(),
                    availability = PidAvailability.MALFORMED_OR_TIMEOUT,
                    rawHex = null,
                    timestampEpochMs = nowMs,
                    validationNote = err.localizedMessage ?: "Command timed out or failed."
                )
                traces += ObdRawTraceEntry(pidCmd, err.message ?: "Timeout/Error", nowMs, false)
            }
        }

        if (candidatePids.isNotEmpty() && transportSuccessCount == 0 && transportFailureCount > 0 && traces.isEmpty()) {
            return Result.failure(
                lastTransportError ?: IllegalStateException("Lost communication with OBD-II adapter.")
            )
        }

        val liveReadings = mergedReadings.values.toList()
        val availableCount = liveReadings.count { it.isAvailable }
        val reliabilityLevel = when {
            availableCount >= 4 && confirmedReadSucceeded -> DataReliabilityLevel.HIGH_VERIFIED_HARDWARE
            confirmedReadSucceeded || availableCount > 0 -> DataReliabilityLevel.MODERATE_PARTIAL_DATA
            else -> DataReliabilityLevel.UNVERIFIED_INSUFFICIENT
        }

        return Result.success(
            ObdDiagnosticSession(
                sessionId = previousSession?.sessionId ?: "live_${UUID.randomUUID()}",
                vehicle = vehicle,
                sourceMetadata = ObdSourceMetadata(
                    sourceType = transport.sourceType,
                    adapterOrFileLabel = transport.adapterLabel,
                    protocolLabel = detectedProtocol ?: preferredProtocol.displayName,
                    connectionMethod = transport.method,
                    bluetoothArchitecture = transport.bluetoothArchitecture,
                    readOnlyEnforced = true,
                    reliabilityLevel = reliabilityLevel,
                    reliabilityNotes = emptyList(),
                    capturedAtEpochMs = nowMs
                ),
                confirmedDtcs = confirmedDtcs,
                pendingDtcs = pendingDtcs,
                freezeFrames = previousSession?.freezeFrames.orEmpty(),
                livePidReadings = liveReadings,
                supportedPids = supportedPids,
                unsupportedCommands = liveReadings
                    .filter { it.availability == PidAvailability.UNSUPPORTED_BY_VEHICLE }
                    .map { it.pidCode }
                    .distinct(),
                rawTraces = traces,
                ownerSymptoms = previousSession?.ownerSymptoms,
                capturedAtEpochMs = nowMs,
                isSimulatedTestData = false,
                monitorStatus = monitorStatus
            )
        )
    }
}
