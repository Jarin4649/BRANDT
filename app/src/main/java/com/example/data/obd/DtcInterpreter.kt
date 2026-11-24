package com.example.data.obd

/**
 * Standardized Diagnostic Trouble Code (DTC) Interpreter.
 *
 * Separation of concerns:
 * - Strictly interprets standardized SAE J2012 / ISO 15031-6 code definitions and automotive engineering context.
 * - Intentionally kept separate from AI-based diagnosis and everyday phrasing.
 * - Never assumes that a component named inside a DTC definition is proven defective.
 */

data class DtcInterpretation(
    val code: String,
    val isValidFormat: Boolean,
    val standardDefinition: String,
    val isManufacturerSpecificCode: Boolean,
    val systemCategory: DtcSystemCategory,
    val subsystemLabel: String,
    val technicalExplanation: String,
    val possibleCauses: List<String>,
    val additionalInformationRequired: List<String>,
    val vehicleSpecificConsiderations: List<String>,
    val componentFailureDisclaimer: String,
    val knowledgeSources: List<String>
)

private data class KnownDtcEntry(
    val definition: String,
    val subsystem: String,
    val technicalExplanation: String,
    val possibleCauses: List<String>,
    val requiredInfo: List<String>,
    val safetySensitive: Boolean = false
)

object DtcInterpreter {

    private const val STANDARD_DISCLAIMER =
        "A DTC indicates that a monitoring test observed parameters outside expected thresholds; it does NOT prove the named sensor or component itself has failed."

