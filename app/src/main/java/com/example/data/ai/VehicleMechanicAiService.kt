package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.example.data.model.AttachmentKind
import com.example.data.model.RequestedCaptureType
import com.example.data.model.Vehicle
import com.example.data.model.VehicleKnownIssue
import com.example.data.obd.DiagnosticStatusDetailSummary
import com.example.data.obd.DtcInterpreter
import com.example.data.obd.DiagnosticTroubleCode
import com.example.data.obd.DtcStatus
import com.example.data.obd.ObdVehicleSnapshot
import com.example.data.settings.AppLanguage
import com.example.ui.components.LicensePlatePrivacyProtector
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

sealed interface PendingChatAttachment {
    val kind: AttachmentKind
    val displayLabel: String

    data class Photo(
        val pathOrUri: String,
        override val displayLabel: String
    ) : PendingChatAttachment {
        override val kind: AttachmentKind = AttachmentKind.PHOTO
    }

    data class DocumentFile(
        val uriString: String,
        val mimeType: String,
        override val displayLabel: String
    ) : PendingChatAttachment {
        override val kind: AttachmentKind = AttachmentKind.FILE
    }

    data class RecordedAudio(
        val filePath: String,
        val durationSeconds: Int,
        override val displayLabel: String
    ) : PendingChatAttachment {
        override val kind: AttachmentKind = AttachmentKind.AUDIO
    }

    data class VideoClip(
        val uriString: String,
        val mimeType: String,
        override val displayLabel: String
    ) : PendingChatAttachment {
        override val kind: AttachmentKind = AttachmentKind.VIDEO
    }
}

data class CarChatTurn(
    val isFromUser: Boolean,
    val text: String,
    val attachmentLabel: String = ""
)

data class CarChatReply(
    val isCarRelated: Boolean,
    val simpleReply: String,
    val modelCommonality: String,
    val followUpQuestion: String,
    val suggestedQuickReplies: List<String>,
    val logSummary: String,
    val requestedCaptureType: RequestedCaptureType = RequestedCaptureType.NONE,
    val requestedCaptureReason: String = ""
)

data class VehicleModelIssuesResult(
    val headlineSummary: String,
    val issues: List<VehicleKnownIssue>
)

data class DiagnosticStatusPlainExplanation(
    val headline: String,
    val plainSummary: String,
    val codeBreakdown: List<String> = emptyList(),
    val whatToDoNext: String
)

interface DiagnosticStatusAiExplainer {
    suspend fun explainDiagnosticStatusPlainLanguage(
        vehicle: Vehicle,
        status: DiagnosticStatusDetailSummary,
        language: AppLanguage = AppLanguage.ENGLISH
    ): Result<DiagnosticStatusPlainExplanation>
}

interface VehicleMechanicAiService {
    suspend fun loadCommonIssuesForVehicle(
        vehicle: Vehicle,
        language: AppLanguage = AppLanguage.ENGLISH
    ): Result<VehicleModelIssuesResult>

    suspend fun sendCarMechanicMessage(
        context: Context,
        vehicle: Vehicle,
        history: List<CarChatTurn>,
        userText: String,
        attachment: PendingChatAttachment?,
        language: AppLanguage = AppLanguage.ENGLISH
    ): Result<CarChatReply>
}

class FirebaseVehicleMechanicAiService : VehicleMechanicAiService, DiagnosticStatusAiExplainer {

