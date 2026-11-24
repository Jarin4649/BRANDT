package com.example.ui.registration

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.catalog.DemoVehicleCatalog
import com.example.data.model.BodyStyle
import com.example.data.model.MileageUnit
import com.example.data.model.SpecSource
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleValidator
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.components.BundledPcPhoto
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.components.loadBundledPcUploadPhotos
import com.example.ui.components.persistVehiclePhotoToAppStorage
import com.example.ui.theme.BrandtAuthInk
import com.example.ui.theme.BrandtAuthMutedInk
import com.example.ui.theme.BrandtEditorialLightTheme
import com.example.ui.theme.BrandtFormHairlineBorder
import com.example.ui.theme.BrandtFormInactiveTab
import com.example.ui.theme.BrandtFormPlaceholderSurface
import com.example.ui.theme.BrandtGuideDivider
import com.example.ui.theme.BrandtMintAction
import com.example.ui.theme.BrandtPureWhite
import com.example.ui.theme.MotoraEmerald
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SlateSecondary
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleFormScreen(
    viewModel: VehicleFormViewModel,
    onNavigateBack: () -> Unit,
    onVehicleSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = appStrings(LocalAppLanguage.current)
    var pcFolderPhotos by remember { mutableStateOf<List<BundledPcPhoto>>(emptyList()) }
    var showPcFolderStrip by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraErrorMessage by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.attachPhotoAndAnalyze(context, savedPath, uiState.selectedCaptureAngle)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraErrorMessage = null
            val uri = createFormCameraCaptureUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraErrorMessage = strings.chatCameraPermissionError
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.attachPhotoAndAnalyze(context, savedPath, uiState.selectedCaptureAngle)
        }
    }

    val browseFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.attachPhotoAndAnalyze(context, savedPath, uiState.selectedCaptureAngle)
        }
    }

    LaunchedEffect(uiState.savedVehicleId) {
        val savedId = uiState.savedVehicleId
        if (savedId != null) {
            onVehicleSaved(savedId)
        }
    }

    BrandtEditorialLightTheme {
        Scaffold(
            containerColor = BrandtPureWhite,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandtPureWhite)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("form_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.chatBackDesc,
                        tint = BrandtAuthInk
                    )
                }

                Text(
                    text = if (uiState.isEditMode) strings.formEditTitle else strings.formRegisterTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp
                    ),
                    color = SlateSecondary,
                    modifier = Modifier.weight(1f)
                )

                LanguageSettingsActionButton()
            }
        }
    ) { innerPadding ->
        if (uiState.isLoadingExisting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandtAuthInk)
            }
            return@Scaffold
        }

        val draft = uiState.draft
        val errors = uiState.fieldErrors
        val activeAngle = uiState.selectedCaptureAngle
        val activeAnglePhoto = draft.anglePhotos[activeAngle].orEmpty()
        val stagePhotoUri = activeAnglePhoto.ifBlank { draft.photoUri }
        val guidedAngles = remember {
            listOf(
                VehicleAngle.FRONT,
                VehicleAngle.SIDE_LEFT,
                VehicleAngle.SIDE_RIGHT,
                VehicleAngle.REAR
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandtPureWhite)
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Complete Figma Hero Graphic Card (Frame_1)
                VehicleFormHeroBanner(
                    contentDescription = strings.formHeroHeadline,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    thickness = 1.dp,
                    color = BrandtFormHairlineBorder
                )

                // 2. 4-Angle Tabs (Front / Left Side / Right Side / REAR)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    guidedAngles.forEach { angle ->
                        val isSelected = activeAngle == angle
                        val hasPhoto = draft.anglePhotos[angle].orEmpty().isNotBlank()
                        Surface(
                            onClick = { viewModel.selectCaptureAngle(angle) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandtMintAction else BrandtFormInactiveTab,
                            border = if (isSelected) BorderStroke(1.2.dp, BrandtAuthInk) else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                                .testTag("form_angle_slot_${angle.name}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (angle == VehicleAngle.REAR && strings.language == com.example.data.settings.AppLanguage.ENGLISH) {
                                        "REAR"
                                    } else {
                                        strings.angleShortLabel(angle)
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = if (isSelected) BrandtAuthInk else BrandtAuthMutedInk,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                if (hasPhoto) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = strings.formAngleCapturedBadge,
                                        tint = MotoraEmerald,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Step/Capture Ratio Pill ("1/4") & Clean Preview Box ("NO IMAGE AVAILABLE" or Real Photo)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeCountText = if (draft.anglePhotos.isNotEmpty()) {
                        "${draft.anglePhotos.size}/4"
                    } else {
                        "${guidedAngles.indexOf(activeAngle) + 1}/4"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.angleTitle(activeAngle),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = PlusJakartaSansFontFamily
                            ),
                            color = SlateSecondary
                        )

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = BrandtPureWhite,
                            border = BorderStroke(1.dp, BrandtFormHairlineBorder)
                        ) {
                            Text(
                                text = badgeCountText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = BrandtAuthInk,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(256.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BrandtFormPlaceholderSurface)
                            .testTag("form_photo_preview_stage"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (stagePhotoUri.isNotBlank()) {
                            AsyncImage(
                                model = stagePhotoUri,
                                contentDescription = "Selected vehicle photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (activeAnglePhoto.isNotBlank()) {
                                Surface(
                                    onClick = { viewModel.clearAnglePhoto(activeAngle) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = BrandtPureWhite.copy(alpha = 0.92f),
                                    border = BorderStroke(1.dp, BrandtFormHairlineBorder),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .testTag("form_clear_angle_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = strings.formClearAngle,
                                            tint = BrandtAuthInk,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = strings.formClearAngle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BrandtAuthInk
                                        )
                                    }
                                }
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_no_image_available),
                                contentDescription = "No image available yet",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(196.dp)
                            )
                        }
                    }
                }

                // 4. Unified Split Outlined Bar: CAMERA | FILES
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BrandtPureWhite,
                    border = BorderStroke(1.dp, BrandtGuideDivider),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    val hasCamera = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        cameraErrorMessage = null
                                        val uri = createFormCameraCaptureUri(context)
                                        pendingCameraUri = uri
                                        takePictureLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                                .testTag("form_take_photo_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoCamera,
                                contentDescription = strings.formTakePhoto,
                                tint = BrandtAuthInk,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.formCameraCaps,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp
                                ),
                                color = BrandtAuthInk
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(BrandtGuideDivider)
                        )

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("pick_photo_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = strings.formFilesCaps,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp
                                ),
                                color = BrandtAuthInk
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = strings.formAttachPhoto,
                                tint = BrandtAuthInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Subtle compact links for Document Browser, PC Folder, & Angle Framing Tip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { browseFilesLauncher.launch(arrayOf("image/*")) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("form_browse_files_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = SlateSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.formBrowseFiles,
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateSecondary
                        )
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                val loaded = loadBundledPcUploadPhotos(context)
                                pcFolderPhotos = loaded
                                showPcFolderStrip = true
                                val angleHint = when (activeAngle) {
                                    VehicleAngle.FRONT -> "front"
                                    VehicleAngle.SIDE_LEFT -> "left"
                                    VehicleAngle.SIDE_RIGHT -> "right"
                                    VehicleAngle.REAR -> "rear"
                                }
                                val preferred = loaded.firstOrNull {
                                    it.fileName.contains(angleHint, ignoreCase = true)
                                } ?: loaded.firstOrNull {
                                    it.fileName.contains("front", ignoreCase = true)
                                } ?: loaded.firstOrNull()
                                if (preferred != null) {
                                    viewModel.attachPhotoAndAnalyze(context, preferred.localFileUri, activeAngle)
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("form_pc_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.UploadFile,
                            contentDescription = null,
                            tint = SlateSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.formPcFolder,
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateSecondary
                        )
                    }

                    if (draft.photoUri.isNotBlank() || draft.anglePhotos.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.updatePhotoUri("") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = SlateSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.formUseSilhouette,
                                style = MaterialTheme.typography.labelMedium,
                                color = SlateSecondary
                            )
                        }
                    }
                }

                // Compact Active Angle Framing Guide Callout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BrandtFormPlaceholderSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_angle_guide_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TipsAndUpdates,
                            contentDescription = null,
                            tint = BrandtAuthMutedInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = strings.angleGuideInstruction(activeAngle),
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandtAuthMutedInk
                        )
                    }
                }

                if (showPcFolderStrip && pcFolderPhotos.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("form_pc_folder_strip")
                    ) {
                        Text(
                            text = "PC Photos (${pcFolderPhotos.size}) — ${strings.angleShortLabel(activeAngle)}:",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateSecondary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pcFolderPhotos, key = { it.fileName }) { item ->
                                val isCurrent = activeAnglePhoto == item.localFileUri || draft.photoUri == item.localFileUri
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) BrandtAuthInk else BrandtFormHairlineBorder
                                    ),
                                    modifier = Modifier
                                        .width(88.dp)
                                        .height(62.dp)
                                        .clickable {
                                            viewModel.attachPhotoAndAnalyze(context, item.localFileUri, activeAngle)
                                        }
                                ) {
                                    AsyncImage(
                                        model = item.localFileUri,
                                        contentDescription = item.fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                if (cameraErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = cameraErrorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        )
                    }
                }

                if (uiState.isProtectingPlate || uiState.platePrivacyNotice != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandtMintAction.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, BrandtMintAction),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_plate_privacy_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isProtectingPlate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandtAuthInk
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = BrandtAuthInk,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (uiState.isProtectingPlate) {
                                    strings.plateScanningNotice
                                } else {
                                    uiState.platePrivacyNotice.orEmpty()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandtAuthInk
                            )
                        }
                    }
                }

                // 5. Primary Mint Green AI Scan Button ("Let's AI start scanning the photo")
                Button(
                    onClick = { viewModel.analyzeAttachedPhotosWithAi(context) },
                    enabled = !uiState.isAnalyzingPhoto,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandtMintAction,
                        contentColor = BrandtAuthInk
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("form_ai_analyze_button")
                ) {
                    if (uiState.isAnalyzingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = BrandtAuthInk
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.formAiScanning,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = BrandtAuthInk
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = BrandtAuthInk,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (draft.aiInspectionSummary.isBlank()) {
                                strings.formAnalyzeWithAi
                            } else {
                                strings.formRescanWithAi
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = BrandtAuthInk
                        )
                    }
                }

                if (uiState.aiAnalysisError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.testTag("form_ai_error_banner")
                    ) {
                        Text(
                            text = uiState.aiAnalysisError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                if (draft.aiInspectionSummary.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandtMintAction.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, BrandtMintAction),
                        modifier = Modifier.testTag("form_ai_summary_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = BrandtAuthInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Gemini Vision Auto-Fill (Unverified)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandtAuthInk
                                )
                                Text(
                                    text = draft.aiInspectionSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandtAuthInk
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = BrandtFormHairlineBorder
                )

                // 6. Clean White Rounded Container Box Holding All User Vehicle Form Inputs
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BrandtPureWhite,
                    border = BorderStroke(1.25.dp, BrandtFormHairlineBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_white_container")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Catalog vs Custom Entry Switcher
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = SlateSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = DemoVehicleCatalog.CATALOG_NOTICE,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = BrandtAuthInk
                                )
                            }
                            Text(
                                text = "Select from our starter catalog below, let AI pre-fill from your photos, or switch to Custom User Entry for any car worldwide.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !uiState.useCustomEntry,
                                    onClick = { viewModel.toggleCustomEntry(false) },
                                    label = { Text("Demo Catalog Selector") },
                                    colors = brandtFormChipColors(),
                                    border = brandtFormChipBorder(selected = !uiState.useCustomEntry),
                                    leadingIcon = if (!uiState.useCustomEntry) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                                FilterChip(
                                    selected = uiState.useCustomEntry,
                                    onClick = { viewModel.toggleCustomEntry(true) },
                                    label = { Text("Custom User Entry") },
                                    colors = brandtFormChipColors(),
                                    border = brandtFormChipBorder(selected = uiState.useCustomEntry),
                                    leadingIcon = if (uiState.useCustomEntry) {
                                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("custom_entry_toggle")
                                )
                            }
                        }

                        HorizontalDivider(color = BrandtFormHairlineBorder.copy(alpha = 0.45f))

                        // Section 1: Vehicle Identity
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "1. Vehicle Identity",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandtAuthInk
                            )

                            if (!uiState.useCustomEntry) {
                                CatalogDropdownField(
                                    label = "Manufacturer (Demo Catalog) *",
                                    value = draft.manufacturer,
                                    options = DemoVehicleCatalog.manufacturers.map { it.name },
                                    error = errors[VehicleValidator.FIELD_MANUFACTURER],
                                    testTag = "manufacturer_dropdown",
                                    onSelected = { viewModel.selectCatalogManufacturer(it) }
                                )

                                CatalogDropdownField(
                                    label = "Model (Dependent) *",
                                    value = draft.model,
                                    options = uiState.availableModels.map { it.name },
                                    enabled = uiState.availableModels.isNotEmpty(),
                                    error = errors[VehicleValidator.FIELD_MODEL],
                                    testTag = "model_dropdown",
                                    onSelected = { selectedName ->
                                        val modelObj = uiState.availableModels.firstOrNull { it.name == selectedName }
                                        if (modelObj != null) viewModel.selectCatalogModel(modelObj)
                                    }
                                )

                                CatalogDropdownField(
                                    label = "Trim / Variant (Dependent) *",
                                    value = draft.trim,
                                    options = uiState.availableVariants.map { it.trimName },
                                    enabled = uiState.availableVariants.isNotEmpty(),
                                    error = errors[VehicleValidator.FIELD_TRIM],
                                    testTag = "trim_dropdown",
                                    onSelected = { selectedTrim ->
                                        val variantObj = uiState.availableVariants.firstOrNull { it.trimName == selectedTrim }
                                        if (variantObj != null) viewModel.selectCatalogVariant(variantObj)
                                    }
                                )
                            } else {
                                OutlinedTextField(
                                    value = draft.manufacturer,
                                    onValueChange = { viewModel.updateManufacturer(it) },
                                    label = { Text("Manufacturer *") },
                                    placeholder = { Text("e.g., Nissan, Porsche, Volvo") },
                                    isError = errors.containsKey(VehicleValidator.FIELD_MANUFACTURER),
                                    supportingText = errors[VehicleValidator.FIELD_MANUFACTURER]?.let { msg -> { Text(msg) } },
                                    colors = brandtOutlinedFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manufacturer_input")
                                )

                                OutlinedTextField(
                                    value = draft.model,
                                    onValueChange = { viewModel.updateModel(it) },
                                    label = { Text("Model *") },
                                    placeholder = { Text("e.g., GT-R, 911, EX30") },
                                    isError = errors.containsKey(VehicleValidator.FIELD_MODEL),
                                    supportingText = errors[VehicleValidator.FIELD_MODEL]?.let { msg -> { Text(msg) } },
                                    colors = brandtOutlinedFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("model_input")
                                )

                                OutlinedTextField(
                                    value = draft.trim,
                                    onValueChange = { viewModel.updateTrim(it) },
                                    label = { Text("Trim / Variant *") },
                                    placeholder = { Text("e.g., Premium, Track Edition") },
                                    isError = errors.containsKey(VehicleValidator.FIELD_TRIM),
                                    supportingText = errors[VehicleValidator.FIELD_TRIM]?.let { msg -> { Text(msg) } },
                                    colors = brandtOutlinedFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("trim_input")
                                )
                            }

                            OutlinedTextField(
                                value = draft.modelYearInput,
                                onValueChange = { viewModel.updateModelYear(it) },
                                label = { Text("Model Year *") },
                                placeholder = { Text("e.g., 2025") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = errors.containsKey(VehicleValidator.FIELD_MODEL_YEAR),
                                supportingText = errors[VehicleValidator.FIELD_MODEL_YEAR]?.let { msg -> { Text(msg) } },
                                colors = brandtOutlinedFieldColors(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("model_year_input")
                            )

                            Text(
                                text = "Body Profile",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandtAuthInk
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BodyStyle.entries.forEach { style ->
                                    val selected = draft.bodyStyle == style
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.updateBodyStyle(style) },
                                        label = { Text(style.displayName) },
                                        colors = brandtFormChipColors(),
                                        border = brandtFormChipBorder(selected = selected)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = BrandtFormHairlineBorder.copy(alpha = 0.45f))

                        // Section 2: Technical Specifications
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "2. Technical Specifications",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandtAuthInk
                            )

                            SpecFieldWithUnknownToggle(
                                label = "Engine Type *",
                                value = draft.engineType,
                                isUnknown = VehicleValidator.FIELD_ENGINE_TYPE in draft.unknownFields,
                                suggestions = DemoVehicleCatalog.commonEngineTypes,
                                error = errors[VehicleValidator.FIELD_ENGINE_TYPE],
                                inputTestTag = "engine_type_input",
                                unknownTestTag = "engine_unknown_chip",
                                onValueChange = { viewModel.updateEngineType(it) },
                                onToggleUnknown = { viewModel.toggleUnknownField(VehicleValidator.FIELD_ENGINE_TYPE) }
                            )

                            SpecFieldWithUnknownToggle(
                                label = "Engine Displacement (Optional)",
                                value = draft.engineDisplacement,
                                isUnknown = VehicleValidator.FIELD_ENGINE_DISPLACEMENT in draft.unknownFields,
                                suggestions = listOf("N/A (Electric)", "1498 cc", "1998 cc", "2487 cc", "2993 cc", "3799 cc"),
                                error = errors[VehicleValidator.FIELD_ENGINE_DISPLACEMENT],
                                inputTestTag = "displacement_input",
                                unknownTestTag = "displacement_unknown_chip",
                                onValueChange = { viewModel.updateEngineDisplacement(it) },
                                onToggleUnknown = { viewModel.toggleUnknownField(VehicleValidator.FIELD_ENGINE_DISPLACEMENT) }
                            )

                            SpecFieldWithUnknownToggle(
                                label = "Transmission *",
                                value = draft.transmission,
                                isUnknown = VehicleValidator.FIELD_TRANSMISSION in draft.unknownFields,
                                suggestions = DemoVehicleCatalog.commonTransmissions,
                                error = errors[VehicleValidator.FIELD_TRANSMISSION],
                                inputTestTag = "transmission_input",
                                unknownTestTag = "transmission_unknown_chip",
                                onValueChange = { viewModel.updateTransmission(it) },
                                onToggleUnknown = { viewModel.toggleUnknownField(VehicleValidator.FIELD_TRANSMISSION) }
                            )

                            SpecFieldWithUnknownToggle(
                                label = "Fuel Type *",
                                value = draft.fuelType,
                                isUnknown = VehicleValidator.FIELD_FUEL_TYPE in draft.unknownFields,
                                suggestions = DemoVehicleCatalog.commonFuelTypes,
                                error = errors[VehicleValidator.FIELD_FUEL_TYPE],
                                inputTestTag = "fuel_type_input",
                                unknownTestTag = "fuel_unknown_chip",
                                onValueChange = { viewModel.updateFuelType(it) },
                                onToggleUnknown = { viewModel.toggleUnknownField(VehicleValidator.FIELD_FUEL_TYPE) }
                            )
                        }

                        HorizontalDivider(color = BrandtFormHairlineBorder.copy(alpha = 0.45f))

                        // Section 3: Usage & Registration Market
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "3. Usage & Registration Market",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandtAuthInk
                            )

                            OutlinedTextField(
                                value = draft.mileageInput,
                                onValueChange = { viewModel.updateMileage(it) },
                                label = { Text("Current Mileage *") },
                                placeholder = { Text("e.g., 4250") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = errors.containsKey(VehicleValidator.FIELD_MILEAGE),
                                supportingText = errors[VehicleValidator.FIELD_MILEAGE]?.let { msg -> { Text(msg) } },
                                colors = brandtOutlinedFieldColors(),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mileage_input")
                            )

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                MileageUnit.entries.forEachIndexed { index, unit ->
                                    SegmentedButton(
                                        selected = draft.mileageUnit == unit,
                                        onClick = { viewModel.updateMileageUnit(unit) },
                                        colors = SegmentedButtonDefaults.colors(
                                            activeContainerColor = BrandtMintAction,
                                            activeContentColor = BrandtAuthInk,
                                            inactiveContainerColor = BrandtPureWhite,
                                            inactiveContentColor = BrandtAuthInk,
                                            activeBorderColor = BrandtAuthInk,
                                            inactiveBorderColor = BrandtFormHairlineBorder
                                        ),
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = MileageUnit.entries.size
                                        )
                                    ) {
                                        Text(unit.displayLabel, color = BrandtAuthInk)
                                    }
                                }
                            }

                            CatalogDropdownField(
                                label = "Market / Country *",
                                value = draft.market,
                                options = DemoVehicleCatalog.internationalMarkets,
                                error = errors[VehicleValidator.FIELD_MARKET],
                                testTag = "market_dropdown",
                                onSelected = { viewModel.updateMarket(it) }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Primary Garage Vehicle",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = BrandtAuthInk
                                    )
                                    Text(
                                        text = "Feature this vehicle on the BRANDT Garage stage.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateSecondary
                                    )
                                }
                                Switch(
                                    checked = draft.setAsPrimary,
                                    onCheckedChange = { viewModel.updateSetAsPrimary(it) },
                                    modifier = Modifier.testTag("primary_switch")
                                )
                            }
                        }

                        // Data Provenance Note
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandtFormPlaceholderSurface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Data Attribution: ${draft.specSource.label} (Unverified)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandtAuthInk
                                )
                                Text(
                                    text = when (draft.specSource) {
                                        SpecSource.DEMO_CATALOG_PREFILL ->
                                            "Pre-filled from starter catalog data. Editing any technical field marks the profile as User-Entered."
                                        SpecSource.AI_VISION_PREFILL ->
                                            "Pre-filled by Gemini Vision from your vehicle photo(s). Confirm year, trim, and technical fields before saving."
                                        SpecSource.USER_ENTERED ->
                                            "Owner-entered specifications. Missing or unavailable items are recorded explicitly as Unknown."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateSecondary
                                )
                            }
                        }

                        if (uiState.submitError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = uiState.submitError.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .testTag("form_error_banner")
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.submit() },
                            enabled = !uiState.isSaving,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandtAuthInk,
                                contentColor = BrandtPureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("submit_vehicle_button")
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = BrandtPureWhite,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (uiState.isEditMode) "Save Vehicle Changes" else "Register Vehicle to Garage",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
        }
    }
}

