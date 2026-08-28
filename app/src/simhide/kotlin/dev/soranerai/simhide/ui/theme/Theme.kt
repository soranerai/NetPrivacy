package dev.soranerai.simhide.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TelBlue = Color(0xFF4AA8E8)
private val TelCyan = Color(0xFF28BCC8)
private val TelGreen = Color(0xFF43C978)
private val TelRed = Color(0xFFF44336)

// Shared VPN Hide palette: cool dark surfaces with green protection and blue network accents.
private val DarkColors = darkColorScheme(
    primary = TelGreen,
    primaryContainer = Color(0xFF0D4225),
    onPrimaryContainer = Color(0xFFBDF4D1),
    secondary = TelBlue,
    secondaryContainer = Color(0xFF12384F),
    onSecondaryContainer = Color(0xFFCBEAFF),
    tertiary = TelCyan,
    tertiaryContainer = Color(0xFF0E3C40),
    onTertiaryContainer = Color(0xFFC4F5F7),
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF161616),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = TelRed,
    outline = Color(0xFF607079),
    outlineVariant = Color(0xFF29343A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF168A4A),
    primaryContainer = Color(0xFFD5F5E1),
    onPrimaryContainer = Color(0xFF083C21),
    secondary = Color(0xFF256FA6),
    secondaryContainer = Color(0xFFDCEEFF),
    onSecondaryContainer = Color(0xFF123A56),
    tertiary = Color(0xFF087F8C),
    tertiaryContainer = Color(0xFFC9F3F5),
    onTertiaryContainer = Color(0xFF083B40),
    background = Color(0xFFF4F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7EDF1),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF172027),
    onSurface = Color(0xFF172027),
    onSurfaceVariant = Color(0xFF53616B),
    error = TelRed,
    outline = Color(0xFF74818A),
    outlineVariant = Color(0xFFC7D0D7),
)

@Composable
fun SimHideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}
