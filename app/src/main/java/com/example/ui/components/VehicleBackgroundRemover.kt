package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.sdkinternal.OptionalModuleUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Performs real on-device foreground segmentation via Google ML Kit Subject Segmentation
 * to isolate the user's vehicle photo (prioritizing the Right Side angle) on a clean
 * transparent cutout backdrop. Falls back to uniform studio/perimeter flood-fill removal
 * if ML Kit is unavailable (e.g., missing Play Services optional module or JVM test),
 * and gracefully to the original URI if the background cannot be cleanly separated.
 */
object VehicleBackgroundRemover {

    private const val TAG = "VehicleBgRemover"
    private const val MAX_ANALYSIS_EDGE_PX = 1280

    suspend fun extractVehicleCutoutUri(
        context: Context,
        rawPhotoUri: String
    ): String = withContext(Dispatchers.IO) {
        if (rawPhotoUri.isBlank()) return@withContext ""

        val cacheDir = File(context.cacheDir, "vehicle_cutouts").apply { mkdirs() }
        val cacheKey = sha256(rawPhotoUri)
        val cachedPng = File(cacheDir, "${cacheKey}_v2_cutout.png")
        if (cachedPng.exists() && cachedPng.length() > 1024L) {
            return@withContext cachedPng.toURI().toString()
        }

        val sourceBitmap = decodeOrientedBitmap(context, rawPhotoUri) ?: return@withContext rawPhotoUri
        try {
            // If image already has meaningful alpha transparency (e.g., pre-cut PNG), trim and cache it directly.
            if (hasSignificantTransparency(sourceBitmap)) {
                val trimmed = trimTransparentBounds(sourceBitmap)
                if (savePng(trimmed, cachedPng)) {
                    return@withContext cachedPng.toURI().toString()
                }
                return@withContext rawPhotoUri
            }

            val mlKitForeground = runSubjectSegmentation(context.applicationContext, sourceBitmap)
            if (mlKitForeground != null && !isMostlyTransparent(mlKitForeground)) {
                val refined = if (!hasSignificantTransparency(mlKitForeground)) {
                    removeUniformEdgeBackground(mlKitForeground) ?: mlKitForeground
                } else {
                    mlKitForeground
                }
                val trimmed = trimTransparentBounds(refined)
                if (savePng(trimmed, cachedPng)) {
                    return@withContext cachedPng.toURI().toString()
                }
            }

            // Secondary deterministic uniform-backdrop removal (studio white/gray/plain wall photos)
            val floodCutout = removeUniformEdgeBackground(sourceBitmap)
            if (floodCutout != null && !isMostlyTransparent(floodCutout)) {
                val trimmed = trimTransparentBounds(floodCutout)
                if (savePng(trimmed, cachedPng)) {
                    return@withContext cachedPng.toURI().toString()
                }
            }

            rawPhotoUri
        } catch (t: Throwable) {
            Log.w(TAG, "Falling back to original vehicle photo; background removal unavailable: ${t.message}")
            rawPhotoUri
        } finally {
            if (!sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
        }
    }

    private suspend fun runSubjectSegmentation(context: Context, bitmap: Bitmap): Bitmap? {
        val optionalModuleApi = OptionalModuleApi {
            arrayOf(OptionalModuleUtils.FEATURE_SUBJECT_SEGMENTATION)
        }
        val isModuleReady = checkOrRequestSubjectModule(context, optionalModuleApi)
        if (!isModuleReady) {
            return null
        }

        val segmenter = try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
            SubjectSegmentation.getClient(options)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to create SubjectSegmentation client: ${t.message}")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            val input = InputImage.fromBitmap(bitmap, 0)
            segmenter.process(input)
                .addOnSuccessListener { result ->
                    val fg = result.foregroundBitmap
                    runCatching { segmenter.close() }
                    if (continuation.isActive) continuation.resume(fg)
                }
                .addOnFailureListener { err ->
                    Log.w(TAG, "ML Kit SubjectSegmentation failed: ${err.message}")
                    runCatching { segmenter.close() }
                    if (continuation.isActive) continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                runCatching { segmenter.close() }
            }
        }
    }

