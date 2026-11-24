package com.example.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AttachmentKind
import com.example.data.model.RequestedCaptureType
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.MotoraStrings
import com.example.data.settings.appStrings
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.components.persistVehiclePhotoToAppStorage
import java.io.File

private val MackBackgroundBrush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to Color(0xFF1F2022),
        0.48f to Color(0xFF23262C),
        0.74f to Color(0xFF2B4D75),
        1.0f to Color(0xFF3E7EC5)
    )
)

private val MackCardSurface = Color(0xFF4F5257).copy(alpha = 0.62f)
private val MackGlassPillSurface = Color.White.copy(alpha = 0.16f)
private val MackGlassMenuSurface = Color(0xFF3976B9).copy(alpha = 0.88f)
private val MackGlassBorder = Color.White.copy(alpha = 0.42f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VehicleAiChatScreen(
    viewModel: VehicleAiChatViewModel,
    onNavigateBack: () -> Unit,
    onOpenIssueLog: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val strings = appStrings(LocalAppLanguage.current)

    var pendingCameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraRequestKind by remember { mutableStateOf<RequestedCaptureType?>(null) }
    var showMediaChooserSheet by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, uri) ?: uri.toString()
            val name = resolveDisplayName(context, uri) ?: strings.chatDefaultPhotoName
            viewModel.attachPhoto(savedPath, name, context)
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val name = resolveDisplayName(context, uri) ?: strings.chatDefaultVideoName
            viewModel.attachVideo(uri.toString(), mime, name)
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraPhotoUri
        pendingCameraPhotoUri = null
        if (success && capturedUri != null) {
            val savedPath = persistVehiclePhotoToAppStorage(context, capturedUri) ?: capturedUri.toString()
            viewModel.attachPhoto(savedPath, strings.chatDefaultPhotoName, context)
        }
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val capturedUri = pendingCameraVideoUri
        pendingCameraVideoUri = null
        if (success && capturedUri != null) {
            viewModel.attachVideo(
                uriString = capturedUri.toString(),
                mimeType = "video/mp4",
                fileName = strings.chatDefaultVideoName
            )
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/pdf"
            val name = resolveDisplayName(context, uri) ?: strings.chatDefaultDocName
            viewModel.attachDocument(uri.toString(), mime, name)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startAudioRecording(context)
        } else {
            viewModel.setPermissionDeniedError()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingCameraRequestKind
        pendingCameraRequestKind = null
        if (!granted) {
            viewModel.setCameraPermissionDeniedError()
            return@rememberLauncherForActivityResult
        }
        when (kind) {
            RequestedCaptureType.PHOTO -> {
                val uri = createChatCameraCaptureUri(context, "chat_photo_", ".jpg")
                pendingCameraPhotoUri = uri
                takePhotoLauncher.launch(uri)
            }

            RequestedCaptureType.VIDEO -> {
                val uri = createChatCameraCaptureUri(context, "chat_video_", ".mp4")
                pendingCameraVideoUri = uri
                captureVideoLauncher.launch(uri)
            }

            else -> Unit
        }
    }

    val launchCameraPhoto: () -> Unit = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            val uri = createChatCameraCaptureUri(context, "chat_photo_", ".jpg")
            pendingCameraPhotoUri = uri
            takePhotoLauncher.launch(uri)
        } else {
            pendingCameraRequestKind = RequestedCaptureType.PHOTO
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val launchCameraVideo: () -> Unit = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            val uri = createChatCameraCaptureUri(context, "chat_video_", ".mp4")
            pendingCameraVideoUri = uri
            captureVideoLauncher.launch(uri)
        } else {
            pendingCameraRequestKind = RequestedCaptureType.VIDEO
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val launchAudioRecorder: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.startAudioRecording(context)
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.vehicle?.id) {
        if (uiState.vehicle != null) {
            viewModel.sendInitialTopicIfPresent(context)
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isSending) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelAudioRecording()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MackBackgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Minimal dark glass top bar matching MACK design
            MackChatTopBar(
                title = strings.chatTitle,
                vehicle = uiState.vehicle,
                isObdLiveConnected = uiState.isObdLiveConnected,
                strings = strings,
                onNavigateBack = onNavigateBack,
                onOpenIssueLog = onOpenIssueLog
            )

            val vehicle = uiState.vehicle
            if (uiState.isLoadingVehicle) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                return@Column
            }

            if (vehicle == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.vehicleError ?: strings.overviewVehicleErrorFallback,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFFCA5A5)
                    )
                }
                return@Column
            }

            // Main stage + bottom floating pill composer overlay (using uploaded iPhone 17-22 hero art on landing and transitioning normally to conversation & recording)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.messages.isEmpty() && !uiState.isSending,
                    enter = fadeIn(animationSpec = tween(280)),
                    exit = fadeOut(animationSpec = tween(220)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    MackLandingHeroStage(
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("chat_message_list"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(uiState.messages, key = { _, item -> item.id }) { idx, msg ->
                            ChatMessageBubble(
                                message = msg,
                                index = idx,
                                strings = strings,
                                onSelectQuickReply = { quick ->
                                    viewModel.sendMessage(context, overrideText = quick)
                                },
                                onTriggerCaptureRequest = { requestType, useSecondaryGallery ->
                                    when (requestType) {
                                        RequestedCaptureType.PHOTO -> {
                                            if (useSecondaryGallery) {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } else {
                                                launchCameraPhoto()
                                            }
                                        }

                                        RequestedCaptureType.AUDIO -> launchAudioRecorder()
                                        RequestedCaptureType.VIDEO -> {
                                            if (useSecondaryGallery) {
                                                videoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                                )
                                            } else {
                                                launchCameraVideo()
                                            }
                                        }

                                        RequestedCaptureType.NONE -> Unit
                                    }
                                }
                            )
                        }

                        if (uiState.isSending) {
                            item(key = "ai_typing") {
                                MackTypingCard(statusText = strings.chatTypingStatus)
                            }
                        }
                    }

                    // Bottom error banner
                    if (uiState.errorMessage != null) {
                        Surface(
                            color = Color(0xFF7F1D1D).copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                                .testTag("chat_error_banner"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.dismissError() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Pending attachment preview pill
                    val pending = uiState.pendingAttachment
                    if (pending != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E3A5F).copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp)
                                .testTag("chat_pending_attachment_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when (pending.kind) {
                                        AttachmentKind.PHOTO -> Icons.Filled.AddPhotoAlternate
                                        AttachmentKind.FILE -> Icons.Filled.AttachFile
                                        AttachmentKind.AUDIO -> Icons.Filled.GraphicEq
                                        AttachmentKind.VIDEO -> Icons.Filled.Videocam
                                        AttachmentKind.TEXT_ONLY -> Icons.Filled.DirectionsCar
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.chatPendingPrefix} ${pending.displayLabel}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val privacyMsg = uiState.platePrivacyNotice
                                    if (!privacyMsg.isNullOrBlank()) {
                                        Text(
                                            text = privacyMsg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF93C5FD),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.clearPendingAttachment() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove attachment",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Live car audio recording bar
                    AnimatedVisibility(visible = uiState.isRecordingAudio) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF7F1D1D).copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .testTag("chat_recording_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${strings.chatRecordingStatusPrefix} 0:${uiState.recordingSeconds.toString().padStart(2, '0')} / 0:25",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                    Text(
                                        text = strings.chatRecordingHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.cancelAudioRecording() },
                                    modifier = Modifier.testTag("chat_cancel_recording_button")
                                ) {
                                    Text(strings.chatCancelRecording, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.stopAudioRecording() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF7F1D1D)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("chat_stop_recording_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = strings.chatUseAudio,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(strings.chatUseAudio)
                                }
                            }
                        }
                    }

                    // Bottom Pill Composer + Frame 6 Floating Vertical Capsule
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        val canSend = !uiState.isSending && !uiState.isRecordingAudio &&
                            (uiState.inputText.isNotBlank() || uiState.pendingAttachment != null)

                        // Frosted Pill Bar matching iPhone 17 - 20 & iPhone 17 - 21
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MackGlassPillSurface,
                            border = BorderStroke(1.dp, MackGlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleAttachmentMenu() },
                                    enabled = !uiState.isRecordingAudio && !uiState.isSending,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_plus_button")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isAttachmentMenuOpen) Icons.Filled.Close else Icons.Filled.Add,
                                        contentDescription = strings.chatPlusDesc,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (uiState.inputText.isEmpty()) {
                                        Text(
                                            text = strings.chatInputPlaceholder,
                                            style = TextStyle(
                                                color = Color.White.copy(alpha = 0.78f),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.inputText,
                                        onValueChange = { viewModel.updateInputText(it) },
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(Color.White),
                                        maxLines = 4,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (canSend) viewModel.sendMessage(context)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("chat_message_input")
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.sendMessage(context) },
                                    enabled = canSend,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_send_button")
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (canSend) Color.White else Color.White.copy(alpha = 0.22f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = strings.chatSendDesc,
                                            tint = if (canSend) Color(0xFF1F3E68) else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Frame 6 Vertical Frosted Attachment Capsule docked directly above the '+' / 'x' button
                        androidx.compose.animation.AnimatedVisibility(
                            visible = uiState.isAttachmentMenuOpen && !uiState.isRecordingAudio,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 6.dp, bottom = 64.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MackGlassMenuSurface,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                shadowElevation = 10.dp,
                                modifier = Modifier.testTag("chat_attachment_menu")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Camera / Photo & Video trigger (top icon matching Frame 6)
                                    IconButton(
                                        onClick = { showMediaChooserSheet = true },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("chat_attach_photo_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoCamera,
                                            contentDescription = strings.chatPhotoTileTitle,
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }

                                    // 2. Live Car Audio Recording (middle icon matching Frame 6)
                                    IconButton(
                                        onClick = launchAudioRecorder,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("chat_attach_audio_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Mic,
                                            contentDescription = strings.chatAudioTileTitle,
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }

                                    // 3. Document+ picker (bottom icon matching Frame 6)
                                    IconButton(
                                        onClick = {
                                            documentPickerLauncher.launch(
                                                arrayOf("application/pdf", "image/*", "text/plain")
                                            )
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("chat_attach_file_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.NoteAdd,
                                            contentDescription = strings.chatFileTileTitle,
                                            tint = Color.White,
                                            modifier = Modifier.size(25.dp)
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

    if (showMediaChooserSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMediaChooserSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1F242D),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = strings.chatCameraSheetTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showMediaChooserSheet = false },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_media_sheet_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.chatCancelRecording,
                            tint = Color.White
                        )
                    }
                }

                MediaChooserRow(
                    icon = Icons.Filled.PhotoCamera,
                    title = strings.chatRequestTakePhotoCta,
                    onClick = {
                        showMediaChooserSheet = false
                        launchCameraPhoto()
                    }
                )
                MediaChooserRow(
                    icon = Icons.Filled.PhotoLibrary,
                    title = strings.chatRequestPickPhotoCta,
                    onClick = {
                        showMediaChooserSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                MediaChooserRow(
                    icon = Icons.Filled.Videocam,
                    title = strings.chatRequestRecordVideoCta,
                    onClick = {
                        showMediaChooserSheet = false
                        launchCameraVideo()
                    }
                )
                MediaChooserRow(
                    icon = Icons.Filled.AddPhotoAlternate,
                    title = strings.chatRequestPickVideoCta,
                    modifier = Modifier.testTag("chat_attach_video_button"),
                    onClick = {
                        showMediaChooserSheet = false
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MediaChooserRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MackChatTopBar(
    title: String,
    vehicle: com.example.data.model.Vehicle?,
    isObdLiveConnected: Boolean = false,
    strings: MotoraStrings,
    onNavigateBack: () -> Unit,
    onOpenIssueLog: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.chatBackDesc,
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isObdLiveConnected) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF065F46).copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                        modifier = Modifier.testTag("chat_obd_live_pill")
                    ) {
                        Text(
                            text = "● OBD LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFD1FAE5),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (vehicle != null) {
                Text(
                    text = "${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (vehicle != null) {
            Surface(
                onClick = { onOpenIssueLog(vehicle.id) },
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("chat_switch_to_log_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FactCheck,
                        contentDescription = strings.chatOpenLogDesc,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.chatOpenLogLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        LanguageSettingsActionButton(tint = Color.White)
    }
}

@Composable
private fun MackLandingHeroStage(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "mack_hero_motion")
    val breatheScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.038f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_breathe_scale"
    )
    val floatY by transition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_float_y"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
    ) {
        // Direct embedded iPhone 17-22 "MEET MACK" artwork with subtle breathing & float animation
        Image(
            painter = painterResource(id = R.drawable.img_mack_hero_bg),
            contentDescription = "MEET MACK",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                    translationY = floatY * density
                }
                .testTag("chat_mack_landing_hero")
        )

        // Soft bottom atmospheric gradient so the frosted pill composer stays legible
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF2B4D75).copy(alpha = 0.55f),
                            Color(0xFF3E7EC5).copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun MackTypingCard(
    statusText: String
) {
    val thinkingTransition = rememberInfiniteTransition(label = "mack_typing_avatar")
    val nodRotation by thinkingTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_nod"
    )
    val bounceY by thinkingTransition.animateFloat(
        initialValue = -3f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_bounce"
    )
    val pulseScale by thinkingTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_typing_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MackCardSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier
                .padding(start = 18.dp, top = 24.dp)
                .widthIn(max = 340.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_mack_mascot),
                contentDescription = "MACK",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        rotationZ = nodRotation
                        translationY = bounceY * density
                    }
                    .clip(CircleShape)
            )
            Text(
                text = "MACK",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessageItem,
    index: Int,
    strings: MotoraStrings,
    onSelectQuickReply: (String) -> Unit,
    onTriggerCaptureRequest: (RequestedCaptureType, useSecondaryGallery: Boolean) -> Unit
) {
    if (message.isFromUser) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = Color(0xFF5D9CE6).copy(alpha = 0.45f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .testTag("chat_user_bubble_$index")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.attachmentLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "📎 ${message.attachmentLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    } else {
        // MACK AI reply card matching iPhone 17 - 21 (dark frosted translucent card + overlapping top-left MACK avatar + "MACK" label)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MackCardSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                modifier = Modifier
                    .padding(start = 18.dp, top = 24.dp)
                    .widthIn(max = 360.dp)
                    .testTag("chat_ai_bubble_$index")
            ) {
                Column(
                    modifier = Modifier.padding(start = 22.dp, top = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!message.isCarRelated) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF991B1B).copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = strings.chatCarOnlyBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else if (message.modelCommonality.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF60A5FA).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = message.modelCommonality,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFDBEAFE),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    if (message.followUpQuestion.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.HelpOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = message.followUpQuestion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }

                    if (message.isCarRelated && message.requestedCaptureType != RequestedCaptureType.NONE) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E3A5F).copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, Color(0xFF93C5FD).copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_ai_capture_request_$index")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = when (message.requestedCaptureType) {
                                            RequestedCaptureType.PHOTO -> Icons.Filled.AddAPhoto
                                            RequestedCaptureType.AUDIO -> Icons.Filled.Mic
                                            RequestedCaptureType.VIDEO -> Icons.Filled.Videocam
                                            RequestedCaptureType.NONE -> Icons.Filled.HelpOutline
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFF93C5FD),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = strings.captureRequestTitle(message.requestedCaptureType),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                                if (message.requestedCaptureReason.isNotBlank()) {
                                    Text(
                                        text = message.requestedCaptureReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.88f)
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (message.requestedCaptureType) {
                                        RequestedCaptureType.PHOTO -> {
                                            Button(
                                                onClick = { onTriggerCaptureRequest(RequestedCaptureType.PHOTO, false) },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF1F3E68)
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("chat_ai_capture_action_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AddAPhoto,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(strings.chatRequestTakePhotoCta)
                                            }
                                            OutlinedButton(
                                                onClick = { onTriggerCaptureRequest(RequestedCaptureType.PHOTO, true) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("chat_ai_capture_secondary_$index")
                                            ) {
                                                Text(strings.chatRequestPickPhotoCta)
                                            }
                                        }

                                        RequestedCaptureType.AUDIO -> {
                                            Button(
                                                onClick = { onTriggerCaptureRequest(RequestedCaptureType.AUDIO, false) },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF1F3E68)
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("chat_ai_capture_action_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Mic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(strings.chatRequestRecordAudioCta)
                                            }
                                        }

                                        RequestedCaptureType.VIDEO -> {
                                            Button(
                                                onClick = { onTriggerCaptureRequest(RequestedCaptureType.VIDEO, false) },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF1F3E68)
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("chat_ai_capture_action_$index")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Videocam,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(strings.chatRequestRecordVideoCta)
                                            }
                                            OutlinedButton(
                                                onClick = { onTriggerCaptureRequest(RequestedCaptureType.VIDEO, true) },
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("chat_ai_capture_secondary_$index")
                                            ) {
                                                Text(strings.chatRequestPickVideoCta)
                                            }
                                        }

                                        RequestedCaptureType.NONE -> Unit
                                    }
                                }
                            }
                        }
                    }

                    if (message.suggestedQuickReplies.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            message.suggestedQuickReplies.forEachIndexed { qIdx, option ->
                                Surface(
                                    onClick = { onSelectQuickReply(option) },
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .heightIn(min = 38.dp)
                                        .testTag("chat_quick_reply_${index}_$qIdx")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (message.savedToLog) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.chatSavedToLogLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBFDBFE)
                            )
                        }
                    }
                }
            }

            // Animated MACK character avatar & "MACK" title matching iPhone 17 - 21
            var avatarAppeared by remember(message.id) { mutableStateOf(false) }
            LaunchedEffect(message.id) { avatarAppeared = true }
            val popScale by animateFloatAsState(
                targetValue = if (avatarAppeared) 1f else 0.72f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "mack_reply_pop"
            )
            val replyIdleTransition = rememberInfiniteTransition(label = "mack_reply_idle")
            val gentleFloatY by replyIdleTransition.animateFloat(
                initialValue = -1.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mack_reply_float"
            )
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_mack_mascot),
                    contentDescription = "MACK",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .graphicsLayer {
                            scaleX = popScale
                            scaleY = popScale
                            translationY = gentleFloatY * density
                        }
                        .clip(CircleShape)
                )
                Text(
                    text = "MACK",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
        }
    }
}

private fun createChatCameraCaptureUri(context: Context, prefix: String, extension: String): Uri {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val file = File.createTempFile(prefix, extension, dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
}
