package com.example.ui.overview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.MileageUnit
import com.example.data.model.UNKNOWN_SPEC_LABEL
import com.example.data.model.Vehicle
import com.example.data.model.VehicleAngle
import com.example.data.model.VehicleKnownIssue
import com.example.data.model.VehicleValidator
import com.example.data.obd.LiveDataCategoryId
import com.example.data.obd.LiveVehicleCategorySummary
import com.example.data.obd.LiveVehicleDataSnapshot
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.components.CoveragePill
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.components.PrimaryVehiclePill
import com.example.ui.components.ProvenanceBadge
import com.example.ui.components.VehicleBackgroundRemover
import com.example.ui.components.VehicleStudioSilhouette
import com.example.ui.components.persistVehiclePhotoToAppStorage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleOverviewScreen(
    viewModel: VehicleOverviewViewModel,
    onNavigateBack: () -> Unit,
    onEditVehicle: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onOpenAiChat: (vehicleId: String, initialTopic: String?) -> Unit = { _, _ -> },
    onOpenIssueLog: (vehicleId: String) -> Unit = { _ -> },
    onOpenDiagnostics: (vehicleId: String) -> Unit = { _ -> }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val knownIssuesState by viewModel.knownIssuesState.collectAsStateWithLifecycle()
    val liveVehicleData by viewModel.liveVehicleData.collectAsStateWithLifecycle()
    val isRefreshingEcu by viewModel.isRefreshingEcu.collectAsStateWithLifecycle()
    val diagnosticAiState by viewModel.diagnosticAiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsStateWithLifecycle()
    val deletedSuccessfully by viewModel.deletedSuccessfully.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var activeDetailCategoryId by remember { mutableStateOf<LiveDataCategoryId?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            viewModel.updatePhoto(savedPath, context)
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(deletedSuccessfully) {
        if (deletedSuccessfully) {
            onNavigateBack()
        }
    }

    val readyVehicle = (uiState as? VehicleOverviewUiState.Ready)?.vehicle

    // If user opened "See More Details" for a Live Vehicle Data category, show the dedicated detail screen
    val selectedCategory = activeDetailCategoryId?.let { targetId ->
        liveVehicleData.categories.firstOrNull { it.categoryId == targetId }
    }
    if (readyVehicle != null && selectedCategory != null) {
        LiveVehicleDataDetailScreen(
            category = selectedCategory,
            ecuSourceLabel = liveVehicleData.sourceLabel,
            isAdapterConnected = liveVehicleData.isAdapterConnected,
            isRefreshing = isRefreshingEcu,
            onRefreshEcu = { viewModel.refreshLiveEcuData() },
            onOpenDiagnostics = {
                activeDetailCategoryId = null
                onOpenDiagnostics(readyVehicle.id)
            },
            onBack = { activeDetailCategoryId = null },
            diagnosticDetail = liveVehicleData.diagnosticStatusDetail,
            diagnosticAiState = diagnosticAiState,
            onExplainDiagnosticStatus = { force -> viewModel.explainDiagnosticStatus(force) },
            systemVoltageData = liveVehicleData.systemVoltageData,
            onUpdateSystemVoltageCriteria = { sys, bat, cond ->
                viewModel.updateSystemVoltageCriteria(sys, bat, cond)
            }
        )
        return
    }

    if (showDeleteDialog && readyVehicle != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Remove Vehicle?") },
            text = {
                Text(
                    "Permanently remove ${readyVehicle.modelYear} ${readyVehicle.manufacturer} ${readyVehicle.model} from your BRANDT garage?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("overview_confirm_delete_button")
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }

    val strings = appStrings(LocalAppLanguage.current)
    var showMileageEditor by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.overviewTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color(0xFF4B5563),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.overviewBackToGarage,
                            tint = Color(0xFF1F2937)
                        )
                    }
                },
                actions = {
                    LanguageSettingsActionButton()
                    if (readyVehicle != null) {
                        IconButton(
                            onClick = { onOpenDiagnostics(readyVehicle.id) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("overview_diagnostics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Troubleshoot,
                                contentDescription = strings.navDiagnostics
                            )
                        }
                        IconButton(
                            onClick = { onEditVehicle(readyVehicle.id) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("overview_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit vehicle profile"
                            )
                        }
                        IconButton(
                            onClick = { viewModel.requestDelete() },
                            enabled = !isMutating,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("overview_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Remove vehicle",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is VehicleOverviewUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is VehicleOverviewUiState.NotFound -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Vehicle record not found",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Button(onClick = onNavigateBack) {
                                Text("Return to Garage")
                            }
                        }
                    }
                }

                is VehicleOverviewUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is VehicleOverviewUiState.Ready -> {
                    val vehicle = state.vehicle
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWide = maxWidth >= 780.dp
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 980.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // 1. Hero Section: NAME CAR, Type car, Right Side background-removed cutout, Year of car
                                OverviewCarHeroSection(
                                    vehicle = vehicle,
                                    isMutating = isMutating,
                                    onSetPrimary = { viewModel.setAsPrimary() },
                                    onChangePhoto = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )

                                // 2. Separate Current Mileage Card matching iPhone 17 - 16
                                CurrentMileageCard(
                                    vehicle = vehicle,
                                    showMileageEditor = showMileageEditor,
                                    onToggleMileageEditor = { showMileageEditor = !showMileageEditor },
                                    onSaveMileage = { raw, unit ->
                                        if (viewModel.updateMileage(raw, unit)) {
                                            showMileageEditor = false
                                        }
                                    }
                                )

                                // 3. Known Model Issue & Watch-Outs (AI Chat buttons removed per user request)
                                KnownModelIssuesCard(
                                    vehicle = vehicle,
                                    knownIssuesState = knownIssuesState,
                                    onRefresh = { viewModel.refreshCommonIssues() }
                                )

                                // 4. Live Vehicle Data (Real ECU telemetry categories with 0-49 Red / 50-69 Yellow / 70-100 Green)
                                LiveVehicleDataSection(
                                    snapshot = liveVehicleData,
                                    isRefreshing = isRefreshingEcu,
                                    onRefreshEcu = { viewModel.refreshLiveEcuData() },
                                    onOpenDiagnostics = { onOpenDiagnostics(vehicle.id) },
                                    onSeeMoreDetails = { categoryId -> activeDetailCategoryId = categoryId }
                                )

                                // 5. Vehicle Insight / AI Analysis Staged Placeholder + Red ECU Note (no AI chat trigger)
                                VehicleInsightPlaceholderSection(
                                    onOpenIssueLog = { onOpenIssueLog(vehicle.id) }
                                )

                                // 6. Technical Specs & Data Verification
                                if (isWide) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            SpecificationsCard(vehicle = vehicle)
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            DataVerificationAndCoverageCard(vehicle = vehicle)
                                        }
                                    }
                                } else {
                                    SpecificationsCard(vehicle = vehicle)
                                    DataVerificationAndCoverageCard(vehicle = vehicle)
                                }

                                // 7. Registration Details ALWAYS at the very bottom
                                RegistrationDetailsCard(vehicle = vehicle)

                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCarHeroSection(
    vehicle: Vehicle,
    isMutating: Boolean,
    onSetPrimary: () -> Unit,
    onChangePhoto: () -> Unit
) {
    val context = LocalContext.current
    val resolvedAngles = remember(vehicle.anglePhotos) { vehicle.resolvedAnglePhotos() }

    // Prioritize Right Side photo as instructed; cleanly fall back to main photo / any angle if missing
    val rawHeroPhotoUri = remember(vehicle.photoUri, resolvedAngles) {
        resolvedAngles[VehicleAngle.SIDE_RIGHT]
            ?.takeIf { it.isNotBlank() }
            ?: vehicle.photoUri.takeIf { it.isNotBlank() }
            ?: resolvedAngles.values.firstOrNull { it.isNotBlank() }
            ?: ""
    }

    var cutoutUri by remember(rawHeroPhotoUri) { mutableStateOf(rawHeroPhotoUri) }
    var isProcessingCutout by remember(rawHeroPhotoUri) { mutableStateOf(rawHeroPhotoUri.isNotBlank()) }

    LaunchedEffect(rawHeroPhotoUri) {
        if (rawHeroPhotoUri.isBlank()) {
            cutoutUri = ""
            isProcessingCutout = false
        } else {
            isProcessingCutout = true
            cutoutUri = VehicleBackgroundRemover.extractVehicleCutoutUri(context, rawHeroPhotoUri)
            isProcessingCutout = false
        }
    }

    val carTitle = remember(vehicle.manufacturer, vehicle.model) {
        listOf(vehicle.manufacturer.trim(), vehicle.model.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { vehicle.model }
    }
    val typeSubtitle = remember(vehicle.trim, vehicle.bodyStyle) {
        val trimPart = vehicle.trim.takeIf { it.isNotBlank() && it != UNKNOWN_SPEC_LABEL }
        val stylePart = vehicle.resolvedBodyStyle().displayName
        listOfNotNull(trimPart, stylePart).distinct().joinToString(" • ")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_hero_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = carTitle,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("overview_vehicle_title")
                )
                Text(
                    text = typeSubtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF6B7280)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (vehicle.primary) {
                    PrimaryVehiclePill()
                } else {
                    OutlinedButton(
                        onClick = onSetPrimary,
                        enabled = !isMutating,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("overview_set_primary_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Primary", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(
                    onClick = onChangePhoto,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("overview_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = "Attach or change vehicle photo",
                        tint = Color(0xFF4B5563)
                    )
                }
            }
        }

        // Studio cutout stage matching iPhone 17 - 16
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF6F7F9),
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .testTag("overview_right_side_cutout_stage")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Soft elliptical studio shadow underneath the car
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.48f
                        ),
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(size.width, size.height * 0.7f)
                    )
                }

                if (cutoutUri.isNotBlank()) {
                    AsyncImage(
                        model = cutoutUri,
                        contentDescription = "${vehicle.modelYear} $carTitle Right Side Cutout",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    VehicleStudioSilhouette(
                        bodyStyle = vehicle.resolvedBodyStyle(),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 18.dp)
                    )
                }

                if (isProcessingCutout) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Year of car aligned bottom-right underneath cutout frame
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = vehicle.modelYear.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4B5563),
                modifier = Modifier.testTag("overview_vehicle_year")
            )
        }
    }
}

