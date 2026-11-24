package com.example.data.ai

import android.util.Log
import com.example.data.obd.DtcInterpretation
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.settings.AppLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * AI-powered Everyday Explanation Engine via Firebase AI Logic (`Firebase.ai`).
 *
 * Combines:
 * 1. Deterministic DTC interpretations ([DtcInterpreter])
 * 2. Evidence & uncertainty auditing ([DiagnosticEvidenceEvaluator])
 * 3. Deterministic safety guardrails ([DiagnosticSafetyAssessor])
 * 4. Gemini structured JSON output constrained by [Schema.obj]
 * 5. Strict validation & anti-fabrication enforcement ([DiagnosticResultSchemaValidator])
 */
interface EverydayExplanationEngine {
    suspend fun explainDiagnosticSession(
        session: ObdDiagnosticSession,
        language: AppLanguage = AppLanguage.ENGLISH
    ): Result<EverydayDiagnosticResult>
}

class FirebaseEverydayExplanationEngine : EverydayExplanationEngine {

    companion object {
        private const val TAG = "EverydayExplanation"
        private const val INFERENCE_TIMEOUT_MS = 45_000L
    }

    private val diagnosticResultSchema = Schema.obj(
        properties = mapOf(
            "overall_summary" to Schema.string(
                "2-3 clear everyday sentences summarizing what the vehicle scan and owner context show, written for someone with zero automotive background."
            ),
            "findings" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "finding_title" to Schema.string("Clear everyday title including code if applicable."),
                        "what_happened" to Schema.string("Section A: Plain explanation of what the vehicle's computer detected."),
                        "what_it_means" to Schema.string("Section B: Explain the automotive concept simply without jargon overload."),
                        "plain_language_explanation" to Schema.string("Combined Section A + B accessible explanation for a first-time driver."),
                        "technical_explanation" to Schema.string("Accurate engineering explanation for the expandable technical section and mechanics."),
                        "possible_causes" to Schema.array(
                            items = Schema.string(),
                            description = "Realistic possible causes; never claim the named component has definitely failed."
                        ),
                        "everyday_impact" to Schema.obj(
                            properties = mapOf(
                                "normal_operation" to Schema.string("Effect on normal driving; state clearly if unknown/unsupported."),
                                "driving_comfort" to Schema.string("Effect on ride, noise, vibration, or smoothness."),
                                "fuel_consumption" to Schema.string("Fuel economy effect ONLY if supported by evidence, otherwise state insufficient evidence."),
                                "vehicle_reliability" to Schema.string("Long-term wear or reliability effect if ignored."),
                                "safety_note" to Schema.string("Safety relevance; never claim guaranteed safe to drive from scan alone.")
                            )
                        ),
                        "urgency_level" to Schema.enumeration(
                            values = listOf("Information", "Monitor", "Service Recommended", "Urgent"),
                            description = "Assigned urgency category. Never use mild status when safety risks exist or data is insufficient."
                        ),
                        "urgency_reason" to Schema.string("Concrete reason explaining why this urgency category was assigned."),
                        "safe_observations" to Schema.array(
                            items = Schema.string(),
                            description = "Safe, non-hazardous things the owner can visually or audibly observe."
                        ),
                        "recommended_next_steps" to Schema.obj(
                            properties = mapOf(
                                "observe_safely" to Schema.array(items = Schema.string()),
                                "record_for_mechanic" to Schema.array(items = Schema.string()),
                                "mechanic_required" to Schema.array(items = Schema.string()),
                                "stop_driving_situations" to Schema.array(items = Schema.string())
                            )
                        ),
                        "professional_inspection_required" to Schema.boolean(),
                        "supporting_evidence" to Schema.obj(
                            properties = mapOf(
                                "diagnostic_codes_used" to Schema.array(items = Schema.string()),
                                "sensor_values_used" to Schema.array(items = Schema.string()),
                                "vehicle_information_used" to Schema.array(items = Schema.string())
                            )
                        ),
                        "missing_information" to Schema.array(items = Schema.string()),
                        "confidence_level" to Schema.enumeration(
                            values = listOf("High", "Moderate", "Low", "Insufficient")
                        ),
                        "reasons_for_uncertainty" to Schema.array(items = Schema.string()),
                        "knowledge_sources" to Schema.array(items = Schema.string())
                    )
                ),
                description = "One or more structured diagnostic findings."
            )
        )
    )

    private val structuredModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = diagnosticResultSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = RequestOptions(35_000L)
        )
    }

    override suspend fun explainDiagnosticSession(
        session: ObdDiagnosticSession,
        language: AppLanguage
    ): Result<EverydayDiagnosticResult> {
        val interpretations = session.allDtcs.map { dtc ->
            DtcInterpreter.interpret(dtc, session.vehicle)
        }
        val evidenceAudit = DiagnosticEvidenceEvaluator.evaluate(session, interpretations)
        val safetyAssessment = DiagnosticSafetyAssessor.assess(session, evidenceAudit)

        val promptText = buildPrompt(
            session = session,
            interpretations = interpretations,
            evidenceAudit = evidenceAudit,
            safetyAssessment = safetyAssessment,
            language = language
        )

        return try {
            val rawResponse = withContext(Dispatchers.IO) {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    val response = structuredModel.generateContent(content { text(promptText) })
                    response.text
                }
            } ?: return Result.failure(
                IllegalStateException("AI explanation engine returned an empty response.")
            )

            val validation = DiagnosticResultSchemaValidator.parseAndValidateJson(rawResponse, session)
            if (!validation.isValid) {
                Log.e(TAG, "AI output failed schema validation: ${validation.errors}")
                return Result.failure(
                    IllegalStateException(
                        "Diagnostic explanation failed schema validation: ${validation.errors.joinToString("; ")}"
                    )
                )
            }

            // Apply deterministic safety & confidence guardrails over validated AI output
            val safeguardedFindings = applyDeterministicSafeguards(
                findings = validation.findings,
                evidenceAudit = evidenceAudit,
                safetyAssessment = safetyAssessment
            )

            val topUrgency = safeguardedFindings
                .maxByOrNull { it.urgencyLevel.severityRank }
                ?.urgencyLevel
                ?: safetyAssessment.minimumUrgencyFloor

            Result.success(
                EverydayDiagnosticResult(
                    sessionId = session.sessionId,
                    generatedAtEpochMs = System.currentTimeMillis(),
                    overallSummary = validation.overallSummary,
                    overallUrgencyLevel = if (topUrgency.severityRank < safetyAssessment.minimumUrgencyFloor.severityRank) {
                        safetyAssessment.minimumUrgencyFloor
                    } else {
                        topUrgency
                    },
                    driveabilityCautionNotice = safetyAssessment.driveabilityDisclaimer,
                    hasSufficientEvidenceForFullAssessment = evidenceAudit.hasSufficientInformation,
                    findings = safeguardedFindings,
                    readOnlyConfirmed = session.sourceMetadata.readOnlyEnforced,
                    safetyAssessment = safetyAssessment
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Everyday diagnostic explanation timed out", e)
            Result.failure(
                IllegalStateException("Diagnostic explanation timed out after 45 seconds. Please try again.")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Everyday diagnostic explanation failed", e)
            Result.failure(e)
        }
    }

    internal fun applyDeterministicSafeguards(
        findings: List<EverydayDiagnosticFinding>,
        evidenceAudit: DiagnosticEvidenceAudit,
        safetyAssessment: DiagnosticSafetyAssessment
    ): List<EverydayDiagnosticFinding> {
        return findings.map { finding ->
            val adjustedUrgency = if (finding.urgencyLevel.severityRank < safetyAssessment.minimumUrgencyFloor.severityRank) {
                safetyAssessment.minimumUrgencyFloor
            } else {
                finding.urgencyLevel
            }

            val mergedMissing = (finding.missingInformation + evidenceAudit.missingInformation).distinct()
            val mergedUncertainty = (finding.reasonsForUncertainty + evidenceAudit.reasonsForUncertainty).distinct()
            val mergedSources = (finding.knowledgeSources + evidenceAudit.knowledgeSources).distinct()
            val mergedStopSituations = (
                finding.recommendedNextSteps.stopDrivingSituations +
                    safetyAssessment.stopDrivingGuidance
                ).distinct()

            val adjustedConfidence = if (!evidenceAudit.hasSufficientInformation &&
                finding.confidenceLevel == DiagnosticConfidenceLevel.HIGH
            ) {
                DiagnosticConfidenceLevel.MODERATE
            } else {
                finding.confidenceLevel
            }

            val guardedSafetyNote = buildString {
                append(finding.everydayImpact.safetyNote.ifBlank { "Monitor vehicle behavior carefully." })
                if (!finding.everydayImpact.safetyNote.contains("cannot guarantee", ignoreCase = true)) {
                    append(" (${DiagnosticSafetyAssessor.MANDATORY_SCAN_SAFETY_DISCLAIMER})")
                }
            }

            finding.copy(
                urgencyLevel = adjustedUrgency,
                missingInformation = mergedMissing,
                reasonsForUncertainty = mergedUncertainty,
                knowledgeSources = mergedSources,
                confidenceLevel = adjustedConfidence,
                everydayImpact = finding.everydayImpact.copy(safetyNote = guardedSafetyNote),
                recommendedNextSteps = finding.recommendedNextSteps.copy(
                    stopDrivingSituations = mergedStopSituations
                )
            )
        }
    }

    private fun buildPrompt(
        session: ObdDiagnosticSession,
        interpretations: List<DtcInterpretation>,
        evidenceAudit: DiagnosticEvidenceAudit,
        safetyAssessment: DiagnosticSafetyAssessment,
        language: AppLanguage
    ): String {
        val outputLanguageLine = if (language == AppLanguage.THAI) {
            "OUTPUT LANGUAGE: Write all human-readable explanation text in clear, accessible Thai (ภาษาไทยที่เจ้าของรถทั่วไปเข้าใจได้ง่าย)."
        } else {
            "OUTPUT LANGUAGE: Write all human-readable explanation text in clear, accessible English."
        }

        val dtcBlock = if (interpretations.isEmpty()) {
            "No DTCs present in this session."
        } else {
            interpretations.joinToString("\n\n") { interp ->
                """
                Code: ${interp.code} (ValidFormat=${interp.isValidFormat})
                Standard Definition: ${interp.standardDefinition}
                System: ${interp.systemCategory.displayName} / ${interp.subsystemLabel}
                Engineering Detail: ${interp.technicalExplanation}
                Candidate Causes: ${interp.possibleCauses.joinToString("; ")}
                Vehicle Considerations: ${interp.vehicleSpecificConsiderations.joinToString("; ")}
                """.trimIndent()
            }
        }

        return """
            You are BRANDT's Automotive Engineering & Everyday Communication Engine.
            $outputLanguageLine

            STRICT INTEGRITY & ACCESSIBILITY RULES:
            1. Write for everyday vehicle owners (any age, gender, or technical background) — never overwhelm them with unexplained jargon, and never condescend.
            2. Keep technical_explanation accurate for experienced users/mechanics, while plain_language_explanation, what_happened, and what_it_means are warm and simple.
            3. NEVER invent DTCs, sensor readings, or vehicle specifications. Only cite evidence listed below.
            4. NEVER claim that a part named in a DTC is definitely faulty — treat a DTC as a symptom/signal needing verification.
            5. NEVER claim a fuel economy impact unless fuel trim, O2, MAF, or misfire/thermostat evidence supports it; otherwise state there is not enough evidence yet.
            6. NEVER instruct an inexperienced owner to perform hazardous repairs (e.g., jacking the car, opening pressurized hot coolant, touching high-voltage wiring, fuel lines, or airbags).
            7. Minimum enforced urgency floor for this session: ${safetyAssessment.minimumUrgencyFloor.label}.
            8. NEVER guarantee the car is safe to drive based on an OBD scan alone.

            VERIFIED EVIDENCE INPUTS:
            - Vehicle Info: ${evidenceAudit.evidenceBundle.vehicleInformationUsed.joinToString(" | ")}
            - Source & Reliability: ${session.sourceMetadata.sourceType.displayLabel} (${session.sourceMetadata.reliabilityLevel.displayLabel})
            - DTCs Used: ${evidenceAudit.evidenceBundle.diagnosticCodesUsed.joinToString(" | ")}
            - Available Sensor Telemetry: ${evidenceAudit.evidenceBundle.sensorValuesUsed.joinToString(" | ")}
            - Owner Symptoms: ${session.ownerSymptoms ?: "Not provided"}
            - Missing Information: ${evidenceAudit.missingInformation.joinToString(" | ").ifBlank { "None" }}
            - Safety Signals: ${safetyAssessment.triggeredSafetySignals.joinToString(" | ").ifBlank { "None triggered by scan data" }}

            STANDARDIZED DTC ENGINEERING CONTEXT:
            $dtcBlock

            Return strictly valid JSON matching the schema.
        """.trimIndent()
    }
}
