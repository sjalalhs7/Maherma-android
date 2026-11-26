package com.maherma.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object OrbColors {
    val Indigo = Color(0xFF5B6CFF)
    val Violet = Color(0xFFA66BFF)
    val Saffron = Color(0xFFFFB454)
    val Offline = Color(0xFF8A8FA3)
}

private val Dark = darkColorScheme(
    primary = Color(0xFF9AA5FF), onPrimary = Color(0xFF0C0E18),
    primaryContainer = Color(0xFF2A3070), onPrimaryContainer = Color(0xFFDDE0FF),
    secondary = Color(0xFFFFB454), onSecondary = Color(0xFF3A2300),
    secondaryContainer = Color(0xFF5A3A00), onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFD2B4FF),
    background = Color(0xFF0C0E18), onBackground = Color(0xFFE6E7F2),
    surface = Color(0xFF0C0E18), onSurface = Color(0xFFE6E7F2),
    surfaceVariant = Color(0xFF2A2D40), onSurfaceVariant = Color(0xFFC4C6D8),
    error = Color(0xFFFFB4AB),
)

private val Light = lightColorScheme(
    primary = Color(0xFF3F4FD8), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE0FF), onPrimaryContainer = Color(0xFF0A1260),
    secondary = Color(0xFF8A5100), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0), onSecondaryContainer = Color(0xFF2C1700),
    tertiary = Color(0xFF7A45C8),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF14162B),
    surface = Color(0xFFFBF8F3), onSurface = Color(0xFF14162B),
    surfaceVariant = Color(0xFFE6E3EC), onSurfaceVariant = Color(0xFF47495C),
    error = Color(0xFFBA1A1A),
)

private val MahermaTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun MahermaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = MahermaTypography, content = content)
}
