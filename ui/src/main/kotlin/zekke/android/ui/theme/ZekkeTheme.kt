package zekke.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalZekkePalette = staticCompositionLocalOf { LightPalette }

object Zekke {
    val palette: ZekkePalette
        @Composable @ReadOnlyComposable get() = LocalZekkePalette.current
}

@Composable
fun ZekkeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalZekkePalette provides palette) {
        MaterialTheme(colorScheme = palette.colorScheme(), typography = ZekkeTypography, shapes = ZekkeShapes, content = content)
    }
}
