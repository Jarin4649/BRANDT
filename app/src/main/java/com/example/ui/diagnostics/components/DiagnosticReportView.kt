package com.example.ui.diagnostics.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.DiagnosticConfidenceLevel
import com.example.data.ai.DiagnosticUrgencyLevel
import com.example.data.ai.EverydayDiagnosticFinding
import com.example.data.ai.EverydayDiagnosticResult

@Composable
fun DiagnosticUrgencyBadge(
    urgency: DiagnosticUrgencyLevel,
    hasSufficientEvidence: Boolean,
    modifier: Modifier = Modifier
) {
    // Strictly avoid misleading green/safe badge across all urgency states (and especially insufficient data)
    val bgColor = when (urgency) {
        DiagnosticUrgencyLevel.URGENT -> MaterialTheme.colorScheme.errorContainer
        DiagnosticUrgencyLevel.SERVICE_RECOMMENDED -> MaterialTheme.colorScheme.tertiaryContainer
        DiagnosticUrgencyLevel.MONITOR,
        DiagnosticUrgencyLevel.INFORMATION -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = when (urgency) {
        DiagnosticUrgencyLevel.URGENT -> MaterialTheme.colorScheme.onErrorContainer
        DiagnosticUrgencyLevel.SERVICE_RECOMMENDED -> MaterialTheme.colorScheme.onTertiaryContainer
        DiagnosticUrgencyLevel.MONITOR,
        DiagnosticUrgencyLevel.INFORMATION -> MaterialTheme.colorScheme.onSurface
    }

    val label = if (!hasSufficientEvidence && urgency == DiagnosticUrgencyLevel.INFORMATION) {
        "Insufficient Data • Check Context"
    } else {
        urgency.label
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag("diagnostic_urgency_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (urgency == DiagnosticUrgencyLevel.URGENT || urgency == DiagnosticUrgencyLevel.SERVICE_RECOMMENDED) {
                    Icons.Filled.Warning
                } else {
                    Icons.Filled.Info
                },
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = fgColor
            )
        }
    }
}

@Composable
fun DiagnosticReportView(
    result: EverydayDiagnosticResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostic_report_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Headline Summary & Driveability Caution Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Everyday Understanding",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    DiagnosticUrgencyBadge(
                        urgency = result.overallUrgencyLevel,
                        hasSufficientEvidence = result.hasSufficientEvidenceForFullAssessment
                    )
                }

                Text(
                    text = result.overallSummary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("results_overall_summary")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.driveabilityCautionNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Structured Everyday Findings
        result.findings.forEachIndexed { index, finding ->
            FindingDetailCard(
                index = index + 1,
                finding = finding
            )
        }
    }
}

@Composable
private fun FindingDetailCard(
    index: Int,
    finding: EverydayDiagnosticFinding
) {
    var technicalExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_finding_card_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$index. ${finding.findingTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Confidence: ${finding.confidenceLevel.label} • Urgency: ${finding.urgencyLevel.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (finding.confidenceLevel == DiagnosticConfidenceLevel.INSUFFICIENT ||
                            finding.confidenceLevel == DiagnosticConfidenceLevel.LOW
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // A. What happened & B. What it means
            LabeledExplanationBlock(
                label = "A. What happened",
                body = finding.whatHappened
            )
            LabeledExplanationBlock(
                label = "B. What this means in everyday words",
                body = finding.whatItMeans
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // C. Everyday Driving Impact
            Text(
                text = "C. How it affects everyday driving",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            BulletLine("Normal Driving", finding.everydayImpact.normalOperation)
            if (finding.everydayImpact.drivingComfort.isNotBlank()) {
                BulletLine("Comfort & Noise", finding.everydayImpact.drivingComfort)
            }
            if (finding.everydayImpact.fuelConsumption.isNotBlank()) {
                BulletLine("Fuel Economy", finding.everydayImpact.fuelConsumption)
            }
            BulletLine("Reliability Over Time", finding.everydayImpact.vehicleReliability)
            BulletLine("Safety", finding.everydayImpact.safetyNote)

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // D. Separated Actionable Next Steps
            Text(
                text = "D. What to do next",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = finding.urgencyReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ActionGroup(
                title = "Safe things you can observe",
                items = finding.recommendedNextSteps.thingsOwnerCanSafelyObserve.ifEmpty { finding.safeObservations }
            )
            ActionGroup(
                title = "Helpful notes to write down for a mechanic",
                items = finding.recommendedNextSteps.informationOwnerCanRecord
            )
            ActionGroup(
                title = "Qualified mechanic verification needed",
                items = finding.recommendedNextSteps.mechanicRequiredActions
            )
            ActionGroup(
                title = "Stop driving when safe & seek roadside help if:",
                items = finding.recommendedNextSteps.stopDrivingSituations
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Possible Causes + Uncertainty + Missing Information
            ActionGroup(
                title = "Possible causes (requires inspection to confirm)",
                items = finding.possibleCauses
            )
            ActionGroup(
                title = "Missing information limiting precision",
                items = finding.missingInformation
            )
            ActionGroup(
                title = "Reasons for uncertainty",
                items = finding.reasonsForUncertainty
            )

            // Expandable Technical / Engineering Detail
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { technicalExpanded = !technicalExpanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("finding_technical_toggle_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (technicalExpanded) "Hide Technical Engineering Detail" else "View Technical & Evidence Detail",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (technicalExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = technicalExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = finding.technicalExplanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ActionGroup(
                                title = "Diagnostic Codes Used",
                                items = finding.supportingEvidence.diagnosticCodesUsed
                            )
                            ActionGroup(
                                title = "Sensor Readings Used",
                                items = finding.supportingEvidence.sensorValuesUsed
                            )
                            ActionGroup(
                                title = "Knowledge References",
                                items = finding.knowledgeSources
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledExplanationBlock(
    label: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BulletLine(label: String, value: String) {
    Text(
        text = "• $label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ActionGroup(
    title: String,
    items: List<String>
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        items.forEach { entry ->
            Text(
                text = "• $entry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
