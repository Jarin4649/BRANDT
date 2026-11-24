package com.example.data.ai

import com.example.data.obd.DataReliabilityLevel
import com.example.data.obd.DtcInterpretation
import com.example.data.obd.ObdDiagnosticSession

/**
 * Deterministic Evidence & Confidence Evaluation module.
 * Links every diagnostic finding strictly to observed DTCs, available (non-null) sensor telemetry,
 * known vehicle specs, and explicitly documents all gaps causing diagnostic uncertainty.
 */
data class DiagnosticEvidenceAudit(
    val evidenceBundle: SupportingEvidenceBundle,
    val missingInformation: List<String>,
    val reasonsForUncertainty: List<String>,
    val baselineConfidence: DiagnosticConfidenceLevel,
    val knowledgeSources: List<String>,
    val hasSufficientInformation: Boolean
)

object DiagnosticEvidenceEvaluator {

    fun evaluate(
        session: ObdDiagnosticSession,
        interpretations: List<DtcInterpretation>
    ): DiagnosticEvidenceAudit {
        val dtcList = session.allDtcs.map { "${it.code} (${it.status.displayLabel})" }

        val availableSensors = session.availableReadings.map { reading ->
            "${reading.name} [${reading.pidCode}]: ${reading.formattedValueOrUnavailable()}"
        }
        val freezeFrameSensors = session.freezeFrames.flatMap { ff ->
            ff.readings.filter { it.isAvailable }.map { reading ->
                "Freeze Frame (${ff.associatedDtcCode ?: "Snapshot"}) ${reading.name}: ${reading.formattedValueOrUnavailable()}"
            }
        }
        val sensorValuesUsed = (availableSensors + freezeFrameSensors).distinct()

        val vehicleSpecsUsed = buildList {
            val v = session.vehicle
            add("Vehicle: ${v.displayTitle()}")
            v.engineType?.takeIf { !it.equals("Unknown / Unavailable", true) }?.let { add("Engine: $it") }
            v.fuelType?.takeIf { !it.equals("Unknown / Unavailable", true) }?.let { add("Fuel: $it") }
            v.transmission?.takeIf { !it.equals("Unknown / Unavailable", true) }?.let { add("Transmission: $it") }
            v.mileage?.let { add("Mileage: $it ${v.mileageUnit.orEmpty()}".trim()) }
            v.market?.let { add("Market: $it") }
        }

        val missingInfo = linkedSetOf<String>()
        val uncertaintyReasons = linkedSetOf<String>()

        if (session.allDtcs.isEmpty() && availableSensors.isEmpty()) {
            missingInfo += "No OBD-II trouble codes or live sensor readings were captured."
            uncertaintyReasons += "Session contains neither trouble codes nor live ECU telemetry."
        }
        if (session.allDtcs.isNotEmpty() && session.freezeFrames.isEmpty()) {
            missingInfo += "Freeze Frame snapshot (driving conditions when the fault occurred) is unavailable."
            uncertaintyReasons += "Without Freeze Frame data, engine load, speed, and temperature at fault onset are unknown."
        }
        val unavailableLivePids = session.livePidReadings.filterNot { it.isAvailable }
        if (unavailableLivePids.isNotEmpty()) {
            val pidNames = unavailableLivePids.take(4).joinToString(", ") { "${it.name} (${it.pidCode})" }
            missingInfo += "Unavailable or unsupported sensor PIDs: $pidNames."
        }
        if (availableSensors.isEmpty() && session.allDtcs.isNotEmpty()) {
            missingInfo += "Live Mode 01 sensor telemetry (coolant temp, fuel trims, RPM, battery voltage)."
            uncertaintyReasons += "DTCs alone show a tripped monitor threshold, not live operating values or mechanical root cause."
        }
        if (session.ownerSymptoms.isNullOrBlank()) {
            missingInfo += "Owner symptom observations (noises, dashboard warnings, hesitation, smells, or drivability changes)."
            uncertaintyReasons += "Physical driving symptoms were not provided to correlate with electronic data."
        }
        val missingVehicleFields = session.vehicle.missingSpecificationFields()
        if (missingVehicleFields.isNotEmpty()) {
            missingInfo += "Incomplete vehicle profile: ${missingVehicleFields.joinToString(", ")}."
            uncertaintyReasons += "Engine-, transmission-, or market-specific service bulletins cannot be narrowed accurately without full specs."
        }
        if (interpretations.any { it.isManufacturerSpecificCode }) {
            missingInfo += "Manufacturer-specific OEM diagnostic flowchart for ${session.vehicle.displayTitle()}."
            uncertaintyReasons += "Manufacturer-specific DTC criteria differ across model years, engines, and regional markets."
        }
        if (interpretations.any { !it.isValidFormat }) {
            missingInfo += "Corrected 5-character OBD-II code format."
            uncertaintyReasons += "One or more entered codes failed SAE J2012 format validation."
        }
        if (session.sourceMetadata.reliabilityLevel == DataReliabilityLevel.LOW_MANUAL_OR_UNVERIFIED ||
            session.sourceMetadata.reliabilityLevel == DataReliabilityLevel.UNVERIFIED_INSUFFICIENT
        ) {
            uncertaintyReasons += "Source is ${session.sourceMetadata.reliabilityLevel.displayLabel}; direct ECU verification is still needed."
        }
        if (session.allDtcs.isNotEmpty()) {
            uncertaintyReasons += "An OBD-II code never proves the named part has failed without physical wiring, mechanical, and sensor verification."
        }

        val sources = linkedSetOf(
            "SAE J2012 / ISO 15031-6 Standard DTC Definitions",
            "SAE J1979 OBD-II Diagnostic Test Modes (Read-Only)"
        )
        interpretations.forEach { sources += it.knowledgeSources }

        val hasDtcOrPid = session.allDtcs.isNotEmpty() || availableSensors.isNotEmpty()
        val hasSufficientInfo = hasDtcOrPid &&
            availableSensors.isNotEmpty() &&
            missingVehicleFields.size <= 2 &&
            interpretations.none { !it.isValidFormat }

        val confidence = when {
            !hasDtcOrPid || interpretations.any { !it.isValidFormat } -> DiagnosticConfidenceLevel.INSUFFICIENT
            session.sourceMetadata.reliabilityLevel == DataReliabilityLevel.HIGH_VERIFIED_HARDWARE &&
                availableSensors.size >= 4 &&
                missingVehicleFields.isEmpty() &&
                !session.ownerSymptoms.isNullOrBlank() -> DiagnosticConfidenceLevel.HIGH
            availableSensors.isNotEmpty() || (!session.ownerSymptoms.isNullOrBlank() && session.allDtcs.isNotEmpty()) ->
                DiagnosticConfidenceLevel.MODERATE
            else -> DiagnosticConfidenceLevel.LOW
        }

        return DiagnosticEvidenceAudit(
            evidenceBundle = SupportingEvidenceBundle(
                diagnosticCodesUsed = dtcList.ifEmpty { listOf("None captured") },
                sensorValuesUsed = sensorValuesUsed.ifEmpty { listOf("No live sensor telemetry available") },
                vehicleInformationUsed = vehicleSpecsUsed
            ),
            missingInformation = missingInfo.toList(),
            reasonsForUncertainty = uncertaintyReasons.toList(),
            baselineConfidence = confidence,
            knowledgeSources = sources.toList(),
            hasSufficientInformation = hasSufficientInfo
        )
    }
}