@Composable
private fun CurrentMileageCard(
    vehicle: Vehicle,
    showMileageEditor: Boolean,
    onToggleMileageEditor: () -> Unit,
    onSaveMileage: (String, MileageUnit) -> Unit
) {
    val formattedNumber = remember(vehicle.mileage) {
        NumberFormat.getIntegerInstance(Locale.US).format(vehicle.mileage)
    }
    val unitLabel = vehicle.resolvedMileageUnit().code

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_mileage_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Current Mileage",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F2937)
            )
            TextButton(
                onClick = onToggleMileageEditor,
                modifier = Modifier.testTag("update_mileage_button")
            ) {
                Text(if (showMileageEditor) "Close" else "Update")
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Soft sky-blue mileage band extending behind digits matching iPhone 17 - 16
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFD7ECFD),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 74.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = formattedNumber,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        modifier = Modifier.testTag("overview_mileage_text")
                    )
                    Text(
                        text = "Mileage ($unitLabel)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF374151)
                    )
                }
            }

            Image(
                painter = painterResource(id = R.drawable.ic_gauge_mileage),
                contentDescription = "Odometer Speedometer",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(116.dp)
            )
        }

        if (showMileageEditor) {
            var mileageInput by remember(vehicle.id, vehicle.mileage) {
                mutableStateOf(vehicle.mileage.toString())
            }
            var mileageUnit by remember(vehicle.id, vehicle.mileageUnit) {
                mutableStateOf(vehicle.resolvedMileageUnit())
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Update Current Mileage",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = mileageInput,
                        onValueChange = { mileageInput = it.filter { ch -> ch.isDigit() }.take(7) },
                        label = { Text("Odometer Reading") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_mileage_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MileageUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = mileageUnit == unit,
                                onClick = { mileageUnit = unit },
                                label = { Text(unit.code.uppercase()) }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onToggleMileageEditor) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSaveMileage(mileageInput, mileageUnit) },
                            modifier = Modifier.testTag("save_mileage_button")
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnownModelIssuesCard(
    vehicle: Vehicle,
    knownIssuesState: VehicleKnownIssuesUiState,
    onRefresh: () -> Unit
) {
    val strings = appStrings(LocalAppLanguage.current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_known_issues_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = strings.overviewKnownIssuesTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1F2937),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("overview_refresh_known_issues_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = strings.overviewKnownIssuesRefreshDesc,
                    tint = Color(0xFF4B5563)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF5F6F8),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (knownIssuesState) {
                    is VehicleKnownIssuesUiState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Information",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }

                    is VehicleKnownIssuesUiState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "${strings.overviewKnownIssuesLoadingPrefix} ${vehicle.manufacturer} ${vehicle.model}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }

                    is VehicleKnownIssuesUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("overview_known_issues_error"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = knownIssuesState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier.testTag("overview_retry_known_issues_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.overviewKnownIssuesRetry)
                            }
                        }
                    }

                    is VehicleKnownIssuesUiState.Ready -> {
                        val data = knownIssuesState.result
                        if (data.headlineSummary.isNotBlank()) {
                            Text(
                                text = data.headlineSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1F2937)
                            )
                        }
                        data.issues.forEachIndexed { index, issue ->
                            KnownIssueItemCard(
                                issue = issue,
                                index = index,
                                noticePrefix = strings.overviewKnownIssuesNoticePrefix
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnownIssueItemCard(
    issue: VehicleKnownIssue,
    index: Int,
    noticePrefix: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("known_issue_item_$index")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF111827),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = issue.frequencyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = issue.simpleExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151)
            )
            if (issue.whatToNotice.isNotBlank()) {
                Text(
                    text = "$noticePrefix ${issue.whatToNotice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
private fun LiveVehicleDataSection(
    snapshot: LiveVehicleDataSnapshot,
    isRefreshing: Boolean,
    onRefreshEcu: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSeeMoreDetails: (LiveDataCategoryId) -> Unit
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_live_vehicle_data_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isThai) "ข้อมูลรถยนต์แบบเรียลไทม์" else "Live Vehicle Data",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1F2937)
                    )
                    if (snapshot.isAdapterConnected) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFFD1FAE5),
                            modifier = Modifier.testTag("overview_live_badge")
                        ) {
                            Text(
                                text = "● LIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF065F46),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = snapshot.sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (snapshot.hasActiveEcuSession || snapshot.isAdapterConnected) {
                        Color(0xFF15803D)
                    } else {
                        Color(0xFF6B7280)
                    },
                    modifier = Modifier.testTag("overview_ecu_source_label")
                )
            }

            if (snapshot.isAdapterConnected) {
                OutlinedButton(
                    onClick = onRefreshEcu,
                    enabled = !isRefreshing,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("overview_refresh_ecu_button")
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isThai) "อัปเดต ECU" else "Update ECU",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                TextButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag("overview_connect_ecu_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Troubleshoot,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isThai) "เชื่อมต่อ ECU" else "Connect ECU",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        snapshot.categories.forEach { category ->
            LiveVehicleCategoryCard(
                category = category,
                isThai = isThai,
                onClick = { onSeeMoreDetails(category.categoryId) }
            )
        }
    }
}

