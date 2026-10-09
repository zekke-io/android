package zekke.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4648D4),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF121C2A),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF121C2A),
)

@Composable
fun ZekkeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
