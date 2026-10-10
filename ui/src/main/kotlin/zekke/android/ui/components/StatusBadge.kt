package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.ToneColors
import zekke.android.ui.theme.Zekke

enum class Tone { Neutral, Brand, Success, Warning, Danger }

@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    val colors = when (tone) {
        Tone.Success -> palette.success
        Tone.Warning -> palette.warning
        Tone.Danger -> palette.danger
        Tone.Brand -> ToneColors(palette.brandText, palette.brandTint, palette.brandTintLine)
        Tone.Neutral -> ToneColors(palette.inkMuted, palette.raised, palette.line)
    }
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.text,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(colors.background, CircleShape)
            .border(1.dp, colors.line, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
