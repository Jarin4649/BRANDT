package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val UNKNOWN_SPEC_LABEL = "Unknown / Unavailable"
const val MIN_MODEL_YEAR = 1886
const val MAX_MODEL_YEAR = 2028
const val MAX_MILEAGE = 9_999_999L

enum class BodyStyle(val displayName: String) {
    CROSSOVER("Crossover"),
    SEDAN("Sedan"),
    COUPE("Coupe / GT"),
    HATCHBACK("Hatchback"),
    SUV("SUV"),
    TRUCK("Pickup / Utility"),
    WAGON("Estate / Wagon");

    companion object {
        fun fromCode(raw: String?): BodyStyle {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CROSSOVER
        }
    }
}

enum class MileageUnit(val code: String, val displayLabel: String) {
    KM("km", "Kilometers (km)"),
    MI("mi", "Miles (mi)");

    companion object {
        fun fromCode(raw: String?): MileageUnit {
            return entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: KM
        }
    }
}

enum class VehicleAngle(
    val shortLabel: String,
    val displayTitle: String,
    val degrees: Int,
    val framingHint: String
) {
    FRONT(
        shortLabel = "Front",
        displayTitle = "Front View",
        degrees = 0,
        framingHint = "Center the front grille, headlights, and bumper inside the studio frame."
    ),
    SIDE_LEFT(
        shortLabel = "Left Side",
        displayTitle = "Left Profile",
        degrees = 90,
        framingHint = "Step back to fit both front and rear wheels in a level side profile."
    ),
    REAR(
        shortLabel = "Rear",
        displayTitle = "Rear View",
        degrees = 180,
        framingHint = "Center the taillights, tailgate, and rear bumper cleanly."
    ),
    SIDE_RIGHT(
        shortLabel = "Right Side",
        displayTitle = "Right Profile",
        degrees = 270,
        framingHint = "Capture the full right profile to complete your 360° studio loop."
    );

    companion object {
        fun fromName(raw: String?): VehicleAngle? {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    }
}

enum class SpecSource(val code: String, val label: String) {
    USER_ENTERED("USER_ENTERED", "User-Entered"),
    DEMO_CATALOG_PREFILL("DEMO_CATALOG_PREFILL", "Demo Catalog Pre-fill"),
    AI_VISION_PREFILL("AI_VISION_PREFILL", "AI Vision Pre-fill");

    companion object {
        fun fromCode(raw: String?): SpecSource {
            return entries.firstOrNull { it.code == raw } ?: USER_ENTERED
        }
    }
}

enum class VerificationStatus(
    val code: String,
    val badgeTitle: String,
    val summary: String
) {
    UNVERIFIED_USER_DATA(
        code = "UNVERIFIED_USER_DATA",
        badgeTitle = "User-Entered • Unverified",
        summary = "Specifications were entered directly by the vehicle owner and have not been cross-checked against an OEM registry."
    ),
    DEMO_DATA_UNVERIFIED(
        code = "DEMO_DATA_UNVERIFIED",
        badgeTitle = "Demo Catalog • Unverified",
        summary = "Pre-filled from BRANDT's demonstration catalog for convenience. Confirm details against your registration documents."
    ),
    AI_EXTRACTED_UNVERIFIED(
        code = "AI_EXTRACTED_UNVERIFIED",
        badgeTitle = "AI Vision • Unverified",
        summary = "Pre-filled by Gemini Vision from your multi-angle photos. Always confirm model year, trim, and powertrain details manually."
    );

    companion object {
        fun fromCode(raw: String?): VerificationStatus {
            return entries.firstOrNull { it.code == raw } ?: UNVERIFIED_USER_DATA
        }
    }
}

enum class AttachmentKind(val code: String, val label: String) {
    TEXT_ONLY("TEXT_ONLY", "Symptom Note"),
    PHOTO("PHOTO", "Photo"),
    FILE("FILE", "Document"),
    AUDIO("AUDIO", "Vehicle Audio"),
    VIDEO("VIDEO", "Video Clip");

    companion object {
        fun fromCode(raw: String?): AttachmentKind {
            return entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: TEXT_ONLY
        }
    }
}

enum class RequestedCaptureType {
    NONE,
    PHOTO,
    AUDIO,
    VIDEO;

    companion object {
        fun fromCode(raw: String?): RequestedCaptureType {
            return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
        }
    }
}

@Keep
@IgnoreExtraProperties
data class VehicleDiagnosisLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleId: String = "",
    val userPrompt: String = "",
    val aiSummary: String = "",
    val attachmentType: String = AttachmentKind.TEXT_ONLY.code,
    val attachmentLabel: String = "",
    val modelCommonality: String = "",
    val followUpQuestion: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedAttachmentKind(): AttachmentKind = AttachmentKind.fromCode(attachmentType)
}

