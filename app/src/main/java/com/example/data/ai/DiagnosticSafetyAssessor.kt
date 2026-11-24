package com.example.data.ai

import com.example.data.obd.DtcInterpreter
import com.example.data.obd.DtcSystemCategory
import com.example.data.obd.ObdDiagnosticSession

/**
 * Deterministic Safety Assessment & Read-Only Guardrail Layer.
 *
 * Enforces safety invariants independently of LLM phrasing:
 * 1. Safety-critical indicators (brake, steering, overheating, SRS, flashing misfire, loss of ECU comms)
 *    enforce URGENT guidance to stop driving when safe and contact a qualified professional.
 * 2. Never guarantees a vehicle is safe to drive based solely on an OBD scan.
 * 3. Never assigns a low/green-style "Information" status when evidence is insufficient.
 */
data class DiagnosticSafetyAssessment(
    val minimumUrgencyFloor: DiagnosticUrgencyLevel,
    val isSafetyCritical: Boolean,
    val requiresImmediateStopWhenSafe: Boolean,
    val triggeredSafetySignals: List<String>,
    val stopDrivingGuidance: List<String>,
    val driveabilityDisclaimer: String
)

object DiagnosticSafetyAssessor {

    const val MANDATORY_SCAN_SAFETY_DISCLAIMER =
        "An OBD-II scan reads electronic monitors only and cannot guarantee that mechanical systems (brakes, tires, steering, suspension, or fluid integrity) are safe to drive."

    private val criticalSymptomKeywords = listOf(
        "brake fail", "no brake", "soft brake", "spongy brake", "brake pedal",
        "steering lock", "hard to steer", "power steering", "lost steering",
        "overheat", "boiling", "steam", "smoke", "burning smell", "fire", "fuel leak",
        "stalling", "stalled", "flashing check engine", "blinking check engine",
        "เบรกไม่อยู่", "เบรกจม", "เบรกแตก", "พวงมาลัยล็อก", "พวงมาลัยหนัก", "ความร้อนขึ้น", "ฮีท", "ควันขึ้น", "ไฟเครื่องกระพริบ", "น้ำมันรั่ว", "ดับกลางทาง"
    )

    fun assess(
        session: ObdDiagnosticSession,
        evidenceAudit: DiagnosticEvidenceAudit
    ): DiagnosticSafetyAssessment {
        val signals = mutableListOf<String>()
        val stopGuidance = mutableListOf<String>()

        // 1. Check DTC categories & safety-sensitive codes
        session.allDtcs.forEach { dtc ->
            when {
                dtc.code == "P0117" || dtc.code == "P0118" || dtc.code == "P0217" -> {
                    signals += "Thermal / Engine Coolant alert (${dtc.code})"
                    stopGuidance += "If the temperature gauge rises into the red zone, steam appears, or a coolant warning lights up, pull over safely, shut off the engine, and let it cool completely before opening the hood."
                }
                dtc.code.startsWith("P030") -> {
                    signals += "Engine misfire detected (${dtc.code})"
                    stopGuidance += "If the Check Engine Light is flashing/blinking or the vehicle shakes severely with power loss, pull over safely and avoid driving to prevent catalytic converter overheating or sudden stall."
                }
                dtc.systemCategory == DtcSystemCategory.CHASSIS -> {
                    signals += "Chassis / Brake / Stability code (${dtc.code})"
                    stopGuidance += "If the brake warning light illuminates, pedal feel changes, or steering/stability feels abnormal, stop safely and arrange towing."
                }
                dtc.systemCategory == DtcSystemCategory.NETWORK -> {
                    signals += "ECU / CAN Bus communication loss (${dtc.code})"
                    stopGuidance += "If dashboard gauges drop out, the engine stalls, or transmission refuses to shift, stop safely and seek professional inspection."
                }
                dtc.code.startsWith("B00") -> {
                    signals += "Restraint / Airbag (SRS) circuit code (${dtc.code})"
                    stopGuidance += "Airbags or seat belt pretensioners may not deploy as intended in a collision; schedule inspection immediately."
                }
                DtcInterpreter.isCatalogSafetySensitiveCode(dtc.code) -> {
                    signals += "Safety-sensitive fault indicator (${dtc.code})"
                }
            }
        }

        // 2. Check available (non-null) live PID telemetry thresholds
        val coolant = session.availableReadings.firstOrNull { it.pidCode == "0105" }?.value
        if (coolant != null && coolant >= 112.0) {
            signals += "Elevated coolant temperature reading (${coolant.toInt()} °C)"
            stopGuidance += "Coolant temperature is dangerously high (${coolant.toInt()} °C). Stop in a safe location, turn off the engine, and do NOT open the pressurized coolant cap while hot."
        }

        val moduleVoltage = session.availableReadings.firstOrNull { it.pidCode == "0142" }?.value
        if (moduleVoltage != null && moduleVoltage < 11.4) {
            signals += "Critically low control module voltage (${String.format(java.util.Locale.US, "%.2f", moduleVoltage)} V)"
            stopGuidance += "Battery/charging voltage is critically low (${String.format(java.util.Locale.US, "%.2f", moduleVoltage)} V); electrical steering/braking or engine power may shut down unexpectedly."
        }

        // 3. Check owner symptom notes for safety-critical keywords
        val symptomLower = session.ownerSymptoms.orEmpty().lowercase()
        val matchedKeywords = criticalSymptomKeywords.filter { symptomLower.contains(it) }
        if (matchedKeywords.isNotEmpty()) {
            signals += "Safety-critical symptom reported (${matchedKeywords.joinToString(", ")})"
            stopGuidance += "Reported symptoms (${matchedKeywords.first()}) involve critical driving safety. Pull over safely as soon as possible and contact a qualified mechanic or roadside assistance."
        }

        val isCritical = signals.isNotEmpty()
        val requiresImmediateStop = (coolant != null && coolant >= 112.0) ||
            matchedKeywords.isNotEmpty() ||
            session.allDtcs.any {
                it.systemCategory == DtcSystemCategory.CHASSIS ||
                    it.systemCategory == DtcSystemCategory.NETWORK ||
                    it.code == "P0217"
            }

        val minFloor = when {
            requiresImmediateStop -> DiagnosticUrgencyLevel.URGENT
            isCritical -> DiagnosticUrgencyLevel.SERVICE_RECOMMENDED
            !evidenceAudit.hasSufficientInformation -> DiagnosticUrgencyLevel.MONITOR
            session.allDtcs.isNotEmpty() -> DiagnosticUrgencyLevel.MONITOR
            else -> DiagnosticUrgencyLevel.INFORMATION
        }

        if (stopGuidance.isEmpty()) {
            stopGuidance += "Pull over safely and call a qualified mechanic if warning lights begin flashing, brakes or steering feel unusual, temperature rises, or you smell fuel/burning."
        }

        return DiagnosticSafetyAssessment(
            minimumUrgencyFloor = minFloor,
            isSafetyCritical = isCritical,
            requiresImmediateStopWhenSafe = requiresImmediateStop,
            triggeredSafetySignals = signals.distinct(),
            stopDrivingGuidance = stopGuidance.distinct(),
            driveabilityDisclaimer = MANDATORY_SCAN_SAFETY_DISCLAIMER
        )
    }
}
