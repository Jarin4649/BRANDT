package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Persisted read-only OBD-II diagnostic session summary for a registered vehicle.
 * Uses native Firestore Maps/Lists and explicit field bounds (zero JSON string blobs).
 */
@Keep
@IgnoreExtraProperties
data class SavedObdSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleId: String = "",
    val vehicleTitle: String = "",
    val sourceType: String = "MANUAL_DTC_ENTRY",
    val adapterOrFileLabel: String = "",
    val reliabilityLevel: String = "LOW_MANUAL_OR_UNVERIFIED",
    val overallUrgency: String = "Monitor",
    val confidenceLevel: String = "Low",
    val ownerSymptoms: String = "",
    val headlineSummary: String = "",
    val driveabilityNotice: String = "",
    val dtcCodes: List<String> = emptyList(),
    val findingTitles: List<String> = emptyList(),
    val telemetrySummary: List<String> = emptyList(),
    val missingInformation: List<String> = emptyList(),
    val mechanicChecklist: List<String> = emptyList(),
    val reportPlainText: String = "",
    val readOnlyVerified: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String, targetVehicleId: String): Map<String, Any> {
        return mapOf(
            "userId" to uid,
            "vehicleId" to targetVehicleId,
            "vehicleTitle" to vehicleTitle.trim().take(120).ifEmpty { "Registered Vehicle" },
            "sourceType" to sourceType.trim().take(40).ifEmpty { "MANUAL_DTC_ENTRY" },
            "adapterOrFileLabel" to adapterOrFileLabel.trim().take(140).ifEmpty { "Diagnostic Entry" },
            "reliabilityLevel" to reliabilityLevel.trim().take(40).ifEmpty { "LOW_MANUAL_OR_UNVERIFIED" },
            "overallUrgency" to overallUrgency.trim().take(32).ifEmpty { "Monitor" },
            "confidenceLevel" to confidenceLevel.trim().take(24).ifEmpty { "Low" },
            "ownerSymptoms" to ownerSymptoms.trim().take(800),
            "headlineSummary" to headlineSummary.trim().take(1200).ifEmpty { "Read-only diagnostic session saved." },
            "driveabilityNotice" to driveabilityNotice.trim().take(500),
            "dtcCodes" to dtcCodes.map { it.trim().take(16) }.filter { it.isNotEmpty() }.take(20),
            "findingTitles" to findingTitles.map { it.trim().take(140) }.filter { it.isNotEmpty() }.take(15),
            "telemetrySummary" to telemetrySummary.map { it.trim().take(140) }.filter { it.isNotEmpty() }.take(25),
            "missingInformation" to missingInformation.map { it.trim().take(200) }.filter { it.isNotEmpty() }.take(15),
            "mechanicChecklist" to mechanicChecklist.map { it.trim().take(200) }.filter { it.isNotEmpty() }.take(20),
            "reportPlainText" to reportPlainText.trim().take(8000).ifEmpty { headlineSummary.trim().take(1200) },
            "readOnlyVerified" to true
        )
    }
}
