package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.appStrings

/**
 * Animated floating MACK AI mascot entry button displayed over almost all authenticated screens.
 * Tapping opens the MACK AI diagnostic screen (iPhone 17 - 20) for the active vehicle.
 */
@Composable
fun FloatingMackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings(LocalAppLanguage.current)
    val transition = rememberInfiniteTransition(label = "mack_floating_idle")

    val bobDp by transition.animateFloat(
        initialValue = -3f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_bob"
    )
    val breatheScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.055f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_breathe"
    )
    val headTiltDeg by transition.animateFloat(
        initialValue = -2.4f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_tilt"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mack_ring_alpha"
    )

    Box(
        modifier = modifier
            .size(width = 72.dp, height = 78.dp)
            .graphicsLayer {
                translationY = bobDp * density
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.8.dp, Color(0xFF93C5FD).copy(alpha = ringAlpha)),
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer {
                    scaleX = breatheScale
                    scaleY = breatheScale
                    rotationZ = headTiltDeg
                }
                .shadow(elevation = 12.dp, shape = CircleShape)
                .testTag("floating_mack_button")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF202328),
                                Color(0xFF2E5C94)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_mack_mascot),
                    contentDescription = strings.floatingMackDesc,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(CircleShape)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFF1F2937),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.78f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
        ) {
            Text(
                text = "MACK",
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
