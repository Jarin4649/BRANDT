package com.example.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.CarChatTurn
import com.example.data.ai.FirebaseVehicleMechanicAiService
import com.example.data.ai.PendingChatAttachment
import com.example.data.ai.VehicleMechanicAiService
import com.example.data.model.AttachmentKind
import com.example.data.model.RequestedCaptureType
import com.example.data.model.Vehicle
import com.example.data.obd.BatteryChemistryType
import com.example.data.obd.ElectricalSystemType
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionStatus
import com.example.data.obd.ObdDiagnosticSession
import com.example.data.obd.SystemVoltageCriteria
import com.example.data.obd.SystemVoltageProcessor
import com.example.data.repository.VehicleDataSource
import com.example.data.settings.AppLanguage
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.appStrings
import com.example.ui.components.LicensePlatePrivacyProtector
import java.util.Locale
import kotlin.math.roundToInt
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val isFromUser: Boolean,
    val text: String,
    val attachmentKind: AttachmentKind = AttachmentKind.TEXT_ONLY,
    val attachmentLabel: String = "",
    val isCarRelated: Boolean = true,
    val modelCommonality: String = "",
    val followUpQuestion: String = "",
    val suggestedQuickReplies: List<String> = emptyList(),
    val savedToLog: Boolean = false,
    val requestedCaptureType: RequestedCaptureType = RequestedCaptureType.NONE,
    val requestedCaptureReason: String = ""
)

data class VehicleAiChatUiState(
    val vehicle: Vehicle? = null,
    val isLoadingVehicle: Boolean = true,
    val vehicleError: String? = null,
    val messages: List<ChatMessageItem> = emptyList(),
    val inputText: String = "",
    val isAttachmentMenuOpen: Boolean = false,
    val pendingAttachment: PendingChatAttachment? = null,
    val isRecordingAudio: Boolean = false,
    val recordingSeconds: Int = 0,
    val isSending: Boolean = false,
    val platePrivacyNotice: String? = null,
    val isObdLiveConnected: Boolean = false,
    val liveObdSummaryLabel: String? = null,
    val errorMessage: String? = null
)