data class VehicleKnownIssue(
    val title: String,
    val frequencyLabel: String,
    val simpleExplanation: String,
    val whatToNotice: String,
    val askAiPrompt: String
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredMarket: String = "United States",
    val preferredMileageUnit: String = MileageUnit.KM.code,
    val primaryVehicleId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Vehicle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val modelYear: Int = 2025,
    val trim: String = "",
    val bodyStyle: String = BodyStyle.CROSSOVER.name,
    val engineType: String = "",
    val engineDisplacement: String = "",
    val transmission: String = "",
    val fuelType: String = "",
    val mileage: Long = 0L,
    val mileageUnit: String = MileageUnit.KM.code,
    val market: String = "",
    val photoUri: String = "",
    val anglePhotos: Map<String, String> = emptyMap(),
    val aiInspectionSummary: String = "",
    val primary: Boolean = false,
    val specSource: String = SpecSource.USER_ENTERED.code,
    val verificationStatus: String = VerificationStatus.UNVERIFIED_USER_DATA.code,
    val unknownFields: List<String> = emptyList(),
    val electricalSystemType: String = "UNKNOWN",
    val batteryChemistryType: String = "UNSPECIFIED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedBodyStyle(): BodyStyle = BodyStyle.fromCode(bodyStyle)
    fun resolvedMileageUnit(): MileageUnit = MileageUnit.fromCode(mileageUnit)
    fun resolvedSpecSource(): SpecSource = SpecSource.fromCode(specSource)
    fun resolvedVerificationStatus(): VerificationStatus = VerificationStatus.fromCode(verificationStatus)

    fun resolvedAnglePhotos(): Map<VehicleAngle, String> {
        return VehicleAngle.entries.mapNotNull { angle ->
            val path = anglePhotos[angle.name]?.trim().orEmpty()
            if (path.isNotBlank()) angle to path else null
        }.toMap()
    }

    fun angleCoverageCount(): Int = resolvedAnglePhotos().size

    fun dataCoverage(): VehicleDataCoverage {
        val specChecks = listOf(
            "Manufacturer" to (manufacturer.isNotBlank() && manufacturer != UNKNOWN_SPEC_LABEL),
            "Model" to (model.isNotBlank() && model != UNKNOWN_SPEC_LABEL),
            "Model Year" to (modelYear in MIN_MODEL_YEAR..MAX_MODEL_YEAR),
            "Trim / Variant" to (trim.isNotBlank() && trim != UNKNOWN_SPEC_LABEL),
            "Engine Type" to (engineType.isNotBlank() && engineType != UNKNOWN_SPEC_LABEL && "engineType" !in unknownFields),
            "Displacement" to (engineDisplacement.isNotBlank() && engineDisplacement != UNKNOWN_SPEC_LABEL && "engineDisplacement" !in unknownFields),
            "Transmission" to (transmission.isNotBlank() && transmission != UNKNOWN_SPEC_LABEL && "transmission" !in unknownFields),
            "Fuel Type" to (fuelType.isNotBlank() && fuelType != UNKNOWN_SPEC_LABEL && "fuelType" !in unknownFields),
            "Current Mileage" to (mileage >= 0L),
            "Market / Country" to (market.isNotBlank() && market != UNKNOWN_SPEC_LABEL)
        )
        val completedCount = specChecks.count { it.second }
        val missingOrUnknown = specChecks.filterNot { it.second }.map { it.first }
        return VehicleDataCoverage(
            completedFields = completedCount,
            totalFields = specChecks.size,
            hasCustomPhoto = photoUri.isNotBlank() || anglePhotos.values.any { it.isNotBlank() },
            missingOrUnknownLabels = missingOrUnknown
        )
    }
}

data class VehicleDataCoverage(
    val completedFields: Int,
    val totalFields: Int,
    val hasCustomPhoto: Boolean,
    val missingOrUnknownLabels: List<String>
) {
    val completenessPercent: Int
        get() = ((completedFields.toFloat() / totalFields.coerceAtLeast(1)) * 100).toInt()
}

// Structured Catalog Entities (Module 4)
data class Manufacturer(
    val id: String,
    val name: String,
    val countryOfOrigin: String,
    val isDemonstrationData: Boolean = true
)

data class VehicleModel(
    val id: String,
    val manufacturerId: String,
    val name: String,
    val bodyStyle: BodyStyle,
    val minYear: Int,
    val maxYear: Int,
    val isDemonstrationData: Boolean = true
)

data class VehicleVariant(
    val id: String,
    val modelId: String,
    val trimName: String,
    val engineType: String,
    val engineDisplacement: String,
    val transmission: String,
    val fuelType: String,
    val suggestedMarkets: List<String>,
    val isDemonstrationData: Boolean = true
)

