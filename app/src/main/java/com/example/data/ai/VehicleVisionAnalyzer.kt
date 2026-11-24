package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.example.data.model.BodyStyle
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.VehicleAngle
import com.example.ui.components.LicensePlatePrivacyProtector
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

data class VehicleVisionResult(
    val manufacturer: String,
    val model: String,
    val modelYearGuess: String,
    val bodyStyle: BodyStyle,
    val fuelTypeHint: String,
    val trimOrBadgeHints: String,
    val exteriorSummary: String,
    val angleCoverageOk: Boolean,
    val qualityFeedback: String
)

interface VehicleVisionAnalyzer {
    suspend fun analyzeVehicleAngles(
        context: Context,
        anglePhotos: Map<VehicleAngle, String>
    ): Result<VehicleVisionResult>
}

class FirebaseVehicleVisionAnalyzer : VehicleVisionAnalyzer {

    private val schema = Schema.obj(
        properties = mapOf(
            "manufacturer" to Schema.string("Vehicle make/brand e.g. Mitsubishi, Toyota, Honda, Volvo, BMW, or '$UNKNOWN_SPEC_LABEL'"),
            "model" to Schema.string("Vehicle model name e.g. Mirage, Civic, EX30, or '$UNKNOWN_SPEC_LABEL'"),
            "modelYearGuess" to Schema.string("4-digit approximate model year or empty string"),
            "bodyStyle" to Schema.string("One of: CROSSOVER, SEDAN, COUPE, HATCHBACK, SUV, TRUCK, WAGON"),
            "fuelTypeHint" to Schema.string("Likely fuel type e.g. Petrol / Gasoline, Electric, Hybrid, Diesel, or '$UNKNOWN_SPEC_LABEL'"),
            "trimOrBadgeHints" to Schema.string("Visible model/trim badge text ONLY (never license plate numbers) or '$UNKNOWN_SPEC_LABEL'"),
            "exteriorSummary" to Schema.string("Short 1-2 sentence factual description of car color and body design. NEVER include license plates or geographic location/city/province."),
            "angleCoverageOk" to Schema.boolean("True if a vehicle is clearly visible in the photo."),
            "qualityFeedback" to Schema.string("Short helpful tip if photo is dark or unclear, else empty string.")
        )
    )

