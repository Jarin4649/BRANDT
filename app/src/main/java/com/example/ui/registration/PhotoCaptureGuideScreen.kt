package com.example.ui.registration

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VehicleAngle
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings
import com.example.ui.components.LanguageSettingsActionButton
import com.example.ui.theme.BrandtAuthInk
import com.example.ui.theme.BrandtGuideDivider
import com.example.ui.theme.BrandtGuideIceCyan
import com.example.ui.theme.BrandtGuideRoseMist
import com.example.ui.theme.BrandtGuideVioletGlow
import com.example.ui.theme.BrandtGuideWhiteSheen
import com.example.ui.theme.BrandtMintAction
import com.example.ui.theme.BrandtPureWhite
import com.example.ui.theme.MotoraCrimson
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.SlateSecondary
import com.example.ui.theme.SpaceGroteskFontFamily

private data class GuideAngleSpec(
    val angle: VehicleAngle,
    val label: String,
    @param:DrawableRes val drawableRes: Int,
    val isSideProfile: Boolean,
    val testTag: String
)

@Composable
fun PhotoCaptureGuideScreen(
    onNavigateBack: () -> Unit,
    onContinueToPhotoCapture: () -> Unit
) {
    val strings = appStrings(LocalAppLanguage.current)
    var expandedTipAngle by remember { mutableStateOf<VehicleAngle?>(null) }

    val guideAngles = remember(strings) {
        listOf(
            GuideAngleSpec(
                angle = VehicleAngle.FRONT,
                label = strings.guideFrontLabel,
                drawableRes = R.drawable.guide_car_front,
                isSideProfile = false,
                testTag = "guide_card_front"
            ),
            GuideAngleSpec(
                angle = VehicleAngle.SIDE_LEFT,
                label = strings.guideLeftLabel,
                drawableRes = R.drawable.guide_car_left,
                isSideProfile = true,
                testTag = "guide_card_left"
            ),
            GuideAngleSpec(
                angle = VehicleAngle.SIDE_RIGHT,
                label = strings.guideRightLabel,
                drawableRes = R.drawable.guide_car_right,
                isSideProfile = true,
                testTag = "guide_card_right"
            ),
            GuideAngleSpec(
                angle = VehicleAngle.REAR,
                label = strings.guideRearLabel,
                drawableRes = R.drawable.guide_car_rear,
                isSideProfile = false,
                testTag = "guide_card_rear"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandtPureWhite)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pinned minimal top navigation bar ("Before fill information") + back & language settings
        Row(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("guide_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.overviewBackToGarage,
                    tint = BrandtAuthInk
                )
            }

            Text(
                text = strings.guidePreTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                ),
                color = SlateSecondary,
                modifier = Modifier.weight(1f)
            )

            LanguageSettingsActionButton()
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Hero Headline ("Guided Photo Capture")
            Text(
                text = strings.guideTitle,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = SpaceGroteskFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 31.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = BrandtAuthInk,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("photo_guide_title")
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Responsive 4-angle studio guide cards (1 column on phones, 2x2 grid on tablets)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 600.dp
                if (isWide) {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        guideAngles.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                rowItems.forEach { item ->
                                    GuideAngleShowcaseCard(
                                        spec = item,
                                        tipText = strings.angleGuideInstruction(item.angle),
                                        isTipExpanded = expandedTipAngle == item.angle,
                                        onToggleTip = {
                                            expandedTipAngle = if (expandedTipAngle == item.angle) null else item.angle
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        guideAngles.forEach { item ->
                            GuideAngleShowcaseCard(
                                spec = item,
                                tipText = strings.angleGuideInstruction(item.angle),
                                isTipExpanded = expandedTipAngle == item.angle,
                                onToggleTip = {
                                    expandedTipAngle = if (expandedTipAngle == item.angle) null else item.angle
                                },
                                modifier = Modifier
                                    .widthIn(max = 420.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Minimalist centered vertical divider
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(42.dp)
                        .background(BrandtGuideDivider)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Why taking photos is necessary + privacy license plate note
            val whyTitleAnnotated = buildAnnotatedString {
                withStyle(SpanStyle(color = MotoraCrimson, fontWeight = FontWeight.Bold)) {
                    append("*")
                }
                withStyle(SpanStyle(color = BrandtAuthInk, fontWeight = FontWeight.Medium)) {
                    append(strings.guideWhyTitle)
                }
                withStyle(SpanStyle(color = MotoraCrimson, fontWeight = FontWeight.Bold)) {
                    append(" *")
                }
            }

            Text(
                text = whyTitleAnnotated,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 15.sp
                ),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("guide_why_title")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = strings.guideWhyBody,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                color = BrandtAuthInk,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.guidePrivacyNote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                color = MotoraCrimson,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("guide_privacy_note")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Right-aligned mint CTA button matching mockup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onContinueToPhotoCapture,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandtMintAction,
                        contentColor = BrandtAuthInk
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("guide_lets_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = BrandtAuthInk,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.guideStartButton,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = BrandtAuthInk
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GuideAngleShowcaseCard(
    spec: GuideAngleSpec,
    tipText: String,
    isTipExpanded: Boolean,
    onToggleTip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(206.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onToggleTip)
                .testTag(spec.testTag)
        ) {
            // Soft luminous multi-stop pastel violet -> white sheen -> ice cyan studio backdrop
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        0.0f to BrandtGuideVioletGlow,
                        0.45f to BrandtGuideWhiteSheen,
                        1.0f to BrandtGuideIceCyan,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to BrandtGuideRoseMist.copy(alpha = 0.72f),
                        0.75f to Color.Transparent,
                        center = Offset(size.width * 0.78f, size.height * 0.14f),
                        radius = size.maxDimension * 0.65f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        0.0f to BrandtPureWhite.copy(alpha = 0.85f),
                        0.68f to Color.Transparent,
                        center = Offset(size.width * 0.50f, size.height * 0.56f),
                        radius = size.maxDimension * 0.55f
                    )
                )
            }

            // Angle label (Top Left) & Outlined Thumbs-Up check indicator (Top Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    ),
                    color = BrandtAuthInk
                )
                Icon(
                    imageVector = Icons.Outlined.ThumbUp,
                    contentDescription = "${spec.label} recommended framing",
                    tint = BrandtAuthInk,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Transparent car angle asset centered cleanly inside the studio frame
            Image(
                painter = painterResource(id = spec.drawableRes),
                contentDescription = "${spec.label} vehicle framing guide",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxSize()
                    .padding(
                        top = if (spec.isSideProfile) 56.dp else 42.dp,
                        start = if (spec.isSideProfile) 14.dp else 24.dp,
                        end = if (spec.isSideProfile) 14.dp else 24.dp,
                        bottom = if (spec.isSideProfile) 14.dp else 6.dp
                    )
            )
        }

        AnimatedVisibility(visible = isTipExpanded) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}
