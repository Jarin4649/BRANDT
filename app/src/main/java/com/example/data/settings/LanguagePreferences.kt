package com.example.data.settings

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AttachmentKind
import com.example.data.model.RequestedCaptureType
import com.example.data.model.VehicleAngle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.motoraSettingsDataStore by preferencesDataStore(name = "motora_settings")

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val subtitleEn: String,
    val subtitleTh: String
) {
    ENGLISH(
        code = "en",
        displayName = "English (Default)",
        subtitleEn = "Standard English interface & Master Technician AI",
        subtitleTh = "ภาษาอังกฤษ (ค่าเริ่มต้น) • หน้าจอและ AI ช่างผู้เชี่ยวชาญ"
    ),
    THAI(
        code = "th",
        displayName = "ภาษาไทย (Thai)",
        subtitleEn = "Translate screens, Known Issues, and AI diagnostics to Thai",
        subtitleTh = "แปลเมนู ปัญหาประจำรุ่น และคำตอบ AI ช่างผู้เชี่ยวชาญเป็นภาษาไทย"
    );

    companion object {
        fun fromCode(raw: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(raw, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ENGLISH }

object LanguagePreferenceStore {
    private val LANGUAGE_KEY = stringPreferencesKey("app_language")

    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    suspend fun loadSavedLanguage(context: Context) {
        runCatching {
            val prefs = context.applicationContext.motoraSettingsDataStore.data.first()
            AppLanguage.fromCode(prefs[LANGUAGE_KEY])
        }.onSuccess { loaded ->
            _currentLanguage.value = loaded
        }
    }

    fun setLanguage(context: Context, language: AppLanguage, scope: CoroutineScope) {
        _currentLanguage.value = language
        scope.launch {
            runCatching {
                context.applicationContext.motoraSettingsDataStore.edit { prefs ->
                    prefs[LANGUAGE_KEY] = language.code
                }
            }
        }
    }

    fun setLanguageImmediate(language: AppLanguage) {
        _currentLanguage.value = language
    }
}

data class MotoraStrings(
    val language: AppLanguage,
    // Settings Dialog
    val settingsTitle: String,
    val settingsDescription: String,
    val settingsLanguageSectionTitle: String,
    val settingsDone: String,
    val openSettingsContentDesc: String,
    // Garage Screen
    val garageSubtitlePrefix: String,
    val garageSubtitleStandalone: String,
    val garageAddVehicle: String,
    val garageViewVehicle: String,
    val garageEdit: String,
    val garageAskAiButton: String,
    val garageAskAiDesc: String,
    val garageLogButton: String,
    val garageLogDesc: String,
    val garageCollectionHeader: String,
    val garageSignOut: String,
    val garageRemoveTitle: String,
    val garageRemoveConfirm: String,
    val garageCancel: String,
    val emptyGarageHeadline: String,
    val emptyGarageCta: String,
    // Pre-Registration Photo Guide Screen
    val guidePreTitle: String,
    val guideTitle: String,
    val guideFrontLabel: String,
    val guideLeftLabel: String,
    val guideRightLabel: String,
    val guideRearLabel: String,
    val guideWhyTitle: String,
    val guideWhyBody: String,
    val guidePrivacyNote: String,
    val guideStartButton: String,
    // Vehicle Overview Screen
    val overviewTitle: String,
    val overviewBackToGarage: String,
    val overviewAskAiTitle: String,
    val overviewAskAiSubtitle: String,
    val overviewIssueLogTitle: String,
    val overviewIssueLogSubtitle: String,
    val overviewKnownIssuesTitle: String,
    val overviewKnownIssuesSubtitleSuffix: String,
    val overviewKnownIssuesRefreshDesc: String,
    val overviewKnownIssuesLoadingPrefix: String,
    val overviewKnownIssuesRetry: String,
    val overviewKnownIssuesLoadErrorFallback: String,
    val overviewKnownIssuesNoticePrefix: String,
    val overviewKnownIssuesAskAi: String,
    val overviewVehicleErrorFallback: String,
    // AI Chat Screen & ViewModel
    val chatTitle: String,
    val chatOpenLogLabel: String,
    val chatOpenLogDesc: String,
    val chatBackDesc: String,
    val chatBannerVehiclePrefix: String,
    val chatBannerScopeSubtitle: String,
    val chatWelcomeTitle: String,
    val chatWelcomeBody: String,
    val chatStarterHeadingPrefix: String,
    val chatStarters: List<String>,
    val chatTypingStatus: String,
    val chatCarOnlyBadge: String,
    val chatSavedToLogLabel: String,
    val chatPlusDesc: String,
    val chatInputPlaceholder: String,
    val chatSendDesc: String,
    val chatPhotoTileTitle: String,
    val chatPhotoTileSub: String,
    val chatFileTileTitle: String,
    val chatFileTileSub: String,
    val chatAudioTileTitle: String,
    val chatAudioTileSub: String,
    val chatVideoTileTitle: String,
    val chatVideoTileSub: String,
    val chatPendingPrefix: String,
    val chatRecordingStatusPrefix: String,
    val chatRecordingHint: String,
    val chatCancelRecording: String,
    val chatUseAudio: String,
    val chatDefaultPhotoName: String,
    val chatDefaultDocName: String,
    val chatDefaultVideoName: String,
    val chatDefaultAudioLabel: (Int) -> String,
    val chatDefaultPhotoPrompt: String,
    val chatDefaultDocPrompt: String,
    val chatDefaultAudioPrompt: String,
    val chatDefaultVideoPrompt: String,
    val chatMicPermissionError: String,
    val chatCameraPermissionError: String,
    val chatEmptySendError: String,
    val chatVehicleNotLoadedError: String,
    val chatSendFailedFallback: String,
    // AI Proactive Capture Requests
    val chatRequestPhotoTitle: String,
    val chatRequestAudioTitle: String,
    val chatRequestVideoTitle: String,
    val chatRequestTakePhotoCta: String,
    val chatRequestPickPhotoCta: String,
    val chatRequestRecordAudioCta: String,
    val chatRequestRecordVideoCta: String,
    val chatRequestPickVideoCta: String,
    val chatCameraSheetTitle: String,
    val floatingMackDesc: String,
    // Issue Log Screen & ViewModel
    val logTitle: String,
    val logAskAiAction: String,
    val logAskAiActionDesc: String,
    val logFilterAll: String,
    val logFilterOpen: String,
    val logFilterResolved: String,
    val logCountLabel: (Int) -> String,
    val logEmptyNoEntries: String,
    val logEmptyFiltered: String,
    val logEmptyDescription: String,
    val logEmptyStartAi: String,
    val logSyncingDate: String,
    val logQueryPrefix: String,
    val logFollowUpPrefix: String,
    val logStatusResolved: String,
    val logStatusOpen: String,
    val logAskMore: String,
    val logAskMoreTopicPrefix: String,
    val logDeleteDesc: String,
    val logMarkedResolvedToast: String,
    val logReopenedToast: String,
    val logUpdateFailedFallback: String,
    val logDeletedToast: String,
    val logDeleteFailedFallback: String,
    val logLoadErrorFallback: String,
    // Vehicle Form Screen & Guided Capture
    val formRegisterTitle: String,
    val formEditTitle: String,
    val formHeroHeadline: String,
    val formCameraCaps: String,
    val formFilesCaps: String,
    val formGuideSectionTitle: String,
    val formGuideSubtitle: String,
    val formTakePhoto: String,
    val formAttachPhoto: String,
    val formReplacePhoto: String,
    val formBrowseFiles: String,
    val formPcFolder: String,
    val formClearAngle: String,
    val formUseSilhouette: String,
    val formAnalyzeWithAi: String,
    val formRescanWithAi: String,
    val formAiScanning: String,
    val formAngleCapturedBadge: String,
    val formAngleEmptyBadge: String,
    // Studio Import Hint
    val studioPcFolderEmptyHint: String,
    // License Plate Privacy Protection
    val plateCoveredNotice: String,
    val plateScanningNotice: String,
    // Vehicle Diagnostics (Phase 1+)
    val navDiagnostics: String,
    val diagnosticsTitle: String,
    val diagnosticsSubtitle: String,
    val diagnosticsReadOnlyBadge: String,
    val diagnosticsVehicleSectionTitle: String,
    val diagnosticsNoVehiclesTitle: String,
    val diagnosticsNoVehiclesBody: String,
    val diagnosticsRegisterVehicleCta: String,
    val diagnosticsConnectionSectionTitle: String,
    val diagnosticsStatusDisconnected: String,
    val diagnosticsConnectionNotice: String,
    val diagnosticsWifiOptionTitle: String,
    val diagnosticsWifiOptionSubtitle: String,
    val diagnosticsBluetoothOptionTitle: String,
    val diagnosticsBluetoothOptionSubtitle: String,
    val diagnosticsConfigureConnectionCta: String,
    val diagnosticsScanSectionTitle: String,
    val diagnosticsScanReadySummary: String,
    val diagnosticsOpenScanCta: String,
    val diagnosticsHistorySectionTitle: String,
    val diagnosticsEmptyHistoryLabel: String,
    val diagnosticsOpenResultsCta: String,
    val diagnosticsOpenHistoryCta: String,
    val diagnosticsOpenReportCta: String,
    val diagnosticsReadOnlyPolicyNote: String
) {
    fun attachmentKindLabel(kind: AttachmentKind): String = when (language) {
        AppLanguage.ENGLISH -> when (kind) {
            AttachmentKind.TEXT_ONLY -> "Symptom Note"
            AttachmentKind.PHOTO -> "Photo"
            AttachmentKind.FILE -> "Document"
            AttachmentKind.AUDIO -> "Vehicle Audio"
            AttachmentKind.VIDEO -> "Video + Audio"
        }
        AppLanguage.THAI -> when (kind) {
            AttachmentKind.TEXT_ONLY -> "ข้อความ"
            AttachmentKind.PHOTO -> "รูปถ่าย"
            AttachmentKind.FILE -> "ไฟล์แนบ"
            AttachmentKind.AUDIO -> "เสียงรถ"
            AttachmentKind.VIDEO -> "วิดีโอ+เสียงรถ"
        }
    }

    fun captureRequestTitle(type: RequestedCaptureType): String = when (type) {
        RequestedCaptureType.PHOTO -> chatRequestPhotoTitle
        RequestedCaptureType.AUDIO -> chatRequestAudioTitle
        RequestedCaptureType.VIDEO -> chatRequestVideoTitle
        RequestedCaptureType.NONE -> ""
    }

    fun angleShortLabel(angle: VehicleAngle): String = when (language) {
        AppLanguage.ENGLISH -> when (angle) {
            VehicleAngle.FRONT -> "Front"
            VehicleAngle.SIDE_LEFT -> "Left Side"
            VehicleAngle.SIDE_RIGHT -> "Right Side"
            VehicleAngle.REAR -> "Rear"
        }
        AppLanguage.THAI -> when (angle) {
            VehicleAngle.FRONT -> "ข้างหน้า"
            VehicleAngle.SIDE_LEFT -> "ด้านข้างซ้าย"
            VehicleAngle.SIDE_RIGHT -> "ด้านข้างขวา"
            VehicleAngle.REAR -> "ด้านหลังรถ"
        }
    }

    fun angleTitle(angle: VehicleAngle): String = when (language) {
        AppLanguage.ENGLISH -> when (angle) {
            VehicleAngle.FRONT -> "Front View (0°)"
            VehicleAngle.SIDE_LEFT -> "Left Side Profile (90°)"
            VehicleAngle.SIDE_RIGHT -> "Right Side Profile (270°)"
            VehicleAngle.REAR -> "Rear View (180°)"
        }
        AppLanguage.THAI -> when (angle) {
            VehicleAngle.FRONT -> "มุมด้านหน้ารถ (0°)"
            VehicleAngle.SIDE_LEFT -> "มุมด้านข้างซ้าย (90°)"
            VehicleAngle.SIDE_RIGHT -> "มุมด้านข้างขวา (270°)"
            VehicleAngle.REAR -> "มุมด้านหลังรถ (180°)"
        }
    }

    fun angleGuideInstruction(angle: VehicleAngle): String = when (language) {
        AppLanguage.ENGLISH -> when (angle) {
            VehicleAngle.FRONT -> "Stand 2–3 steps straight in front of the bumper. Center the grille emblem, both headlights, and lower intake clearly in good light."
            VehicleAngle.SIDE_LEFT -> "Step back far enough to fit both front and rear wheels inside the frame from the driver/left side at waist height."
            VehicleAngle.SIDE_RIGHT -> "Capture the full right profile cleanly in bright even light so roofline, body creases, and wheel design are clear."
            VehicleAngle.REAR -> "Frame the full rear tailgate, taillights, and trim/model badges clearly so AI can verify the exact variant and generation."
        }
        AppLanguage.THAI -> when (angle) {
            VehicleAngle.FRONT -> "ยืนห่างด้านหน้ารถ 2–3 ก้าว ให้เห็นโลโก้กระจังหน้า ไฟหน้าทั้งสองข้าง และกันชนหน้าชัดเจนในแสงสว่าง"
            VehicleAngle.SIDE_LEFT -> "ถอยห่างด้านซ้ายของตัวรถให้เห็นล้อหน้าและล้อหลังครบทั้งคันในระดับเอว เพื่อดูสัดส่วนและตัวถัง"
            VehicleAngle.SIDE_RIGHT -> "ถ่ายมุมด้านขวาของตัวรถเต็มคันให้เห็นแนวหลังคา เส้นสายข้างรถ และลายล้อชัดเจน"
            VehicleAngle.REAR -> "ถ่ายด้านท้ายรถให้เห็นไฟท้าย ฝากระโปรงหลัง และตัวอักษรรุ่นย่อยชัดเจนเพื่อให้ AI ระบุรุ่นได้แม่นยำ"
        }
    }

    fun filterLabel(filterName: String): String = when (filterName) {
        "ALL" -> logFilterAll
        "OPEN" -> logFilterOpen
        "RESOLVED" -> logFilterResolved
        else -> filterName
    }
}

fun appStrings(language: AppLanguage): MotoraStrings {
    return when (language) {
        AppLanguage.ENGLISH -> MotoraStrings(
            language = AppLanguage.ENGLISH,
            settingsTitle = "Language & Preferences",
            settingsDescription = "BRANDT defaults to English with an Automotive Master Technician AI. Switch to Thai anytime to translate the interface and AI diagnostic responses.",
            settingsLanguageSectionTitle = "App & AI Response Language",
            settingsDone = "Done",
            openSettingsContentDesc = "Language and app settings",
            garageSubtitlePrefix = "My Garage",
            garageSubtitleStandalone = "My Garage",
            garageAddVehicle = "Add Vehicle",
            garageViewVehicle = "View Car",
            garageEdit = "Edit",
            garageAskAiButton = "Ask Car AI",
            garageAskAiDesc = "Ask BRANDT Auto Specialist",
            garageLogButton = "Issue Log",
            garageLogDesc = "Open Vehicle Issue Log",
            garageCollectionHeader = "Garage Collection",
            garageSignOut = "Sign out",
            garageRemoveTitle = "Remove Vehicle?",
            garageRemoveConfirm = "Remove",
            garageCancel = "Cancel",
            emptyGarageHeadline = "YOUR\nBRANDT\nGARAGE\nIS EMPTY",
            emptyGarageCta = "Add Your First Car",
            guidePreTitle = "Before fill information",
            guideTitle = "Guided Photo\nCapture",
            guideFrontLabel = "Front",
            guideLeftLabel = "Left Side",
            guideRightLabel = "Right Side",
            guideRearLabel = "REAR",
            guideWhyTitle = "Why is taking a photo necessary?",
            guideWhyBody = "A photo enables the AI to identify the vehicle's make and model for initial data logging, surface known drawbacks or common issues of that model for the user, and build context for future diagnostics when the user encounters issues down the road.",
            guidePrivacyNote = "If possible, it is better to cover your license plate before taking the photo for your safety — and BRANDT will also automatically obscure visible plates.",
            guideStartButton = "Let's Photo",
            overviewTitle = "Car Overview",
            overviewBackToGarage = "Back to My Garage",
            overviewAskAiTitle = "Ask Car Specialist AI",
            overviewAskAiSubtitle = "Pro diagnostics • Audio, video & photo",
            overviewIssueLogTitle = "Vehicle Issue Log",
            overviewIssueLogSubtitle = "Saved AI diagnostic history",
            overviewKnownIssuesTitle = "Known Model Issues & Watch-Outs",
            overviewKnownIssuesSubtitleSuffix = "Pro insight • Clear automotive terms",
            overviewKnownIssuesRefreshDesc = "Refresh model watch-outs",
            overviewKnownIssuesLoadingPrefix = "Analyzing common mechanical watch-outs for your",
            overviewKnownIssuesRetry = "Retry",
            overviewKnownIssuesLoadErrorFallback = "Unable to load common model issues right now.",
            overviewKnownIssuesNoticePrefix = "What to look for:",
            overviewKnownIssuesAskAi = "Ask Specialist AI",
            overviewVehicleErrorFallback = "Unable to load vehicle overview.",
            chatTitle = "BRANDT Auto Specialist",
            chatOpenLogLabel = "Issue Log",
            chatOpenLogDesc = "Open Vehicle Issue Log",
            chatBackDesc = "Back",
            chatBannerVehiclePrefix = "Servicing:",
            chatBannerScopeSubtitle = "Vehicle diagnostics only • Real automotive terms explained clearly",
            chatWelcomeTitle = "Certified Vehicle Diagnostic Assistant",
            chatWelcomeBody = "Describe any symptom, noise, or warning indicator in plain words. Use + to attach a photo, document, live vehicle audio, or video clip.",
            chatStarterHeadingPrefix = "Diagnostic starters for",
            chatStarters = listOf(
                "Knocking or squealing sound when driving — what component causes it?",
                "A warning light appeared on the instrument cluster — is it safe to drive?",
                "Vibration in the steering wheel or pedal under braking",
                "Air conditioning blowing warm air — likely causes"
            ),
            chatTypingStatus = "Analyzing vehicle symptom & model history...",
            chatCarOnlyBadge = "Automotive diagnostics only",
            chatSavedToLogLabel = "Saved to Vehicle Issue Log",
            chatPlusDesc = "Attach photo, file, vehicle audio, or video",
            chatInputPlaceholder = "Ask anything...",
            chatSendDesc = "Send to MACK",
            chatPhotoTileTitle = "Camera & Photo",
            chatPhotoTileSub = "Photo / Video",
            chatFileTileTitle = "Document & Files",
            chatFileTileSub = "PDF / bill",
            chatAudioTileTitle = "Car Sound",
            chatAudioTileSub = "Live mic",
            chatVideoTileTitle = "Video",
            chatVideoTileSub = "Clip + audio",
            chatPendingPrefix = "Attached:",
            chatRecordingStatusPrefix = "Recording vehicle audio...",
            chatRecordingHint = "Hold mic near the sound source, then tap Use Audio",
            chatCancelRecording = "Cancel",
            chatUseAudio = "Use Audio",
            chatDefaultPhotoName = "Vehicle photo",
            chatDefaultDocName = "Attached vehicle document",
            chatDefaultVideoName = "Vehicle video clip",
            chatDefaultAudioLabel = { sec -> "Car audio (${sec}s)" },
            chatDefaultPhotoPrompt = "Please inspect this vehicle photo and explain what component or issue is shown.",
            chatDefaultDocPrompt = "Please review this attached vehicle document and explain the key findings clearly.",
            chatDefaultAudioPrompt = "Please analyze this recorded vehicle sound, explain what component may cause it, and whether it is safe to drive.",
            chatDefaultVideoPrompt = "Please analyze this vehicle video and its audio track, identify any visual or acoustic issue, and advise if it is safe to drive.",
            chatMicPermissionError = "Microphone permission is required so the AI Specialist can listen to your car sound.",
            chatCameraPermissionError = "Camera permission is required to take a vehicle photo or video directly.",
            chatEmptySendError = "Enter a vehicle symptom or tap + to attach a photo, document, audio, or video clip.",
            chatVehicleNotLoadedError = "Vehicle profile is still loading. Please wait a moment.",
            chatSendFailedFallback = "Unable to reach BRANDT Auto Specialist right now.",
            chatRequestPhotoTitle = "Technician Request • Capture Photo",
            chatRequestAudioTitle = "Technician Request • Record Vehicle Sound",
            chatRequestVideoTitle = "Technician Request • Share Video + Audio",
            chatRequestTakePhotoCta = "Take Photo",
            chatRequestPickPhotoCta = "Gallery",
            chatRequestRecordAudioCta = "Record Audio Now",
            chatRequestRecordVideoCta = "Record Video",
            chatRequestPickVideoCta = "Pick Video",
            chatCameraSheetTitle = "Capture or choose vehicle media",
            floatingMackDesc = "Open MACK AI mechanic",
            logTitle = "Vehicle Issue Log",
            logAskAiAction = "Ask AI",
            logAskAiActionDesc = "Open BRANDT Auto Specialist",
            logFilterAll = "All",
            logFilterOpen = "Open",
            logFilterResolved = "Resolved",
            logCountLabel = { count -> if (count == 1) "1 entry" else "$count entries" },
            logEmptyNoEntries = "No diagnostic logs yet",
            logEmptyFiltered = "No matching logs in this filter",
            logEmptyDescription = "Every car symptom, photo, sound, or video analyzed with BRANDT Auto Specialist is automatically summarized and saved here.",
            logEmptyStartAi = "Consult Specialist AI",
            logSyncingDate = "Syncing...",
            logQueryPrefix = "Symptom / Query:",
            logFollowUpPrefix = "Recommended check:",
            logStatusResolved = "Resolved",
            logStatusOpen = "Tracking",
            logAskMore = "Follow Up",
            logAskMoreTopicPrefix = "Follow up on this issue:",
            logDeleteDesc = "Delete log entry",
            logMarkedResolvedToast = "Marked issue as resolved.",
            logReopenedToast = "Reopened issue for tracking.",
            logUpdateFailedFallback = "Unable to update issue status.",
            logDeletedToast = "Issue log entry deleted.",
            logDeleteFailedFallback = "Unable to delete log entry.",
            logLoadErrorFallback = "Unable to load vehicle issue logs.",
            formRegisterTitle = "fill information",
            formEditTitle = "edit information",
            formHeroHeadline = "Let's take a picture\nand brag your car",
            formCameraCaps = "CAMERA",
            formFilesCaps = "FILES",
            formGuideSectionTitle = "Guided Vehicle Photo Capture",
            formGuideSubtitle = "Select an angle below and follow the framing tip so AI identifies brand, model, and trim accurately.",
            formTakePhoto = "Camera",
            formAttachPhoto = "Attach Photo",
            formReplacePhoto = "Change Photo",
            formBrowseFiles = "Browse Files",
            formPcFolder = "PC Folder",
            formClearAngle = "Clear Angle",
            formUseSilhouette = "Use Silhouette",
            formAnalyzeWithAi = "Let's AI start scanning the photo",
            formRescanWithAi = "Re-scan photos with AI",
            formAiScanning = "AI Scanning Photos...",
            formAngleCapturedBadge = "Captured",
            formAngleEmptyBadge = "Add photo",
            studioPcFolderEmptyHint = "No photos in app/src/main/assets/pc_import/ yet. Add .jpg/.png vehicle photos to that folder and reopen to import automatically.",
            plateCoveredNotice = "Visible license plate detected and covered automatically for your privacy.",
            plateScanningNotice = "Checking photo for visible license plates...",
            navDiagnostics = "Vehicle Diagnostics",
            diagnosticsTitle = "Vehicle Diagnostics",
            diagnosticsSubtitle = "Read-only OBD-II & technical analysis",
            diagnosticsReadOnlyBadge = "READ-ONLY",
            diagnosticsVehicleSectionTitle = "Target Vehicle",
            diagnosticsNoVehiclesTitle = "No vehicle registered in BRANDT",
            diagnosticsNoVehiclesBody = "Add your vehicle first so diagnostics, OBD-II data, and AI evidence always match your exact make, model, year, and engine.",
            diagnosticsRegisterVehicleCta = "Add Vehicle",
            diagnosticsConnectionSectionTitle = "OBD-II Interface",
            diagnosticsStatusDisconnected = "Disconnected • Hardware Not Connected",
            diagnosticsConnectionNotice = "Scanner hardware has not been tested or connected yet. Connection compatibility is only reported after verified scanner communication.",
            diagnosticsWifiOptionTitle = "Wi-Fi OBD-II Adapter",
            diagnosticsWifiOptionSubtitle = "Wireless TCP ELM327 (192.168.0.10:35000) • Not configured",
            diagnosticsBluetoothOptionTitle = "Bluetooth OBD-II Adapter",
            diagnosticsBluetoothOptionSubtitle = "Classic SPP/RFCOMM & BLE • Not configured",
            diagnosticsConfigureConnectionCta = "Connection Setup",
            diagnosticsScanSectionTitle = "Diagnostic Scan",
            diagnosticsScanReadySummary = "Open the dedicated scan workspace to inspect read-only ECU status, prepare diagnostic input, and track available PIDs.",
            diagnosticsOpenScanCta = "Open Scan Workspace",
            diagnosticsHistorySectionTitle = "Reports & Session History",
            diagnosticsEmptyHistoryLabel = "No saved diagnostic sessions or reports yet.",
            diagnosticsOpenResultsCta = "Results",
            diagnosticsOpenHistoryCta = "History",
            diagnosticsOpenReportCta = "Mechanic Report",
            diagnosticsReadOnlyPolicyNote = "Strict Read-Only Mode: BRANDT never clears DTCs, resets ECUs, flashes firmware, or issues actuator control commands."
        )

        AppLanguage.THAI -> MotoraStrings(
            language = AppLanguage.THAI,
            settingsTitle = "ตั้งค่าภาษาและผู้เชี่ยวชาญ AI",
            settingsDescription = "ค่าเริ่มต้นของ BRANDT คือภาษาอังกฤษ สามารถสลับเป็นภาษาไทยได้ทุกเมื่อเพื่อแปลหน้าจอและให้ AI ช่างผู้เชี่ยวชาญตอบเป็นภาษาไทย",
            settingsLanguageSectionTitle = "ภาษาของแอปและคำตอบ AI",
            settingsDone = "เสร็จสิ้น",
            openSettingsContentDesc = "ตั้งค่าภาษาและการใช้งาน",
            garageSubtitlePrefix = "โรงรถของฉัน",
            garageSubtitleStandalone = "โรงรถของฉัน",
            garageAddVehicle = "เพิ่มรถใหม่",
            garageViewVehicle = "ดูข้อมูลรถ",
            garageEdit = "แก้ไข",
            garageAskAiButton = "ถาม AI รถ",
            garageAskAiDesc = "ถาม AI ช่างผู้เชี่ยวชาญรถ",
            garageLogButton = "Log ปัญหา",
            garageLogDesc = "เปิด Log ปัญหารถ",
            garageCollectionHeader = "รถในโรงรถของคุณ",
            garageSignOut = "ออกจากระบบ",
            garageRemoveTitle = "ลบข้อมูลรถคันนี้?",
            garageRemoveConfirm = "ลบออก",
            garageCancel = "ยกเลิก",
            emptyGarageHeadline = "โรงรถ\nBRANDT\nของคุณ\nยังว่างอยู่",
            emptyGarageCta = "เพิ่มรถคันแรกของคุณ",
            guidePreTitle = "ขั้นตอนก่อนกรอกข้อมูลรถ",
            guideTitle = "แนะนำมุมถ่าย\nรูปรถ 4 ด้าน",
            guideFrontLabel = "ด้านหน้ารถ (Front)",
            guideLeftLabel = "ด้านข้างซ้าย (Left Side)",
            guideRightLabel = "ด้านข้างขวา (Right Side)",
            guideRearLabel = "ด้านหลังรถ (REAR)",
            guideWhyTitle = "ทำไมต้องถ่ายรูปรถไว้ล่วงหน้า?",
            guideWhyBody = "การถ่ายรูปรถช่วยให้ AI ระบุยี่ห้อ รุ่น และรุ่นย่อยสำหรับบันทึกข้อมูลตั้งต้นได้แม่นยำ พร้อมดึงจุดสังเกตหรือปัญหาประจำรุ่นมาแสดงให้ทราบ รวมถึงใช้เป็นบริบทอ้างอิงเมื่อคุณสอบถามอาการเสียในอนาคต",
            guidePrivacyNote = "ถ้าเป็นไปได้ แนะนำให้ปิดป้ายทะเบียนรถไว้ก่อนถ่ายดีกว่านะครับ เพื่อความปลอดภัยของคุณ (และระบบจะช่วยปิดบังป้ายทะเบียนให้อัตโนมัติหากตรวจพบ)",
            guideStartButton = "ไปถ่ายรูปรถเลย",
            overviewTitle = "ข้อมูลรถของคุณ (Car Overview)",
            overviewBackToGarage = "กลับไปที่โรงรถ",
            overviewAskAiTitle = "ถาม AI ช่างผู้เชี่ยวชาญ",
            overviewAskAiSubtitle = "วิเคราะห์มืออาชีพ • ภาพ เสียง และวิดีโอ",
            overviewIssueLogTitle = "Log ปัญหารถ",
            overviewIssueLogSubtitle = "ดูประวัติที่เคยปรึกษา AI ไว้",
            overviewKnownIssuesTitle = "จุดสังเกตและอาการประจำรุ่นรถ",
            overviewKnownIssuesSubtitleSuffix = "ข้อมูลเชิงช่าง • อธิบายศัพท์รถเข้าใจง่าย",
            overviewKnownIssuesRefreshDesc = "อัปเดตข้อมูลปัญหาประจำรุ่น",
            overviewKnownIssuesLoadingPrefix = "กำลังวิเคราะห์อาการประจำรุ่นและจุดสังเกตสำหรับ",
            overviewKnownIssuesRetry = "ลองโหลดอีกครั้ง",
            overviewKnownIssuesLoadErrorFallback = "ไม่สามารถโหลดข้อมูลปัญหาประจำรุ่นได้ในขณะนี้",
            overviewKnownIssuesNoticePrefix = "จุดสังเกตที่ควรดู:",
            overviewKnownIssuesAskAi = "ปรึกษา AI เรื่องนี้",
            overviewVehicleErrorFallback = "ไม่สามารถโหลดข้อมูลรถได้ในขณะนี้",
            chatTitle = "ผู้เชี่ยวชาญรถยนต์ BRANDT",
            chatOpenLogLabel = "Log ปัญหา",
            chatOpenLogDesc = "เปิดประวัติ Log ปัญหารถ",
            chatBackDesc = "ย้อนกลับ",
            chatBannerVehiclePrefix = "กำลังดูแล:",
            chatBannerScopeSubtitle = "วิเคราะห์เฉพาะเรื่องรถ • ศัพท์ช่างอธิบายเข้าใจง่าย",
            chatWelcomeTitle = "ผู้เชี่ยวชาญวิเคราะห์ปัญหารถยนต์",
            chatWelcomeBody = "อธิบายอาการ เสียงผิดปกติ หรือไฟเตือนได้เลย หรือกด + เพื่อส่งรูป เอกสารซ่อม อัดเสียงรถสด หรือแนบคลิปวิดีโอ+เสียงให้ AI วิเคราะห์",
            chatStarterHeadingPrefix = "ตัวอย่างหัวข้อวิเคราะห์สำหรับ",
            chatStarters = listOf(
                "ขับแล้วมีเสียงกึกๆ หรือเสียงเอี๊ยด เกิดจากชิ้นส่วนใด",
                "มีไฟเตือนขึ้นบนหน้าปัด ยังขับต่อไปศูนย์บริการได้ไหม",
                "เหยียบเบรกแล้วพวงมาลัยหรือแป้นเบรกสั่น เกิดจากอะไร",
                "แอร์รถไม่เย็นมีแต่ลมร้อน สาเหตุหลักมาจากระบบใด"
            ),
            chatTypingStatus = "กำลังวิเคราะห์อาการและข้อมูลประจำรุ่น...",
            chatCarOnlyBadge = "ตอบเฉพาะเรื่องรถยนต์เท่านั้น",
            chatSavedToLogLabel = "บันทึกลง Log ปัญหารถเรียบร้อย",
            chatPlusDesc = "แนบรูป ไฟล์ อัดเสียง หรือคลิปวิดีโอรถ",
            chatInputPlaceholder = "ถามอะไรก็ได้...",
            chatSendDesc = "ส่งให้ MACK วิเคราะห์",
            chatPhotoTileTitle = "กล้องและรูปภาพ",
            chatPhotoTileSub = "ถ่ายรูป / วิดีโอ",
            chatFileTileTitle = "แนบเอกสาร/ไฟล์",
            chatFileTileSub = "PDF / บิลซ่อม",
            chatAudioTileTitle = "ฟังเสียงรถ",
            chatAudioTileSub = "อัดเสียงสด",
            chatVideoTileTitle = "วิดีโอ",
            chatVideoTileSub = "คลิปภาพ+เสียง",
            chatPendingPrefix = "แนบแล้ว:",
            chatRecordingStatusPrefix = "กำลังอัดเสียงรถสดๆ...",
            chatRecordingHint = "จ่อไมค์ใกล้จุดที่ได้ยินเสียง แล้วกดใช้เสียงนี้",
            chatCancelRecording = "ยกเลิก",
            chatUseAudio = "ใช้เสียงนี้",
            chatDefaultPhotoName = "รูปภาพรถที่แนบ",
            chatDefaultDocName = "ไฟล์เอกสารรถ",
            chatDefaultVideoName = "วิดีโออาการรถ",
            chatDefaultAudioLabel = { sec -> "เสียงรถที่อัด (${sec} วินาที)" },
            chatDefaultPhotoPrompt = "กรุณาตรวจสอบรูปภาพรถที่แนบมานี้ อธิบายว่าเป็นชิ้นส่วนหรืออาการใด และควรดำเนินการอย่างไร",
            chatDefaultDocPrompt = "กรุณาอ่านและสรุปสาระสำคัญจากเอกสารรถที่แนบมานี้ให้เข้าใจง่าย",
            chatDefaultAudioPrompt = "กรุณาฟังเสียงรถที่อัดมานี้ วิเคราะห์ว่าเกิดจากชิ้นส่วนใด และยังขับต่อไปศูนย์บริการได้อย่างปลอดภัยหรือไม่",
            chatDefaultVideoPrompt = "กรุณาดูคลิปวิดีโอและฟังเสียงรถที่แนบมานี้ วิเคราะห์ความผิดปกติทั้งภาพและเสียง พร้อมประเมินความปลอดภัยในการขับขี่",
            chatMicPermissionError = "กรุณาอนุญาตการใช้ไมโครโฟน เพื่อให้ AI ฟังเสียงรถของคุณได้",
            chatCameraPermissionError = "กรุณาอนุญาตการใช้กล้อง เพื่อถ่ายรูปหรืออัดวิดีโอส่งให้ AI วิเคราะห์",
            chatEmptySendError = "กรุณาพิมพ์อาการของรถ หรือกด + เพื่อส่งรูป/ไฟล์/เสียง/วิดีโอก่อนส่ง",
            chatVehicleNotLoadedError = "ยังโหลดข้อมูลรถไม่เสร็จ กรุณารอสักครู่",
            chatSendFailedFallback = "ไม่สามารถติดต่อผู้เชี่ยวชาญ AI ได้ในขณะนี้",
            chatRequestPhotoTitle = "คำขอจากช่าง AI • ถ่ายหรือแนบรูปภาพเพิ่มเติม",
            chatRequestAudioTitle = "คำขอจากช่าง AI • อัดเสียงรถให้ช่วยฟัง",
            chatRequestVideoTitle = "คำขอจากช่าง AI • ส่งคลิปวิดีโอ+เสียงอาการรถ",
            chatRequestTakePhotoCta = "เปิดกล้องถ่ายรูป",
            chatRequestPickPhotoCta = "เลือกรูป",
            chatRequestRecordAudioCta = "กดอัดเสียงทันที",
            chatRequestRecordVideoCta = "ถ่ายวิดีโอ",
            chatRequestPickVideoCta = "เลือกคลิป",
            chatCameraSheetTitle = "ถ่ายรูปหรือเลือกสื่ออาการรถ",
            floatingMackDesc = "เปิดหน้าคุยกับ AI MACK",
            logTitle = "ประวัติ Log ปัญหารถ",
            logAskAiAction = "ถาม AI",
            logAskAiActionDesc = "เปิดหน้าปรึกษาผู้เชี่ยวชาญ AI",
            logFilterAll = "ทั้งหมด",
            logFilterOpen = "กำลังติดตาม",
            logFilterResolved = "แก้ไขแล้ว",
            logCountLabel = { count -> "$count รายการ" },
            logEmptyNoEntries = "ยังไม่มี Log ปัญหารถคันนี้",
            logEmptyFiltered = "ไม่พบรายการในตัวกรองนี้",
            logEmptyDescription = "ทุกครั้งที่คุณปรึกษาอาการ รูปถ่าย เสียง หรือวิดีโอรถกับผู้เชี่ยวชาญ AI ระบบจะสรุปเก็บไว้ที่หน้านี้อัตโนมัติ",
            logEmptyStartAi = "เริ่มปรึกษา AI ช่างรถ",
            logSyncingDate = "กำลังซิงค์...",
            logQueryPrefix = "อาการที่สอบถาม:",
            logFollowUpPrefix = "จุดที่แนะนำให้เช็กเพิ่ม:",
            logStatusResolved = "แก้ไขแล้ว",
            logStatusOpen = "กำลังติดตาม",
            logAskMore = "ถาม AI ต่อ",
            logAskMoreTopicPrefix = "ขอปรึกษาเพิ่มเติมเรื่องนี้:",
            logDeleteDesc = "ลบประวัติรายการนี้",
            logMarkedResolvedToast = "ทำเครื่องหมายว่าแก้ไขแล้ว",
            logReopenedToast = "เปิดสถานะติดตามอาการอีกครั้ง",
            logUpdateFailedFallback = "อัปเดตสถานะไม่สำเร็จ",
            logDeletedToast = "ลบรายการออกจาก Log แล้ว",
            logDeleteFailedFallback = "ลบรายการไม่สำเร็จ",
            logLoadErrorFallback = "ไม่สามารถโหลดประวัติ Log ปัญหารถได้",
            formRegisterTitle = "กรอกข้อมูลรถ",
            formEditTitle = "แก้ไขข้อมูลรถ",
            formHeroHeadline = "ถ่ายรูปรถของคุณ\nเพื่อเริ่มบันทึกข้อมูล",
            formCameraCaps = "ถ่ายรูป",
            formFilesCaps = "เลือกไฟล์",
            formGuideSectionTitle = "ไกด์มุมถ่ายรูปรถ 4 ด้าน",
            formGuideSubtitle = "เลือกมุมด้านล่างและจัดเฟรมตามคำแนะนำ เพื่อให้ AI อ่านยี่ห้อ รุ่น และรายละเอียดได้แม่นยำขึ้น",
            formTakePhoto = "ถ่ายรูป",
            formAttachPhoto = "เลือกรูปถ่าย",
            formReplacePhoto = "เปลี่ยนรูปมุมนี้",
            formBrowseFiles = "เลือกไฟล์",
            formPcFolder = "โฟลเดอร์ PC",
            formClearAngle = "ลบรูปมุมนี้",
            formUseSilhouette = "กลับไปใช้ภาพจำลอง",
            formAnalyzeWithAi = "ให้ AI เริ่มสแกนรูปรถ",
            formRescanWithAi = "ให้ AI สแกนรูปรถซ้ำ",
            formAiScanning = "AI กำลังวิเคราะห์รูปรถ...",
            formAngleCapturedBadge = "ใส่รูปแล้ว",
            formAngleEmptyBadge = "ยังไม่มีรูป",
            studioPcFolderEmptyHint = "ยังไม่มีรูปในโฟลเดอร์ app/src/main/assets/pc_import/ เพิ่มไฟล์ .jpg/.png แล้วกดเปิดใหม่เพื่อนำเข้า",
            plateCoveredNotice = "ระบบตรวจพบและปิดบังป้ายทะเบียนรถให้อัตโนมัติเพื่อความปลอดภัยของคุณแล้ว",
            plateScanningNotice = "กำลังตรวจสอบและปกป้องข้อมูลป้ายทะเบียนรถ...",
            navDiagnostics = "วิเคราะห์ระบบรถ OBD-II",
            diagnosticsTitle = "Vehicle Diagnostics",
            diagnosticsSubtitle = "ระบบอ่านและวิเคราะห์รหัสปัญหารถยนต์ (อ่านอย่างเดียว)",
            diagnosticsReadOnlyBadge = "READ-ONLY",
            diagnosticsVehicleSectionTitle = "รถที่ต้องการตรวจสอบ",
            diagnosticsNoVehiclesTitle = "ยังไม่มีข้อมูลรถในโรงรถ BRANDT",
            diagnosticsNoVehiclesBody = "กรุณาเพิ่มข้อมูลรถก่อน เพื่อให้การวิเคราะห์ OBD-II และ AI อ้างอิงยี่ห้อ รุ่น ปี และเครื่องยนต์ได้อย่างถูกต้อง",
            diagnosticsRegisterVehicleCta = "เพิ่มข้อมูลรถ",
            diagnosticsConnectionSectionTitle = "การเชื่อมต่อ OBD-II",
            diagnosticsStatusDisconnected = "ไม่ได้เชื่อมต่อ • ยังไม่มีฮาร์ดแวร์สแกนเนอร์",
            diagnosticsConnectionNotice = "ยังไม่ได้เชื่อมต่อหรือทดสอบตัวสแกนเนอร์จริง ระบบจะไม่ยืนยันความเข้ากันได้จนกว่าจะทดสอบกับอุปกรณ์จริง",
            diagnosticsWifiOptionTitle = "เชื่อมต่อผ่าน Wi-Fi OBD-II",
            diagnosticsWifiOptionSubtitle = "ELM327 Wi-Fi TCP (192.168.0.10:35000) • ยังไม่ได้ตั้งค่า",
            diagnosticsBluetoothOptionTitle = "เชื่อมต่อผ่าน Bluetooth OBD-II",
            diagnosticsBluetoothOptionSubtitle = "Bluetooth Classic (SPP) และ BLE • ยังไม่ได้ตั้งค่า",
            diagnosticsConfigureConnectionCta = "ตั้งค่าการเชื่อมต่อ",
            diagnosticsScanSectionTitle = "หน้าสแกนวิเคราะห์ระบบ",
            diagnosticsScanReadySummary = "เปิดหน้าพื้นที่สแกนเพื่อตรวจสอบสถานะการอ่านข้อมูล ECU และเตรียมข้อมูลรหัสปัญหาแบบอ่านอย่างเดียว",
            diagnosticsOpenScanCta = "เปิดหน้า Scan",
            diagnosticsHistorySectionTitle = "รายงานและประวัติการวิเคราะห์",
            diagnosticsEmptyHistoryLabel = "ยังไม่มีประวัติการสแกนหรือรายงานช่างที่บันทึกไว้",
            diagnosticsOpenResultsCta = "ผลวิเคราะห์",
            diagnosticsOpenHistoryCta = "ประวัติสแกน",
            diagnosticsOpenReportCta = "รายงานส่งช่าง",
            diagnosticsReadOnlyPolicyNote = "โหมดอ่านอย่างเดียว (Read-Only): ระบบจะไม่ลบโค้ด DTC, ไม่รีเซ็ตกล่อง ECU, ไม่แฟลชซอฟต์แวร์ และไม่สั่งงานแอคชูเอเตอร์ใดๆ"
        )
    }
}