@Composable
private fun brandtOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BrandtAuthInk,
    unfocusedTextColor = BrandtAuthInk,
    disabledTextColor = BrandtAuthMutedInk,
    focusedLabelColor = BrandtAuthInk,
    unfocusedLabelColor = BrandtAuthInk,
    disabledLabelColor = BrandtAuthMutedInk,
    focusedPlaceholderColor = BrandtAuthMutedInk,
    unfocusedPlaceholderColor = BrandtAuthMutedInk,
    focusedBorderColor = BrandtAuthInk,
    unfocusedBorderColor = BrandtFormHairlineBorder,
    disabledBorderColor = BrandtFormHairlineBorder.copy(alpha = 0.72f),
    cursorColor = BrandtAuthInk,
    focusedTrailingIconColor = BrandtAuthInk,
    unfocusedTrailingIconColor = BrandtAuthInk
)

@Composable
private fun brandtFormChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = BrandtPureWhite,
    labelColor = BrandtAuthInk,
    iconColor = BrandtAuthInk,
    selectedContainerColor = BrandtMintAction,
    selectedLabelColor = BrandtAuthInk,
    selectedLeadingIconColor = BrandtAuthInk
)

@Composable
private fun brandtFormChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = BrandtFormHairlineBorder,
    selectedBorderColor = BrandtAuthInk,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.5.dp
)

