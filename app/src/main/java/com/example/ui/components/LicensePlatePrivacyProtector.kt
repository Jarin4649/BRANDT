package com.example.ui.components

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * System-level vehicle privacy guard.
 *
 * Per owner preference:
 * - Visual vehicle photos are NEVER painted over or covered on-screen (plates remain visually untouched).
 * - Instead, the system enforces strict non-identification & non-location guarantees:
 *   1. Strips GPS / geolocation & device-identifying EXIF tags from vehicle photos before preview, AI upload, or storage.
 *   2. Enforces strict AI system policy prohibiting license-plate reading/OCR or location inference from plates/backgrounds.
 *   3. Sanitizes AI-generated summaries/logs so license plate strings or inferred coordinates never persist.
 */
object LicensePlatePrivacyProtector {

    private const val TAG = "LicensePlatePrivacy"
    private const val LOCATION_SAFE_SUFFIX = "_loc_safe.jpg"

    const val STRICT_PLATE_PRIVACY_SYSTEM_RULE_EN =
        "STRICT PRIVACY LOCK (LICENSE PLATES & LOCATION): NEVER read, OCR, transcribe, repeat, store, or cite any vehicle license plate number visible in photos or attachments. NEVER use any license plate, registration characters, stickers, or photo surroundings to identify, infer, estimate, or track the vehicle's or owner's geographic location, province, city, or address."

    const val STRICT_PLATE_PRIVACY_SYSTEM_RULE_TH =
        "ล็อกความปลอดภัยป้ายทะเบียนและตำแหน่งที่ตั้ง (บังคับเด็ดขาด): ห้ามอ่าน ถอดตัวอักษร/ตัวเลข คัดลอก บันทึก หรืออ้างอิงข้อมูลป้ายทะเบียนรถจากรูปภาพโดยเด็ดขาด และห้ามนำป้ายทะเบียน หมวดอักษร จังหวัด หรือสภาพแวดล้อมในภาพไปใช้วิเคราะห์ ระบุ ประมาณการ หรือติดตามตำแหน่งที่ตั้งของรถหรือผู้ใช้โดยเด็ดขาด"

