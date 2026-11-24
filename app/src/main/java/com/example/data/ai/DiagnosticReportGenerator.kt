package com.example.data.ai

import com.example.data.obd.DtcInterpretation
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.ObdDiagnosticSession
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modular Diagnostic Report Generator.
 * Transforms a read-only [ObdDiagnosticSession] and validated [EverydayDiagnosticResult]
 * into an owner-friendly summary and an expandable technical / mechanic handoff report.
 */
data class DiagnosticReportTelemetryRow(
    val pidCode: String,
    val label: String,
    val displayValue: String,
    val isAvailable: Boolean
)

data class GeneratedDiagnosticReport(
    val reportId: String,
    val sessionId: String,
    val vehicleDisplayTitle: String,
    val capturedTimestampLabel: String,
    val sourceLabel: String,
    val reliabilityLabel: String,
    val readOnlyVerificationLine: String,
    val overallUrgencyLevel: DiagnosticUrgencyLevel,
    val ownerHeadlineSummary: String,
    val driveabilityCautionNotice: String,
    val findings: List<EverydayDiagnosticFinding>,
    val technicalDtcBreakdown: List<DtcInterpretation>,
    val telemetryRows: List<DiagnosticReportTelemetryRow>,
    val mechanicHandoffChecklist: List<String>,
    val shareablePlainText: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("report_id", reportId)
        put("session_id", sessionId)
        put("vehicle", vehicleDisplayTitle)
        put("captured_at", capturedTimestampLabel)
        put("source", sourceLabel)
        put("reliability", reliabilityLabel)
        put("read_only_verified", true)
        put("overall_urgency_level", overallUrgencyLevel.label)
        put("owner_headline_summary", ownerHeadlineSummary)
        put("driveability_caution_notice", driveabilityCautionNotice)
        put("findings", JSONArray(findings.map { it.toJson() }))
        put("mechanic_handoff_checklist", JSONArray(mechanicHandoffChecklist))
    }
}

object DiagnosticReportGenerator {

    fun generate(
        session: ObdDiagnosticSession,
        explanationResult: EverydayDiagnosticResult
    ): GeneratedDiagnosticReport {
        val dtcInterpretations = session.allDtcs.map { dtc ->
            DtcInterpreter.interpret(dtc, session.vehicle)
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val capturedAtLabel = formatter.format(Date(session.capturedAtEpochMs))

        val telemetryRows = session.livePidReadings.map { reading ->
            DiagnosticReportTelemetryRow(
                pidCode = reading.pidCode,
                label = reading.name,
                displayValue = reading.formattedValueOrUnavailable(),
                isAvailable = reading.isAvailable
            )
        }

        val checklist = buildList {
            if (session.allDtcs.isNotEmpty()) {
                add("Confirmed/Pending DTCs observed: ${session.allDtcs.joinToString(", ") { "${it.code} (${it.status.code})" }}")
            } else {
                add("No stored/pending Mode 03/07 DTCs detected in this read-only session.")
            }
            session.ownerSymptoms?.let { add("Owner symptom notes: $it") }
            explanationResult.findings.flatMap { it.recommendedNextSteps.informationOwnerCanRecord }
                .distinct()
                .forEach { add("Requested observation: $it") }
            explanationResult.findings.flatMap { it.recommendedNextSteps.mechanicRequiredActions }
                .distinct()
                .forEach { add("Technician verification: $it") }
            val missing = explanationResult.findings.flatMap { it.missingInformation }.distinct()
            if (missing.isNotEmpty()) {
                add("Missing diagnostic context: ${missing.take(4).joinToString("; ")}")
            }
        }

        val readOnlyLine = if (session.sourceMetadata.readOnlyEnforced) {
            "Read-Only Verified • No DTCs cleared, no ECU reset, no programming or actuator control sent."
        } else {
            "Verification Pending"
        }

        val shareText = buildString {
            appendLine("=== BRANDT Vehicle Diagnostic Summary ===")
            appendLine("Vehicle: ${session.vehicle.displayTitle()}")
            appendLine("Captured: $capturedAtLabel")
            appendLine("Source: ${session.sourceMetadata.adapterOrFileLabel} (${session.sourceMetadata.sourceType.displayLabel})")
            appendLine("Reliability: ${session.sourceMetadata.reliabilityLevel.displayLabel}")
            appendLine("Policy: $readOnlyLine")
            appendLine("Urgency: ${explanationResult.overallUrgencyLevel.label}")
            appendLine()
            appendLine("Summary:")
            appendLine(explanationResult.overallSummary)
            appendLine()
            explanationResult.findings.forEachIndexed { idx, f ->
                appendLine("${idx + 1}. ${f.findingTitle} [${f.urgencyLevel.label} • Confidence: ${f.confidenceLevel.label}]")
                appendLine("   Everyday Explanation: ${f.plainLanguageExplanation}")
                appendLine("   Technical Note: ${f.technicalExplanation}")
                appendLine("   Possible Causes: ${f.possibleCauses.joinToString("; ")}")
            }
            appendLine()
            appendLine("Safety Notice: ${explanationResult.driveabilityCautionNotice}")
        }.trim()

        return GeneratedDiagnosticReport(
            reportId = "rpt_${session.sessionId}",
            sessionId = session.sessionId,
            vehicleDisplayTitle = session.vehicle.displayTitle(),
            capturedTimestampLabel = capturedAtLabel,
            sourceLabel = "${session.sourceMetadata.sourceType.displayLabel} • ${session.sourceMetadata.adapterOrFileLabel}",
            reliabilityLabel = session.sourceMetadata.reliabilityLevel.displayLabel,
            readOnlyVerificationLine = readOnlyLine,
            overallUrgencyLevel = explanationResult.overallUrgencyLevel,
            ownerHeadlineSummary = explanationResult.overallSummary,
            driveabilityCautionNotice = explanationResult.driveabilityCautionNotice,
            findings = explanationResult.findings,
            technicalDtcBreakdown = dtcInterpretations,
            telemetryRows = telemetryRows,
            mechanicHandoffChecklist = checklist,
            shareablePlainText = shareText
        )
    }
}
