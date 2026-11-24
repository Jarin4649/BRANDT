package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.BodyStyle
import com.example.data.model.VehicleAngle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import java.io.File
import java.util.UUID

/**
 * Persists a Photo Picker URI into app-internal storage so the vehicle photo
 * remains accessible across restarts without broad storage permissions.
 */
fun persistVehiclePhotoToAppStorage(context: Context, sourceUri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
        val destination = File(dir, "motora_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (destination.exists() && destination.length() > 0L) {
            destination.toURI().toString()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

fun createVehicleCaptureFile(context: Context, angle: VehicleAngle): File {
    val dir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
    return File(dir, "motora_${angle.name.lowercase()}_${UUID.randomUUID().toString().take(8)}.jpg")
}

suspend fun downloadVehiclePhotoToAppStorage(context: Context, rawUrl: String): Result<String> =
    withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawUrl.trim()
            if (trimmed.startsWith("data:image/", ignoreCase = true) && ";base64," in trimmed) {
                val base64Payload = trimmed.substringAfter(";base64,")
                val bytes = Base64.decode(base64Payload, Base64.DEFAULT)
                require(bytes.isNotEmpty() && bytes.size <= 10 * 1024 * 1024) {
                    "Base64 image is empty or exceeds 10 MB limit."
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "Pasted Base64 data is not a valid image."
                }
                val dir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
                val destination = File(dir, "motora_b64_${UUID.randomUUID().toString().take(8)}.jpg")
                destination.writeBytes(bytes)
                return@runCatching destination.toURI().toString()
            }

            val parsed = Uri.parse(trimmed)
            require(parsed.scheme.equals("https", ignoreCase = true) || parsed.scheme.equals("http", ignoreCase = true)) {
                "Enter a valid http://, https://, or data:image base64 string."
            }
            val connection = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MOTORA-Android/1.0")
            }
            try {
                val status = connection.responseCode
                require(status in 200..299) { "Image download failed (HTTP $status)." }

                val maxBytes = 10 * 1024 * 1024 // 10 MB safety limit
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                connection.inputStream.use { input ->
                    while (true) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        total += read
                        require(total <= maxBytes) { "Image exceeds 10 MB limit." }
                        buffer.write(chunk, 0, read)
                    }
                }
                val bytes = buffer.toByteArray()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "URL did not return a valid readable image."
                }

                val dir = File(context.filesDir, "vehicle_photos").apply { mkdirs() }
                val destination = File(dir, "motora_url_${UUID.randomUUID().toString().take(8)}.jpg")
                destination.writeBytes(bytes)
                destination.toURI().toString()
            } finally {
                connection.disconnect()
            }
        }
    }

data class BundledPcPhoto(
    val fileName: String,
    val localFileUri: String
)

