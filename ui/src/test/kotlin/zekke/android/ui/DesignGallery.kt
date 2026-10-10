package zekke.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.CopyButton
import zekke.android.ui.components.PinField
import zekke.android.ui.components.SecretField
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke

@Composable
private fun Swatch(name: String, color: Color) {
    val palette = Zekke.palette
    Column(Modifier.padding(end = 8.dp, bottom = 8.dp)) {
        Box(Modifier.size(56.dp, 36.dp).background(color, MaterialTheme.shapes.small).border(1.dp, palette.line, MaterialTheme.shapes.small))
        Text(name, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
    }
}

@Composable
fun DesignGallery() {
    val palette = Zekke.palette
    Column(
        modifier = Modifier.fillMaxWidth().background(palette.ground).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Zekke", style = MaterialTheme.typography.headlineLarge, color = palette.ink)
        Text("Your encrypted vault, drive and passwords.", style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft)
        FlowRow {
            Swatch("brand", palette.brand)
            Swatch("accent", palette.accent)
            Swatch("ground", palette.ground)
            Swatch("surface", palette.surface)
            Swatch("raised", palette.raised)
            Swatch("line", palette.line)
            Swatch("ink", palette.ink)
            Swatch("muted", palette.inkMuted)
        }
        Column(
            Modifier.fillMaxWidth().background(palette.surface, MaterialTheme.shapes.large).border(1.dp, palette.line, MaterialTheme.shapes.large).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bank account", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Text("Updated 2 hours ago", style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            SecretField(value = "correct horse battery", onValueChange = {}, label = "Value", modifier = Modifier.fillMaxWidth())
            Text("8D6B-1F7E-2C4A-4F1B-9E3D-5A7C", style = MonoStyle, color = palette.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge("Encrypted", Tone.Success)
                StatusBadge("Pending", Tone.Warning)
                StatusBadge("Removed", Tone.Danger)
                StatusBadge("Paranoid", Tone.Brand)
            }
        }
        PinField(value = "1234", onValueChange = {}, label = "Your PIN", modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZekkeButton("Unlock", onClick = {})
            ZekkeButton("Cancel", onClick = {}, variant = ButtonVariant.Secondary)
            ZekkeButton("Learn more", onClick = {}, variant = ButtonVariant.Ghost)
            ZekkeButton("Delete", onClick = {}, variant = ButtonVariant.Danger)
            CopyButton(label = "Copy", value = { "" })
        }
    }
}
