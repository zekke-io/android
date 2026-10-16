package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

private val OptionShape = RoundedCornerShape(24.dp)

@Composable
fun OptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    tagTone: Tone = Tone.Neutral,
) {
    val palette = Zekke.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) palette.surface else palette.ground, OptionShape)
            .border(3.dp, if (selected) palette.strongBorder else palette.line, OptionShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(20.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(top = 2.dp).size(28.dp).border(3.dp, palette.strongBorder, CircleShape),
        ) {
            if (selected) Box(Modifier.size(12.dp).background(palette.brand, CircleShape))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.ink)
                if (tag != null) StatusBadge(tag, tagTone)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
        }
    }
}