suspend fun loadBundledPcUploadPhotos(context: Context): List<BundledPcPhoto> =
    withContext(Dispatchers.IO) {
        val assetFiles = try {
            context.assets.list("pc_import")?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val supportedExts = setOf("jpg", "jpeg", "png", "webp")
        val imageNames = assetFiles.filter { name ->
            val ext = name.substringAfterLast('.', "").lowercase()
            ext in supportedExts
        }.sorted()

        if (imageNames.isEmpty()) return@withContext emptyList()

        val destDir = File(context.filesDir, "vehicle_photos/pc_import").apply { mkdirs() }
        imageNames.mapNotNull { fileName ->
            try {
                val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val targetFile = File(destDir, safeName)
                context.assets.open("pc_import/$fileName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    BundledPcPhoto(
                        fileName = fileName,
                        localFileUri = targetFile.toURI().toString()
                    )
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

fun autoMapBundledPhotosToAngles(photos: List<BundledPcPhoto>): Map<VehicleAngle, String> {
    if (photos.isEmpty()) return emptyMap()
    val assigned = linkedMapOf<VehicleAngle, String>()
    val remaining = photos.toMutableList()

    val rules: List<Pair<VehicleAngle, List<String>>> = listOf(
        VehicleAngle.FRONT to listOf("front", "head", "หน้า"),
        VehicleAngle.SIDE_LEFT to listOf("left", "side_l", "ซ้าย"),
        VehicleAngle.REAR to listOf("rear", "back", "tail", "หลัง"),
        VehicleAngle.SIDE_RIGHT to listOf("right", "side_r", "ขวา")
    )

    for ((angle, keywords) in rules) {
        val matchIdx = remaining.indexOfFirst { item ->
            val lower = item.fileName.lowercase()
            keywords.any { kw -> lower.contains(kw) }
        }
        if (matchIdx >= 0) {
            assigned[angle] = remaining.removeAt(matchIdx).localFileUri
        }
    }

    for (angle in VehicleAngle.entries) {
        if (angle !in assigned && remaining.isNotEmpty()) {
            assigned[angle] = remaining.removeAt(0).localFileUri
        }
    }
    return assigned
}

@Composable
fun VehicleVisualStage(
    photoUri: String,
    bodyStyle: BodyStyle,
    contentDescription: String,
    modifier: Modifier = Modifier,
    showSilhouetteBadge: Boolean = true
) {
    val surfaceTop = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceBottom = MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceTop, surfaceBottom)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (photoUri.isNotBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VehicleStudioSilhouette(
                bodyStyle = bodyStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
            if (showSilhouetteBadge) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = "${bodyStyle.displayName} Studio Profile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

private fun resolveNearestVehicleAngle(normalizedDeg: Float): Pair<VehicleAngle, Float> {
    val d = ((normalizedDeg % 360f) + 360f) % 360f
    return when {
        d < 45f -> VehicleAngle.FRONT to d
        d >= 315f -> VehicleAngle.FRONT to (d - 360f)
        d < 135f -> VehicleAngle.SIDE_LEFT to (d - 90f)
        d < 225f -> VehicleAngle.REAR to (d - 180f)
        else -> VehicleAngle.SIDE_RIGHT to (d - 270f)
    }
}

private fun shortestAngularDelta(fromNormalized: Float, toDegrees: Float): Float {
    var diff = (toDegrees - fromNormalized) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}

@Composable
fun InteractiveVehicleStudioStage(
    anglePhotos: Map<VehicleAngle, String>,
    fallbackPhotoUri: String,
    bodyStyle: BodyStyle,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onCaptureMissingAngle: ((VehicleAngle) -> Unit)? = null
) {
    val angles = VehicleAngle.entries
    val initialAngle = remember(anglePhotos) {
        angles.firstOrNull { !anglePhotos[it].isNullOrBlank() } ?: VehicleAngle.FRONT
    }
    var unwrappedTargetDegrees by remember(anglePhotos.keys) {
        mutableFloatStateOf(initialAngle.degrees.toFloat())
    }
    var autoSpin by remember { mutableStateOf(false) }

    LaunchedEffect(autoSpin) {
        while (autoSpin) {
            delay(20L)
            unwrappedTargetDegrees += 1.6f
        }
    }

    val animatedDegrees by animateFloatAsState(
        targetValue = unwrappedTargetDegrees,
        animationSpec = tween(durationMillis = if (autoSpin) 16 else 220),
        label = "turntable_orbit"
    )

    val immediateNormalized = ((unwrappedTargetDegrees % 360f) + 360f) % 360f
    val (activeAngle, _) = resolveNearestVehicleAngle(immediateNormalized)

    val animatedNormalized = ((animatedDegrees % 360f) + 360f) % 360f
    val (visualAngle, deltaFromAnchor) = resolveNearestVehicleAngle(animatedNormalized)

    val anglePhoto = anglePhotos[visualAngle].orEmpty()
    val effectivePhoto = when {
        anglePhoto.isNotBlank() -> anglePhoto
        anglePhotos.isEmpty() && visualAngle == VehicleAngle.FRONT && fallbackPhotoUri.isNotBlank() -> fallbackPhotoUri
        else -> ""
    }

    val surfaceTop = MaterialTheme.colorScheme.surfaceContainerHigh
    val surfaceBottom = MaterialTheme.colorScheme.surfaceContainerLowest
    val ringTint = MaterialTheme.colorScheme.primary
    val subtleRingTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(surfaceTop, surfaceBottom)
                    )
                )
                .pointerInput(angles.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { autoSpin = false },
                        onHorizontalDrag = { _, dragAmount ->
                            unwrappedTargetDegrees -= dragAmount * 0.48f
                        }
                    )
                }
                .testTag("interactive_studio_viewport"),
            contentAlignment = Alignment.Center
        ) {
            // Showroom 3D Perspective Floor Orbit Ring
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.62f
                val rx = size.width * 0.44f
                val ry = size.height * 0.28f

                drawOval(
                    color = subtleRingTint,
                    topLeft = Offset(cx - rx, cy - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 2.5f)
                )

                val rad = Math.toRadians((animatedNormalized - 90f).toDouble())
                val dotX = cx + (rx * cos(rad)).toFloat()
                val dotY = cy + (ry * sin(rad)).toFloat()
                drawCircle(
                    color = ringTint,
                    radius = 7f,
                    center = Offset(dotX, dotY)
                )
            }

            // 3D Perspective Tilting Content Stage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = -deltaFromAnchor * 0.42f
                        cameraDistance = 14f * density
                        val depthScale = 1f - (abs(deltaFromAnchor) / 45f) * 0.06f
                        scaleX = depthScale
                        scaleY = depthScale
                        alpha = 1f - (abs(deltaFromAnchor) / 45f) * 0.14f
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = visualAngle to effectivePhoto,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "studio_angle_transition"
                ) { (angle, photo) ->
                    if (photo.isNotBlank()) {
                        AsyncImage(
                            model = photo,
                            contentDescription = "$contentDescription - ${angle.displayTitle}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            VehicleStudioSilhouette(
                                bodyStyle = bodyStyle,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .let { base ->
                                        if (onCaptureMissingAngle != null) {
                                            base.clickable { onCaptureMissingAngle(angle) }
                                        } else {
                                            base
                                        }
                                    }
                            ) {
                                Text(
                                    text = if (onCaptureMissingAngle != null) {
                                        "${angle.shortLabel} Not Captured • Tap to Capture"
                                    } else {
                                        "${angle.shortLabel} Studio Silhouette"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top-left 360 degree indicator pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ViewInAr,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${activeAngle.degrees}° ${activeAngle.shortLabel.uppercase()} • ${anglePhotos.size}/4 ANGLES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("studio_active_angle_label")
                    )
                }
            }

            // Top-right Auto-Spin & Rotate controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (autoSpin) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    },
                    modifier = Modifier
                        .clickable { autoSpin = !autoSpin }
                        .testTag("studio_autospin_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (autoSpin) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Toggle 360 auto-spin",
                            tint = if (autoSpin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (autoSpin) "Spinning" else "Auto-Spin",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (autoSpin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.clickable {
                        autoSpin = false
                        val nextAngle = angles[(angles.indexOf(activeAngle) + 1) % angles.size]
                        unwrappedTargetDegrees += shortestAngularDelta(immediateNormalized, nextAngle.degrees.toFloat())
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RotateRight,
                            contentDescription = "Next angle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${animatedNormalized.toInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Angle selector pills (0° / 90° / 180° / 270°)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            angles.forEach { angle ->
                val isSelected = angle == activeAngle
                val hasPhoto = !anglePhotos[angle].isNullOrBlank()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            autoSpin = false
                            unwrappedTargetDegrees += shortestAngularDelta(immediateNormalized, angle.degrees.toFloat())
                        }
                        .testTag("studio_angle_tab_${angle.name}")
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${angle.degrees}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (hasPhoto) "${angle.shortLabel} •" else angle.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else if (hasPhoto) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleAngleFramingOverlay(
    angle: VehicleAngle,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val guideColor = Color.White.copy(alpha = 0.72f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = w * 0.1f
        val padY = h * 0.16f
        val boxW = w - padX * 2f
        val boxH = h - padY * 2f
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)

        // Studio viewport frame
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(padX, padY),
            size = Size(boxW, boxH),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3f, pathEffect = dashEffect)
        )

        // Ground alignment horizon
        val groundY = padY + boxH * 0.80f
        drawLine(
            color = guideColor,
            start = Offset(padX + boxW * 0.08f, groundY),
            end = Offset(padX + boxW * 0.92f, groundY),
            strokeWidth = 2.5f
        )

        // Center alignment crosshair depending on angle
        when (angle) {
            VehicleAngle.FRONT, VehicleAngle.REAR -> {
                drawLine(
                    color = guideColor,
                    start = Offset(w * 0.5f, padY + boxH * 0.18f),
                    end = Offset(w * 0.5f, groundY),
                    strokeWidth = 2f,
                    pathEffect = dashEffect
                )
            }
            VehicleAngle.SIDE_LEFT, VehicleAngle.SIDE_RIGHT -> {
                val leftWheelX = padX + boxW * 0.25f
                val rightWheelX = padX + boxW * 0.75f
                drawCircle(
                    color = lineColor,
                    radius = boxH * 0.13f,
                    center = Offset(leftWheelX, groundY - boxH * 0.11f),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = lineColor,
                    radius = boxH * 0.13f,
                    center = Offset(rightWheelX, groundY - boxH * 0.11f),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}

@Composable
fun VehicleStudioSilhouette(
    bodyStyle: BodyStyle,
    modifier: Modifier = Modifier,
    bodyTint: Color = MaterialTheme.colorScheme.onSurface,
    accentTint: Color = MaterialTheme.colorScheme.primary
) {
    val studioFloorTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val glassTint = Color(0xFFE2E8F0).copy(alpha = 0.88f)
    val rimAlloyTint = Color(0xFFF1F5F9)
    val rimInnerDarkTint = Color(0xFF334155)

    Canvas(modifier = modifier) {
        val cw = size.width
        val ch = size.height
        if (cw <= 0f || ch <= 0f) return@Canvas

        // Canonical 1000 x 420 automotive coordinate frame with 1:1 uniform scale so wheels
        // never shrink/stretch disproportionally across wide (Garage) or tall containers.
        val refW = 1000f
        val refH = 420f
        val uniformScale = minOf(cw / refW, ch / refH)
        val drawW = refW * uniformScale
        val drawH = refH * uniformScale
        val originX = (cw - drawW) * 0.5f
        val originY = ch - drawH

        withTransform({
            translate(left = originX, top = originY)
            scale(scaleX = uniformScale, scaleY = uniformScale, pivot = Offset.Zero)
        }) {
            val w = refW
            val groundY = 368f
            val frontWheelX = 248f
            val rearWheelX = 762f

            val wheelRadius = when (bodyStyle) {
                BodyStyle.TRUCK -> 65f
                BodyStyle.SUV -> 63f
                BodyStyle.CROSSOVER -> 60f
                else -> 55f
            }
            val wheelY = groundY - wheelRadius
            val archRadius = wheelRadius * 1.15f
            val sillY = when (bodyStyle) {
                BodyStyle.TRUCK -> 308f
                BodyStyle.SUV -> 312f
                BodyStyle.CROSSOVER -> 318f
                else -> 326f
            }

            // Soft studio ground shadow underneath tires and chassis
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(studioFloorTint, Color.Transparent),
                    center = Offset(w * 0.50f, groundY + 4f),
                    radius = w * 0.44f
                ),
                topLeft = Offset(72f, groundY - 12f),
                size = Size(856f, 36f)
            )

            val bodyPath = Path()
            val cabinPath = Path()

            when (bodyStyle) {
                BodyStyle.CROSSOVER, BodyStyle.SUV -> {
                    val roofY = if (bodyStyle == BodyStyle.SUV) 78f else 92f
                    val beltY = 204f
                    bodyPath.apply {
                        moveTo(88f, sillY)
                        lineTo(74f, 260f)
                        quadraticTo(82f, 204f, 224f, beltY)
                        lineTo(392f, roofY)
                        lineTo(790f, roofY + 6f)
                        quadraticTo(888f, 146f, 922f, 258f)
                        lineTo(908f, sillY)
                        close()
                    }
                    cabinPath.apply {
                        moveTo(272f, beltY - 6f)
                        lineTo(404f, roofY + 18f)
                        lineTo(766f, roofY + 22f)
                        lineTo(848f, beltY - 8f)
                        close()
                    }
                }
                BodyStyle.SEDAN -> {
                    val roofY = 110f
                    val beltY = 206f
                    bodyPath.apply {
                        moveTo(76f, sillY)
                        lineTo(68f, 268f)
                        quadraticTo(96f, 208f, 264f, beltY)
                        lineTo(418f, roofY)
                        lineTo(690f, roofY)
                        lineTo(852f, 204f)
                        quadraticTo(926f, 220f, 928f, 286f)
                        lineTo(902f, sillY)
                        close()
                    }
                    cabinPath.apply {
                        moveTo(316f, beltY - 6f)
                        lineTo(434f, roofY + 18f)
                        lineTo(672f, roofY + 18f)
                        lineTo(796f, beltY - 8f)
                        close()
                    }
                }
                BodyStyle.COUPE -> {
                    val roofY = 116f
                    val beltY = 212f
                    bodyPath.apply {
                        moveTo(74f, sillY)
                        lineTo(68f, 272f)
                        quadraticTo(108f, 210f, 294f, beltY)
                        lineTo(462f, roofY)
                        quadraticTo(646f, roofY - 4f, 864f, 208f)
                        quadraticTo(928f, 232f, 920f, 306f)
                        lineTo(894f, sillY)
                        close()
                    }
                    cabinPath.apply {
                        moveTo(348f, beltY - 6f)
                        lineTo(476f, roofY + 18f)
                        quadraticTo(632f, roofY + 18f, 774f, beltY - 6f)
                        close()
                    }
                }
                BodyStyle.HATCHBACK, BodyStyle.WAGON -> {
                    val roofY = 108f
                    val beltY = 206f
                    val tailX = if (bodyStyle == BodyStyle.WAGON) 862f else 814f
                    bodyPath.apply {
                        moveTo(80f, sillY)
                        lineTo(72f, 268f)
                        quadraticTo(96f, 208f, 256f, beltY)
                        lineTo(402f, roofY)
                        lineTo(tailX, roofY + 4f)
                        lineTo(918f, 242f)
                        lineTo(908f, sillY)
                        close()
                    }
                    cabinPath.apply {
                        moveTo(298f, beltY - 6f)
                        lineTo(422f, roofY + 18f)
                        lineTo(tailX - 24f, roofY + 20f)
                        lineTo(850f, beltY - 6f)
                        close()
                    }
                }
                BodyStyle.TRUCK -> {
                    val roofY = 74f
                    val beltY = 186f
                    bodyPath.apply {
                        moveTo(78f, sillY)
                        lineTo(70f, 224f)
                        quadraticTo(88f, 188f, 244f, beltY)
                        lineTo(348f, roofY)
                        lineTo(596f, roofY)
                        lineTo(616f, 188f)
                        lineTo(920f, 194f)
                        lineTo(920f, sillY)
                        close()
                    }
                    cabinPath.apply {
                        moveTo(286f, beltY - 6f)
                        lineTo(366f, roofY + 18f)
                        lineTo(568f, roofY + 18f)
                        lineTo(582f, beltY - 6f)
                        close()
                    }
                }
            }

            // Carve true semicircular fender arches out of the body silhouette cleanly
            val archCutouts = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = frontWheelX - archRadius,
                        top = wheelY - archRadius,
                        right = frontWheelX + archRadius,
                        bottom = wheelY + archRadius
                    )
                )
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        left = rearWheelX - archRadius,
                        top = wheelY - archRadius,
                        right = rearWheelX + archRadius,
                        bottom = wheelY + archRadius
                    )
                )
            }
            val sculptedBody = Path.combine(PathOperation.Difference, bodyPath, archCutouts)

            // Studio metallic silhouette fill
            drawPath(
                path = sculptedBody,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        bodyTint.copy(alpha = 0.92f),
                        bodyTint.copy(alpha = 0.76f)
                    )
                )
            )

            // Glasshouse window cutout
            drawPath(path = cabinPath, color = glassTint)

            // Headlight signature accent
            drawLine(
                color = accentTint,
                start = Offset(98f, 246f),
                end = Offset(164f, 232f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )

            // Precision proportional wheels & 5-spoke alloy rims nested in fender arches
            listOf(frontWheelX, rearWheelX).forEach { cx ->
                // Dark rubber tire
                drawCircle(
                    color = Color(0xFF18202C),
                    radius = wheelRadius,
                    center = Offset(cx, wheelY)
                )
                // Inner sidewall subtle ring
                drawCircle(
                    color = Color(0xFF334155),
                    radius = wheelRadius * 0.80f,
                    center = Offset(cx, wheelY),
                    style = Stroke(width = 3f)
                )
                // Alloy rim dish
                val rimRadius = wheelRadius * 0.62f
                drawCircle(
                    color = rimInnerDarkTint,
                    radius = rimRadius,
                    center = Offset(cx, wheelY)
                )
                drawCircle(
                    color = rimAlloyTint,
                    radius = rimRadius,
                    center = Offset(cx, wheelY),
                    style = Stroke(width = wheelRadius * 0.10f)
                )
                // 5 sporty spokes
                for (i in 0 until 5) {
                    val angleRad = Math.toRadians((i * 72.0) - 90.0)
                    val endX = cx + (rimRadius * 0.88f * cos(angleRad)).toFloat()
                    val endY = wheelY + (rimRadius * 0.88f * sin(angleRad)).toFloat()
                    drawLine(
                        color = rimAlloyTint,
                        start = Offset(cx, wheelY),
                        end = Offset(endX, endY),
                        strokeWidth = wheelRadius * 0.11f,
                        cap = StrokeCap.Round
                    )
                }
                // Center cap
                drawCircle(
                    color = accentTint,
                    radius = wheelRadius * 0.15f,
                    center = Offset(cx, wheelY)
                )
            }
        }
    }
}