class VehicleAiChatViewModel(
    private val repository: VehicleDataSource,
    private val currentUserId: String,
    private val vehicleId: String,
    private val initialTopic: String? = null,
    private val aiService: VehicleMechanicAiService = FirebaseVehicleMechanicAiService(),
    private val audioRecorder: CarAudioRecorder = CarAudioRecorder(),
    private val languageState: StateFlow<AppLanguage> = LanguagePreferenceStore.currentLanguage,
    private val connectionManager: ObdConnectionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VehicleAiChatUiState(
            inputText = initialTopic?.trim().orEmpty()
        )
    )
    val uiState: StateFlow<VehicleAiChatUiState> = _uiState.asStateFlow()

    private var recordingTimerJob: Job? = null
    private var initialTopicSent = false

    init {
        viewModelScope.launch {
            repository.observeVehicle(currentUserId, vehicleId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    Log.w(TAG, "Error observing vehicle for AI chat", root)
                    _uiState.update {
                        it.copy(
                            isLoadingVehicle = false,
                            vehicleError = root.localizedMessage ?: appStrings(languageState.value).overviewVehicleErrorFallback
                        )
                    }
                }
                .collectLatest { vehicle ->
                    val missingMsg = if (languageState.value == AppLanguage.THAI) {
                        "ไม่พบข้อมูลรถคันนี้"
                    } else {
                        "Vehicle record not found."
                    }
                    _uiState.update {
                        it.copy(
                            vehicle = vehicle,
                            isLoadingVehicle = false,
                            vehicleError = if (vehicle == null) missingMsg else null
                        )
                    }
                }
        }
        observeObdLiveStatus()
    }

    private fun observeObdLiveStatus() {
        val manager = connectionManager ?: return
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(manager.state, manager.liveTelemetry) { conn, telemetry ->
                val isConnected = conn.connectionStatus is ObdConnectionStatus.Connected
                val session = telemetry.latestSession
                val label = if (isConnected && session != null) {
                    buildCompactObdBadgeLabel(session)
                } else if (isConnected) {
                    (conn.connectionStatus as? ObdConnectionStatus.Connected)?.targetLabel
                } else null
                isConnected to label
            }.collect { (connected, label) ->
                _uiState.update {
                    it.copy(
                        isObdLiveConnected = connected,
                        liveObdSummaryLabel = label
                    )
                }
            }
        }
    }

    private fun buildCompactObdBadgeLabel(session: ObdDiagnosticSession): String {
        val pids = session.livePidReadings.associateBy { it.pidCode.uppercase() }
        val rpm = pids["010C"]?.takeIf { it.isAvailable }?.value?.roundToInt()?.let { "$it rpm" }
        val coolant = pids["0105"]?.takeIf { it.isAvailable }?.value?.roundToInt()?.let { "$it°C" }
        val dtc = session.confirmedDtcs.size + session.pendingDtcs.size
        return listOfNotNull(rpm, coolant, if (dtc > 0) "$dtc DTC" else null).joinToString(" • ")
    }

    internal fun buildPromptWithLiveObdContext(
        userText: String,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val manager = connectionManager ?: return userText
        val connState = manager.state.value
        val telemetry = manager.liveTelemetry.value
        val vehicle = _uiState.value.vehicle
        val isTransportConnected = connState.connectionStatus is ObdConnectionStatus.Connected &&
            manager.activeTransportStream?.isConnected == true

        val mergedCriteria = connState.systemVoltageCriteria.let { base ->
            val vSys = ElectricalSystemType.fromCode(vehicle?.electricalSystemType)
            val vBat = BatteryChemistryType.fromCode(vehicle?.batteryChemistryType)
            base.copy(
                systemType = if (base.systemType != ElectricalSystemType.UNKNOWN) base.systemType else vSys,
                batteryType = if (base.batteryType != BatteryChemistryType.UNSPECIFIED) base.batteryType else vBat
            )
        }

        val voltageData = SystemVoltageProcessor.processSession(
            session = telemetry.latestSession,
            connectionManagerState = connState,
            isTransportConnected = isTransportConnected,
            criteria = mergedCriteria,
            recentVoltageSamples = telemetry.recentVoltageSamples,
            lastDisconnectError = telemetry.lastDisconnectReason
                ?: (connState.connectionStatus as? ObdConnectionStatus.Error)?.message,
            isCommunicationInterrupted = telemetry.isCommunicationInterrupted,
            nowMs = nowMs
        )
        val voltageBlock = voltageData.toMackContextString(languageState.value)

        val session = telemetry.latestSession
        val sessionBlock = if (connState.connectionStatus is ObdConnectionStatus.Connected && session != null) {
            val readings = session.livePidReadings
                .filter { it.isAvailable }
                .take(10)
                .joinToString(", ") { "${it.name} (${it.pidCode}): ${it.formattedValueOrUnavailable()}" }
            val dtcs = session.allDtcs.joinToString(", ") { "${it.code} (${it.status.code})" }
                .ifBlank { "None detected (0)" }
            "[Live OBD-II ECU Snapshot from Vehicle • DTCs: $dtcs • Sensors: ${readings.ifBlank { "Connected, waiting for sensors" }}]"
        } else {
            null
        }

        return listOfNotNull(voltageBlock, sessionBlock, userText).joinToString("\n")
    }

    fun sendInitialTopicIfPresent(context: Context) {
        val topic = initialTopic?.trim().orEmpty()
        if (topic.isNotEmpty() && !initialTopicSent && _uiState.value.vehicle != null) {
            initialTopicSent = true
            sendMessage(context, overrideText = topic)
        }
    }

    fun updateInputText(newText: String) {
        _uiState.update { it.copy(inputText = newText.take(800), errorMessage = null) }
    }

    fun toggleAttachmentMenu() {
        _uiState.update { it.copy(isAttachmentMenuOpen = !it.isAttachmentMenuOpen, errorMessage = null) }
    }

    fun closeAttachmentMenu() {
        _uiState.update { it.copy(isAttachmentMenuOpen = false) }
    }

    fun attachPhoto(uriOrPath: String, label: String? = null, context: Context? = null) {
        val resolvedLabel = label?.takeIf { it.isNotBlank() } ?: appStrings(languageState.value).chatDefaultPhotoName
        _uiState.update {
            it.copy(
                pendingAttachment = PendingChatAttachment.Photo(uriOrPath, resolvedLabel),
                isAttachmentMenuOpen = false,
                platePrivacyNotice = null,
                errorMessage = null
            )
        }
        if (context != null && uriOrPath.isNotBlank()) {
            viewModelScope.launch {
                val protection = LicensePlatePrivacyProtector.protectVehiclePhoto(
                    context = context.applicationContext,
                    sourceUri = uriOrPath
                )
                if (protection.outputUri != uriOrPath) {
                    _uiState.update { state ->
                        val pending = state.pendingAttachment
                        if (pending is PendingChatAttachment.Photo && pending.pathOrUri == uriOrPath) {
                            state.copy(
                                pendingAttachment = pending.copy(pathOrUri = protection.outputUri),
                                platePrivacyNotice = null
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun attachDocument(uriString: String, mimeType: String, fileName: String) {
        val strings = appStrings(languageState.value)
        _uiState.update {
            it.copy(
                pendingAttachment = PendingChatAttachment.DocumentFile(
                    uriString = uriString,
                    mimeType = mimeType,
                    displayLabel = fileName.ifBlank { strings.chatDefaultDocName }
                ),
                isAttachmentMenuOpen = false,
                errorMessage = null
            )
        }
    }

    fun attachAudioFileForTest(filePath: String, durationSeconds: Int) {
        val strings = appStrings(languageState.value)
        _uiState.update {
            it.copy(
                pendingAttachment = PendingChatAttachment.RecordedAudio(
                    filePath = filePath,
                    durationSeconds = durationSeconds,
                    displayLabel = strings.chatDefaultAudioLabel(durationSeconds)
                ),
                isAttachmentMenuOpen = false,
                errorMessage = null
            )
        }
    }

    fun attachVideo(uriString: String, mimeType: String = "video/mp4", fileName: String = "") {
        val strings = appStrings(languageState.value)
        _uiState.update {
            it.copy(
                pendingAttachment = PendingChatAttachment.VideoClip(
                    uriString = uriString,
                    mimeType = mimeType,
                    displayLabel = fileName.ifBlank { strings.chatDefaultVideoName }
                ),
                isAttachmentMenuOpen = false,
                errorMessage = null
            )
        }
    }

    fun clearPendingAttachment() {
        _uiState.update { it.copy(pendingAttachment = null, platePrivacyNotice = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setPermissionDeniedError() {
        _uiState.update {
            it.copy(
                isAttachmentMenuOpen = false,
                errorMessage = appStrings(languageState.value).chatMicPermissionError
            )
        }
    }

    fun setCameraPermissionDeniedError() {
        _uiState.update {
            it.copy(
                isAttachmentMenuOpen = false,
                errorMessage = appStrings(languageState.value).chatCameraPermissionError
            )
        }
    }

    fun startAudioRecording(context: Context) {
        if (_uiState.value.isRecordingAudio) return
        val startResult = audioRecorder.startRecording(context)
        startResult.fold(
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isAttachmentMenuOpen = false,
                        isRecordingAudio = true,
                        recordingSeconds = 0,
                        errorMessage = null
                    )
                }
                recordingTimerJob?.cancel()
                recordingTimerJob = viewModelScope.launch {
                    for (sec in 1..MAX_RECORD_SECONDS) {
                        delay(1000L)
                        if (!_uiState.value.isRecordingAudio) break
                        _uiState.update { it.copy(recordingSeconds = sec) }
                    }
                    if (_uiState.value.isRecordingAudio) {
                        stopAudioRecording()
                    }
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isAttachmentMenuOpen = false,
                        isRecordingAudio = false,
                        errorMessage = error.localizedMessage ?: appStrings(languageState.value).chatMicPermissionError
                    )
                }
            }
        )
    }

    fun stopAudioRecording() {
        if (!_uiState.value.isRecordingAudio) return
        recordingTimerJob?.cancel()
        val elapsed = _uiState.value.recordingSeconds.coerceAtLeast(1)
        val strings = appStrings(languageState.value)
        val stopResult = audioRecorder.stopRecording()
        stopResult.fold(
            onSuccess = { file: File ->
                _uiState.update {
                    it.copy(
                        isRecordingAudio = false,
                        recordingSeconds = 0,
                        pendingAttachment = PendingChatAttachment.RecordedAudio(
                            filePath = file.absolutePath,
                            durationSeconds = elapsed,
                            displayLabel = strings.chatDefaultAudioLabel(elapsed)
                        ),
                        errorMessage = null
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isRecordingAudio = false,
                        recordingSeconds = 0,
                        errorMessage = error.localizedMessage ?: "Audio recording failed."
                    )
                }
            }
        )
    }

    fun cancelAudioRecording() {
        recordingTimerJob?.cancel()
        audioRecorder.cancelRecording()
        _uiState.update {
            it.copy(
                isRecordingAudio = false,
                recordingSeconds = 0
            )
        }
    }

    fun sendMessage(context: Context, overrideText: String? = null) {
        val currentState = _uiState.value
        if (currentState.isSending) return
        val activeLanguage = languageState.value
        val strings = appStrings(activeLanguage)

        val vehicle = currentState.vehicle ?: run {
            _uiState.update { it.copy(errorMessage = strings.chatVehicleNotLoadedError) }
            return
        }

        val textToSend = (overrideText ?: currentState.inputText).trim()
        val attachment = currentState.pendingAttachment
        if (textToSend.isEmpty() && attachment == null) {
            _uiState.update { it.copy(errorMessage = strings.chatEmptySendError) }
            return
        }

        val displayUserText = textToSend.ifEmpty {
            when (attachment) {
                is PendingChatAttachment.Photo -> strings.chatDefaultPhotoPrompt
                is PendingChatAttachment.DocumentFile -> strings.chatDefaultDocPrompt
                is PendingChatAttachment.RecordedAudio -> strings.chatDefaultAudioPrompt
                is PendingChatAttachment.VideoClip -> strings.chatDefaultVideoPrompt
                null -> ""
            }
        }

        val historySnapshot = currentState.messages.map {
            CarChatTurn(isFromUser = it.isFromUser, text = it.text, attachmentLabel = it.attachmentLabel)
        }

        val userBubble = ChatMessageItem(
            isFromUser = true,
            text = displayUserText,
            attachmentKind = attachment?.kind ?: AttachmentKind.TEXT_ONLY,
            attachmentLabel = attachment?.displayLabel.orEmpty()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userBubble,
                inputText = if (overrideText == null) "" else it.inputText,
                pendingAttachment = null,
                platePrivacyNotice = null,
                isAttachmentMenuOpen = false,
                isSending = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val protectedAttachment = if (attachment is PendingChatAttachment.Photo) {
                val protection = LicensePlatePrivacyProtector.protectVehiclePhoto(
                    context = context.applicationContext,
                    sourceUri = attachment.pathOrUri
                )
                attachment.copy(pathOrUri = protection.outputUri)
            } else {
                attachment
            }

            val aiResult = aiService.sendCarMechanicMessage(
                context = context,
                vehicle = vehicle,
                history = historySnapshot,
                userText = buildPromptWithLiveObdContext(displayUserText),
                attachment = protectedAttachment,
                language = activeLanguage
            )

            aiResult.fold(
                onSuccess = { reply ->
                    var savedToLog = false
                    if (reply.isCarRelated) {
                        val logSaveResult = repository.addDiagnosisLog(
                            userId = currentUserId,
                            vehicleId = vehicle.id,
                            userPrompt = displayUserText,
                            aiSummary = reply.logSummary.ifBlank { reply.simpleReply },
                            attachmentKind = attachment?.kind ?: AttachmentKind.TEXT_ONLY,
                            attachmentLabel = attachment?.displayLabel.orEmpty(),
                            modelCommonality = reply.modelCommonality,
                            followUpQuestion = reply.followUpQuestion
                        )
                        savedToLog = logSaveResult.isSuccess
                        if (logSaveResult.isFailure) {
                            Log.w(TAG, "Chat succeeded but failed saving DiagnosisLog", logSaveResult.exceptionOrNull())
                        }
                    }

                    val aiBubble = ChatMessageItem(
                        isFromUser = false,
                        text = reply.simpleReply,
                        isCarRelated = reply.isCarRelated,
                        modelCommonality = reply.modelCommonality,
                        followUpQuestion = reply.followUpQuestion,
                        suggestedQuickReplies = reply.suggestedQuickReplies,
                        savedToLog = savedToLog,
                        requestedCaptureType = reply.requestedCaptureType,
                        requestedCaptureReason = reply.requestedCaptureReason
                    )
                    _uiState.update {
                        it.copy(
                            messages = it.messages + aiBubble,
                            isSending = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.localizedMessage ?: strings.chatSendFailedFallback
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingTimerJob?.cancel()
        audioRecorder.cancelRecording()
    }

    private companion object {
        const val TAG = "VehicleAiChatVM"
        const val MAX_RECORD_SECONDS = 25
    }
}
