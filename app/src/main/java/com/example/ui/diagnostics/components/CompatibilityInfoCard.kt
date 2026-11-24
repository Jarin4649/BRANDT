package com.example.ui.diagnostics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.obd.ObdProtocolPreference
import com.example.data.obd.WifiObdCatalog
import com.example.data.settings.AppLanguage
import com.example.data.settings.LocalAppLanguage

@Composable
fun CompatibilityInfoCard(
    selectedProtocol: ObdProtocolPreference,
    onSelectProtocol: (ObdProtocolPreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val isThai = LocalAppLanguage.current == AppLanguage.THAI

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF3F4F6),
        border = BorderStroke(1.5.dp, Color(0xFF4B5563)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_compatibility_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isThai) {
                        "ความเข้ากันได้ของโปรโตคอลและตัวสแกนเนอร์"
                    } else {
                        "Protocol & Scanner Compatibility"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black
                )
            }

            Text(
                text = if (isThai) {
                    "ตัวสแกนเนอร์ OBD-II แต่ละรุ่นรองรับคำสั่ง AT, บัส CAN และพอร์ต Wi-Fi/Bluetooth ต่างกัน ระบบ BRANDT จะยืนยันความเข้ากันได้หลังจากทดสอบสื่อสารกับอุปกรณ์จริงเท่านั้น"
                } else {
                    "OBD-II adapters vary by AT firmware, CAN bus speed, and Wi-Fi/Bluetooth transport. BRANDT confirms scanner compatibility only after live hardware handshake verification."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF111827)
            )

            Text(
                text = if (isThai) {
                    "เลือกโปรโตคอลสื่อสารของรถ (Bus Protocol)"
                } else {
                    "Preferred Vehicle Bus Protocol"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Black
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ObdProtocolPreference.entries.forEach { protocol ->
                    val selected = selectedProtocol == protocol
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectProtocol(protocol) },
                        label = { Text(protocol.localizedName(isThai)) },
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color(0xFF4B5563),
                            selectedBorderColor = Color.Black,
                            borderWidth = 1.5.dp,
                            selectedBorderWidth = 1.5.dp
                        ),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFFE5E7EB),
                            labelColor = Color.Black,
                            selectedContainerColor = Color(0xFFD1D5DB),
                            selectedLabelColor = Color.Black,
                            selectedLeadingIconColor = Color.Black
                        ),
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("protocol_chip_${protocol.name}")
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE5E7EB),
                border = BorderStroke(1.2.dp, Color(0xFF4B5563))
            ) {
                Text(
                    text = "${selectedProtocol.atCommandCode}: ${selectedProtocol.localizedSummary(isThai)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF111827),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = Color(0xFF9CA3AF))

            Text(
                text = if (isThai) {
                    "ตัวอย่างโปรไฟล์ตัวสแกนเนอร์ Wi-Fi OBD-II ยอดนิยม"
                } else {
                    "Common Wi-Fi OBD-II Adapter Profiles"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Black
            )

            WifiObdCatalog.localizedProfiles(isThai).forEach { profile ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black
                    )
                    Text(
                        text = profile.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF111827)
                    )
                }
            }
        }
    }
}
