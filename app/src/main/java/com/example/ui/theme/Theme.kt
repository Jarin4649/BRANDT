package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.settings.LanguagePreferenceStore
import com.example.data.settings.LocalAppLanguage

private val MotoraLightColorScheme = lightColorScheme(
    primary = MotoraEmerald,
    onPrimary = Color.White,
    primaryContainer = MotoraEmeraldContainer,
    onPrimaryContainer = MotoraOnEmeraldContainer,
    secondary = SlateSecondary,
    onSecondary = Color.White,
    secondaryContainer = StudioSurfaceMutedLight,
    onSecondaryContainer = ObsidianInk,
    tertiary = MotoraAmber,
    onTertiary = Color.White,
    tertiaryContainer = MotoraAmberContainer,
    onTertiaryContainer = Color(0xFF4A2C00),
    background = StudioCanvasLight,
    onBackground = ObsidianInk,
    surface = StudioSurfaceLight,
    onSurface = ObsidianInk,
    surfaceVariant = StudioSurfaceMutedLight,
    onSurfaceVariant = SlateSecondary,
    surfaceContainerLowest = StudioSurfaceElevatedLight,
    surfaceContainerLow = StudioSurfaceLight,
    surfaceContainer = StudioCanvasLight,
    surfaceContainerHigh = StudioSurfaceMutedLight,
    outline = StudioOutlineLight,
    outlineVariant = StudioOutlineLight.copy(alpha = 0.6f),
    error = MotoraCrimson,
    onError = Color.White,
    errorContainer = MotoraCrimsonContainer,
    onErrorContainer = Color(0xFF5F1111)
)

private val MotoraDarkColorScheme = darkColorScheme(
    primary = MotoraEmeraldBright,
    onPrimary = Color(0xFF04291B),
    primaryContainer = MotoraEmeraldContainerDark,
    onPrimaryContainer = MotoraOnEmeraldContainerDark,
    secondary = SilverSecondary,
    onSecondary = Color(0xFF141B19),
    secondaryContainer = StudioSurfaceMutedDark,
    onSecondaryContainer = AlabasterInk,
    tertiary = MotoraAmberBright,
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = MotoraAmberContainerDark,
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = StudioCanvasDark,
    onBackground = AlabasterInk,
    surface = StudioSurfaceDark,
    onSurface = AlabasterInk,
    surfaceVariant = StudioSurfaceMutedDark,
    onSurfaceVariant = SilverSecondary,
    surfaceContainerLowest = StudioCanvasDark,
    surfaceContainerLow = StudioSurfaceDark,
    surfaceContainer = StudioSurfaceElevatedDark,
    surfaceContainerHigh = StudioSurfaceMutedDark,
    outline = StudioOutlineDark,
    outlineVariant = StudioOutlineDark.copy(alpha = 0.6f),
    error = MotoraCrimsonBright,
    onError = Color(0xFF3E0A0A),
    errorContainer = MotoraCrimsonContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

val MotoraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val BrandtEditorialLightColorScheme = MotoraLightColorScheme.copy(
    primary = BrandtAuthInk,
    onPrimary = BrandtPureWhite,
    primaryContainer = BrandtMintAction,
    onPrimaryContainer = BrandtAuthInk,
    secondary = BrandtAuthMutedInk,
    onSecondary = BrandtPureWhite,
    secondaryContainer = BrandtFormInactiveTab,
    onSecondaryContainer = BrandtAuthInk,
    background = BrandtPureWhite,
    onBackground = BrandtAuthInk,
    surface = BrandtPureWhite,
    onSurface = BrandtAuthInk,
    surfaceVariant = BrandtFormPlaceholderSurface,
    onSurfaceVariant = BrandtAuthInk,
    surfaceContainerLowest = BrandtPureWhite,
    surfaceContainerLow = BrandtPureWhite,
    surfaceContainer = BrandtFormPlaceholderSurface,
    surfaceContainerHigh = BrandtFormInactiveTab,
    outline = BrandtFormHairlineBorder,
    outlineVariant = BrandtFormHairlineBorder
)

@Composable
fun BrandtEditorialLightTheme(
    content: @Composable () -> Unit
) {
    val activeLanguage by LanguagePreferenceStore.currentLanguage.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppLanguage provides activeLanguage) {
        MaterialTheme(
            colorScheme = BrandtEditorialLightColorScheme,
            typography = MotoraTypography,
            shapes = MotoraShapes,
            content = content
        )
    }
}

private val DiagnosticsSurfaceGray = Color(0xFFF3F4F6)
private val DiagnosticsNestedGray = Color(0xFFE5E7EB)
private val DiagnosticsBorderGray = Color(0xFF4B5563)
private val DiagnosticsOutlineGray = Color(0xFF1F2937)

private val BrandtDiagnosticsLightColorScheme = MotoraLightColorScheme.copy(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1D5DB),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF1F2937),
    onSecondary = Color.White,
    secondaryContainer = DiagnosticsNestedGray,
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = DiagnosticsSurfaceGray,
    onSurface = Color.Black,
    surfaceVariant = DiagnosticsNestedGray,
    onSurfaceVariant = Color(0xFF111827),
    surfaceContainerLowest = DiagnosticsSurfaceGray,
    surfaceContainerLow = DiagnosticsNestedGray,
    surfaceContainer = DiagnosticsSurfaceGray,
    surfaceContainerHigh = DiagnosticsNestedGray,
    outline = DiagnosticsOutlineGray,
    outlineVariant = DiagnosticsBorderGray,
    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color.Black
)

@Composable
fun BrandtDiagnosticsLightTheme(
    content: @Composable () -> Unit
) {
    val activeLanguage by LanguagePreferenceStore.currentLanguage.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppLanguage provides activeLanguage) {
        MaterialTheme(
            colorScheme = BrandtDiagnosticsLightColorScheme,
            typography = MotoraTypography,
            shapes = MotoraShapes,
            content = content
        )
    }
}

@Composable
fun MotoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MotoraDarkColorScheme else MotoraLightColorScheme
    val activeLanguage by LanguagePreferenceStore.currentLanguage.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalAppLanguage provides activeLanguage) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MotoraTypography,
            shapes = MotoraShapes,
            content = content
        )
    }
}
