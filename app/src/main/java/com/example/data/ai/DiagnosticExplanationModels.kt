package com.example.data.ai

import com.example.data.obd.ObdDiagnosticSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured Everyday Explanation & Diagnostic Result models + strict JSON schema validator.
 * Field names match Section 8 (`Diagnostic Result Structure`) verbatim in serialized JSON.
 */

enum class DiagnosticUrgencyLevel(
    val label: String,
    val severityRank: Int,
    val allowsGreenSafeBadge: Boolean
) {
    INFORMATION("Information", 1, false),
    MONITOR("Monitor", 2, false),
    SERVICE_RECOMMENDED("Service Recommended", 3, false),
    URGENT("Urgent", 4, false);

    companion object {
        fun fromLabel(raw: String?): DiagnosticUrgencyLevel? {
            val norm = raw?.trim()?.lowercase() ?: return null
            return when (norm) {
                "information", "info" -> INFORMATION
                "monitor" -> MONITOR
                "service recommended", "service_recommended" -> SERVICE_RECOMMENDED
                "urgent" -> URGENT
                else -> null
            }
        }
    }
}

enum class DiagnosticConfidenceLevel(val label: String) {
    HIGH("High"),
    MODERATE("Moderate"),
    LOW("Low"),
    INSUFFICIENT("Insufficient");

    companion object {
        fun fromLabel(raw: String?): DiagnosticConfidenceLevel? {
            val norm = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.label.lowercase() == norm || it.name.lowercase() == norm }
        }
    }
}

data class EverydayImpactBreakdown(
    val normalOperation: String,
    val drivingComfort: String,
    val fuelConsumption: String,
    val vehicleReliability: String,
    val safetyNote: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("normal_operation", normalOperation)
        put("driving_comfort", drivingComfort)
        put("fuel_consumption", fuelConsumption)
        put("vehicle_reliability", vehicleReliability)
        put("safety_note", safetyNote)
    }
}

data class SupportingEvidenceBundle(
    val diagnosticCodesUsed: List<String>,
    val sensorValuesUsed: List<String>,
    val vehicleInformationUsed: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("diagnostic_codes_used", JSONArray(diagnosticCodesUsed))
        put("sensor_values_used", JSONArray(sensorValuesUsed))
        put("vehicle_information_used", JSONArray(vehicleInformationUsed))
    }
}

data class StructuredNextSteps(
    val thingsOwnerCanSafelyObserve: List<String>,
    val informationOwnerCanRecord: List<String>,
    val mechanicRequiredActions: List<String>,
    val stopDrivingSituations: List<String>
) {
    fun flattenedList(): List<String> = buildList {
        thingsOwnerCanSafelyObserve.forEach { add("Observe safely: $it") }
        informationOwnerCanRecord.forEach { add("Record for mechanic: $it") }
        mechanicRequiredActions.forEach { add("Qualified mechanic: $it") }
        stopDrivingSituations.forEach { add("Stop driving when safe if: $it") }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("observe_safely", JSONArray(thingsOwnerCanSafelyObserve))
        put("record_for_mechanic", JSONArray(informationOwnerCanRecord))
        put("mechanic_required", JSONArray(mechanicRequiredActions))
        put("stop_driving_situations", JSONArray(stopDrivingSituations))
    }
}

data class EverydayDiagnosticFinding(
    val findingTitle: String,
    val whatHappened: String,
    val whatItMeans: String,
    val plainLanguageExplanation: String,
    val technicalExplanation: String,
    val possibleCauses: List<String>,
    val everydayImpact: EverydayImpactBreakdown,
    val urgencyLevel: DiagnosticUrgencyLevel,
    val urgencyReason: String,
    val safeObservations: List<String>,
    val recommendedNextSteps: StructuredNextSteps,
    val professionalInspectionRequired: Boolean,
    val supportingEvidence: SupportingEvidenceBundle,
    val missingInformation: List<String>,
    val confidenceLevel: DiagnosticConfidenceLevel,
    val reasonsForUncertainty: List<String>,
    val knowledgeSources: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("finding_title", findingTitle)
        put("what_happened", whatHappened)
        put("what_it_means", whatItMeans)
        put("plain_language_explanation", plainLanguageExplanation)
        put("technical_explanation", technicalExplanation)
        put("possible_causes", JSONArray(possibleCauses))
        put("everyday_impact", everydayImpact.toJson())
        put("urgency_level", urgencyLevel.label)
        put("urgency_reason", urgencyReason)
        put("safe_observations", JSONArray(safeObservations))
        put("recommended_next_steps", recommendedNextSteps.toJson())
        put("professional_inspection_required", professionalInspectionRequired)
        put("supporting_evidence", supportingEvidence.toJson())
        put("missing_information", JSONArray(missingInformation))
        put("confidence_level", confidenceLevel.label)
        put("reasons_for_uncertainty", JSONArray(reasonsForUncertainty))
        put("knowledge_sources", JSONArray(knowledgeSources))
    }
}