    private val verifiedCatalog: Map<String, KnownDtcEntry> = mapOf(
        "P0171" to KnownDtcEntry(
            definition = "System Too Lean (Bank 1)",
            subsystem = "Fuel & Air Metering",
            technicalExplanation = "Positive long-term/short-term fuel trim corrections on Bank 1 exceeded ECU lean threshold, indicating unmeasured air entering the intake or insufficient fuel delivery.",
            possibleCauses = listOf(
                "Unmetered vacuum or intake boot air leak downstream of MAF sensor",
                "Contaminated or skewed Mass Air Flow (MAF) or MAP sensor reading",
                "Weak fuel pump pressure, clogged fuel filter, or partially restricted fuel injectors",
                "Exhaust manifold or upstream O2 sensor leak drawing false oxygen",
                "Stuck-open PCV or EVAP purge valve"
            ),
            requiredInfo = listOf(
                "Short Term Fuel Trim (PID 0106) and Long Term Fuel Trim (PID 0107) at idle vs 2,500 RPM",
                "Mass Air Flow rate (PID 0110) and Freeze Frame engine load/RPM",
                "Smoke/vacuum leak check and measured fuel rail pressure"
            )
        ),
        "P0174" to KnownDtcEntry(
            definition = "System Too Lean (Bank 2)",
            subsystem = "Fuel & Air Metering (Bank 2)",
            technicalExplanation = "Fuel trim corrections on Bank 2 reached lean adaptive limits. On V6/V8 engines, co-occurrence with P0171 points toward a shared intake/fuel supply factor rather than a single-bank fault.",
            possibleCauses = listOf(
                "Shared intake duct leak, PCV hose crack, or MAF sensor contamination (especially if paired with P0171)",
                "Low fuel system rail pressure",
                "Bank 2 intake manifold gasket or exhaust leak upstream of sensor 1"
            ),
            requiredInfo = listOf(
                "Bank 1 vs Bank 2 STFT/LTFT comparison (PIDs 0106–0109)",
                "Presence of co-occurring P0171 and Freeze Frame conditions"
            )
        ),
        "P0300" to KnownDtcEntry(
            definition = "Random / Multiple Cylinder Misfire Detected",
            subsystem = "Ignition & Combustion Monitoring",
            technicalExplanation = "Crankshaft position sensor angular velocity monitoring detected combustion variations across multiple cylinders exceeding emissions and catalyst protection limits.",
            possibleCauses = listOf(
                "Worn spark plugs or degraded ignition coils across cylinders",
                "Vacuum leak or lean air-fuel condition",
                "Low fuel pressure or contaminated fuel",
                "Exhaust gas recirculation (EGR) flow issue or mechanical compression variance"
            ),
            requiredInfo = listOf(
                "Cylinder-specific misfire counters or co-occurring P0301–P0308 codes",
                "Freeze Frame RPM/load/coolant temp when misfire occurred",
                "Whether Check Engine Light was flashing (indicates catalyst-damaging misfire)"
            ),
            safetySensitive = true
        ),
        "P0301" to KnownDtcEntry(
            definition = "Cylinder 1 Misfire Detected",
            subsystem = "Ignition & Combustion (Cylinder 1)",
            technicalExplanation = "ECU crankshaft fluctuation tracking isolated misfire events to Cylinder 1 firing windows.",
            possibleCauses = listOf(
                "Spark plug fouling or excessive electrode gap on Cylinder 1",
                "Ignition coil or ignition wiring/connector fault",
                "Cylinder 1 fuel injector restriction or electrical fault",
                "Cylinder 1 compression/valve sealing issue"
            ),
            requiredInfo = listOf(
                "Fuel trims (PIDs 0106/0107) and Freeze Frame coolant temp",
                "Coil/plug swap test by a technician and compression check if persistent"
            ),
            safetySensitive = true
        ),
        "P0420" to KnownDtcEntry(
            definition = "Catalyst System Efficiency Below Threshold (Bank 1)",
            subsystem = "Exhaust Aftertreatment / Emissions",
            technicalExplanation = "Downstream O2 sensor (Bank 1 Sensor 2) waveform mirrors the upstream O2 sensor too closely, indicating reduced oxygen storage capacity in the catalytic converter—or exhaust/sensor measurement skew.",
            possibleCauses = listOf(
                "Aged or thermally damaged catalytic converter substrate (often secondary to past misfires or oil/coolant consumption)",
                "Exhaust leak between upstream and downstream O2 sensors",
                "Slow/biased upstream air-fuel or downstream O2 sensor"
            ),
            requiredInfo = listOf(
                "Upstream vs downstream O2 sensor voltage traces (PIDs 0114, 0115)",
                "Any concurrent misfire (P030x) or fuel trim (P0171/P0172) codes"
            )
        ),
        "P0128" to KnownDtcEntry(
            definition = "Coolant Thermostat (Coolant Temperature Below Thermostat Regulating Temperature)",
            subsystem = "Engine Cooling & Thermal Management",
            technicalExplanation = "Engine coolant temperature did not reach closed-loop regulating temperature within expected runtime and airflow integral limits.",
            possibleCauses = listOf(
                "Thermostat stuck partially open or opening too early",
                "Engine Coolant Temperature (ECT) sensor reading inaccurately low",
                "Low coolant level trapping air near temperature sensor"
            ),
            requiredInfo = listOf(
                "Cold-start vs warmed-up coolant temperature progression (PID 0105)",
                "Ambient intake air temperature (PID 010F) and cabin heater warmth"
            )
        ),
        "P0117" to KnownDtcEntry(
            definition = "Engine Coolant Temperature Sensor 1 Circuit Low",
            subsystem = "Engine Cooling Sensors",
            technicalExplanation = "ECT thermistor signal voltage dropped below calibrated minimum threshold (high temperature / short-to-ground condition). May indicate severe overheating OR a shorted sensor/harness.",
            possibleCauses = listOf(
                "Short to ground in ECT sensor signal wiring or damaged connector",
                "Failed Engine Coolant Temperature sensor",
                "Genuine severe engine coolant overheating condition"
            ),
            requiredInfo = listOf(
                "Actual physical coolant level/temperature verification (IR thermometer or visual check only when cool)",
                "Coolant temperature PID (0105) reading and dashboard temperature gauge behavior"
            ),
            safetySensitive = true
        ),
        "C0035" to KnownDtcEntry(
            definition = "Left Front Wheel Speed Sensor Circuit / Signal Fault",
            subsystem = "ABS / Anti-Lock Braking & Stability Control",
            technicalExplanation = "ABS control module detected missing, erratic, or implausible speed signal from the left front wheel speed sensor, which can disable ABS and traction/stability intervention.",
            possibleCauses = listOf(
                "Damaged, chafed, or corroded wheel speed sensor harness near steering knuckle",
                "Fouled or cracked tone ring / magnetic encoder bearing",
                "Faulty wheel speed sensor or hub bearing play"
            ),
            requiredInfo = listOf(
                "Whether ABS, traction control, or brake warning lights are illuminated",
                "Live wheel speed comparison across all four wheels (OEM-enhanced scan)"
            ),
            safetySensitive = true
        ),
        "U0100" to KnownDtcEntry(
            definition = "Lost Communication With ECM / PCM \"A\"",
            subsystem = "CAN Bus / Vehicle Communication Network",
            technicalExplanation = "One or more vehicle modules failed to receive expected periodic CAN bus messages from the Engine/Powertrain Control Module within timeout limits.",
            possibleCauses = listOf(
                "Low 12V battery voltage or poor ground/power supply to ECM",
                "Loose OBD/harness connector or CAN High/Low wiring fault",
                "Aftermarket accessory interfering with CAN bus or intermittent ECM relay"
            ),
            requiredInfo = listOf(
                "Battery/control module voltage (PID 0142) at rest and cranking",
                "Additional U-codes across other modules and whether engine cranks/stalls"
            ),
            safetySensitive = true
        )
    )