@Composable
private fun LiveVehicleCategoryCard(
    category: LiveVehicleCategorySummary,
    isThai: Boolean,
    onClick: () -> Unit
) {
    val iconRes = when (category.categoryId) {
        LiveDataCategoryId.DRIVING_ENGINE -> R.drawable.ic_gauge_driving_engine
        LiveDataCategoryId.ENGINE_TEMPERATURE -> R.drawable.ic_gauge_engine_temp
        LiveDataCategoryId.SYSTEM_VOLTAGE -> R.drawable.ic_gauge_system_voltage
        LiveDataCategoryId.AIR_INTAKE_FUEL -> R.drawable.ic_gauge_air_fuel
        LiveDataCategoryId.TRANSMISSION -> R.drawable.ic_gauge_transmission
        LiveDataCategoryId.DIAGNOSTIC_STATUS -> R.drawable.ic_gauge_diagnostic_status
    }
    val bandColor = categoryBandColor(category.band)
    val bandBg = categoryBandTintColor(category.band)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFD1D5DB)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_category_card_${category.categoryId.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = category.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF111827),
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 52.dp, end = 6.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bandBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 66.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = category.formattedPercent,
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Medium,
                                color = bandColor,
                                modifier = Modifier.testTag("live_category_percent_${category.categoryId.id}")
                            )
                        }
                    }
                }

                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = category.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(104.dp)
                )
            }

            HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThai) "ดูรายละเอียดเพิ่มเติม" else "See More Details",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8),
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.testTag("live_category_details_${category.categoryId.id}")
                )
            }
        }
    }
}