data class EverydayDiagnosticResult(
    val sessionId: String,
    val generatedAtEpochMs: Long,
    val overallSummary: String,
    val overallUrgencyLevel: DiagnosticUrgencyLevel,
    val driveabilityCautionNotice: String,
    val hasSufficientEvidenceForFullAssessment: Boolean,
    val findings: List<EverydayDiagnosticFinding>,
    val readOnlyConfirmed: Boolean = true,
    val safetyAssessment: DiagnosticSafetyAssessment
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("session_id", sessionId)
        put("generated_at_epoch_ms", generatedAtEpochMs)
        put("read_only_confirmed", readOnlyConfirmed)
        put("overall_summary", overallSummary)
        put("overall_urgency_level", overallUrgencyLevel.label)
        put("driveability_caution_notice", driveabilityCautionNotice)
        put("has_sufficient_evidence", hasSufficientEvidenceForFullAssessment)
        put("findings", JSONArray(findings.map { it.toJson() }))
    }
}

data class SchemaValidationOutcome(
    val isValid: Boolean,
    val errors: List<String>,
    val findings: List<EverydayDiagnosticFinding> = emptyList(),
    val overallSummary: String = ""
)

/**
 * Strict schema and anti-fabrication validator for AI diagnostic JSON payloads.
 * Rejects missing required keys, invalid enums, empty explanations, or fabricated DTCs.
 */
object DiagnosticResultSchemaValidator {

    private val REQUIRED_FINDING_KEYS = listOf(
        "finding_title",
        "plain_language_explanation",
        "technical_explanation",
        "possible_causes",
        "everyday_impact",
        "urgency_level",
        "urgency_reason",
        "safe_observations",
        "recommended_next_steps",
        "professional_inspection_required",
        "supporting_evidence",
        "missing_information",
        "confidence_level",
        "knowledge_sources"
    )