    private val structuredModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                temperature = 0.2f
                maxOutputTokens = 768
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    private val fastFallbackModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.2f
                maxOutputTokens = 768
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.MINIMAL
                }
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    override suspend fun analyzeVehicleAngles(
        context: Context,
        anglePhotos: Map<VehicleAngle, String>
    ): Result<VehicleVisionResult> {
        if (anglePhotos.isEmpty()) {
            return Result.failure(IllegalArgumentException("Attach or select a vehicle photo first."))
        }

        // Prioritize Front and Rear (max 2 photos) for fast, reliable multimodal inference
        val prioritizedEntries = buildList {
            anglePhotos[VehicleAngle.FRONT]?.let { add(VehicleAngle.FRONT to it) }
            anglePhotos[VehicleAngle.REAR]?.let { add(VehicleAngle.REAR to it) }
            if (isEmpty()) {
                anglePhotos.entries.take(2).forEach { add(it.key to it.value) }
            }
        }

        return try {
            val encodedImages = withContext(Dispatchers.IO) {
                prioritizedEntries.mapNotNull { (angle, path) ->
                    encodeScaledJpeg(context, path, maxDim = 512, quality = 78)?.let { bytes ->
                        angle to bytes
                    }
                }
            }

            if (encodedImages.isEmpty()) {
                return Result.failure(IllegalStateException("Unable to read the selected vehicle image file."))
            }

            val promptText = """
                You are an automotive identification assistant for BRANDT.
                Inspect the vehicle photo(s) carefully — look closely at the front grille emblem/logo, headlight shape, bumper, side profile, and rear tailgate badges — and identify the car.
                Rules:
                - Identify manufacturer (brand) and model whenever emblems or body design are recognizable.
                - Return a 4-digit modelYearGuess if recognizable, otherwise "".
                - For bodyStyle return one of: CROSSOVER, SEDAN, COUPE, HATCHBACK, SUV, TRUCK, WAGON.
                - Never invent odometer mileage or engine displacement.
                - ${LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_EN}
                - Only return "$UNKNOWN_SPEC_LABEL" if the car genuinely cannot be recognized.
                - Return ONLY a valid JSON object with keys: manufacturer, model, modelYearGuess, bodyStyle, fuelTypeHint, trimOrBadgeHints, exteriorSummary, angleCoverageOk, qualityFeedback.
            """.trimIndent()

            val promptContent = content {
                text(promptText)
                encodedImages.forEach { (angle, jpegBytes) ->
                    text("Photo (${angle.displayTitle}):")
                    inlineData(jpegBytes, "image/jpeg")
                }
            }

            val rawText = withContext(Dispatchers.IO) {
                runPrimaryWithFastFallback(promptContent)
            } ?: return Result.failure(IllegalStateException("Gemini Vision returned an empty response."))

            Result.success(parseVisionJson(rawText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Vision vehicle analysis timed out", e)
            Result.failure(
                IllegalStateException(
                    "AI analysis timed out. Please verify emulator internet access and tap 'Auto-Fill Vehicle Fields with AI' to try again."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Vision vehicle analysis failed", e)
            val causeMsg = e.cause?.localizedMessage?.takeIf {
                it.isNotBlank() && it != e.localizedMessage
            }
            val detail = buildString {
                append(e.localizedMessage ?: e.javaClass.simpleName)
                if (causeMsg != null) {
                    append(" (")
                    append(causeMsg)
                    append(")")
                }
            }
            Result.failure(IllegalStateException("${e.javaClass.simpleName}: $detail", e))
        }
    }

    private suspend fun runPrimaryWithFastFallback(promptContent: com.google.firebase.ai.type.Content): String? {
        val primaryError: Exception? = try {
            val streamResult = streamWithTimeout(timeoutMs = 24_000L) {
                val collected = StringBuilder()
                structuredModel.generateContentStream(promptContent).collect { chunk ->
                    chunk.text?.let { collected.append(it) }
                }
                collected.toString()
            }
            if (streamResult.isNotBlank()) return streamResult
            IllegalStateException("Primary model returned an empty stream.")
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Primary gemini-3.8-flash timed out at 24s; retrying with gemini-3.5-flash-lite", e)
            e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isFatalAuthOrCheckError(e)) throw e
            Log.w(TAG, "Primary structured vision failed, retrying with gemini-3.5-flash-lite", e)
            e
        }

        val fallbackText = streamWithTimeout(timeoutMs = 24_000L) {
            val collected = StringBuilder()
            fastFallbackModel.generateContentStream(promptContent).collect { chunk ->
                chunk.text?.let { collected.append(it) }
            }
            collected.toString()
        }
        return fallbackText.ifBlank {
            if (primaryError != null) throw primaryError else null
        }
    }

    private suspend fun <T> streamWithTimeout(timeoutMs: Long, block: suspend () -> T): T {
        return withTimeout(timeoutMs) { block() }
    }

    private fun isFatalAuthOrCheckError(err: Exception): Boolean {
        val msg = err.message.orEmpty()
        return msg.contains("403") ||
            msg.contains("App Check", ignoreCase = true) ||
            msg.contains("attestation", ignoreCase = true) ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private fun encodeScaledJpeg(
        context: Context,
        path: String,
        maxDim: Int = 512,
        quality: Int = 78
    ): ByteArray? {
        val bitmap = decodeScaledBitmap(context, path, maxDim = maxDim) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray().takeIf { it.isNotEmpty() }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun decodeScaledBitmap(context: Context, path: String, maxDim: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStreamOrFileBounds(context, path, boundsOptions)
            val largest = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (largest / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = decodeBitmap(context, path, decodeOptions) ?: return null
            val scale = max(raw.width, raw.height).toFloat() / maxDim.toFloat()
            val resized = if (scale > 1.0f) {
                val targetW = (raw.width / scale).toInt().coerceAtLeast(1)
                val targetH = (raw.height / scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
                if (scaled != raw) raw.recycle()
                scaled
            } else {
                raw
            }
            if (resized.hasAlpha()) {
                val flattened = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(resized, 0f, 0f, null)
                resized.recycle()
                flattened
            } else {
                resized
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode vehicle angle image: $path", e)
            null
        }
    }

    private fun resolveLocalFile(path: String): File? {
        val direct = File(path)
        if (direct.exists()) return direct
        val parsed = Uri.parse(path)
        if (parsed.scheme == "file") {
            val uriPath = parsed.path
            if (!uriPath.isNullOrBlank()) {
                val fromUri = File(uriPath)
                if (fromUri.exists()) return fromUri
            }
        }
        return null
    }

    private fun openStreamOrFileBounds(context: Context, path: String, options: BitmapFactory.Options) {
        val file = resolveLocalFile(path)
        if (file != null) {
            BitmapFactory.decodeFile(file.absolutePath, options)
        } else {
            context.contentResolver.openInputStream(Uri.parse(path))?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
    }

    private fun decodeBitmap(context: Context, path: String, options: BitmapFactory.Options): Bitmap? {
        val file = resolveLocalFile(path)
        if (file != null) {
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }
        return context.contentResolver.openInputStream(Uri.parse(path))?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    companion object {
        private const val TAG = "VehicleVisionAI"

        fun parseVisionJson(rawText: String): VehicleVisionResult {
            val jsonText = extractJsonObject(rawText)
            val json = JSONObject(jsonText)
            return VehicleVisionResult(
                manufacturer = normalizeVisionText(json.optString("manufacturer", "")),
                model = normalizeVisionText(json.optString("model", "")),
                modelYearGuess = json.optString("modelYearGuess", "").trim().filter { it.isDigit() }.take(4),
                bodyStyle = BodyStyle.fromCode(json.optString("bodyStyle", BodyStyle.CROSSOVER.name)),
                fuelTypeHint = normalizeVisionText(json.optString("fuelTypeHint", "")),
                trimOrBadgeHints = LicensePlatePrivacyProtector.sanitizePlateAndLocationText(
                    normalizeVisionText(json.optString("trimOrBadgeHints", ""))
                ),
                exteriorSummary = LicensePlatePrivacyProtector.sanitizePlateAndLocationText(
                    json.optString("exteriorSummary", "").trim().take(580)
                ),
                angleCoverageOk = json.optBoolean("angleCoverageOk", true),
                qualityFeedback = LicensePlatePrivacyProtector.sanitizePlateAndLocationText(
                    json.optString("qualityFeedback", "").trim().take(240)
                )
            )
        }

        private fun extractJsonObject(raw: String): String {
            val trimmed = raw.trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            return if (start >= 0 && end > start) {
                trimmed.substring(start, end + 1)
            } else {
                trimmed
            }
        }

        private fun normalizeVisionText(raw: String): String {
            val trimmed = raw.trim()
            return when {
                trimmed.isBlank() -> UNKNOWN_SPEC_LABEL
                trimmed.equals("unknown", ignoreCase = true) -> UNKNOWN_SPEC_LABEL
                trimmed.equals("n/a", ignoreCase = true) -> UNKNOWN_SPEC_LABEL
                trimmed.equals("none", ignoreCase = true) -> UNKNOWN_SPEC_LABEL
                trimmed.equals("null", ignoreCase = true) -> UNKNOWN_SPEC_LABEL
                else -> trimmed
            }
        }
    }
}