@Composable
private fun VehicleInsightPlaceholderSection(
    onOpenIssueLog: () -> Unit
) {
    val strings = appStrings(LocalAppLanguage.current)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_vehicle_insight_placeholder"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Vehicle Insight",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF111827)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = Color(0xFF2563EB),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "What does this mean?",
                fontSize = 15.sp,
                color = Color(0xFF1F2937)
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF5F6F8),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "AI Analysis",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1F2937)
                )
                Text(
                    text = "Staged for the next AI Insight update.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center
                )
            }
        }

        Text(
            text = "This information is taken from your car's ECU (Engine Control Unit).",
            fontSize = 12.sp,
            color = Color(0xFFDC2626),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        // Saved Issue Log shortcut (white card with crisp black text for high contrast)
        Surface(
            onClick = onOpenIssueLog,
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.2.dp, Color(0xFFD1D5DB)),
            shadowElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overview_open_log_button")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.FactCheck,
                            contentDescription = strings.overviewIssueLogTitle,
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.overviewIssueLogTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Text(
                        text = strings.overviewIssueLogSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecificationsCard(vehicle: Vehicle) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFD1D5DB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Technical Specifications",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black
            )

            SpecLineItem(
                label = "Engine Type",
                value = vehicle.engineType,
                isUnknown = VehicleValidator.FIELD_ENGINE_TYPE in vehicle.unknownFields ||
                    vehicle.engineType == UNKNOWN_SPEC_LABEL
            )
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(
                label = "Engine Displacement",
                value = vehicle.engineDisplacement.ifBlank { "Not specified (Optional)" },
                isUnknown = vehicle.engineDisplacement.isBlank() ||
                    VehicleValidator.FIELD_ENGINE_DISPLACEMENT in vehicle.unknownFields ||
                    vehicle.engineDisplacement == UNKNOWN_SPEC_LABEL
            )
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(
                label = "Transmission",
                value = vehicle.transmission,
                isUnknown = VehicleValidator.FIELD_TRANSMISSION in vehicle.unknownFields ||
                    vehicle.transmission == UNKNOWN_SPEC_LABEL
            )
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(
                label = "Fuel Type",
                value = vehicle.fuelType,
                isUnknown = VehicleValidator.FIELD_FUEL_TYPE in vehicle.unknownFields ||
                    vehicle.fuelType == UNKNOWN_SPEC_LABEL
            )
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(
                label = "Body Profile",
                value = vehicle.resolvedBodyStyle().displayName,
                isUnknown = false
            )
        }
    }
}