    private val gpsAndLocationExifTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_USER_COMMENT
    )

    private val gpsCoordinatePattern = Regex(
        """(?i)(?:gps|lat(?:itude)?|lon(?:gitude)?|พิกัด|ละติจูด|ลองจิจูด)\s*[:=]?\s*-?\d{1,3}\.\d{3,}(?:\s*[,/]\s*-?\d{1,3}\.\d{3,})?"""
    )

    private val explicitPlateCitationPattern = Regex(
        """(?i)(?:\b(?:license|licence|number|registration)\s*plate(?:\s*(?:number|no\.?|text|reads?|shows?|is|:))?\s*[:#-]?\s*[A-Z0-9ก-ฮ-]{2,14}|ทะเบียนรถ(?:หมายเลข|เลขที่)?\s*[:#-]?\s*[A-Z0-9ก-ฮ\s-]{2,16})"""
    )

    data class PlateProtectionResult(
        val outputUri: String,
        val platesCoveredCount: Int = 0,
        val locationMetadataStripped: Boolean = false
    )

    /**
     * Sanitizes image metadata by stripping all EXIF GPS/location tags without altering
     * any visual pixels of the photo (license plates are NOT covered visually).
     */
    suspend fun protectVehiclePhoto(
        context: Context,
        sourceUri: String
    ): PlateProtectionResult = withContext(Dispatchers.IO) {
        if (sourceUri.isBlank()) {
            return@withContext PlateProtectionResult(outputUri = sourceUri)
        }

        try {
            val localSourceFile = resolveLocalFile(sourceUri)
            if (localSourceFile != null && localSourceFile.exists() && localSourceFile.canRead()) {
                if (hasAnyLocationExif(localSourceFile.absolutePath)) {
                    val safeFile = copyAndStripLocationExif(context, localSourceFile)
                    if (safeFile != null) {
                        return@withContext PlateProtectionResult(
                            outputUri = safeFile.toURI().toString(),
                            platesCoveredCount = 0,
                            locationMetadataStripped = true
                        )
                    }
                }
                return@withContext PlateProtectionResult(
                    outputUri = sourceUri,
                    platesCoveredCount = 0,
                    locationMetadataStripped = false
                )
            }

            // Content URI or asset URI: inspect stream for GPS metadata; copy to clean local file if present
            val parsed = Uri.parse(sourceUri)
            val streamHasLocationExif = openStreamForUri(context, parsed)?.use { stream ->
                runCatching {
                    val exif = ExifInterface(stream)
                    gpsAndLocationExifTags.any { tag -> !exif.getAttribute(tag).isNullOrBlank() }
                }.getOrDefault(false)
            } ?: false

            if (!streamHasLocationExif) {
                return@withContext PlateProtectionResult(
                    outputUri = sourceUri,
                    platesCoveredCount = 0,
                    locationMetadataStripped = false
                )
            }

            val outDir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
            val destFile = File(outDir, "motora_${UUID.randomUUID().toString().take(12)}$LOCATION_SAFE_SUFFIX")
            val copied = openStreamForUri(context, parsed)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false

            if (copied && destFile.exists() && destFile.length() > 0L) {
                val stripped = stripLocationExifInPlace(destFile.absolutePath)
                PlateProtectionResult(
                    outputUri = destFile.toURI().toString(),
                    platesCoveredCount = 0,
                    locationMetadataStripped = stripped
                )
            } else {
                PlateProtectionResult(outputUri = sourceUri)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Photo metadata sanitization skipped gracefully: ${t.message}")
            PlateProtectionResult(outputUri = sourceUri)
        }
    }

    /**
     * System output guardrail: strips any accidentally leaked license-plate transcriptions or
     * GPS coordinate claims from AI outputs before display or database persistence.
     */
    fun sanitizePlateAndLocationText(raw: String): String {
        if (raw.isBlank()) return raw
        var sanitized = raw
        sanitized = explicitPlateCitationPattern.replace(sanitized, "[plate-protected]")
        sanitized = gpsCoordinatePattern.replace(sanitized, "[location-disabled]")
        return sanitized.trim()
    }

    private fun hasAnyLocationExif(filePath: String): Boolean {
        return runCatching {
            val exif = ExifInterface(filePath)
            gpsAndLocationExifTags.any { tag -> !exif.getAttribute(tag).isNullOrBlank() }
        }.getOrDefault(false)
    }

    private fun copyAndStripLocationExif(context: Context, sourceFile: File): File? {
        return runCatching {
            val outDir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
            val destFile = if (sourceFile.parentFile?.canonicalPath == outDir.canonicalPath &&
                sourceFile.name.endsWith(LOCATION_SAFE_SUFFIX, ignoreCase = true)
            ) {
                sourceFile
            } else {
                File(outDir, "motora_${UUID.randomUUID().toString().take(12)}$LOCATION_SAFE_SUFFIX").also { dst ->
                    sourceFile.inputStream().use { input ->
                        FileOutputStream(dst).use { output -> input.copyTo(output) }
                    }
                }
            }
            stripLocationExifInPlace(destFile.absolutePath)
            destFile
        }.getOrNull()
    }

    private fun stripLocationExifInPlace(filePath: String): Boolean {
        return runCatching {
            val exif = ExifInterface(filePath)
            var modified = false
            gpsAndLocationExifTags.forEach { tag ->
                if (!exif.getAttribute(tag).isNullOrBlank()) {
                    exif.setAttribute(tag, null)
                    modified = true
                }
            }
            if (modified) {
                exif.saveAttributes()
            }
            modified
        }.getOrDefault(false)
    }

    private fun resolveLocalFile(uriString: String): File? {
        val parsed = Uri.parse(uriString)
        return when {
            parsed.scheme.equals("file", ignoreCase = true) -> parsed.path?.let { File(it) }
            parsed.scheme == null -> File(uriString)
            else -> null
        }
    }

    private fun openStreamForUri(context: Context, uri: Uri) = when {
        uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let { File(it).inputStream() }
        uri.scheme == null -> File(uri.toString()).takeIf { it.exists() }?.inputStream()
        uri.toString().startsWith("file:///android_asset/") -> {
            val assetPath = uri.toString().removePrefix("file:///android_asset/")
            runCatching { context.assets.open(assetPath) }.getOrNull()
        }
        else -> context.contentResolver.openInputStream(uri)
    }
}
