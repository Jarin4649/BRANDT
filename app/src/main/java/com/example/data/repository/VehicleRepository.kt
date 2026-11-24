package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface VehicleDataSource {
    fun observeUserProfile(userId: String): Flow<UserProfile?>
    fun observeVehicles(userId: String): Flow<List<Vehicle>>
    fun observeVehicle(userId: String, vehicleId: String): Flow<Vehicle?>
    suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit>
    suspend fun createVehicle(userId: String, draft: VehicleDraft): Result<String>
    suspend fun updateVehicle(userId: String, existing: Vehicle, draft: VehicleDraft): Result<Unit>
    suspend fun updateMileage(userId: String, vehicleId: String, mileage: Long, unit: MileageUnit): Result<Unit>
    suspend fun updatePhoto(userId: String, vehicleId: String, photoUri: String): Result<Unit>
    suspend fun updateStudioAngles(
        userId: String,
        vehicleId: String,
        anglePhotos: Map<VehicleAngle, String>,
        primaryPhotoUri: String,
        aiInspectionSummary: String
    ): Result<Unit>
    suspend fun setPrimaryVehicle(userId: String, vehicleId: String): Result<Unit>
    suspend fun deleteVehicle(userId: String, vehicleId: String): Result<Unit>
    fun observeDiagnosisLogs(userId: String, vehicleId: String): Flow<List<VehicleDiagnosisLog>>
    suspend fun addDiagnosisLog(
        userId: String,
        vehicleId: String,
        userPrompt: String,
        aiSummary: String,
        attachmentKind: AttachmentKind,
        attachmentLabel: String = "",
        modelCommonality: String = "",
        followUpQuestion: String = ""
    ): Result<String>
    suspend fun toggleDiagnosisLogResolved(
        userId: String,
        vehicleId: String,
        logId: String,
        resolved: Boolean
    ): Result<Unit>
    suspend fun deleteDiagnosisLog(userId: String, vehicleId: String, logId: String): Result<Unit>
    fun observeObdSessions(userId: String, vehicleId: String): Flow<List<SavedObdSessionRecord>>
    suspend fun saveObdSession(userId: String, vehicleId: String, record: SavedObdSessionRecord): Result<String>
    suspend fun deleteObdSession(userId: String, vehicleId: String, sessionId: String): Result<Unit>
}

class FirestoreVehicleRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : VehicleDataSource {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing BRANDT.")
        check(uid == expectedUserId) {
            "Signed-in user changed; refusing cross-user Firestore operation."
        }
        return uid
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override fun observeVehicles(userId: String): Flow<List<Vehicle>> = flow {
        val path = "users/$userId/vehicles"
        emitAll(
            db.collection("users").document(userId).collection("vehicles")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Vehicle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Vehicle> { it.primary }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                                .thenByDescending { it.modelYear }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override fun observeVehicle(userId: String, vehicleId: String): Flow<Vehicle?> = flow {
        val path = "users/$userId/vehicles/$vehicleId"
        emitAll(
            db.collection("users").document(userId)
                .collection("vehicles").document(vehicleId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Vehicle::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().take(100).ifEmpty { "BRANDT Owner" }
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "preferredMarket" to "United States",
                        "preferredMileageUnit" to MileageUnit.KM.code,
                        "primaryVehicleId" to "",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    override suspend fun createVehicle(userId: String, draft: VehicleDraft): Result<String> {
        val collectionPath = "users/$userId/vehicles"
        return try {
            val uid = requireUserId(userId)
            val validation = VehicleValidator.validate(draft)
            require(validation.isValid) {
                validation.fieldErrors.values.firstOrNull() ?: "Invalid vehicle fields."
            }

            val vehiclesRef = db.collection("users").document(uid).collection("vehicles")
            val existingDocs = vehiclesRef.get().await().documents
            val shouldBePrimary = draft.setAsPrimary || existingDocs.isEmpty()

            val newDocRef = vehiclesRef.document()
            val profileRef = db.collection("users").document(uid)
            val profileSnap = profileRef.get().await()

            val verification = when (draft.specSource) {
                SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
                SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
                SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
            }

            val serializedAngles = draft.anglePhotos
                .filterValues { it.isNotBlank() }
                .mapKeys { it.key.name }
                .mapValues { it.value.trim().take(1000) }
            val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
                serializedAngles[VehicleAngle.FRONT.name]
                    ?: serializedAngles.values.firstOrNull().orEmpty()
            }.take(1000)

            val payload = mapOf(
                "userId" to uid,
                "manufacturer" to draft.manufacturer.trim(),
                "model" to draft.model.trim(),
                "modelYear" to (validation.parsedYear ?: 2025),
                "trim" to draft.trim.trim(),
                "bodyStyle" to draft.bodyStyle.name,
                "engineType" to resolveSpecValue(draft.engineType, VehicleValidator.FIELD_ENGINE_TYPE, draft.unknownFields),
                "engineDisplacement" to resolveOptionalDisplacement(draft.engineDisplacement, draft.unknownFields),
                "transmission" to resolveSpecValue(draft.transmission, VehicleValidator.FIELD_TRANSMISSION, draft.unknownFields),
                "fuelType" to resolveSpecValue(draft.fuelType, VehicleValidator.FIELD_FUEL_TYPE, draft.unknownFields),
                "mileage" to (validation.parsedMileage ?: 0L),
                "mileageUnit" to draft.mileageUnit.code,
                "market" to draft.market.trim(),
                "photoUri" to resolvedPrimaryPhoto,
                "anglePhotos" to serializedAngles,
                "aiInspectionSummary" to draft.aiInspectionSummary.trim().take(600),
                "primary" to shouldBePrimary,
                "specSource" to draft.specSource.code,
                "verificationStatus" to verification.code,
                "unknownFields" to draft.unknownFields.toList().take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            if (shouldBePrimary) {
                existingDocs.forEach { doc ->
                    if (doc.getBoolean("primary") == true) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "primary" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
                if (profileSnap.exists()) {
                    batch.update(
                        profileRef,
                        mapOf(
                            "primaryVehicleId" to newDocRef.id,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        profileRef,
                        mapOf(
                            "userId" to uid,
                            "displayName" to (auth.currentUser?.displayName?.take(100) ?: "BRANDT Owner"),
                            "preferredMarket" to draft.market.trim().take(64),
                            "preferredMileageUnit" to draft.mileageUnit.code,
                            "primaryVehicleId" to newDocRef.id,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.set(newDocRef, payload)
            batch.commit().await()
            Result.success(newDocRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, collectionPath)
            Result.failure(e)
        }
    }

    override suspend fun updateVehicle(userId: String, existing: Vehicle, draft: VehicleDraft): Result<Unit> {
        val docPath = "users/$userId/vehicles/${existing.id}"
        return try {
            val uid = requireUserId(userId)
            require(existing.id.isNotBlank()) { "Vehicle ID must not be blank for update." }
            val validation = VehicleValidator.validate(draft)
            require(validation.isValid) {
                validation.fieldErrors.values.firstOrNull() ?: "Invalid vehicle fields."
            }

            val vehiclesRef = db.collection("users").document(uid).collection("vehicles")
            val docRef = vehiclesRef.document(existing.id)
            val verification = when (draft.specSource) {
                SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
                SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
                SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
            }

            val serializedAngles = draft.anglePhotos
                .filterValues { it.isNotBlank() }
                .mapKeys { it.key.name }
                .mapValues { it.value.trim().take(1000) }
            val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
                serializedAngles[VehicleAngle.FRONT.name]
                    ?: serializedAngles.values.firstOrNull().orEmpty()
            }.take(1000)

            val updateMap = mapOf(
                "manufacturer" to draft.manufacturer.trim(),
                "model" to draft.model.trim(),
                "modelYear" to (validation.parsedYear ?: existing.modelYear),
                "trim" to draft.trim.trim(),
                "bodyStyle" to draft.bodyStyle.name,
                "engineType" to resolveSpecValue(draft.engineType, VehicleValidator.FIELD_ENGINE_TYPE, draft.unknownFields),
                "engineDisplacement" to resolveOptionalDisplacement(draft.engineDisplacement, draft.unknownFields),
                "transmission" to resolveSpecValue(draft.transmission, VehicleValidator.FIELD_TRANSMISSION, draft.unknownFields),
                "fuelType" to resolveSpecValue(draft.fuelType, VehicleValidator.FIELD_FUEL_TYPE, draft.unknownFields),
                "mileage" to (validation.parsedMileage ?: existing.mileage),
                "mileageUnit" to draft.mileageUnit.code,
                "market" to draft.market.trim(),
                "photoUri" to resolvedPrimaryPhoto,
                "anglePhotos" to serializedAngles,
                "aiInspectionSummary" to draft.aiInspectionSummary.trim().take(600),
                "primary" to draft.setAsPrimary,
                "specSource" to draft.specSource.code,
                "verificationStatus" to verification.code,
                "unknownFields" to draft.unknownFields.toList().take(10),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            if (draft.setAsPrimary && !existing.primary) {
                val otherDocs = vehiclesRef.get().await().documents
                otherDocs.forEach { doc ->
                    if (doc.id != existing.id && doc.getBoolean("primary") == true) {
                        batch.update(
                            doc.reference,
                            mapOf("primary" to false, "updatedAt" to FieldValue.serverTimestamp())
                        )
                    }
                }
                val profileRef = db.collection("users").document(uid)
                if (profileRef.get().await().exists()) {
                    batch.update(
                        profileRef,
                        mapOf("primaryVehicleId" to existing.id, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }
            batch.update(docRef, updateMap)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateMileage(
        userId: String,
        vehicleId: String,
        mileage: Long,
        unit: MileageUnit
    ): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            require(mileage in 0L..9_999_999L) { "Mileage must be between 0 and 9,999,999." }

            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "mileage" to mileage,
                        "mileageUnit" to unit.code,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updatePhoto(userId: String, vehicleId: String, photoUri: String): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "photoUri" to photoUri.trim().take(1000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun updateStudioAngles(
        userId: String,
        vehicleId: String,
        anglePhotos: Map<VehicleAngle, String>,
        primaryPhotoUri: String,
        aiInspectionSummary: String
    ): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val serializedAngles = anglePhotos
                .filterValues { it.isNotBlank() }
                .mapKeys { it.key.name }
                .mapValues { it.value.trim().take(1000) }
            val resolvedPrimaryPhoto = primaryPhotoUri.trim().ifBlank {
                serializedAngles[VehicleAngle.FRONT.name]
                    ?: serializedAngles.values.firstOrNull().orEmpty()
            }.take(1000)

            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .update(
                    mapOf(
                        "anglePhotos" to serializedAngles,
                        "photoUri" to resolvedPrimaryPhoto,
                        "aiInspectionSummary" to aiInspectionSummary.trim().take(600),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun setPrimaryVehicle(userId: String, vehicleId: String): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val vehiclesRef = db.collection("users").document(uid).collection("vehicles")
            val docs = vehiclesRef.get().await().documents
            val targetExists = docs.any { it.id == vehicleId }
            require(targetExists) { "Selected vehicle not found in your garage." }

            val batch = db.batch()
            docs.forEach { doc ->
                val shouldBePrimary = doc.id == vehicleId
                val currentPrimary = doc.getBoolean("primary") == true
                if (currentPrimary != shouldBePrimary) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "primary" to shouldBePrimary,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            val profileRef = db.collection("users").document(uid)
            if (profileRef.get().await().exists()) {
                batch.update(
                    profileRef,
                    mapOf(
                        "primaryVehicleId" to vehicleId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val vehiclesRef = db.collection("users").document(uid).collection("vehicles")
            val docs = vehiclesRef.get().await().documents
            val targetDoc = docs.firstOrNull { it.id == vehicleId }
                ?: return Result.success(Unit)
            val wasPrimary = targetDoc.getBoolean("primary") == true
            val nextCandidate = docs.firstOrNull { it.id != vehicleId }

            val batch = db.batch()
            batch.delete(targetDoc.reference)
            if (wasPrimary) {
                if (nextCandidate != null) {
                    batch.update(
                        nextCandidate.reference,
                        mapOf(
                            "primary" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val profileRef = db.collection("users").document(uid)
                if (profileRef.get().await().exists()) {
                    batch.update(
                        profileRef,
                        mapOf(
                            "primaryVehicleId" to (nextCandidate?.id ?: ""),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun observeDiagnosisLogs(userId: String, vehicleId: String): Flow<List<VehicleDiagnosisLog>> = flow {
        val path = "users/$userId/vehicles/$vehicleId/diagnosisLogs"
        emitAll(
            db.collection("users").document(userId)
                .collection("vehicles").document(vehicleId)
                .collection("diagnosisLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VehicleDiagnosisLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
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
    ): Result<String> {
        val path = "users/$userId/vehicles/$vehicleId/diagnosisLogs"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val cleanPrompt = userPrompt.trim().take(800).ifEmpty { attachmentKind.label }
            val cleanSummary = aiSummary.trim().take(1600)
            require(cleanSummary.isNotEmpty()) { "AI summary must not be empty." }

            val logsRef = db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .collection("diagnosisLogs")
            val newDoc = logsRef.document()
            val payload = mapOf(
                "userId" to uid,
                "vehicleId" to vehicleId,
                "userPrompt" to cleanPrompt,
                "aiSummary" to cleanSummary,
                "attachmentType" to attachmentKind.code,
                "attachmentLabel" to attachmentLabel.trim().take(120),
                "modelCommonality" to modelCommonality.trim().take(80),
                "followUpQuestion" to followUpQuestion.trim().take(300),
                "resolved" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            newDoc.set(payload).await()
            Result.success(newDoc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun toggleDiagnosisLogResolved(
        userId: String,
        vehicleId: String,
        logId: String,
        resolved: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId/diagnosisLogs/$logId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank() && logId.isNotBlank()) { "Vehicle ID and Log ID are required." }
            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .collection("diagnosisLogs").document(logId)
                .update(
                    mapOf(
                        "resolved" to resolved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun deleteDiagnosisLog(
        userId: String,
        vehicleId: String,
        logId: String
    ): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId/diagnosisLogs/$logId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank() && logId.isNotBlank()) { "Vehicle ID and Log ID are required." }
            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .collection("diagnosisLogs").document(logId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun observeObdSessions(userId: String, vehicleId: String): Flow<List<SavedObdSessionRecord>> = flow {
        val path = "users/$userId/vehicles/$vehicleId/obdSessions"
        emitAll(
            db.collection("users").document(userId)
                .collection("vehicles").document(vehicleId)
                .collection("obdSessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedObdSessionRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    override suspend fun saveObdSession(
        userId: String,
        vehicleId: String,
        record: SavedObdSessionRecord
    ): Result<String> {
        val path = "users/$userId/vehicles/$vehicleId/obdSessions"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank()) { "Vehicle ID must not be blank." }
            val sessionsRef = db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .collection("obdSessions")
            val newDoc = sessionsRef.document()
            val payload = record.toCreateMap(uid, vehicleId) + mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            newDoc.set(payload).await()
            Result.success(newDoc.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    override suspend fun deleteObdSession(userId: String, vehicleId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/vehicles/$vehicleId/obdSessions/$sessionId"
        return try {
            val uid = requireUserId(userId)
            require(vehicleId.isNotBlank() && sessionId.isNotBlank()) { "Vehicle ID and Session ID are required." }
            db.collection("users").document(uid)
                .collection("vehicles").document(vehicleId)
                .collection("obdSessions").document(sessionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun resolveSpecValue(raw: String, fieldKey: String, unknownFields: Set<String>): String {
        return if (fieldKey in unknownFields) UNKNOWN_SPEC_LABEL else raw.trim()
    }

    private fun resolveOptionalDisplacement(raw: String, unknownFields: Set<String>): String {
        return if (VehicleValidator.FIELD_ENGINE_DISPLACEMENT in unknownFields) {
            UNKNOWN_SPEC_LABEL
        } else {
            raw.trim().take(40)
        }
    }
}

/**
 * In-memory temporary data source used solely for unauthenticated UI preview sessions.
 * Does not touch Cloud Firestore or persist data across app restarts.
 */
class InMemoryPreviewVehicleDataSource : VehicleDataSource {
    private val profileFlow = MutableStateFlow<UserProfile?>(null)
    private val vehiclesFlow = MutableStateFlow<List<Vehicle>>(emptyList())
    private val diagnosisLogsFlow = MutableStateFlow<List<VehicleDiagnosisLog>>(emptyList())
    private val obdSessionsFlow = MutableStateFlow<List<SavedObdSessionRecord>>(emptyList())

    override fun observeUserProfile(userId: String): Flow<UserProfile?> = profileFlow

    override fun observeVehicles(userId: String): Flow<List<Vehicle>> =
        vehiclesFlow.map { list ->
            list.filter { it.userId == userId }
                .sortedWith(
                    compareByDescending<Vehicle> { it.primary }
                        .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        .thenByDescending { it.modelYear }
                )
        }

    override fun observeVehicle(userId: String, vehicleId: String): Flow<Vehicle?> =
        vehiclesFlow.map { list -> list.firstOrNull { it.userId == userId && it.id == vehicleId } }

    override suspend fun ensureUserProfile(userId: String, displayName: String): Result<Unit> {
        if (profileFlow.value == null) {
            val now = Timestamp.now()
            profileFlow.value = UserProfile(
                userId = userId,
                displayName = displayName.trim().ifEmpty { "Preview Session" },
                preferredMarket = "United States",
                preferredMileageUnit = MileageUnit.KM.code,
                primaryVehicleId = "",
                createdAt = now,
                updatedAt = now
            )
        }
        return Result.success(Unit)
    }

    override suspend fun createVehicle(userId: String, draft: VehicleDraft): Result<String> {
        val validation = VehicleValidator.validate(draft)
        if (!validation.isValid) {
            return Result.failure(
                IllegalArgumentException(validation.fieldErrors.values.firstOrNull() ?: "Invalid vehicle fields.")
            )
        }

        val now = Timestamp.now()
        val id = "preview_${UUID.randomUUID().toString().take(8)}"
        val existing = vehiclesFlow.value
        val shouldBePrimary = draft.setAsPrimary || existing.none { it.userId == userId }
        val verification = when (draft.specSource) {
            SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
            SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
            SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
        }
        val serializedAngles = draft.anglePhotos
            .filterValues { it.isNotBlank() }
            .mapKeys { it.key.name }
            .mapValues { it.value.trim().take(1000) }
        val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
            serializedAngles[VehicleAngle.FRONT.name]
                ?: serializedAngles.values.firstOrNull().orEmpty()
        }.take(1000)

        val created = Vehicle(
            id = id,
            userId = userId,
            manufacturer = draft.manufacturer.trim(),
            model = draft.model.trim(),
            modelYear = validation.parsedYear ?: 2025,
            trim = draft.trim.trim(),
            bodyStyle = draft.bodyStyle.name,
            engineType = resolvePreviewSpec(draft.engineType, VehicleValidator.FIELD_ENGINE_TYPE, draft.unknownFields),
            engineDisplacement = resolvePreviewDisplacement(draft.engineDisplacement, draft.unknownFields),
            transmission = resolvePreviewSpec(draft.transmission, VehicleValidator.FIELD_TRANSMISSION, draft.unknownFields),
            fuelType = resolvePreviewSpec(draft.fuelType, VehicleValidator.FIELD_FUEL_TYPE, draft.unknownFields),
            mileage = validation.parsedMileage ?: 0L,
            mileageUnit = draft.mileageUnit.code,
            market = draft.market.trim(),
            photoUri = resolvedPrimaryPhoto,
            anglePhotos = serializedAngles,
            aiInspectionSummary = draft.aiInspectionSummary.trim().take(600),
            primary = shouldBePrimary,
            specSource = draft.specSource.code,
            verificationStatus = verification.code,
            unknownFields = draft.unknownFields.toList().take(10),
            electricalSystemType = draft.electricalSystemType.ifBlank { "UNKNOWN" },
            batteryChemistryType = draft.batteryChemistryType.ifBlank { "UNSPECIFIED" },
            createdAt = now,
            updatedAt = now
        )

        vehiclesFlow.update { current ->
            val updatedList = if (shouldBePrimary) {
                current.map { if (it.userId == userId) it.copy(primary = false) else it }
            } else {
                current
            }
            updatedList + created
        }
        if (shouldBePrimary) {
            profileFlow.update { it?.copy(primaryVehicleId = id, updatedAt = now) }
        }
        return Result.success(id)
    }

    override suspend fun updateVehicle(userId: String, existing: Vehicle, draft: VehicleDraft): Result<Unit> {
        val validation = VehicleValidator.validate(draft)
        if (!validation.isValid) {
            return Result.failure(
                IllegalArgumentException(validation.fieldErrors.values.firstOrNull() ?: "Invalid vehicle fields.")
            )
        }
        val now = Timestamp.now()
        val verification = when (draft.specSource) {
            SpecSource.DEMO_CATALOG_PREFILL -> VerificationStatus.DEMO_DATA_UNVERIFIED
            SpecSource.AI_VISION_PREFILL -> VerificationStatus.AI_EXTRACTED_UNVERIFIED
            SpecSource.USER_ENTERED -> VerificationStatus.UNVERIFIED_USER_DATA
        }
        val serializedAngles = draft.anglePhotos
            .filterValues { it.isNotBlank() }
            .mapKeys { it.key.name }
            .mapValues { it.value.trim().take(1000) }
        val resolvedPrimaryPhoto = draft.photoUri.trim().ifBlank {
            serializedAngles[VehicleAngle.FRONT.name]
                ?: serializedAngles.values.firstOrNull().orEmpty()
        }.take(1000)

        vehiclesFlow.update { current ->
            current.map { item ->
                when {
                    item.id == existing.id && item.userId == userId -> item.copy(
                        manufacturer = draft.manufacturer.trim(),
                        model = draft.model.trim(),
                        modelYear = validation.parsedYear ?: item.modelYear,
                        trim = draft.trim.trim(),
                        bodyStyle = draft.bodyStyle.name,
                        engineType = resolvePreviewSpec(draft.engineType, VehicleValidator.FIELD_ENGINE_TYPE, draft.unknownFields),
                        engineDisplacement = resolvePreviewDisplacement(draft.engineDisplacement, draft.unknownFields),
                        transmission = resolvePreviewSpec(draft.transmission, VehicleValidator.FIELD_TRANSMISSION, draft.unknownFields),
                        fuelType = resolvePreviewSpec(draft.fuelType, VehicleValidator.FIELD_FUEL_TYPE, draft.unknownFields),
                        mileage = validation.parsedMileage ?: item.mileage,
                        mileageUnit = draft.mileageUnit.code,
                        market = draft.market.trim(),
                        photoUri = resolvedPrimaryPhoto,
                        anglePhotos = serializedAngles,
                        aiInspectionSummary = draft.aiInspectionSummary.trim().take(600),
                        primary = draft.setAsPrimary,
                        specSource = draft.specSource.code,
                        verificationStatus = verification.code,
                        unknownFields = draft.unknownFields.toList().take(10),
                        electricalSystemType = draft.electricalSystemType.ifBlank { item.electricalSystemType },
                        batteryChemistryType = draft.batteryChemistryType.ifBlank { item.batteryChemistryType },
                        updatedAt = now
                    )
                    draft.setAsPrimary && item.userId == userId -> item.copy(primary = false)
                    else -> item
                }
            }
        }
        if (draft.setAsPrimary) {
            profileFlow.update { it?.copy(primaryVehicleId = existing.id, updatedAt = now) }
        }
        return Result.success(Unit)
    }

    override suspend fun updateMileage(
        userId: String,
        vehicleId: String,
        mileage: Long,
        unit: MileageUnit
    ): Result<Unit> {
        if (mileage !in 0L..9_999_999L) {
            return Result.failure(IllegalArgumentException("Mileage must be between 0 and 9,999,999."))
        }
        val now = Timestamp.now()
        vehiclesFlow.update { current ->
            current.map { item ->
                if (item.id == vehicleId && item.userId == userId) {
                    item.copy(mileage = mileage, mileageUnit = unit.code, updatedAt = now)
                } else {
                    item
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updatePhoto(userId: String, vehicleId: String, photoUri: String): Result<Unit> {
        val now = Timestamp.now()
        vehiclesFlow.update { current ->
            current.map { item ->
                if (item.id == vehicleId && item.userId == userId) {
                    item.copy(photoUri = photoUri.trim().take(1000), updatedAt = now)
                } else {
                    item
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateStudioAngles(
        userId: String,
        vehicleId: String,
        anglePhotos: Map<VehicleAngle, String>,
        primaryPhotoUri: String,
        aiInspectionSummary: String
    ): Result<Unit> {
        val now = Timestamp.now()
        val serializedAngles = anglePhotos
            .filterValues { it.isNotBlank() }
            .mapKeys { it.key.name }
            .mapValues { it.value.trim().take(1000) }
        val resolvedPrimaryPhoto = primaryPhotoUri.trim().ifBlank {
            serializedAngles[VehicleAngle.FRONT.name]
                ?: serializedAngles.values.firstOrNull().orEmpty()
        }.take(1000)

        vehiclesFlow.update { current ->
            current.map { item ->
                if (item.id == vehicleId && item.userId == userId) {
                    item.copy(
                        anglePhotos = serializedAngles,
                        photoUri = resolvedPrimaryPhoto,
                        aiInspectionSummary = aiInspectionSummary.trim().take(600),
                        updatedAt = now
                    )
                } else {
                    item
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun setPrimaryVehicle(userId: String, vehicleId: String): Result<Unit> {
        val now = Timestamp.now()
        vehiclesFlow.update { current ->
            current.map { item ->
                if (item.userId == userId) {
                    item.copy(primary = item.id == vehicleId, updatedAt = now)
                } else {
                    item
                }
            }
        }
        profileFlow.update { it?.copy(primaryVehicleId = vehicleId, updatedAt = now) }
        return Result.success(Unit)
    }

    override suspend fun deleteVehicle(userId: String, vehicleId: String): Result<Unit> {
        val now = Timestamp.now()
        vehiclesFlow.update { current ->
            val target = current.firstOrNull { it.id == vehicleId && it.userId == userId }
            val remaining = current.filterNot { it.id == vehicleId && it.userId == userId }
            if (target?.primary == true) {
                val firstRemaining = remaining.firstOrNull { it.userId == userId }
                if (firstRemaining != null) {
                    remaining.map { item ->
                        if (item.id == firstRemaining.id) item.copy(primary = true, updatedAt = now) else item
                    }
                } else {
                    remaining
                }
            } else {
                remaining
            }
        }
        diagnosisLogsFlow.update { current ->
            current.filterNot { it.userId == userId && it.vehicleId == vehicleId }
        }
        obdSessionsFlow.update { current ->
            current.filterNot { it.userId == userId && it.vehicleId == vehicleId }
        }
        return Result.success(Unit)
    }

    override fun observeDiagnosisLogs(userId: String, vehicleId: String): Flow<List<VehicleDiagnosisLog>> =
        diagnosisLogsFlow.map { logs ->
            logs.filter { it.userId == userId && it.vehicleId == vehicleId }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
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
    ): Result<String> {
        val cleanPrompt = userPrompt.trim().take(800).ifEmpty { attachmentKind.label }
        val cleanSummary = aiSummary.trim().take(1600)
        if (cleanSummary.isEmpty()) {
            return Result.failure(IllegalArgumentException("AI summary must not be empty."))
        }
        val now = Timestamp.now()
        val logId = "preview_log_${UUID.randomUUID().toString().take(8)}"
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
        diagnosisLogsFlow.update { listOf(entry) + it }
        return Result.success(logId)
    }

    override suspend fun toggleDiagnosisLogResolved(
        userId: String,
        vehicleId: String,
        logId: String,
        resolved: Boolean
    ): Result<Unit> {
        val now = Timestamp.now()
        diagnosisLogsFlow.update { logs ->
            logs.map { log ->
                if (log.id == logId && log.userId == userId && log.vehicleId == vehicleId) {
                    log.copy(resolved = resolved, updatedAt = now)
                } else {
                    log
                }
            }
        }
        return Result.success(Unit)
    }

    override suspend fun deleteDiagnosisLog(userId: String, vehicleId: String, logId: String): Result<Unit> {
        diagnosisLogsFlow.update { logs ->
            logs.filterNot { it.id == logId && it.userId == userId && it.vehicleId == vehicleId }
        }
        return Result.success(Unit)
    }

    override fun observeObdSessions(userId: String, vehicleId: String): Flow<List<SavedObdSessionRecord>> =
        obdSessionsFlow.map { sessions ->
            sessions.filter { it.userId == userId && it.vehicleId == vehicleId }
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }

    override suspend fun saveObdSession(
        userId: String,
        vehicleId: String,
        record: SavedObdSessionRecord
    ): Result<String> {
        val now = Timestamp.now()
        val id = "preview_obd_${UUID.randomUUID().toString().take(8)}"
        val sanitized = record.copy(
            id = id,
            userId = userId,
            vehicleId = vehicleId,
            readOnlyVerified = true,
            createdAt = now,
            updatedAt = now
        )
        obdSessionsFlow.update { listOf(sanitized) + it }
        return Result.success(id)
    }

    override suspend fun deleteObdSession(userId: String, vehicleId: String, sessionId: String): Result<Unit> {
        obdSessionsFlow.update { sessions ->
            sessions.filterNot { it.id == sessionId && it.userId == userId && it.vehicleId == vehicleId }
        }
        return Result.success(Unit)
    }

    private fun resolvePreviewSpec(raw: String, fieldKey: String, unknownFields: Set<String>): String {
        return if (fieldKey in unknownFields) UNKNOWN_SPEC_LABEL else raw.trim()
    }

    private fun resolvePreviewDisplacement(raw: String, unknownFields: Set<String>): String {
        return if (VehicleValidator.FIELD_ENGINE_DISPLACEMENT in unknownFields) {
            UNKNOWN_SPEC_LABEL
        } else {
            raw.trim().take(40)
        }
    }
}
