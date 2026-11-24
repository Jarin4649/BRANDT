package com.example.ui.studio

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ai.VehicleVisionResult
import com.example.data.model.BodyStyle
import com.example.data.model.VehicleAngle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.UploadFile
import com.example.ui.components.BundledPcPhoto
import com.example.ui.components.InteractiveVehicleStudioStage
import com.example.ui.components.VehicleAngleFramingOverlay
import com.example.ui.components.autoMapBundledPhotosToAngles
import com.example.ui.components.createVehicleCaptureFile
import com.example.ui.components.downloadVehiclePhotoToAppStorage
import com.example.ui.components.loadBundledPcUploadPhotos
import com.example.ui.components.persistVehiclePhotoToAppStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleStudioCaptureScreen(
    viewModel: VehicleStudioViewModel,
    isAttachedToExistingVehicle: Boolean,
    bodyStyle: BodyStyle = BodyStyle.CROSSOVER,
    onNavigateBack: () -> Unit,
    onApplyDraftStudio: (Map<VehicleAngle, String>, VehicleVisionResult?) -> Unit = { _, _ -> },
    onSavedToExistingVehicle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showTurntablePreview by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showUrlImporter by remember { mutableStateOf(false) }
    var imageUrlInput by remember { mutableStateOf("") }
    var isImportingUrl by remember { mutableStateOf(false) }
    var urlImportError by remember { mutableStateOf<String?>(null) }
    var showPcFolderDrawer by remember { mutableStateOf(false) }
    var isLoadingPcFolder by remember { mutableStateOf(false) }
    var bundledPcPhotos by remember { mutableStateOf<List<BundledPcPhoto>>(emptyList()) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            cameraError = "Camera permission declined. You can still attach photos using Pick Photo, Files, or URL."
        } else {
            cameraError = null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.assignPhotoToActiveAngle(savedPath, context)
        }
    }

    val browseFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.assignPhotoToActiveAngle(savedPath, context)
        }
    }

    LaunchedEffect(uiState.savedSuccessfully) {
        if (uiState.savedSuccessfully) {
            onSavedToExistingVehicle()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "360° Hybrid Capture Studio",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${uiState.anglePhotos.size}/4 angles captured • Option C Hybrid",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (uiState.anglePhotos.isNotEmpty()) {
                        TextButton(
                            onClick = { showTurntablePreview = !showTurntablePreview },
                            modifier = Modifier.testTag("toggle_studio_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ViewInAr,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (showTurntablePreview) "Capture" else "Spin")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step selector strip for Front / Left Side / Rear / Right Side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleAngle.entries.forEach { angle ->
                        val isSelected = angle == uiState.activeAngle
                        val isCaptured = !uiState.anglePhotos[angle].isNullOrBlank()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isCaptured -> MaterialTheme.colorScheme.surfaceContainerLowest
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    showTurntablePreview = false
                                    viewModel.selectAngle(angle)
                                }
                                .testTag("capture_angle_chip_${angle.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isCaptured) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "${angle.shortLabel} captured",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Text(
                                        text = "${angle.degrees}°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = angle.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }

                if (showTurntablePreview) {
                    InteractiveVehicleStudioStage(
                        anglePhotos = uiState.anglePhotos,
                        fallbackPhotoUri = "",
                        bodyStyle = bodyStyle,
                        contentDescription = "360 Hybrid Studio Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        onCaptureMissingAngle = { missing ->
                            showTurntablePreview = false
                            viewModel.selectAngle(missing)
                        }
                    )
                } else {
                    val activeAngle = uiState.activeAngle
                    val existingAnglePhoto = uiState.anglePhotos[activeAngle].orEmpty()

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${activeAngle.displayTitle} (${activeAngle.degrees}°)",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("capture_active_angle_title")
                                    )
                                    Text(
                                        text = activeAngle.framingHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (existingAnglePhoto.isNotBlank()) {
                                    IconButton(
                                        onClick = { viewModel.clearAngle(activeAngle) },
                                        modifier = Modifier.testTag("clear_angle_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove ${activeAngle.shortLabel} photo"
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF141B19)),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    existingAnglePhoto.isNotBlank() -> {
                                        AsyncImage(
                                            model = existingAnglePhoto,
                                            contentDescription = "${activeAngle.displayTitle} photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    hasCameraPermission -> {
                                        CameraXVehiclePreview(
                                            onImageCaptureReady = { capture -> imageCapture = capture },
                                            onCameraError = { err -> cameraError = err },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        VehicleAngleFramingOverlay(
                                            angle = activeAngle,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            VehicleAngleFramingOverlay(
                                                angle = activeAngle,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CameraAlt,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = "Enable camera for guided ${activeAngle.shortLabel.lowercase()} capture, or choose an existing photo below.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.88f)
                                                )
                                                Button(
                                                    onClick = {
                                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                    },
                                                    modifier = Modifier.testTag("grant_camera_button")
                                                ) {
                                                    Text("Enable Guided Camera")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (cameraError != null) {
                                Text(
                                    text = cameraError.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasCameraPermission && existingAnglePhoto.isBlank()) {
                                    Button(
                                        onClick = {
                                            val capture = imageCapture
                                            if (capture == null) {
                                                cameraError = "Camera is still initializing. Try Pick Photo, Files, or URL."
                                            } else {
                                                val outputFile = createVehicleCaptureFile(context, activeAngle)
                                                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                                capture.takePicture(
                                                    outputOptions,
                                                    ContextCompat.getMainExecutor(context),
                                                    object : ImageCapture.OnImageSavedCallback {
                                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                            cameraError = null
                                                            viewModel.assignPhotoToActiveAngle(outputFile.toURI().toString(), context)
                                                        }

                                                        override fun onError(exception: ImageCaptureException) {
                                                            cameraError = exception.localizedMessage ?: "Capture failed."
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("camera_shutter_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CameraAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Capture")
                                    }
                                } else if (existingAnglePhoto.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { viewModel.clearAngle(activeAngle) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Retake ${activeAngle.shortLabel}")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("studio_pick_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick Photo")
                                }

                                OutlinedButton(
                                    onClick = {
                                        browseFilesLauncher.launch(arrayOf("image/*"))
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("studio_browse_files_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Files")
                                }

                                OutlinedButton(
                                    onClick = {
                                        showUrlImporter = !showUrlImporter
                                        if (showUrlImporter) showPcFolderDrawer = false
                                        urlImportError = null
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("studio_toggle_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Link,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("URL")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val nextOpen = !showPcFolderDrawer
                                        showPcFolderDrawer = nextOpen
                                        if (nextOpen) {
                                            showUrlImporter = false
                                            isLoadingPcFolder = true
                                            scope.launch {
                                                bundledPcPhotos = loadBundledPcUploadPhotos(context)
                                                isLoadingPcFolder = false
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("studio_pc_folder_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("PC Folder")
                                }
                            }

                            if (showUrlImporter) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("studio_url_panel")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Import ${activeAngle.shortLabel} photo from URL or Base64 data:image",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = imageUrlInput,
                                            onValueChange = {
                                                imageUrlInput = it
                                                urlImportError = null
                                            },
                                            label = { Text("Image URL (https://...)") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("studio_url_input")
                                        )
                                        if (urlImportError != null) {
                                            Text(
                                                text = urlImportError.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.testTag("studio_url_error")
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { showUrlImporter = false },
                                                enabled = !isImportingUrl
                                            ) {
                                                Text("Cancel")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    if (imageUrlInput.isBlank()) {
                                                        urlImportError = "Paste an image URL first."
                                                    } else {
                                                        isImportingUrl = true
                                                        urlImportError = null
                                                        scope.launch {
                                                            val result = downloadVehiclePhotoToAppStorage(
                                                                context = context,
                                                                rawUrl = imageUrlInput
                                                            )
                                                            isImportingUrl = false
                                                            result.fold(
                                                                onSuccess = { savedUri ->
                                                                    viewModel.assignPhotoToActiveAngle(savedUri, context)
                                                                    imageUrlInput = ""
                                                                    showUrlImporter = false
                                                                },
                                                                onFailure = { err ->
                                                                    urlImportError = err.localizedMessage ?: "Failed to import URL."
                                                                }
                                                            )
                                                        }
                                                    }
                                                },
                                                enabled = !isImportingUrl,
                                                modifier = Modifier.testTag("studio_url_import_button")
                                            ) {
                                                if (isImportingUrl) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                } else {
                                                    Text("Import")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (showPcFolderDrawer) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("studio_pc_folder_panel")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Temporary PC Upload Folder (pc_import)",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (bundledPcPhotos.isNotEmpty()) {
                                                TextButton(
                                                    onClick = {
                                                        val mapped = autoMapBundledPhotosToAngles(bundledPcPhotos)
                                                        mapped.forEach { (angle, uri) ->
                                                            viewModel.assignPhotoToAngle(angle, uri, advanceToNext = false, context = context)
                                                        }
                                                        showPcFolderDrawer = false
                                                    },
                                                    modifier = Modifier.testTag("pc_folder_auto_fill_button")
                                                ) {
                                                    Text("Auto-Fill All")
                                                }
                                            }
                                        }

                                        when {
                                            isLoadingPcFolder -> {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                    Text(
                                                        text = "Scanning pc_import folder...",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }

                                            bundledPcPhotos.isEmpty() -> {
                                                val language = com.example.data.settings.LocalAppLanguage.current
                                                Text(
                                                    text = com.example.data.settings.appStrings(language).studioPcFolderEmptyHint,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.testTag("pc_folder_empty_hint")
                                                )
                                            }

                                            else -> {
                                                Text(
                                                    text = "Tap any photo below to assign to ${activeAngle.shortLabel} (${activeAngle.degrees}°), or tap Auto-Fill All.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    items(bundledPcPhotos, key = { it.fileName }) { item ->
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                            modifier = Modifier
                                                                .width(108.dp)
                                                                .clickable {
                                                                    viewModel.assignPhotoToActiveAngle(item.localFileUri, context)
                                                                }
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.padding(6.dp),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                AsyncImage(
                                                                    model = item.localFileUri,
                                                                    contentDescription = item.fileName,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(68.dp)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                )
                                                                Text(
                                                                    text = item.fileName,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.platePrivacyNotice != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_plate_privacy_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = uiState.platePrivacyNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Gemini Vision Hybrid Analysis Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini Vision Vehicle Inspection",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Analyzes your captured angles to detect make, model, body profile & exterior cues. Phase 2 true 3D (.glb) mesh export is staged for future cloud pipeline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        when (val analysis = uiState.analysisState) {
                            is StudioAnalysisState.Idle -> {
                                Button(
                                    onClick = { viewModel.analyzeWithGeminiVision(context) },
                                    enabled = uiState.anglePhotos.isNotEmpty(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("analyze_vision_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyze ${uiState.anglePhotos.size} Photo(s) with Gemini Vision")
                                }
                            }

                            is StudioAnalysisState.Analyzing -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Text(
                                        text = "Inspecting captured vehicle angles with Gemini Vision...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            is StudioAnalysisState.Error -> {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = analysis.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                            .testTag("vision_error_text")
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.analyzeWithGeminiVision(context) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry Gemini Vision Analysis")
                                }
                            }

                            is StudioAnalysisState.Success -> {
                                val res = analysis.result
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.testTag("vision_result_card")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "AI VISION PRE-FILL (UNVERIFIED)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${res.manufacturer} ${res.model} ${if (res.modelYearGuess.isNotBlank()) "(~${res.modelYearGuess})" else ""}".trim(),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Body: ${res.bodyStyle.displayName} • Fuel cue: ${res.fuelTypeHint} • Trim cue: ${res.trimOrBadgeHints}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (res.exteriorSummary.isNotBlank()) {
                                            Text(
                                                text = res.exteriorSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (res.qualityFeedback.isNotBlank()) {
                                            Text(
                                                text = "Framing note: ${res.qualityFeedback}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.analyzeWithGeminiVision(context) },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Re-analyze Updated Angles")
                                }
                            }
                        }
                    }
                }

                if (uiState.saveError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = uiState.saveError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                // Primary Finish Action
                val visionResult = (uiState.analysisState as? StudioAnalysisState.Success)?.result
                Button(
                    onClick = {
                        if (isAttachedToExistingVehicle) {
                            viewModel.saveStudioToExistingVehicle()
                        } else {
                            onApplyDraftStudio(uiState.anglePhotos, visionResult)
                        }
                    },
                    enabled = !uiState.isSaving && uiState.anglePhotos.isNotEmpty(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 15.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("studio_save_apply_button")
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAttachedToExistingVehicle) {
                                "Save 360° Studio to Vehicle"
                            } else if (visionResult != null) {
                                "Apply Photos & AI Suggestions"
                            } else {
                                "Attach ${uiState.anglePhotos.size} Angle Photo(s) to Registration"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun CameraXVehiclePreview(
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    boundProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    onImageCaptureReady(capture)
                } catch (e: Exception) {
                    Log.w("CameraXStudio", "Unable to bind back camera", e)
                    onCameraError("Camera preview unavailable on this device/emulator. Use Pick Photo instead.")
                }
            },
            executor
        )

        onDispose {
            try {
                boundProvider?.unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