    private suspend fun checkOrRequestSubjectModule(
        context: Context,
        optionalModuleApi: OptionalModuleApi
    ): Boolean {
        val client = runCatching { ModuleInstall.getClient(context) }.getOrNull() ?: return false
        val available = withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { continuation ->
                try {
                    client.areModulesAvailable(optionalModuleApi)
                        .addOnSuccessListener { response ->
                            if (continuation.isActive) {
                                continuation.resume(response.areModulesAvailable())
                            }
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume(false)
                        }
                } catch (_: Throwable) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false

        if (!available) {
            // Request optional module installation silently without instantiating MobileVisionBase
            runCatching {
                val installRequest = ModuleInstallRequest.newBuilder()
                    .addApi(optionalModuleApi)
                    .build()
                client.installModules(installRequest)
                    .addOnFailureListener { err ->
                        Log.d(TAG, "Optional ML Kit module install deferred: ${err.message}")
                    }
            }
        }
        return available
    }

    /**
     * Removes a connected uniform background starting from the outer perimeter/corners when
     * corners share a cohesive tone (such as studio white, light gray, or solid backdrop).
     * Returns null if edges appear busy/non-uniform so real outdoor car scenes aren't eroded.
     */
    private fun removeUniformEdgeBackground(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        if (w < 16 || h < 16) return null

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val cornerSamples = listOf(
            pixels[0],
            pixels[w - 1],
            pixels[(h - 1) * w],
            pixels[(h - 1) * w + (w - 1)],
            pixels[w / 2],
            pixels[(h - 1) * w + w / 2]
        ).filter { (it ushr 24 and 0xFF) > 180 }

        if (cornerSamples.size < 4) return null

        val avgR = cornerSamples.map { Color.red(it) }.average().toFloat()
        val avgG = cornerSamples.map { Color.green(it) }.average().toFloat()
        val avgB = cornerSamples.map { Color.blue(it) }.average().toFloat()

        val maxCornerSpread = cornerSamples.maxOf {
            colorDistance(it, avgR, avgG, avgB)
        }
        if (maxCornerSpread > 48f) {
            return null
        }

        val hardThreshold = 34f
        val softThreshold = 52f
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0

        fun tryEnqueue(idx: Int) {
            if (!visited[idx]) {
                val p = pixels[idx]
                val alpha = (p ushr 24) and 0xFF
                if (alpha <= 20 || colorDistance(p, avgR, avgG, avgB) <= hardThreshold) {
                    visited[idx] = true
                    queue[tail++] = idx
                }
            }
        }

        for (x in 0 until w) {
            tryEnqueue(x)
            tryEnqueue((h - 1) * w + x)
        }
        for (y in 0 until h) {
            tryEnqueue(y * w)
            tryEnqueue(y * w + (w - 1))
        }

        var removedCount = 0
        while (head < tail) {
            val curr = queue[head++]
            val x = curr % w
            val y = curr / w
            pixels[curr] = pixels[curr] and 0x00FFFFFF
            removedCount++

            if (x > 0) tryEnqueue(curr - 1)
            if (x + 1 < w) tryEnqueue(curr + 1)
            if (y > 0) tryEnqueue(curr - w)
            if (y + 1 < h) tryEnqueue(curr + w)
        }

        val totalPixels = w * h
        // Require at least 8% and at most 92% background removal so valid subjects stay intact.
        if (removedCount < totalPixels * 0.08f || removedCount > totalPixels * 0.92f) {
            return null
        }

        // Feather a 1-pixel rim along the transparent boundary for smooth edges.
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                if (visited[idx]) continue
                val touchesBg = visited[idx - 1] || visited[idx + 1] || visited[idx - w] || visited[idx + w]
                if (touchesBg) {
                    val dist = colorDistance(pixels[idx], avgR, avgG, avgB)
                    if (dist < softThreshold) {
                        val t = ((dist - hardThreshold * 0.5f) / (softThreshold - hardThreshold * 0.5f))
                            .coerceIn(0f, 1f)
                        val oldAlpha = (pixels[idx] ushr 24) and 0xFF
                        val newAlpha = (oldAlpha * t).roundToInt().coerceIn(0, 255)
                        pixels[idx] = (pixels[idx] and 0x00FFFFFF) or (newAlpha shl 24)
                    }
                }
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun colorDistance(pixel: Int, r: Float, g: Float, b: Float): Float {
        val dr = Color.red(pixel) - r
        val dg = Color.green(pixel) - g
        val db = Color.blue(pixel) - b
        return sqrt(dr * dr + dg * dg + db * db)
    }

    private fun decodeOrientedBitmap(context: Context, uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStreamForUri(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            val origW = bounds.outWidth
            val origH = bounds.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            val longest = max(origW, origH)
            while (longest / sampleSize > MAX_ANALYSIS_EDGE_PX) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = openStreamForUri(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return null

            val rotation = readExifRotationDegrees(context, uri)
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun openStreamForUri(context: Context, uri: Uri) = when {
        uri.scheme.equals("file", ignoreCase = true) -> uri.path?.let { File(it).inputStream() }
        uri.scheme == null -> File(uri.toString()).takeIf { it.exists() }?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun readExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            val orientation = openStreamForUri(context, uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (_: Throwable) {
            0f
        }
    }

    private fun hasSignificantTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 24).coerceAtLeast(1)
        val stepY = (h / 24).coerceAtLeast(1)
        var transparentCount = 0
        var totalSampled = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha < 30) transparentCount++
                totalSampled++
                x += stepX
            }
            y += stepY
        }
        return totalSampled > 0 && (transparentCount.toFloat() / totalSampled) > 0.12f
    }

    private fun isMostlyTransparent(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 20).coerceAtLeast(1)
        val stepY = (h / 20).coerceAtLeast(1)
        var opaqueCount = 0
        var total = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val alpha = (bitmap.getPixel(x, y) ushr 24) and 0xFF
                if (alpha > 40) opaqueCount++
                total++
                x += stepX
            }
            y += stepY
        }
        return total == 0 || (opaqueCount.toFloat() / total) < 0.04f
    }

    private fun trimTransparentBounds(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                val alpha = (pixels[rowOffset + x] ushr 24) and 0xFF
                if (alpha > 20) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX <= minX || maxY <= minY) return bitmap
        val padX = ((maxX - minX) * 0.04f).roundToInt()
        val padY = ((maxY - minY) * 0.04f).roundToInt()
        val cropLeft = (minX - padX).coerceAtLeast(0)
        val cropTop = (minY - padY).coerceAtLeast(0)
        val cropRight = (maxX + padX).coerceAtMost(w - 1)
        val cropBottom = (maxY + padY).coerceAtMost(h - 1)
        val cropW = (cropRight - cropLeft + 1).coerceAtLeast(1)
        val cropH = (cropBottom - cropTop + 1).coerceAtLeast(1)
        if (abs(cropW - w) < w * 0.04f && abs(cropH - h) < h * 0.04f) return bitmap
        return Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
    }

    private fun savePng(bitmap: Bitmap, dest: File): Boolean {
        return try {
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            dest.exists() && dest.length() > 0L
        } catch (_: Throwable) {
            false
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}
