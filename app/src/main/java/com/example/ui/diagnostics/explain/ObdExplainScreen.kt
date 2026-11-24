package com.example.ui.diagnostics.explain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.obd.ObdConnectionMethod
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage
import com.example.ui.diagnostics.components.ObdAdaptersOverviewBanner
import com.example.ui.theme.BrandtDiagnosticsLightTheme

@Composable
fun ObdExplainScreen(
    onNavigateBack: () -> Unit,
    onOpenConnect: (ObdConnectionMethod) -> Unit = {}
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI

    BrandtDiagnosticsLightTheme {
        Scaffold(
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("obd_explain_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isThai) "ย้อนกลับ" else "Back",
                                tint = Color(0xFF1F2937)
                            )
                        }
                        Text(
                            text = "What is OBD Bluetooth\n& OBD Wi-Fi",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFF374151),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            modifier = Modifier.testTag("obd_explain_screen")
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Top OBD Banner (Frame 2)
                    ObdAdaptersOverviewBanner(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("obd_explain_banner_image")
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (isThai) {
                                "OBD-2 (ย่อมาจาก On-Board Diagnostics II) เปรียบเสมือน \"ช่องตรวจสุขภาพประจำตัวรถ\" ที่มีอยู่ในรถยนต์ยุคใหม่ทุกคัน"
                            } else {
                                "OBD-2 (short for On-Board Diagnostics II) is basically a health-check port inside your car."
                            },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF111827)
                        )

                        Text(
                            text = if (isThai) {
                                "ภายในรถมีเซ็นเซอร์ขนาดเล็กหลายสิบจุดคอยตรวจจับความร้อนเครื่องยนต์ ความเร็วรอบ ปริมาณอากาศ และระบบไฟ แล้วส่งรายงานกลับไปยังสมองกลหลักของรถที่เรียกว่า ECU (Engine Control Unit) หากมีจุดใดทำงานผิดปกติ กล่อง ECU จะบันทึกรหัสปัญหาไว้และอาจเปิดไฟเตือน Check Engine บนหน้าปัด"
                            } else {
                                "Modern cars have dozens of tiny sensors checking things like engine temperature, speed, and air flow, all reporting back to the car's main computer (ECU - Engine Control Unit). Whenever something looks off, the computer saves a little error code and might turn on the Check Engine light on your dashboard."
                            },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF111827)
                        )

                        Text(
                            text = if (isThai) {
                                "เมื่อคุณเสียบตัวอ่าน OBD-2 (แบบเชื่อมต่อไร้สายผ่าน Bluetooth หรือ Wi-Fi เข้ากับมือถือ) อุปกรณ์จะช่วยคุยกับกล่อง ECU ของรถแล้วแปลสัญญาณเทคนิคเหล่านั้นให้กลายเป็นข้อมูลที่เข้าใจง่ายในชีวิตประจำวัน"
                            } else {
                                "When you plug an OBD-2 scanner (or a Bluetooth/Wi-Fi adapter that connects to your phone) into this port, it asks the car's computer (the ECU): \"Hey, what's going on?\" and translates those hidden signals into plain information you can actually read."
                            },
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF111827)
                        )

                        // Section: What It Helps You Do
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isThai) "ช่วยให้คุณทำอะไรได้บ้าง" else "What It Helps You Do",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            ExplainBulletPoint(
                                title = if (isThai) "รู้สาเหตุไฟเตือนก่อนใคร:" else "Spot trouble early:",
                                body = if (isThai) {
                                    "แทนที่จะเดาว่าทำไมไฟหน้าปัดถึงโชว์ คุณสามารถดูรหัสปัญหา (DTC) และคำอธิบายอาการเบื้องต้นได้ทันที"
                                } else {
                                    "Instead of guessing why a warning light is on, you can pull up an error code that points you in the right direction."
                                }
                            )
                            ExplainBulletPoint(
                                title = if (isThai) "เห็นค่าการทำงานจริงแบบเรียลไทม์:" else "See live numbers:",
                                body = if (isThai) {
                                    "ติดตามค่ารอบเครื่องยนต์ (RPM) อุณหภูมิน้ำหล่อเย็น ภาระเครื่องยนต์ และค่าเซ็นเซอร์ต่างๆ ระหว่างจอดหรือขับขี่"
                                } else {
                                    "You can watch real-time stats like RPM (engine speed), coolant temperature, or battery voltage while driving or idling."
                                }
                            )
                            ExplainBulletPoint(
                                title = if (isThai) "เช็กความพร้อมก่อนเข้าอู่หรือเดินทางไกล:" else "Check readiness anytime:",
                                body = if (isThai) {
                                    "เห็นสภาพรวมและประวัติการสแกนชัดเจน เพื่อความปลอดภัย แอปนี้ทำงานแบบ Read-Only เท่านั้น (ไม่ออกคำสั่งลบหรือแก้ไขค่าในกล่อง ECU)"
                                } else {
                                    "Review your scan history and emissions readiness before visiting a mechanic. For your safety, BRANDT operates in strictly Read-Only mode (never alters or clears ECU memory)."
                                }
                            )
                        }

                        // Section: How to find the OBD-II port?
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isThai) "ช่องเสียบ OBD-II อยู่ตรงไหน?" else "How to find the OBD-II port?",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = if (isThai) {
                                    "ช่องเสียบ OBD-II มักอยู่ใต้คอนโซลฝั่งคนขับ บริเวณระดับหัวเข่า เหนือแป้นคันเร่ง/เบรก หรือใกล้กล่องฟิวส์ในห้องโดยสาร (บางรุ่นมีฝาพลาสติกเล็กๆ ปิดอยู่ หรืออยู่ด้านข้างคอนโซลกลาง) ลักษณะเป็นช่อง 16 พินทรงสี่เหลี่ยมคางหมู จึงเสียบเข้าได้ทางเดียวเท่านั้น"
                                } else {
                                    "The OBD-2 port is usually located under the dashboard on the driver's side often near knee level, above the pedals, or close to the interior fuse box (sometimes hidden behind a small plastic cover or near the center console). It has a 16-pin trapezoid shape so it only fits one way."
                                },
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = Color(0xFF111827)
                            )

                            ObdPortLocationDiagram(isThai = isThai)
                        }

                        // Section: Basic 5 Steps to Plug In and Use It
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isThai) {
                                    "5 ขั้นตอนง่ายๆ ในการเสียบและใช้งาน"
                                } else {
                                    "Basic 5 Steps to Plug In and Use It"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            NumberedStepItem(
                                number = 1,
                                title = if (isThai) "ดับเครื่องยนต์และมองหาช่องเสียบ:" else "Turn off the engine and find the port:",
                                body = if (isThai) {
                                    "จอดรถในที่ปลอดภัย ปิดสวิตช์กุญแจ แล้วก้มมองใต้คอนโซลฝั่งคนขับ (ใช้ไฟฉายมือถือส่องช่วยได้) เพื่อหาช่อง 16 พินทรงคางหมู"
                                } else {
                                    "Park safely, turn the ignition off, and look under the driver's side dash (using a phone flashlight helps) for the 16-pin trapezoid socket."
                                }
                            )
                            NumberedStepItem(
                                number = 2,
                                title = if (isThai) "เสียบหัวอ่านเข้าช่องให้สนิท:" else "Plug in the scanner or adapter:",
                                body = if (isThai) {
                                    "เล็งรูปทรงปลั๊กให้ตรงกับช่อง แล้วดันเข้าไปตรงๆ จนแน่นพอดีมือ — ไม่ต้องฝืนหรือโยกแรง"
                                } else {
                                    "Line up the shape of the connector with the port and push it straight in until it feels snug—no forcing or wiggling."
                                }
                            )
                            NumberedStepItem(
                                number = 3,
                                title = if (isThai) "เปิดสวิตช์รถไปตำแหน่ง ON:" else "Switch the car's power to \"ON\":",
                                body = if (isThai) {
                                    "บิดกุญแจหรือกดปุ่มสตาร์ท (ไม่ต้องเหยียบเบรก) ไปที่ตำแหน่ง ON เพื่อให้ไฟหน้าปัดติดและกล่อง ECU จ่ายไฟให้หัวอ่าน (หรือติดเครื่องยนต์เมื่อต้องการดูค่าขณะเครื่องเดิน)"
                                } else {
                                    "Turn the key or press the start button to the ON position (dashboard lights come on, without starting the engine yet—or start the engine if your tool/test calls for it) so the ECU gets power and can talk to the device."
                                }
                            )
                            NumberedStepItem(
                                number = 4,
                                title = if (isThai) "เชื่อมต่อในแอปและอ่านข้อมูล:" else "Connect and read the data:",
                                body = if (isThai) {
                                    "เลือกเชื่อมต่อแบบ Bluetooth หรือ Wi-Fi ในหน้าถัดไป จากนั้นเข้าเมนูสแกนเพื่ออ่านโค้ดและดูข้อมูลสดจาก ECU"
                                } else {
                                    "For a Bluetooth or Wi-Fi OBD-II adapter, choose Bluetooth or Wi-Fi on the next screen, then open the scan workspace to read codes and live data."
                                }
                            )
                            NumberedStepItem(
                                number = 5,
                                title = if (isThai) "ถอดหัวอ่านออกเมื่อใช้งานเสร็จ:" else "Unplug when you're done:",
                                body = if (isThai) {
                                    "ปิดหน้าสแกน ปิดสวิตช์รถ (OFF) แล้วดึงตัวอ่านออกมาตรงๆ อย่างเบามือ"
                                } else {
                                    "Close the app or exit the scan menu, turn the car's power off, and pull the device straight out."
                                }
                            )
                        }

                        // Section: Two Quick Tips
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = if (isThai) "ทริคเล็กๆ น้อยๆ ที่ควรรู้" else "Two Quick Tips",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            ExplainBulletPoint(
                                title = if (isThai) "เบามือกับขาพิน:" else "Be gentle with the pins:",
                                body = if (isThai) {
                                    "ขาโลหะด้านในมีขนาดเล็กและงอได้ง่าย หากเสียบเอียงหรืองัดออกแรงๆ อาจทำให้หน้าสัมผัสเสียหายได้"
                                } else {
                                    "The metal pins inside are small and easy to bend if pushed in crooked or yanked out at an angle."
                                }
                            )
                            ExplainBulletPoint(
                                title = if (isThai) "ควรถอดออกเมื่อไม่ใช้งานหลายวัน:" else "Unplug when you don't need it:",
                                body = if (isThai) {
                                    "หัวอ่านบางรุ่นอาจดึงไฟเลี้ยงจากแบตเตอรี่รถทีละน้อยหากเสียบค้างไว้ขณะจอดนานๆ"
                                } else {
                                    "Leaving some adapters plugged in for days can slowly draw a small amount of battery power while the car is parked."
                                }
                            )
                        }

                        // Quick Jump to Connect Actions
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF3F4F6),
                            border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isThai) {
                                        "พร้อมเชื่อมต่อหัวอ่านของรถคุณแล้วหรือยัง?"
                                    } else {
                                        "Ready to connect your OBD-II adapter?"
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onOpenConnect(ObdConnectionMethod.BLUETOOTH) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Black,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("obd_explain_connect_bt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bluetooth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bluetooth", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { onOpenConnect(ObdConnectionMethod.WIFI) },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                                        border = BorderStroke(1.5.dp, Color(0xFF374151)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("obd_explain_connect_wifi_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Wifi,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Wi-Fi", maxLines = 1)
                                    }
                                }
                            }
                        }

                        // Red ECU AI Disclaimer matching bottom of iPhone 17 - 18
                        Text(
                            text = if (isThai) {
                                "*การเชื่อมต่อกับกล่อง ECU ช่วยให้ AI ประมวลผลข้อมูลการทำงานของรถแบบเรียลไทม์ เพิ่มความแม่นยำในการวิเคราะห์และให้คำแนะนำที่เหมาะสมกับรถของคุณมากขึ้น*"
                            } else {
                                "*Connecting to the ECU allows the AI to process real-time vehicle operating data, increasing the accuracy of analysis and providing more appropriate recommendations*"
                            },
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFFDC2626),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplainBulletPoint(
    title: String,
    body: String
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.Black)) {
                append("$title ")
            }
            withStyle(SpanStyle(color = Color(0xFF111827))) {
                append(body)
            }
        },
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

@Composable
private fun NumberedStepItem(
    number: Int,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.Black)) {
                    append("$title ")
                }
                withStyle(SpanStyle(color = Color(0xFF111827))) {
                    append(body)
                }
            },
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Displays the Frame 5 dashboard OBD-II port diagram (R.drawable.img_obd_port_diagram) inside a clean white bordered frame.
 */
@Composable
private fun ObdPortLocationDiagram(isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("obd_port_location_diagram")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_obd_port_diagram),
                contentDescription = if (isThai) {
                    "แผนภาพตำแหน่งพอร์ต OBD-II ใต้คอนโซลรถและหัวเสียบ 16 พิน"
                } else {
                    "Diagram showing common under-dash OBD-II port locations and the 16-pin connector"
                },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            )

            Text(
                text = if (isThai) {
                    "จุดสีแดง: ตำแหน่งยอดนิยมของพอร์ต OBD-II (ส่วนใหญ่จะอยู่ฝั่งคนขับใต้พวงมาลัย)"
                } else {
                    "Red dots: Common OBD-II port locations (typically under the driver-side dashboard)"
                },
                fontSize = 12.sp,
                color = Color(0xFF4B5563),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
