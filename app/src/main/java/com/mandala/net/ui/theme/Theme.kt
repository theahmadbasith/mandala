package com.mandala.net.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mandala.net.CyberTheme

private val DarkColorScheme = darkColorScheme(
    background = Color(0xFF000000), // Pure AMOLED black
    surface = Color(0xFF0C0C0C),    // Ultra dark surface
    surfaceVariant = Color(0xFF2A374A), // High-visibility slate-blue dark border
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF00325B), // Dark text on light blue accent
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB0BEC5)
)

private val LightColorScheme = lightColorScheme(
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    onBackground = Color(0xFF001D35),
    onSurface = Color(0xFF001D35),
    onSurfaceVariant = Color(0xFF5A6E85)
)

@Composable
fun MandalaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    // Update CyberTheme to be in sync (though we should migrate away from it, for compatibility let's set it here)
    CyberTheme.isDark = darkTheme
    CyberTheme.isAmoled = darkTheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