    private val commonIssuesSchema = Schema.obj(
        properties = mapOf(
            "headlineSummary" to Schema.string(
                "One concise, authoritative executive summary in the requested output language highlighting what the owner should monitor on this exact model/year/mileage."
            ),
            "issues" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string("Authentic automotive system or symptom title in the requested language."),
                        "frequencyLabel" to Schema.string("Concise frequency badge in the requested language e.g. 'Common on this model', 'Age / mileage wear', 'Preventive check' (or Thai equivalents when Thai is requested)."),
                        "simpleExplanation" to Schema.string("2-3 crisp sentences in a Master Technician tone using real automotive part names (e.g., CVT fluid, stabilizer bar link, ignition coil) while immediately clarifying what each part does so a non-expert driver understands clearly. No childish metaphors."),
                        "whatToNotice" to Schema.string("Concrete observable symptom (sound, vibration, warning lamp, temperature, or response) the driver notices in normal operation."),
                        "askAiPrompt" to Schema.string("Short natural diagnostic question in the requested language that the owner can tap to consult the AI specialist.")
                    )
                ),
                description = "List of 3 to 4 real-world mechanical watch-outs for this specific car model, age, and mileage."
            )
        )
    )

    private val chatReplySchema = Schema.obj(
        properties = mapOf(
            "isCarRelated" to Schema.boolean(
                "True ONLY if the question, photo, file, or sound relates to cars, driving, vehicle symptoms, noises, warning lights, maintenance, or auto repair. False if off-topic."
            ),
            "simpleReply" to Schema.string(
                "Respond in the exact requested language like a Senior Master Automotive Technician & Brand Service Advisor: calm, precise, authoritative, and respectful. Use real automotive terms (e.g., brake pad wear indicator, serpentine belt, alternator, CV joint, suspension bushing) and immediately explain in plain, intelligent everyday language what the component does, why this symptom happens, how common it is on this registered model, and whether it is safe to drive calmly to a workshop or requires stopping/towing. Never use childish or toy comparisons. If isCarRelated is false, politely refuse and state you handle automotive diagnostics only."
            ),
            "modelCommonality" to Schema.string(
                "Short badge in the requested language assessing likelihood on the user's registered car e.g. 'Common on this model', 'Typical wear by mileage', or 'Uncommon — inspect promptly' (or Thai equivalents when Thai is requested). Empty string if not car-related."
            ),
            "followUpQuestion" to Schema.string(
                "One sharp diagnostic follow-up question in the requested language asking where the sound/symptom occurs or under what driving condition (braking, accelerating, turning, cold start) to narrow the fault. Empty string if off-topic."
            ),
            "suggestedQuickReplies" to Schema.array(
                items = Schema.string("Short clickable answer option under 8 words in the requested language."),
                description = "2 to 4 concise quick-reply options directly answering followUpQuestion. Empty array if off-topic."
            ),
            "logSummary" to Schema.string(
                "Concise 1-2 sentence service-log summary in the requested language naming the suspected system/part and recommended action. Empty string if isCarRelated is false."
            ),
            "requestedCaptureType" to Schema.enumeration(
                values = listOf("NONE", "PHOTO", "AUDIO", "VIDEO"),
                description = "Proactively choose PHOTO, AUDIO, or VIDEO when asking the owner to capture a photo (e.g., warning lamp, wear item, leak), live audio clip (e.g., squeal, knock, rattle), or short video+audio clip (e.g., cranking sound + tachometer, visible vibration/belt noise) will materially improve diagnostic accuracy. Use NONE if off-topic or already fully diagnosed."
            ),
            "requestedCaptureReason" to Schema.string(
                "Clear, respectful instruction in the requested language telling the owner exactly what to photograph or record (angle, distance, or condition like idling/braking) so diagnosis is accurate. Empty string when requestedCaptureType is NONE."
            )
        )
    )

    private val issuesStructuredModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commonIssuesSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    private val chatStructuredModelEn by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chatReplySchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content {
                text(
                    """
                    You are a Certified Senior Master Automotive Technician and Brand Service Advisor at BRANDT.
                    STRICT OPERATING RULES:
                    1. Scope: Respond ONLY to automotive topics — vehicle symptoms, abnormal noises, warning lights, brakes, steering, suspension, HVAC, engine/transmission, EV/hybrid systems, batteries, tires, repair invoices/documents, or safe driving decisions.
                    2. Off-topic Guard: If the request is unrelated to vehicles (e.g., cooking, coding, politics, general chat, homework), set isCarRelated = false and politely decline, noting that BRANDT Specialist is dedicated exclusively to automotive diagnostics.
                    3. Professional-Yet-Clear Tone: Treat the owner as a capable adult who does not yet know mechanical jargon. Use authentic, accurate industry terminology (e.g., "brake pad wear indicator", "serpentine drive belt", "alternator", "stabilizer link", "CV joint boot", "ignition coil", "OBD-II diagnostic scan") and immediately explain each term clearly in plain, real-world mechanical logic — NEVER use patronizing, childish, or toy comparisons.
                    4. Practical Diagnostic Structure:
                       - Identify the most likely system/component behind the symptom.
                       - State how common it is for the owner's exact registered vehicle (year/make/model/mileage).
                       - Give a clear, decisive safety verdict: safe to drive gently to a workshop vs. avoid driving / tow immediately.
                    5. Diagnostic Follow-up & Media Request: Always ask 1 focused diagnostic follow-up question (followUpQuestion) plus 2–4 concise quick-reply options. Whenever inspecting a part/warning light visually (PHOTO), hearing an abnormal sound cleanly (AUDIO), or observing sound + movement together (VIDEO) would make your diagnosis much more accurate, proactively set requestedCaptureType to PHOTO, AUDIO, or VIDEO and write exact, practical capture instructions in requestedCaptureReason.
                    6. Output Language: Write all JSON text fields strictly in natural, professional English.
                    7. ${LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_EN}
                    8. OBD-II & System Voltage Integrity: When [BRANDT Structured System Voltage Telemetry] is present, interpret the JSON fields accurately (current_voltage, unit, system_type, voltage_status, last_updated, data_source, connection_status, error_state, is_stale, limitations, missing_information). Clearly explain the reading, status, or why data is missing, outdated, unsupported, or disconnected. Respect PID 42's limitation: OBD-II Mode 01 PID 42 reports ECU Control Module Voltage ONLY, never direct battery-terminal voltage. NEVER invent missing values, NEVER diagnose battery or alternator failure from a single voltage reading, and NEVER present estimates as confirmed hardware measurements.
                    """.trimIndent()
                )
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    private val chatStructuredModelTh by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = chatReplySchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content {
                text(
                    """
                    คุณคือ "หัวหน้าช่างเทคนิคอาวุโสและที่ปรึกษาฝ่ายบริการรถยนต์ (Master Automotive Technician)" ประจำ BRANDT
                    กติกาการตอบอย่างมืออาชีพ:
                    1. ขอบเขตหน้าที่: ตอบเฉพาะเรื่องรถยนต์ ปัญหารถ อาการผิดปกติ เสียง ระบบเบรก ช่วงล่าง พวงมาลัย แอร์ เครื่องยนต์ เกียร์ ระบบไฟ/EV แบตเตอรี่ ยาง เอกสารซ่อมรถ หรือความปลอดภัยในการขับขี่เท่านั้น
                    2. การปฏิเสธคำถามนอกเรื่อง: หากคำถามไม่เกี่ยวกับรถเลย (เช่น ทำอาหาร เขียนโค้ด การเมือง เรื่องทั่วไป) ให้ตั้ง isCarRelated = false, requestedCaptureType = NONE และปฏิเสธอย่างสุภาพว่าผู้เชี่ยวชาญ BRANDT ให้บริการเฉพาะด้านรถยนต์เท่านั้น
                    3. ภาษาที่มืออาชีพและเข้าใจง่าย: สื่อสารกับเจ้าของรถอย่างให้เกียรติในฐานะผู้ใหญ่ที่ไม่มีพื้นฐานช่าง — ใช้ "ศัพท์เทคนิควงการรถยนต์จริง" (เช่น ผ้าเบรกและแผ่นเตือนสึกหรอ, สายพานหน้าเครื่อง, ไดชาร์จ, ลูกหมากกันโคลง, ยางหุ้มเพลาขับ, คอยล์จุดระเบิด, ค่าโค้ด OBD-II) แล้วอธิบายหน้าที่และกลไกสาเหตุเป็นภาษาไทยที่ชัดเจน ตรงไปตรงมา ห้ามใช้คำเปรียบเทียบแบบเด็กเล่นหรือของเล่นโดยเด็ดขาด
                    4. โครงสร้างการวินิจฉัย:
                       - ระบุชิ้นส่วนหรือระบบที่คาดว่าเป็นต้นเหตุ
                       - ประเมินว่าอาการนี้พบบ่อยเพียงใดในรถรุ่น/ปี/เลขไมล์ของผู้ใช้ (modelCommonality)
                       - ฟันธงความปลอดภัยในการขับขี่ให้ชัด ("ยังขับประคองไปศูนย์บริการได้" หรือ "ควรงดขับและเรียกรถยก/ช่างตรวจสอบทันที")
                    5. คำถามซักประวัติและการขอภาพ/เสียง/วิดีโอจากผู้ใช้: ถามกลับให้ตรงจุด 1 ข้อ (followUpQuestion) พร้อมตัวเลือกตอบสั้นๆ 2-4 ข้อ และหากการดูรูปชิ้นส่วน/ไฟหน้าปัด (PHOTO), การฟังเสียงสด (AUDIO) หรือการดูคลิปวิดีโอพร้อมเสียง (VIDEO) จะช่วยให้วิเคราะห์แม่นยำขึ้นมาก ให้ระบุ requestedCaptureType เป็น PHOTO, AUDIO หรือ VIDEO และอธิบายใน requestedCaptureReason ว่าควรถ่ายหรืออัดตรงไหน จังหวะใดให้ชัดที่สุด
                    6. ภาษาที่ส่งออก: เขียนข้อความใน JSON ทุกฟิลด์เป็นภาษาไทยที่เป็นทางการระดับที่ปรึกษางานบริการมืออาชีพ
                    7. ${LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_TH}
                    8. ความถูกต้องของข้อมูล OBD-II และแรงดันไฟระบบ (System Voltage): เมื่อมีข้อมูล [BRANDT Structured System Voltage Telemetry] แนบมา ให้อธิบายจากค่า JSON จริงอย่างแม่นยำ (ทั้ง current_voltage, system_type, voltage_status, last_updated, error_state, is_stale, limitations และ missing_information) อธิบายความหมาย สถานะ หรือสาเหตุที่ข้อมูลขาด/เก่า/ไม่รองรับ/ขาดการเชื่อมต่อ โดยต้องระบุข้อจำกัดเสมอว่า OBD-II Mode 01 PID 42 คือค่าแรงดันไฟที่โมดูลควบคุม (ECU) รายงาน ไม่ใช่ค่าที่วัดจากขั้วแบตเตอรี่โดยตรง ห้ามแต่งตัวเลขที่ขาดหาย ห้ามฟันธงว่าแบตเตอรี่หรือไดชาร์จเสียจากค่าแรงดันเพียงค่าเดียว และห้ามนำค่าประมาณการมาบอกว่าเป็นผลการวัดจริง
                    """.trimIndent()
                )
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    private val diagnosticStatusExplainSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string("1 short, calm sentence summarizing the diagnostic status in everyday words."),
            "plainSummary" to Schema.string("2-3 easy, warm sentences explaining what these ECU diagnostic items mean for a car owner with zero automotive background. No dense technical jargon."),
            "codeBreakdown" to Schema.array(
                items = Schema.string(),
                description = "2 to 5 simple bullet points explaining each detected code (or why 0 codes is good news), MIL status, freeze frame, and readiness monitor in plain language."
            ),
            "whatToDoNext" to Schema.string("1-2 simple practical next steps (e.g., safe to drive normally, visit a mechanic calmly, or stop if warning light blinks).")
        )
    )

    private val diagnosticStatusExplainModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = diagnosticStatusExplainSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    private val fallbackFastLiteModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.MINIMAL
                }
            },
            requestOptions = RequestOptions(30_000L)
        )
    }

    override suspend fun loadCommonIssuesForVehicle(
        vehicle: Vehicle,
        language: AppLanguage
    ): Result<VehicleModelIssuesResult> {
        return try {
            val vehicleSpec = formatVehicleContext(vehicle, language)
            val prompt = when (language) {
                AppLanguage.ENGLISH -> """
                    Registered vehicle:
                    $vehicleSpec

                    Summarize 3 to 4 common mechanical watch-outs and known maintenance points for this exact model, model year, powertrain, and mileage.
                    Guidelines:
                    - Write in English with a Senior Master Automotive Technician tone: authoritative, respectful, and crystal clear for a non-expert driver.
                    - Use real automotive part/system names (e.g., transmission fluid, ignition coils, stabilizer end links, brake rotors) and explain what each part does and why owners watch for it — zero childish metaphors.
                    - Label how frequently each appears ("Common on this model", "Age / mileage wear", or "Preventive service item").
                    - Include concrete warning signs (specific noise, vibration, dashboard warning indicator, or driving feel).
                    - Return strict JSON matching the schema (headlineSummary and issues[]).
                """.trimIndent()

                AppLanguage.THAI -> """
                    รถที่ผู้ใช้ลงทะเบียนไว้:
                    $vehicleSpec

                    ช่วยสรุปจุดสังเกตและอาการประจำรุ่นที่มักพบในรถรุ่นนี้ ปีนี้ และช่วงเลขไมล์ประมาณนี้ จำนวน 3 ถึง 4 ข้อ สำหรับเจ้าของรถที่ไม่มีพื้นฐานช่าง
                    กติกา:
                    - ใช้ภาษาไทยระดับหัวหน้าช่างศูนย์บริการมืออาชีพ: สุภาพ ชัดเจน ตรงประเด็น และอธิบายกลไกของ "ศัพท์อะไหล่จริง" (เช่น น้ำมันเกียร์ CVT, คอยล์จุดระเบิด, ลูกหมากกันโคลง, จานเบรก) ให้ผู้ไม่มีพื้นฐานเข้าใจหลักการทำงานได้ทันที ห้ามใช้คำเปรียบเทียบแบบเด็ก
                    - ระบุความถี่ในรุ่นนี้ใน frequencyLabel ("พบบ่อยในรุ่นนี้", "สึกหรอตามระยะไมล์", หรือ "ควรตรวจเช็กตามรอบบริการ")
                    - ระบุอาการสังเกตที่ชัดเจน (เสียง ลักษณะการสั่น หรือไฟเตือนหน้าปัด)
                    - ส่งกลับเป็น JSON ตามสคีมาเท่านั้น (headlineSummary และ issues[]) โดยทุกข้อความต้องเป็นภาษาไทย
                """.trimIndent()
            }

            val primaryContent = content { text(prompt) }
            val fallbackContent = content {
                text(
                    "$prompt\nReturn ONLY a valid JSON object with keys headlineSummary and issues (array of objects with title, frequencyLabel, simpleExplanation, whatToNotice, askAiPrompt)."
                )
            }

            val rawText = withContext(Dispatchers.IO) {
                streamWithFastFallback(
                    primaryModel = issuesStructuredModel,
                    primaryContent = primaryContent,
                    fallbackContent = fallbackContent
                )
            } ?: return Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) {
                        "AI ไม่ได้ส่งข้อมูลอาการประจำรุ่นกลับมา"
                    } else {
                        "AI returned an empty response while loading model watch-outs."
                    }
                )
            )

            val json = JSONObject(extractJsonObject(rawText))
            val headline = json.optString("headlineSummary", "").trim()
            val issuesJson = json.optJSONArray("issues") ?: JSONArray()
            val defaultFrequency = if (language == AppLanguage.THAI) "สึกหรอตามอายุใช้งาน" else "Mileage wear check"
            val issues = buildList {
                for (i in 0 until issuesJson.length()) {
                    val item = issuesJson.optJSONObject(i) ?: continue
                    val title = item.optString("title", "").trim()
                    val simple = item.optString("simpleExplanation", "").trim()
                    if (title.isNotEmpty() && simple.isNotEmpty()) {
                        val defaultPrompt = if (language == AppLanguage.THAI) {
                            "ต้องการตรวจสอบอาการ $title ในรถรุ่นนี้ มีวิธีสังเกตและแนวทางแก้ไขอย่างไร"
                        } else {
                            "What causes $title on my car and how should it be inspected?"
                        }
                        add(
                            VehicleKnownIssue(
                                title = title.take(90),
                                frequencyLabel = item.optString("frequencyLabel", defaultFrequency).trim().take(50),
                                simpleExplanation = simple.take(450),
                                whatToNotice = item.optString("whatToNotice", "").trim().take(260),
                                askAiPrompt = item.optString("askAiPrompt", defaultPrompt).trim().take(180)
                            )
                        )
                    }
                }
            }
            if (issues.isEmpty()) {
                return Result.failure(
                    IllegalStateException(
                        if (language == AppLanguage.THAI) {
                            "ไม่สามารถประมวลผลรายการอาการประจำรุ่นจาก AI ได้"
                        } else {
                            "Unable to parse model watch-outs from AI response."
                        }
                    )
                )
            }
            val fallbackHeadline = if (language == AppLanguage.THAI) {
                "จุดตรวจเช็กสำคัญสำหรับ ${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model}"
            } else {
                "Key reliability and maintenance watch-outs for your ${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model}."
            }
            Result.success(
                VehicleModelIssuesResult(
                    headlineSummary = headline.ifEmpty { fallbackHeadline },
                    issues = issues
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Common issues request timed out", e)
            Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) {
                        "โหลดข้อมูลนานเกินกำหนด กรุณาลองใหม่อีกครั้ง"
                    } else {
                        "Model watch-outs request timed out. Please try again."
                    }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading common issues for vehicle", e)
            Result.failure(formatFriendlyException(e))
        }
    }

    override suspend fun sendCarMechanicMessage(
        context: Context,
        vehicle: Vehicle,
        history: List<CarChatTurn>,
        userText: String,
        attachment: PendingChatAttachment?,
        language: AppLanguage
    ): Result<CarChatReply> {
        return try {
            val trimmedInput = userText.trim()
            if (trimmedInput.isEmpty() && attachment == null) {
                return Result.failure(
                    IllegalArgumentException(
                        if (language == AppLanguage.THAI) {
                            "กรุณาพิมพ์อาการรถ หรือแนบรูป/เอกสาร/เสียงรถก่อนส่ง"
                        } else {
                            "Enter a symptom or attach a photo, document, or vehicle audio before sending."
                        }
                    )
                )
            }

            val filePayload: Pair<ByteArray, String>? = if (attachment != null) {
                withContext(Dispatchers.IO) {
                    when (attachment) {
                        is PendingChatAttachment.Photo -> {
                            val jpegBytes = encodeScaledJpeg(context, attachment.pathOrUri, maxDim = 640, quality = 80)
                                ?: throw IllegalStateException(
                                    if (language == AppLanguage.THAI) {
                                        "ไม่สามารถเปิดรูปภาพที่เลือกได้"
                                    } else {
                                        "Unable to open the selected image."
                                    }
                                )
                            jpegBytes to "image/jpeg"
                        }
                        is PendingChatAttachment.DocumentFile -> {
                            val bytes = readUriBytes(context, attachment.uriString, MAX_INLINE_BYTES)
                                ?: throw IllegalStateException(
                                    if (language == AppLanguage.THAI) {
                                        "ไม่สามารถอ่านไฟล์แนบได้ หรือไฟล์มีขนาดใหญ่เกินไป"
                                    } else {
                                        "Unable to read the attached file or file exceeds 12 MB."
                                    }
                                )
                            bytes to attachment.mimeType.ifBlank { "application/pdf" }
                        }
                        is PendingChatAttachment.RecordedAudio -> {
                            val file = File(attachment.filePath)
                            if (!file.exists() || file.length() <= 0L) {
                                throw IllegalStateException(
                                    if (language == AppLanguage.THAI) {
                                        "ไม่พบไฟล์เสียงที่บันทึกไว้"
                                    } else {
                                        "Recorded audio file could not be found."
                                    }
                                )
                            }
                            if (file.length() > MAX_INLINE_BYTES) {
                                throw IllegalStateException(
                                    if (language == AppLanguage.THAI) {
                                        "ไฟล์เสียงมีขนาดใหญ่เกิน 12 MB"
                                    } else {
                                        "Recorded audio exceeds the 12 MB limit."
                                    }
                                )
                            }
                            file.readBytes() to "audio/mp4"
                        }
                        is PendingChatAttachment.VideoClip -> {
                            val bytes = readUriBytes(context, attachment.uriString, MAX_INLINE_BYTES)
                                ?: throw IllegalStateException(
                                    if (language == AppLanguage.THAI) {
                                        "ไม่สามารถอ่านไฟล์วิดีโอได้ หรือไฟล์มีขนาดใหญ่เกิน 12 MB (แนะนำให้ถ่ายคลิปสั้น 5–10 วินาที)"
                                    } else {
                                        "Unable to read video clip or file exceeds 12 MB (keep clips around 5–10 seconds)."
                                    }
                                )
                            bytes to attachment.mimeType.ifBlank { "video/mp4" }
                        }
                    }
                }
            } else {
                null
            }

            val recentHistoryText = history.takeLast(8).joinToString("\n") { turn ->
                val speaker = if (turn.isFromUser) "Owner" else "Specialist"
                val attachSuffix = if (turn.attachmentLabel.isNotBlank()) " [Attachment: ${turn.attachmentLabel}]" else ""
                "$speaker$attachSuffix: ${turn.text}"
            }

            val vehicleContext = formatVehicleContext(vehicle, language)
            val multimodalInstruction = buildString {
                if (language == AppLanguage.ENGLISH) {
                    appendLine("OUTPUT LANGUAGE: English ONLY.")
                    appendLine("Registered Vehicle:")
                    appendLine(vehicleContext)
                    if (recentHistoryText.isNotBlank()) {
                        appendLine()
                        appendLine("Conversation History:")
                        appendLine(recentHistoryText)
                    }
                    appendLine()
                    appendLine("Latest Owner Inquiry: ${trimmedInput.ifBlank { "(Owner submitted an attachment for inspection)" }}")
                    when (attachment) {
                        is PendingChatAttachment.Photo -> appendLine("Attachment: Vehicle/component/dashboard photo. Inspect carefully, identify the visible part or warning symbol by its real automotive name, and explain what it means and what action to take.")
                        is PendingChatAttachment.DocumentFile -> appendLine("Attachment: Document (${attachment.displayLabel}, ${attachment.mimeType}). Review the technical lines or estimate and explain each item clearly to a non-expert owner.")
                        is PendingChatAttachment.RecordedAudio -> appendLine("Attachment: Live vehicle audio recording (${attachment.durationSeconds}s). Analyze the acoustic signature (e.g., squeal, knock, rattle, whine, belt chirp), state probable components, assess commonality for this model, state drivability safety, and ask one sharp localization follow-up question.")
                        is PendingChatAttachment.VideoClip -> appendLine("Attachment: Vehicle video clip with audio (${attachment.displayLabel}). Inspect both visual cues (belt/engine movement, warning lamps, fluid leak, smoke, vibration) and the sound track, state probable automotive component(s), assess commonality, give clear drivability guidance, and ask 1 follow-up if needed.")
                        null -> Unit
                    }
                } else {
                    appendLine("OUTPUT LANGUAGE: Thai (ภาษาไทย) เท่านั้น — ให้ตอบทุกฟิลด์เป็นภาษาไทยระดับช่างเทคนิคอาวุโสมืออาชีพ")
                    appendLine("ข้อมูลรถที่ลงทะเบียนไว้:")
                    appendLine(vehicleContext)
                    if (recentHistoryText.isNotBlank()) {
                        appendLine()
                        appendLine("ประวัติการสนทนาก่อนหน้า:")
                        appendLine(recentHistoryText)
                    }
                    appendLine()
                    appendLine("ข้อความล่าสุดจากเจ้าของรถ: ${trimmedInput.ifBlank { "(เจ้าของรถส่งไฟล์/รูป/เสียง/วิดีโอมาให้ช่วยตรวจสอบ)" }}")
                    when (attachment) {
                        is PendingChatAttachment.Photo -> appendLine("ไฟล์แนบ: รูปถ่ายรถ/ชิ้นส่วน/หน้าปัด กรุณาตรวจสอบภาพ ระบุชื่อชิ้นส่วนหรือสัญลักษณ์ไฟเตือนตามศัพท์วงการรถจริง แล้วอธิบายสาเหตุและแนวทางปฏิบัติให้เข้าใจชัดเจน")
                        is PendingChatAttachment.DocumentFile -> appendLine("ไฟล์แนบ: เอกสาร (${attachment.displayLabel}, ${attachment.mimeType}) กรุณาอ่านและแปลรายการช่างหรือรหัสซ่อมให้เจ้าของรถเข้าใจได้อย่างถูกต้อง")
                        is PendingChatAttachment.RecordedAudio -> appendLine("ไฟล์แนบ: เสียงรถที่อัดสดๆ (${attachment.durationSeconds} วินาที) กรุณาวิเคราะห์ลักษณะเสียง ระบุชิ้นส่วนที่น่าจะเกี่ยวข้อง ประเมินความพบบ่อยในรุ่นนี้ บอกความเสี่ยงในการขับขี่ และถามคำถามซักประวัติ 1 ข้อเพื่อระบุตำแหน่ง")
                        is PendingChatAttachment.VideoClip -> appendLine("ไฟล์แนบ: คลิปวิดีโอพร้อมเสียงของรถ (${attachment.displayLabel}) กรุณาวิเคราะห์ทั้งภาพ (การสั่นของเครื่องยนต์ สายพาน ไฟเตือน หรือรอยรั่ว) และเสียงในคลิป ระบุชิ้นส่วนที่เกี่ยวข้อง ความเสี่ยงการขับขี่ และคำแนะนำเพิ่มเติม")
                        null -> Unit
                    }
                }
            }

            val promptContent = content {
                text(multimodalInstruction)
                filePayload?.let { (bytes, mimeType) ->
                    inlineData(bytes, mimeType)
                }
            }

            val fallbackSystemPrompt = if (language == AppLanguage.THAI) {
                "คุณคือหัวหน้าช่างเทคนิคอาวุโสด้านรถยนต์ ตอบเฉพาะเรื่องรถยนต์ด้วยภาษามืออาชีพที่อธิบายศัพท์เทคนิคจริงให้คนไม่มีพื้นฐานเข้าใจชัดเจน ห้ามใช้คำเปรียบเทียบแบบเด็ก หากนอกเรื่องให้ปฏิเสธอย่างสุภาพ หากมีข้อมูล System Voltage (OBD-II PID 42) ให้ยึดตาม JSON จริง อธิบายข้อจำกัดว่าเป็นแรงดันโมดูลควบคุม ECU ไม่ใช่ขั้วแบตเตอรี่โดยตรง และห้ามเดาค่าที่ไม่มี ${LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_TH}"
            } else {
                "You are a Senior Master Automotive Technician. Answer ONLY car-related questions in a calm, professional tone using real automotive terminology explained clearly to a non-expert driver (no childish metaphors). Politely refuse off-topic requests. When OBD-II PID 42 System Voltage JSON is present, rely strictly on it, note that PID 42 is ECU Control Module Voltage (not direct battery terminal), never invent missing values, and never declare battery/alternator failure from a single reading. ${LicensePlatePrivacyProtector.STRICT_PLATE_PRIVACY_SYSTEM_RULE_EN}"
            }

            val fallbackContent = content {
                text(
                    """
                    $fallbackSystemPrompt
                    $multimodalInstruction
                    Return ONLY a valid JSON object with keys: isCarRelated (boolean), simpleReply (string), modelCommonality (string), followUpQuestion (string), suggestedQuickReplies (array of strings), logSummary (string), requestedCaptureType (one of NONE, PHOTO, AUDIO, VIDEO), requestedCaptureReason (string).
                    """.trimIndent()
                )
                filePayload?.let { (bytes, mimeType) ->
                    inlineData(bytes, mimeType)
                }
            }

            val primaryChatModel = if (language == AppLanguage.THAI) {
                chatStructuredModelTh
            } else {
                chatStructuredModelEn
            }

            val rawText = withContext(Dispatchers.IO) {
                streamWithFastFallback(
                    primaryModel = primaryChatModel,
                    primaryContent = promptContent,
                    fallbackContent = fallbackContent
                )
            } ?: return Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) {
                        "AI ไม่ได้ส่งคำตอบกลับมา"
                    } else {
                        "BRANDT Auto Specialist did not return a response."
                    }
                )
            )

            val json = JSONObject(extractJsonObject(rawText))
            val isCarRelated = json.optBoolean("isCarRelated", true)
            val simpleReply = json.optString("simpleReply", "").trim()
            if (simpleReply.isEmpty()) {
                return Result.failure(
                    IllegalStateException(
                        if (language == AppLanguage.THAI) {
                            "AI ส่งคำตอบว่างเปล่า กรุณาลองถามอีกครั้ง"
                        } else {
                            "Received an empty diagnostic response. Please ask again."
                        }
                    )
                )
            }

            val quickArray = json.optJSONArray("suggestedQuickReplies") ?: JSONArray()
            val quickReplies = buildList {
                for (i in 0 until quickArray.length()) {
                    val item = quickArray.optString(i, "").trim()
                    if (item.isNotEmpty() && size < 4) add(item.take(64))
                }
            }

            val captureType = if (isCarRelated) {
                RequestedCaptureType.fromCode(json.optString("requestedCaptureType", "NONE"))
            } else {
                RequestedCaptureType.NONE
            }
            val captureReason = if (captureType != RequestedCaptureType.NONE) {
                json.optString("requestedCaptureReason", "").trim().take(280)
            } else {
                ""
            }

            Result.success(
                CarChatReply(
                    isCarRelated = isCarRelated,
                    simpleReply = LicensePlatePrivacyProtector.sanitizePlateAndLocationText(simpleReply.take(1800)),
                    modelCommonality = if (isCarRelated) json.optString("modelCommonality", "").trim().take(80) else "",
                    followUpQuestion = if (isCarRelated) {
                        LicensePlatePrivacyProtector.sanitizePlateAndLocationText(json.optString("followUpQuestion", "").trim().take(280))
                    } else {
                        ""
                    },
                    suggestedQuickReplies = if (isCarRelated) quickReplies else emptyList(),
                    logSummary = if (isCarRelated) {
                        LicensePlatePrivacyProtector.sanitizePlateAndLocationText(
                            json.optString("logSummary", simpleReply).trim().take(1500)
                        )
                    } else {
                        ""
                    },
                    requestedCaptureType = captureType,
                    requestedCaptureReason = LicensePlatePrivacyProtector.sanitizePlateAndLocationText(captureReason)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Mechanic chat timed out", e)
            Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) {
                        "AI ใช้เวลาประมวลผลนานเกินกำหนด กรุณาลองส่งใหม่อีกครั้ง"
                    } else {
                        "Diagnostic request timed out. Please try again."
                    }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Mechanic chat failed", e)
            Result.failure(formatFriendlyException(e))
        }
    }

    override suspend fun explainDiagnosticStatusPlainLanguage(
        vehicle: Vehicle,
        status: DiagnosticStatusDetailSummary,
        language: AppLanguage
    ): Result<DiagnosticStatusPlainExplanation> {
        return try {
            val vehicleSnapshot = ObdVehicleSnapshot.fromVehicle(vehicle)
            val nowMs = System.currentTimeMillis()
            val interpretedConfirmed = status.confirmedCodes.map { code ->
                val dtc = DiagnosticTroubleCode(code = code, status = DtcStatus.CONFIRMED, timestampEpochMs = nowMs)
                val interp = DtcInterpreter.interpret(dtc, vehicleSnapshot)
                "$code (Confirmed): ${interp.standardDefinition} | ${interp.technicalExplanation}"
            }
            val interpretedPending = status.pendingCodes.map { code ->
                val dtc = DiagnosticTroubleCode(code = code, status = DtcStatus.PENDING, timestampEpochMs = nowMs)
                val interp = DtcInterpreter.interpret(dtc, vehicleSnapshot)
                "$code (Pending): ${interp.standardDefinition} | ${interp.technicalExplanation}"
            }
            val vehicleSpec = formatVehicleContext(vehicle, language)

            val prompt = buildString {
                if (language == AppLanguage.THAI) {
                    appendLine("คุณคือผู้ช่วยอธิบายข้อมูลรถสำหรับ 'คนที่ไม่มีความรู้เรื่องรถเลย'")
                    appendLine("ตอบเป็นภาษาไทยที่เข้าใจง่ายสุดๆ อบอุ่น เป็นมิตร หลีกเลี่ยงศัพท์ช่างที่ซับซ้อน (ถ้าต้องเอ่ยถึงชื่อชิ้นส่วน ให้แปลทันทีว่าเป็นอะไร ทำงานยังไง)")
                    appendLine("ห้ามแต่งโค้ดหรือค่าใดๆ ขึ้นมาเอง ให้อ้างอิงเฉพาะข้อมูลจริงด้านล่างเท่านั้น:")
                } else {
                    appendLine("You are a warm, friendly car guide explaining ECU diagnostic status to someone who knows NOTHING about cars.")
                    appendLine("Use simple everyday English — NO dense automotive jargon. If you mention a part or light, explain plainly what it means in real life.")
                    appendLine("NEVER invent any fault code or sensor value. Only explain the real values below:")
                }
                appendLine()
                appendLine("Vehicle:")
                appendLine(vehicleSpec)
                appendLine()
                appendLine("Real ECU Diagnostic Status:")
                appendLine("- ECU Session Active: ${status.hasEcuSession}")
                appendLine("- Confirmed DTC: ${status.confirmedDetailText}")
                appendLine("- Pending DTC: ${status.pendingDetailText}")
                appendLine("- Freeze Frame: ${status.freezeFrameDetailText}")
                appendLine("- MIL Status (Check Engine Light): ${status.milDetailText}")
                appendLine("- Readiness Monitor: ${status.readinessDetailText}")
                if (interpretedConfirmed.isNotEmpty() || interpretedPending.isNotEmpty()) {
                    appendLine()
                    appendLine("Verified DTC Lookup Details:")
                    interpretedConfirmed.forEach { appendLine("- $it") }
                    interpretedPending.forEach { appendLine("- $it") }
                }
            }

            val primaryContent = content { text(prompt) }
            val fallbackContent = content {
                text(
                    "$prompt\nReturn ONLY a valid JSON object with keys: headline (string), plainSummary (string), codeBreakdown (array of strings), whatToDoNext (string)."
                )
            }

            val rawText = withContext(Dispatchers.IO) {
                streamWithFastFallback(
                    primaryModel = diagnosticStatusExplainModel,
                    primaryContent = primaryContent,
                    fallbackContent = fallbackContent
                )
            } ?: return Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) "AI ไม่ได้ส่งคำอธิบายกลับมา" else "AI returned an empty diagnostic explanation."
                )
            )

            val json = JSONObject(extractJsonObject(rawText))
            val headline = json.optString("headline", "").trim()
            val plainSummary = json.optString("plainSummary", "").trim()
            val breakdownArr = json.optJSONArray("codeBreakdown") ?: JSONArray()
            val breakdown = buildList {
                for (i in 0 until breakdownArr.length()) {
                    val line = breakdownArr.optString(i, "").trim()
                    if (line.isNotEmpty() && size < 6) add(line.take(260))
                }
            }
            val nextSteps = json.optString("whatToDoNext", "").trim()

            if (headline.isEmpty() && plainSummary.isEmpty()) {
                return Result.failure(
                    IllegalStateException(
                        if (language == AppLanguage.THAI) "ไม่สามารถแปลคำอธิบายจาก AI ได้" else "Unable to parse AI diagnostic explanation."
                    )
                )
            }

            Result.success(
                DiagnosticStatusPlainExplanation(
                    headline = headline.ifBlank { status.overallStatusText }.take(180),
                    plainSummary = plainSummary.take(900),
                    codeBreakdown = breakdown,
                    whatToDoNext = nextSteps.take(450)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Diagnostic status AI explain timed out", e)
            Result.failure(
                IllegalStateException(
                    if (language == AppLanguage.THAI) "AI ใช้เวลาประมวลผลนานเกินกำหนด กรุณาลองใหม่อีกครั้ง" else "AI explanation timed out. Please try again."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic status AI explain failed", e)
            Result.failure(formatFriendlyException(e))
        }
    }

    private suspend fun streamWithFastFallback(
        primaryModel: GenerativeModel,
        primaryContent: Content,
        fallbackContent: Content
    ): String? {
        val primaryError: Exception? = try {
            val text = withTimeout(25_000L) {
                val sb = StringBuilder()
                primaryModel.generateContentStream(primaryContent).collect { chunk ->
                    chunk.text?.let { sb.append(it) }
                }
                sb.toString()
            }
            if (text.isNotBlank()) return text
            IllegalStateException("Primary AI stream returned empty content.")
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Primary gemini-3.8-flash timed out at 25s; falling back to gemini-3.5-flash-lite", e)
            e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isHardNetworkOrCheckError(e)) throw e
            Log.w(TAG, "Primary structured chat inference failed, falling back to gemini-3.5-flash-lite", e)
            e
        }

        val fallbackText = withTimeout(25_000L) {
            val sb = StringBuilder()
            fallbackFastLiteModel.generateContentStream(fallbackContent).collect { chunk ->
                chunk.text?.let { sb.append(it) }
            }
            sb.toString()
        }
        return fallbackText.ifBlank {
            if (primaryError != null) throw primaryError else null
        }
    }

    private fun encodeScaledJpeg(context: Context, path: String, maxDim: Int, quality: Int): ByteArray? {
        val bitmap = decodeScaledBitmap(context, path, maxDim = maxDim) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray().takeIf { it.isNotEmpty() }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun formatVehicleContext(vehicle: Vehicle, language: AppLanguage): String {
        return if (language == AppLanguage.THAI) {
            """
                - ยี่ห้อ/รุ่น/ปี: ${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model} รุ่นย่อย ${vehicle.trim}
                - ตัวถัง: ${vehicle.resolvedBodyStyle().displayName}
                - เครื่องยนต์/เกียร์/เชื้อเพลิง: ${vehicle.engineType} / ${vehicle.transmission} / ${vehicle.fuelType}
                - เลขไมล์ปัจจุบัน: ${vehicle.mileage} ${vehicle.mileageUnit}
                - ตลาด/ประเทศ: ${vehicle.market}
            """.trimIndent()
        } else {
            """
                - Year / Make / Model / Trim: ${vehicle.modelYear} ${vehicle.manufacturer} ${vehicle.model} ${vehicle.trim}
                - Body Style: ${vehicle.resolvedBodyStyle().displayName}
                - Engine / Transmission / Fuel: ${vehicle.engineType} / ${vehicle.transmission} / ${vehicle.fuelType}
                - Odometer: ${vehicle.mileage} ${vehicle.mileageUnit}
                - Market / Region: ${vehicle.market}
            """.trimIndent()
        }
    }

    private fun isHardNetworkOrCheckError(err: Exception): Boolean {
        val msg = err.message.orEmpty()
        return msg.contains("403") ||
            msg.contains("App Check", ignoreCase = true) ||
            msg.contains("attestation", ignoreCase = true) ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
    }

    private fun formatFriendlyException(e: Exception): IllegalStateException {
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
        return IllegalStateException("${e.javaClass.simpleName}: $detail", e)
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

    private fun readUriBytes(context: Context, uriString: String, maxBytes: Int): ByteArray? {
        val directFile = resolveLocalFile(uriString)
        if (directFile != null) {
            if (directFile.length() > maxBytes) return null
            return directFile.readBytes()
        }
        return context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun decodeScaledBitmap(context: Context, path: String, maxDim: Int): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val localFile = resolveLocalFile(path)
            if (localFile != null) {
                BitmapFactory.decodeFile(localFile.absolutePath, boundsOptions)
            } else {
                context.contentResolver.openInputStream(Uri.parse(path))?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
            }
            val largest = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (largest / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = if (localFile != null) {
                BitmapFactory.decodeFile(localFile.absolutePath, decodeOptions)
            } else {
                context.contentResolver.openInputStream(Uri.parse(path))?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } ?: return null

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
            Log.w(TAG, "Failed to decode chat photo: $path", e)
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

    private companion object {
        const val TAG = "VehicleMechanicAI"
        const val MAX_INLINE_BYTES = 12 * 1024 * 1024
    }
}