    fun parseAndValidateJson(
        rawJson: String,
        session: ObdDiagnosticSession? = null
    ): SchemaValidationOutcome {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return SchemaValidationOutcome(false, listOf("Empty diagnostic JSON response."))
        }

        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            return SchemaValidationOutcome(false, listOf("Malformed JSON object: ${e.message}"))
        }

        val errors = mutableListOf<String>()
        val overallSummary = root.optString("overall_summary", "").trim()
        if (overallSummary.isEmpty()) {
            errors += "Missing required 'overall_summary' field."
        }

        val findingsArray = root.optJSONArray("findings")
        if (findingsArray == null || findingsArray.length() == 0) {
            errors += "Required 'findings' array must contain at least one diagnostic finding."
            return SchemaValidationOutcome(false, errors)
        }

        val sessionDtcSet = session?.allDtcs?.map { it.code.uppercase() }?.toSet()
        val parsedFindings = mutableListOf<EverydayDiagnosticFinding>()

        for (i in 0 until findingsArray.length()) {
            val item = findingsArray.optJSONObject(i)
            if (item == null) {
                errors += "findings[$i] is not a JSON object."
                continue
            }

            REQUIRED_FINDING_KEYS.forEach { key ->
                if (!item.has(key) || item.isNull(key)) {
                    errors += "findings[$i] missing required key '$key'."
                }
            }

            val title = item.optString("finding_title", "").trim()
            val plain = item.optString("plain_language_explanation", "").trim()
            val tech = item.optString("technical_explanation", "").trim()
            val urgency = DiagnosticUrgencyLevel.fromLabel(item.optString("urgency_level", ""))
            val urgencyReason = item.optString("urgency_reason", "").trim()
            val confidence = DiagnosticConfidenceLevel.fromLabel(item.optString("confidence_level", ""))

            if (title.isEmpty()) errors += "findings[$i].finding_title must not be blank."
            if (plain.isEmpty()) errors += "findings[$i].plain_language_explanation must not be blank."
            if (tech.isEmpty()) errors += "findings[$i].technical_explanation must not be blank."
            if (urgency == null) {
                errors += "findings[$i].urgency_level must be one of Information, Monitor, Service Recommended, Urgent."
            }
            if (urgencyReason.isEmpty()) errors += "findings[$i].urgency_reason must not be blank."
            if (confidence == null) {
                errors += "findings[$i].confidence_level must be one of High, Moderate, Low, Insufficient."
            }

            val causes = readStringList(item.optJSONArray("possible_causes"))
            val safeObs = readStringList(item.optJSONArray("safe_observations"))
            val missingInfo = readStringList(item.optJSONArray("missing_information"))
            val knowledgeSources = readStringList(item.optJSONArray("knowledge_sources"))
            val uncertaintyReasons = readStringList(item.optJSONArray("reasons_for_uncertainty"))

            if (causes.isEmpty()) errors += "findings[$i].possible_causes must contain at least one item."
            if (knowledgeSources.isEmpty()) errors += "findings[$i].knowledge_sources must cite at least one source."

            val impactObj = item.optJSONObject("everyday_impact")
            if (impactObj == null) {
                errors += "findings[$i].everyday_impact must be an object."
            }
            val impact = EverydayImpactBreakdown(
                normalOperation = impactObj?.optString("normal_operation", "")?.trim().orEmpty(),
                drivingComfort = impactObj?.optString("driving_comfort", "")?.trim().orEmpty(),
                fuelConsumption = impactObj?.optString("fuel_consumption", "")?.trim().orEmpty(),
                vehicleReliability = impactObj?.optString("vehicle_reliability", "")?.trim().orEmpty(),
                safetyNote = impactObj?.optString("safety_note", "")?.trim().orEmpty()
            )
            if (impact.normalOperation.isEmpty() || impact.vehicleReliability.isEmpty()) {
                errors += "findings[$i].everyday_impact must include normal_operation and vehicle_reliability."
            }

            val stepsObj = item.optJSONObject("recommended_next_steps")
            val steps = if (stepsObj != null) {
                StructuredNextSteps(
                    thingsOwnerCanSafelyObserve = readStringList(stepsObj.optJSONArray("observe_safely")).ifEmpty { safeObs },
                    informationOwnerCanRecord = readStringList(stepsObj.optJSONArray("record_for_mechanic")),
                    mechanicRequiredActions = readStringList(stepsObj.optJSONArray("mechanic_required")),
                    stopDrivingSituations = readStringList(stepsObj.optJSONArray("stop_driving_situations"))
                )
            } else {
                val flatSteps = readStringList(item.optJSONArray("recommended_next_steps"))
                if (flatSteps.isEmpty()) {
                    errors += "findings[$i].recommended_next_steps must provide actionable guidance."
                }
                StructuredNextSteps(
                    thingsOwnerCanSafelyObserve = safeObs,
                    informationOwnerCanRecord = emptyList(),
                    mechanicRequiredActions = flatSteps,
                    stopDrivingSituations = emptyList()
                )
            }

            val evObj = item.optJSONObject("supporting_evidence")
            if (evObj == null) {
                errors += "findings[$i].supporting_evidence must be an object."
            }
            val dtcsUsed = readStringList(evObj?.optJSONArray("diagnostic_codes_used"))
            val sensorsUsed = readStringList(evObj?.optJSONArray("sensor_values_used"))
            val vehicleInfoUsed = readStringList(evObj?.optJSONArray("vehicle_information_used"))

            if (sessionDtcSet != null) {
                val fabricatedDtcs = dtcsUsed.filter { code ->
                    val norm = code.trim().uppercase()
                    norm != "NONE" && norm.matches(Regex("^[PCBU][0-9A-F]{4}$")) && norm !in sessionDtcSet
                }
                if (fabricatedDtcs.isNotEmpty()) {
                    errors += "findings[$i].supporting_evidence cited unverified DTC(s) not present in session: ${fabricatedDtcs.joinToString(", ")}."
                }
            }

            if (urgency != null && confidence != null) {
                parsedFindings += EverydayDiagnosticFinding(
                    findingTitle = title,
                    whatHappened = item.optString("what_happened", "").trim().ifBlank { plain },
                    whatItMeans = item.optString("what_it_means", "").trim().ifBlank { plain },
                    plainLanguageExplanation = plain,
                    technicalExplanation = tech,
                    possibleCauses = causes,
                    everydayImpact = impact,
                    urgencyLevel = urgency,
                    urgencyReason = urgencyReason,
                    safeObservations = safeObs,
                    recommendedNextSteps = steps,
                    professionalInspectionRequired = item.optBoolean("professional_inspection_required", true),
                    supportingEvidence = SupportingEvidenceBundle(
                        diagnosticCodesUsed = dtcsUsed,
                        sensorValuesUsed = sensorsUsed,
                        vehicleInformationUsed = vehicleInfoUsed
                    ),
                    missingInformation = missingInfo,
                    confidenceLevel = confidence,
                    reasonsForUncertainty = uncertaintyReasons,
                    knowledgeSources = knowledgeSources
                )
            }
        }

        return SchemaValidationOutcome(
            isValid = errors.isEmpty() && parsedFindings.isNotEmpty(),
            errors = errors,
            findings = if (errors.isEmpty()) parsedFindings else emptyList(),
            overallSummary = overallSummary
        )
    }

    private fun readStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val v = array.optString(i, "").trim()
                if (v.isNotEmpty()) add(v)
            }
        }
    }
}
