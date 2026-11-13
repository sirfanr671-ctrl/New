package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CsiDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = SapphireBlue,
    onSecondary = Color.White,
    secondaryContainer = ObsidianSurfaceVariant,
    onSecondaryContainer = SoftIceText,
    tertiary = AuroraMint,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = SoftIceText,
    surface = ObsidianSurface,
    onSurface = SoftIceText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MutedSlateText,
    outline = ObsidianOutline,
    error = CoralError,
    onError = ObsidianBg,
    errorContainer = DeepErrorContainer,
    onErrorContainer = CoralError
)

private val CsiLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDF0FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnBg,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44546E),
    outline = LightOutline,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun CsiVoiceTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CsiDarkColorScheme else CsiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CsiTypography,
        content = content
    )
}