    fun interpret(
        dtc: DiagnosticTroubleCode,
        vehicle: ObdVehicleSnapshot? = null
    ): DtcInterpretation {
        val code = DiagnosticTroubleCode.normalize(dtc.code)
        val valid = DiagnosticTroubleCode.isValidDtcFormat(code)
        val category = DtcSystemCategory.fromDtcCode(code)

        if (!valid) {
            return DtcInterpretation(
                code = code,
                isValidFormat = false,
                standardDefinition = "Invalid / Unrecognized Trouble Code Format",
                isManufacturerSpecificCode = false,
                systemCategory = DtcSystemCategory.UNKNOWN,
                subsystemLabel = "Unknown",
                technicalExplanation = "'$code' does not match standard SAE J2012 5-character OBD-II code formatting ([P|C|B|U] + 4 hex digits).",
                possibleCauses = listOf("Typo in manual code entry or corrupted diagnostic text frame"),
                additionalInformationRequired = listOf("Verify exact 5-character code from scanner display or original report"),
                vehicleSpecificConsiderations = buildVehicleNotes(code, category, vehicle, isOemSpecific = false),
                componentFailureDisclaimer = STANDARD_DISCLAIMER,
                knowledgeSources = listOf("SAE J2012 / ISO 15031-6 DTC Format Specification")
            )
        }

        val isManufacturerSpecific = code[1] in setOf('1', '3')
        val known = verifiedCatalog[code]

        if (known != null) {
            return DtcInterpretation(
                code = code,
                isValidFormat = true,
                standardDefinition = known.definition,
                isManufacturerSpecificCode = isManufacturerSpecific,
                systemCategory = category,
                subsystemLabel = known.subsystem,
                technicalExplanation = known.technicalExplanation,
                possibleCauses = known.possibleCauses,
                additionalInformationRequired = known.requiredInfo,
                vehicleSpecificConsiderations = buildVehicleNotes(code, category, vehicle, isManufacturerSpecific),
                componentFailureDisclaimer = STANDARD_DISCLAIMER,
                knowledgeSources = listOf(
                    "SAE J2012 / ISO 15031-6 Standard DTC Definitions",
                    "OBD-II Generic Powertrain & Chassis Monitoring Principles"
                )
            )
        }

        val subsystem = inferSubsystemFromSaeStructure(code, category)
        val genericNote = if (isManufacturerSpecific) {
            "Code '$code' has digit '${code[1]}' indicating a manufacturer-specific (OEM-enhanced) definition. Exact fault criteria vary by automaker, model year, and engine family."
        } else {
            "Code '$code' is a standardized SAE J2012 ${category.displayName} code ($subsystem). Full manufacturer flowchart testing is needed to confirm the root cause."
        }

        return DtcInterpretation(
            code = code,
            isValidFormat = true,
            standardDefinition = if (isManufacturerSpecific) {
                "Manufacturer-Specific ${category.prefixChar}-Code ($subsystem)"
            } else {
                "Standard OBD-II ${category.prefixChar}-Code ($subsystem)"
            },
            isManufacturerSpecificCode = isManufacturerSpecific,
            systemCategory = category,
            subsystemLabel = subsystem,
            technicalExplanation = genericNote,
            possibleCauses = listOf(
                "Sensor, wiring harness, or connector fault within the $subsystem circuit",
                "Mechanical, vacuum, fluid, or actuator condition driving readings outside calibrated limits",
                if (isManufacturerSpecific) {
                    "OEM-specific subsystem condition — verify exact factory definition for ${vehicle?.displayTitle() ?: "this vehicle"}"
                } else {
                    "Upstream system fault triggering secondary monitor failure"
                }
            ),
            additionalInformationRequired = listOf(
                "OEM service manual definition specific to ${vehicle?.displayTitle() ?: "the exact vehicle make, model, year, and engine"}",
                "Freeze Frame snapshot and live PID values at the time the code set",
                "Physical inspection of related connectors, harnesses, fuses, and fluids"
            ),
            vehicleSpecificConsiderations = buildVehicleNotes(code, category, vehicle, isManufacturerSpecific),
            componentFailureDisclaimer = STANDARD_DISCLAIMER,
            knowledgeSources = listOf(
                "SAE J2012 / ISO 15031-6 Structural Code Taxonomy",
                "Manufacturer Factory Service Information (Required for OEM-specific verification)"
            )
        )
    }

