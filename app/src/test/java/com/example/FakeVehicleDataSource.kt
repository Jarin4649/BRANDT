package com.example

import com.example.data.model.AttachmentKind
import com.example.data.model.MileageUnit
import com.example.data.model.SavedObdSessionRecord
import com.example.data.model.SpecSource
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.UserProfile
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleDiagnosisLog
import com.example.data.model.VehicleDraft
import com.example.data.model.VehicleValidator
import com.example.data.model.VerificationStatus
import com.example.data.repository.VehicleDataSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Stateful in-memory data source that enforces strict owner isolation per authenticatedUserId,
 * allowing deterministic Robolectric testing of ViewModels, Compose CUJs, reload persistence,
 * and multi-user access control.
 */
class FakeVehicleDataSource(
    var activeAuthenticatedUserId: String? = null
) : VehicleDataSource {

    private val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    private val vehiclesByOwner = MutableStateFlow<Map<String, List<Vehicle>>>(emptyMap())
    private val logsByOwnerAndVehicle = MutableStateFlow<Map<Pair<String, String>, List<VehicleDiagnosisLog>>>(emptyMap())
    private val obdSessionsByOwnerAndVehicle = MutableStateFlow<Map<Pair<String, String>, List<SavedObdSessionRecord>>>(emptyMap())

    private fun requireAuthorized(expectedUserId: String) {
        val active = activeAuthenticatedUserId
            ?: throw SecurityException("Unauthenticated access denied.")
        if (active != expectedUserId) {
            throw SecurityException("Cross-user access denied for $active -> $expectedUserId")
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> =
        profiles.map { map ->
            requireAuthorized(userId)
            map[userId]
        }

    override fun observeVehicles(userId: String): Flow<List<Vehicle>> =
        vehiclesByOwner.map { map ->
            requireAuthorized(userId)
            (map[userId] ?: emptyList()).sortedWith(
                compareByDescending<Vehicle> { it.primary }
                    .thenByDescending { it.modelYear }
            )
        }

    override fun observeVehicle(userId: String, vehicleId: String): Flow<Vehicle?> =
        vehiclesByOwner.map { map ->
            requireAuthorized(userId)
            map[userId]?.firstOrNull { it.id == vehicleId }
        }

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        profiles.update { current ->
            if (current.containsKey(userId)) current
            else current + (userId to UserProfile(id = userId, userId = userId, displayName = displayName))
        }
    }

    override suspend fun createVehicle(userId: String, draft: VehicleDraft): Result<String> = runCatching {
        requireAuthorized(userId)
        val validation = VehicleValidator.validate(draft)
        require(validation.isValid) { validation.fieldErrors.values.first() }

        val existingList = vehiclesByOwner.value[userId].orEmpty()
        val shouldBePrimary = draft.setAsPrimary || existingList.isEmpty()
        val id = "veh_${UUID.randomUUID().toString().take(8)}"
        val verification = when (draft.specSource) {
            SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
            SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
            SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
        }
        val serializedAngles = draft.anglePhotos.filterValues { it.isNotBlank() }.mapKeys { it.key.name }
        val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
            serializedAngles[VehicleAngle.FRONT.name] ?: serializedAngles.values.firstOrNull().orEmpty()
        }
        val now = Timestamp.now()
        val newVehicle = Vehicle(
            id = id,
            userId = userId,
            manufacturer = draft.manufacturer.trim(),
            model = draft.model.trim(),
            modelYear = validation.parsedYear!!,
            trim = draft.trim.trim(),
            bodyStyle = draft.bodyStyle.name,
            engineType = if (VehicleValidator.FIELD_ENGINE_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.engineType.trim(),
            engineDisplacement = if (VehicleValidator.FIELD_ENGINE_DISPLACEMENT in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.engineDisplacement.trim(),
            transmission = if (VehicleValidator.FIELD_TRANSMISSION in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.transmission.trim(),
            fuelType = if (VehicleValidator.FIELD_FUEL_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.fuelType.trim(),
            mileage = validation.parsedMileage!!,
            mileageUnit = draft.mileageUnit.code,
            market = draft.market.trim(),
            photoUri = resolvedPrimaryPhoto,
            anglePhotos = serializedAngles,
            aiInspectionSummary = draft.aiInspectionSummary.trim(),
            primary = shouldBePrimary,
            specSource = draft.specSource.code,
            verificationStatus = verification.code,
            unknownFields = draft.unknownFields.toList(),
            createdAt = now,
            updatedAt = now
        )

        vehiclesByOwner.update { current ->
            val updatedOwnerList = if (shouldBePrimary) {
                existingList.map { it.copy(primary = false) } + newVehicle
            } else {
                existingList + newVehicle
            }
            current + (userId to updatedOwnerList)
        }
        id
    }

    override suspend fun updateVehicle(userId: String, existing: Vehicle, draft: VehicleDraft): Result<Unit> = runCatching {
        requireAuthorized(userId)
        require(existing.userId == userId) { "Ownership mismatch." }
        val validation = VehicleValidator.validate(draft)
        require(validation.isValid) { validation.fieldErrors.values.first() }
        val verification = when (draft.specSource) {
            SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
            SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
            SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
        }
        val serializedAngles = draft.anglePhotos.filterValues { it.isNotBlank() }.mapKeys { it.key.name }
        val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
            serializedAngles[VehicleAngle.FRONT.name] ?: serializedAngles.values.firstOrNull().orEmpty()
        }

        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty()
            val updatedList = list.map { item ->
                when {
                    item.id == existing.id -> item.copy(
                        manufacturer = draft.manufacturer.trim(),
                        model = draft.model.trim(),
                        modelYear = validation.parsedYear!!,
                        trim = draft.trim.trim(),
                        bodyStyle = draft.bodyStyle.name,
                        engineType = if (VehicleValidator.FIELD_ENGINE_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.engineType.trim(),
                        engineDisplacement = if (VehicleValidator.FIELD_ENGINE_DISPLACEMENT in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.engineDisplacement.trim(),
                        transmission = if (VehicleValidator.FIELD_TRANSMISSION in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.transmission.trim(),
                        fuelType = if (VehicleValidator.FIELD_FUEL_TYPE in draft.unknownFields) UNKNOWN_SPEC_LABEL else draft.fuelType.trim(),
                        mileage = validation.parsedMileage!!,
                        mileageUnit = draft.mileageUnit.code,
                        market = draft.market.trim(),
                        photoUri = resolvedPrimaryPhoto,
                        anglePhotos = serializedAngles,
                        aiInspectionSummary = draft.aiInspectionSummary.trim(),
                        primary = draft.setAsPrimary,
                        specSource = draft.specSource.code,
                        verificationStatus = verification.code,
                        unknownFields = draft.unknownFields.toList(),
                        updatedAt = Timestamp.now()
                    )
                    draft.setAsPrimary -> item.copy(primary = false)
                    else -> item
                }
            }
            current + (userId to updatedList)
        }
    }

    override suspend fun updateMileage(
        userId: String,
        vehicleId: String,
        mileage: Long,
        unit: MileageUnit
    ): Result<Unit> = runCatching {
        requireAuthorized(userId)
        require(mileage in 0L..9_999_999L)
        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty().map {
                if (it.id == vehicleId) it.copy(mileage = mileage, mileageUnit = unit.code, updatedAt = Timestamp.now())
                else it
            }
            current + (userId to list)
        }
    }

    override suspend fun updatePhoto(userId: String, vehicleId: String, photoUri: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty().map {
                if (it.id == vehicleId) it.copy(photoUri = photoUri, updatedAt = Timestamp.now())
                else it
            }
            current + (userId to list)
        }
    }

    override suspend fun updateStudioAngles(
        userId: String,
        vehicleId: String,
        anglePhotos: Map<VehicleAngle, String>,
        primaryPhotoUri: String,
        aiInspectionSummary: String
    ): Result<Unit> = runCatching {
        requireAuthorized(userId)
        val serialized = anglePhotos.filterValues { it.isNotBlank() }.mapKeys { it.key.name }
        val resolvedPrimary = primaryPhotoUri.trim().ifBlank {
            serialized[VehicleAngle.FRONT.name] ?: serialized.values.firstOrNull().orEmpty()
        }
        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty().map {
                if (it.id == vehicleId) {
                    it.copy(
                        anglePhotos = serialized,
                        photoUri = resolvedPrimary,
                        aiInspectionSummary = aiInspectionSummary.trim(),
                        updatedAt = Timestamp.now()
                    )
                } else {
                    it
                }
            }
            current + (userId to list)
        }
    }

    override suspend fun setPrimaryVehicle(userId: String, vehicleId: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty()
            require(list.any { it.id == vehicleId }) { "Vehicle not found" }
            val updated = list.map { it.copy(primary = it.id == vehicleId) }
            current + (userId to updated)
        }
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        vehiclesByOwner.update { current ->
            val list = current[userId].orEmpty()
            val target = list.firstOrNull { it.id == vehicleId } ?: return@update current
            val remaining = list.filterNot { it.id == vehicleId }
            val adjusted = if (target.primary && remaining.isNotEmpty()) {
                remaining.mapIndexed { idx, v -> if (idx == 0) v.copy(primary = true) else v }
            } else {
                remaining
            }
            current + (userId to adjusted)
        }
        logsByOwnerAndVehicle.update { it - (userId to vehicleId) }
        obdSessionsByOwnerAndVehicle.update { it - (userId to vehicleId) }
    }

    override fun observeDiagnosisLogs(userId: String, vehicleId: String): Flow<List<VehicleDiagnosisLog>> =
        logsByOwnerAndVehicle.map { map ->
            requireAuthorized(userId)
            map[userId to vehicleId].orEmpty()
        }

    override suspend fun addDiagnosisLog(
        userId: String,
        vehicleId: String,
        userPrompt: String,
        aiSummary: String,
        attachmentKind: AttachmentKind,
        attachmentLabel: String,
        modelCommonality: String,
        followUpQuestion: String
    ): Result<String> = runCatching {
        requireAuthorized(userId)
        val cleanPrompt = userPrompt.trim().take(800).ifEmpty { attachmentKind.label }
        val cleanSummary = aiSummary.trim().take(1600)
        require(cleanSummary.isNotEmpty())
        val logId = "log_${UUID.randomUUID().toString().take(8)}"
        val now = Timestamp.now()
        val entry = VehicleDiagnosisLog(
            id = logId,
            userId = userId,
            vehicleId = vehicleId,
            userPrompt = cleanPrompt,
            aiSummary = cleanSummary,
            attachmentType = attachmentKind.code,
            attachmentLabel = attachmentLabel.trim().take(120),
            modelCommonality = modelCommonality.trim().take(80),
            followUpQuestion = followUpQuestion.trim().take(300),
            resolved = false,
            createdAt = now,
            updatedAt = now
        )
        logsByOwnerAndVehicle.update { current ->
            val key = userId to vehicleId
            current + (key to (listOf(entry) + current[key].orEmpty()))
        }
        logId
    }

    override suspend fun toggleDiagnosisLogResolved(
        userId: String,
        vehicleId: String,
        logId: String,
        resolved: Boolean
    ): Result<Unit> = runCatching {
        requireAuthorized(userId)
        logsByOwnerAndVehicle.update { current ->
            val key = userId to vehicleId
            val updated = current[key].orEmpty().map {
                if (it.id == logId) it.copy(resolved = resolved, updatedAt = Timestamp.now()) else it
            }
            current + (key to updated)
        }
    }

    override suspend fun deleteDiagnosisLog(userId: String, vehicleId: String, logId: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        logsByOwnerAndVehicle.update { current ->
            val key = userId to vehicleId
            val updated = current[key].orEmpty().filterNot { it.id == logId }
            current + (key to updated)
        }
    }

    override fun observeObdSessions(userId: String, vehicleId: String): Flow<List<SavedObdSessionRecord>> =
        obdSessionsByOwnerAndVehicle.map { map ->
            requireAuthorized(userId)
            map[userId to vehicleId].orEmpty()
        }

    override suspend fun saveObdSession(
        userId: String,
        vehicleId: String,
        record: SavedObdSessionRecord
    ): Result<String> = runCatching {
        requireAuthorized(userId)
        require(vehicleId.isNotBlank())
        val id = "obd_${UUID.randomUUID().toString().take(8)}"
        val now = Timestamp.now()
        val stored = record.copy(
            id = id,
            userId = userId,
            vehicleId = vehicleId,
            readOnlyVerified = true,
            createdAt = now,
            updatedAt = now
        )
        obdSessionsByOwnerAndVehicle.update { current ->
            val key = userId to vehicleId
            current + (key to (listOf(stored) + current[key].orEmpty()))
        }
        id
    }

    override suspend fun deleteObdSession(userId: String, vehicleId: String, sessionId: String): Result<Unit> = runCatching {
        requireAuthorized(userId)
        obdSessionsByOwnerAndVehicle.update { current ->
            val key = userId to vehicleId
            val updated = current[key].orEmpty().filterNot { it.id == sessionId }
            current + (key to updated)
        }
    }
}