data class VehicleDraft(
    val manufacturer: String = "",
    val model: String = "",
    val modelYearInput: String = "",
    val trim: String = "",
    val bodyStyle: BodyStyle = BodyStyle.CROSSOVER,
    val engineType: String = "",
    val engineDisplacement: String = "",
    val transmission: String = "",
    val fuelType: String = "",
    val mileageInput: String = "",
    val mileageUnit: MileageUnit = MileageUnit.KM,
    val market: String = "United States",
    val photoUri: String = "",
    val anglePhotos: Map<VehicleAngle, String> = emptyMap(),
    val aiInspectionSummary: String = "",
    val setAsPrimary: Boolean = false,
    val specSource: SpecSource = SpecSource.USER_ENTERED,
    val unknownFields: Set<String> = emptySet(),
    val electricalSystemType: String = "UNKNOWN",
    val batteryChemistryType: String = "UNSPECIFIED"
)

data class VehicleValidationResult(
    val isValid: Boolean,
    val fieldErrors: Map<String, String> = emptyMap(),
    val parsedYear: Int? = null,
    val parsedMileage: Long? = null
)

object VehicleValidator {
    const val FIELD_MANUFACTURER = "manufacturer"
    const val FIELD_MODEL = "model"
    const val FIELD_MODEL_YEAR = "modelYear"
    const val FIELD_TRIM = "trim"
    const val FIELD_ENGINE_TYPE = "engineType"
    const val FIELD_ENGINE_DISPLACEMENT = "engineDisplacement"
    const val FIELD_TRANSMISSION = "transmission"
    const val FIELD_FUEL_TYPE = "fuelType"
    const val FIELD_MILEAGE = "mileage"
    const val FIELD_MARKET = "market"

    fun validate(draft: VehicleDraft): VehicleValidationResult {
        val errors = mutableMapOf<String, String>()

        val manufacturer = draft.manufacturer.trim()
        if (manufacturer.isEmpty()) {
            errors[FIELD_MANUFACTURER] = "Manufacturer is required."
        } else if (manufacturer.length > 80) {
            errors[FIELD_MANUFACTURER] = "Manufacturer must be 80 characters or fewer."
        }

        val model = draft.model.trim()
        if (model.isEmpty()) {
            errors[FIELD_MODEL] = "Model is required."
        } else if (model.length > 80) {
            errors[FIELD_MODEL] = "Model must be 80 characters or fewer."
        }

        val parsedYear = draft.modelYearInput.trim().toIntOrNull()
        if (draft.modelYearInput.isBlank()) {
            errors[FIELD_MODEL_YEAR] = "Model year is required."
        } else if (parsedYear == null || parsedYear !in MIN_MODEL_YEAR..MAX_MODEL_YEAR) {
            errors[FIELD_MODEL_YEAR] = "Enter a valid model year ($MIN_MODEL_YEAR–$MAX_MODEL_YEAR)."
        }

        val trim = draft.trim.trim()
        if (trim.isEmpty()) {
            errors[FIELD_TRIM] = "Trim / variant is required (or mark Unknown)."
        } else if (trim.length > 100) {
            errors[FIELD_TRIM] = "Trim must be 100 characters or fewer."
        }

        val effectiveEngine = if (FIELD_ENGINE_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.engineType.trim()
        if (effectiveEngine.isEmpty()) {
            errors[FIELD_ENGINE_TYPE] = "Engine type is required (or toggle Unknown)."
        } else if (effectiveEngine.length > 80) {
            errors[FIELD_ENGINE_TYPE] = "Engine type must be 80 characters or fewer."
        }

        if (draft.engineDisplacement.trim().length > 40) {
            errors[FIELD_ENGINE_DISPLACEMENT] = "Displacement must be 40 characters or fewer."
        }

        val effectiveTransmission = if (FIELD_TRANSMISSION in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.transmission.trim()
        if (effectiveTransmission.isEmpty()) {
            errors[FIELD_TRANSMISSION] = "Transmission is required (or toggle Unknown)."
        } else if (effectiveTransmission.length > 80) {
            errors[FIELD_TRANSMISSION] = "Transmission must be 80 characters or fewer."
        }

        val effectiveFuel = if (FIELD_FUEL_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.fuelType.trim()
        if (effectiveFuel.isEmpty()) {
            errors[FIELD_FUEL_TYPE] = "Fuel type is required (or toggle Unknown)."
        } else if (effectiveFuel.length > 60) {
            errors[FIELD_FUEL_TYPE] = "Fuel type must be 60 characters or fewer."
        }

        val parsedMileage = draft.mileageInput.trim().replace(",", "").toLongOrNull()
        if (draft.mileageInput.isBlank()) {
            errors[FIELD_MILEAGE] = "Current mileage is required."
        } else if (parsedMileage == null || parsedMileage !in 0L..MAX_MILEAGE) {
            errors[FIELD_MILEAGE] = "Enter a valid mileage (0–9,999,999)."
        }

        val market = draft.market.trim()
        if (market.isEmpty()) {
            errors[FIELD_MARKET] = "Market / country is required."
        } else if (market.length > 64) {
            errors[FIELD_MARKET] = "Market must be 64 characters or fewer."
        }

        return VehicleValidationResult(
            isValid = errors.isEmpty(),
            fieldErrors = errors,
            parsedYear = parsedYear,
            parsedMileage = parsedMileage
        )
    }
}
