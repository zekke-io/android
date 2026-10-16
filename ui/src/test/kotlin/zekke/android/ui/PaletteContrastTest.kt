package zekke.android.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.ui.theme.DarkPalette
import zekke.android.ui.theme.LightPalette
import zekke.android.ui.theme.ZekkePalette
import kotlin.math.pow

class PaletteContrastTest {
    private fun channel(value: Float): Double = if (value <= 0.03928f) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun textPairs(palette: ZekkePalette) = mapOf(
        "ink on ground" to (palette.ink to palette.ground),
        "ink on surface" to (palette.ink to palette.surface),
        "ink soft on surface" to (palette.inkSoft to palette.surface),
        "ink muted on surface" to (palette.inkMuted to palette.surface),
        "ink muted on ground" to (palette.inkMuted to palette.ground),
        "label on the brand button" to (palette.onBrand to palette.brand),
        "brand text on surface" to (palette.brandText to palette.surface),
        "brand text on its tint" to (palette.brandText to palette.brandTint),
        "success" to (palette.success.text to palette.success.background),
        "warning" to (palette.warning.text to palette.warning.background),
        "danger" to (palette.danger.text to palette.danger.background),
        "danger on surface" to (palette.danger.text to palette.surface),
        "neutral badge" to (palette.inkMuted to palette.raised),
        "brand text on ground" to (palette.brandText to palette.ground),
    )

    private fun markPairs(palette: ZekkePalette) = mapOf(
        "brand mark on its tint" to (palette.brandMark to palette.brandTint),
        "focus ring on ground" to (palette.focusRing to palette.ground),
        "focus ring on surface" to (palette.focusRing to palette.surface),
        "stored bytes on the reservation" to (palette.brandMark to palette.brandSoft),
    )

    @Test
    fun everyTextColourReadsAtTheAaLevelInBothThemes() {
        for (palette in listOf(LightPalette, DarkPalette)) {
            for ((name, pair) in textPairs(palette)) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("${if (palette.isDark) "dark" else "light"}: $name is ${"%.2f".format(ratio)}:1", ratio >= 4.5)
            }
        }
    }

    @Test
    fun everyMarkThatCarriesMeaningReadsAtThreeToOneInBothThemes() {
        for (palette in listOf(LightPalette, DarkPalette)) {
            for ((name, pair) in markPairs(palette)) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("${if (palette.isDark) "dark" else "light"}: $name is ${"%.2f".format(ratio)}:1", ratio >= 3.0)
            }
        }
    }
}