    fun isCatalogSafetySensitiveCode(code: String): Boolean {
        val norm = DiagnosticTroubleCode.normalize(code)
        if (verifiedCatalog[norm]?.safetySensitive == true) return true
        val cat = DtcSystemCategory.fromDtcCode(norm)
        return cat == DtcSystemCategory.CHASSIS || cat == DtcSystemCategory.NETWORK
    }

    private fun inferSubsystemFromSaeStructure(code: String, category: DtcSystemCategory): String {
        val thirdChar = code.getOrNull(2) ?: return category.displayName
        return when (category) {
            DtcSystemCategory.POWERTRAIN -> when (thirdChar) {
                '0', '1', '2' -> "Fuel and Air Metering"
                '3' -> "Ignition System or Misfire"
                '4' -> "Auxiliary Emissions Controls"
                '5' -> "Vehicle Speed Control, Idle & Auxiliary Inputs"
                '6' -> "Computer and Output Circuits"
                '7', '8', '9' -> "Transmission / Driveline"
                'A', 'B', 'C' -> "Hybrid / Electric Propulsion"
                else -> "Powertrain Subsystem"
            }
            DtcSystemCategory.CHASSIS -> "Chassis, Braking, ABS, or Steering"
            DtcSystemCategory.BODY -> "Body, Restraints (SRS), or Interior Electronics"
            DtcSystemCategory.NETWORK -> "Vehicle CAN/LIN Communication Network"
            DtcSystemCategory.UNKNOWN -> "Unclassified Vehicle Subsystem"
        }
    }

    private fun buildVehicleNotes(
        code: String,
        category: DtcSystemCategory,
        vehicle: ObdVehicleSnapshot?,
        isOemSpecific: Boolean
    ): List<String> {
        if (vehicle == null) {
            return listOf("Vehicle profile was not supplied; generic SAE interpretation applies until make, model, year, and engine are confirmed.")
        }
        val notes = mutableListOf<String>()
        val label = vehicle.displayTitle()
        if (isOemSpecific) {
            notes += "Verify OEM definition for $code specifically on $label (${vehicle.market ?: "market unspecified"})."
        }
        val fuelLower = vehicle.fuelType.orEmpty().lowercase()
        val engineLower = vehicle.engineType.orEmpty().lowercase()
        if ((fuelLower.contains("hybrid") || fuelLower.contains("electric") || engineLower.contains("hybrid")) &&
            category == DtcSystemCategory.POWERTRAIN
        ) {
            notes += "Powertrain/hybrid architecture on $label may involve high-voltage or dual-cooling systems; only qualified technicians should service HV components."
        }
        if (fuelLower.contains("diesel") && (code.startsWith("P04") || code.startsWith("P20") || code.startsWith("P24"))) {
            notes += "Diesel emission aftertreatment (DPF/SCR/EGR) strategies vary significantly by model year and regional emissions standard."
        }
        if (engineLower.contains("turbo") || engineLower.contains("twin-turbo")) {
            notes += "Forced-induction ($engineLower) charge pipes, intercooler hoses, and diverter valves are additional check points for air/fuel codes."
        }
        val missing = vehicle.missingSpecificationFields()
        if (missing.isNotEmpty()) {
            notes += "Missing vehicle context (${missing.joinToString(", ")}) limits engine- and market-specific narrowing."
        }
        if (notes.isEmpty()) {
            notes += "Interpret '$code' alongside ${label}'s service schedule and current mileage (${vehicle.mileage?.toString() ?: "unknown"} ${vehicle.mileageUnit.orEmpty()})."
        }
        return notes
    }
}
