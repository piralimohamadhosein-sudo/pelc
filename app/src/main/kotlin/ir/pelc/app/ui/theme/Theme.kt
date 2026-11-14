package ir.pelc.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// pelc brand: gold ink on paper / dark ink theme.
val PelcGold = Color(0xFFD4AF37)
val PelcGoldDeep = Color(0xFF8A5A00)
val PelcInk = Color(0xFF101418)
val PelcPaper = Color(0xFFF7F3EA)

private val LightColors = lightColorScheme(
    primary = PelcGoldDeep,
    onPrimary = Color.White,
    secondary = PelcGold,
    background = PelcPaper,
    onBackground = PelcInk,
    surface = Color(0xFFFFFDF7),
    onSurface = PelcInk,
    surfaceVariant = Color(0xFFEFE8D8),
    onSurfaceVariant = Color(0xFF4A4437),
    primaryContainer = Color(0xFFFFE9B8),
    onPrimaryContainer = Color(0xFF2C2308),
)

private val DarkColors = darkColorScheme(
    primary = PelcGold,
    onPrimary = Color(0xFF1C1B16),
    secondary = PelcGoldDeep,
    background = Color(0xFF0E0F11),
    onBackground = Color(0xFFE8E2D4),
    surface = Color(0xFF16181B),
    onSurface = Color(0xFFE8E2D4),
    surfaceVariant = Color(0xFF23262B),
    onSurfaceVariant = Color(0xFFC7BFA8),
    primaryContainer = Color(0xFF3A2F0B),
    onPrimaryContainer = Color(0xFFFFE9B8),
)

@Composable
fun PelcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
