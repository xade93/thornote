package com.thornotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.thornotes.data.models.AppSettings

private fun darkThorNotesColorScheme(primary: Color) = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    secondary = Color(0xFF4CAF50),
    surface = Color(0xFF1A1A2E),         // Dark navy background
    onSurface = Color(0xFFE0E0E0),       // Light text
    background = Color(0xFF0F0F1A),      // Darker background
    onBackground = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF252540),  // Card backgrounds
    onSurfaceVariant = Color(0xFFB0B0B0),
)

fun themePrimaryColor(themeColor: Int): Color = when (themeColor) {
    AppSettings.THEME_COLOR_AMBER -> Color(0xFFC57A10)
    AppSettings.THEME_COLOR_TEAL -> Color(0xFF148F83)
    AppSettings.THEME_COLOR_VIOLET -> Color(0xFF6D54B8)
    AppSettings.THEME_COLOR_RED -> Color(0xFFB83A3A)
    else -> Color(0xFFC33568)
}

@Composable
fun ThorNotesTheme(
    themeColor: Int = AppSettings.THEME_COLOR_PINK,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkThorNotesColorScheme(themePrimaryColor(themeColor)),
        content = content
    )
}