@Composable
private fun VehicleFormHeroBanner(
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("form_hero_banner"),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.banner_fill_info_hero),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .aspectRatio(350f / 248f)
                .clip(RoundedCornerShape(14.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDropdownField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    error: String?,
    testTag: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            isError = error != null,
            supportingText = error?.let { msg -> { Text(msg) } },
            colors = brandtOutlinedFieldColors(),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
                .testTag(testTag)
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            containerColor = BrandtPureWhite
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = BrandtAuthInk) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecFieldWithUnknownToggle(
    label: String,
    value: String,
    isUnknown: Boolean,
    suggestions: List<String>,
    error: String?,
    inputTestTag: String,
    unknownTestTag: String,
    onValueChange: (String) -> Unit,
    onToggleUnknown: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = if (isUnknown) UNKNOWN_SPEC_LABEL else value,
            onValueChange = onValueChange,
            enabled = !isUnknown,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { msg -> { Text(msg) } },
            colors = brandtOutlinedFieldColors(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = isUnknown,
                onClick = onToggleUnknown,
                label = { Text("Unknown / Unavailable") },
                colors = brandtFormChipColors(),
                border = brandtFormChipBorder(selected = isUnknown),
                leadingIcon = if (isUnknown) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                modifier = Modifier.testTag(unknownTestTag)
            )
            if (!isUnknown) {
                suggestions.filter { it != UNKNOWN_SPEC_LABEL }.take(4).forEach { suggestion ->
                    val isSelected = value.equals(suggestion, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onValueChange(suggestion) },
                        label = { Text(suggestion, style = MaterialTheme.typography.bodySmall) },
                        colors = brandtFormChipColors(),
                        border = brandtFormChipBorder(selected = isSelected)
                    )
                }
            }
        }
    }
}

private fun createFormCameraCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File.createTempFile("vehicle_angle_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