@Composable
private fun DataVerificationAndCoverageCard(vehicle: Vehicle) {
    val verification = vehicle.resolvedVerificationStatus()
    val specSource = vehicle.resolvedSpecSource()
    val coverage = remember(vehicle) { vehicle.dataCoverage() }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.2.dp, Color(0xFFD1D5DB)),
        modifier = Modifier.testTag("verification_coverage_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFF15803D),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Data Verification & Coverage",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProvenanceBadge(
                    specSource = specSource,
                    verificationStatus = verification
                )
                CoveragePill(
                    completed = coverage.completedFields,
                    total = coverage.totalFields
                )
            }

            Text(
                text = verification.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1F2937)
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Specification Coverage",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black
                    )
                    Text(
                        text = "${coverage.completenessPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF15803D)
                    )
                }
                LinearProgressIndicator(
                    progress = { coverage.completedFields.toFloat() / coverage.totalFields.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = Color(0xFF15803D),
                    trackColor = Color(0xFFE5E7EB)
                )
            }

            Text(
                text = if (coverage.missingOrUnknownLabels.isEmpty()) {
                    "All core specifications provided. Studio angles: ${vehicle.angleCoverageCount()}/4. Photo attached: ${if (coverage.hasCustomPhoto) "Yes" else "Using studio silhouette"}."
                } else {
                    "Unknown or optional omitted fields: ${coverage.missingOrUnknownLabels.joinToString(", ")}. Studio angles: ${vehicle.angleCoverageCount()}/4."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF374151)
            )

            if (vehicle.aiInspectionSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.testTag("overview_ai_summary_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Gemini Vision Observation (Unverified)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = vehicle.aiInspectionSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationDetailsCard(vehicle: Vehicle) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val createdLabel = vehicle.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Pending sync"
    val updatedLabel = vehicle.updatedAt?.toDate()?.let { dateFormat.format(it) } ?: "Pending sync"

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFF6F7F9),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overview_registration_details_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Registration Details",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black
            )
            SpecLineItem(label = "Manufacturer", value = vehicle.manufacturer, isUnknown = false)
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(label = "Model & Trim", value = "${vehicle.model} • ${vehicle.trim}", isUnknown = false)
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(label = "Model Year", value = vehicle.modelYear.toString(), isUnknown = false)
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(label = "Market / Country", value = vehicle.market, isUnknown = false)
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(label = "Registered", value = createdLabel, isUnknown = false)
            HorizontalDivider(color = Color(0xFFE5E7EB))
            SpecLineItem(label = "Last Updated", value = updatedLabel, isUnknown = false)
        }
    }
}

@Composable
private fun SpecLineItem(
    label: String,
    value: String,
    isUnknown: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4B5563),
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUnknown) Color(0xFFB45309) else Color.Black,
            modifier = Modifier.weight(0.55f)
        )
    }
}
