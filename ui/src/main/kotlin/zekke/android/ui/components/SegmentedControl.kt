package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

data class Segment<T>(val value: T, val label: String)

@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = Zekke.palette
    Row(
        modifier = modifier
            .semantics { contentDescription = label }
            .selectableGroup()
            .background(palette.raised, CircleShape)
            .border(1.dp, palette.line, CircleShape)
            .padding(4.dp),
    ) {
        segments.forEach { segment ->
            val isSelected = segment.value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp, minWidth = 96.dp)
                    .background(if (isSelected) palette.brand else palette.raised, CircleShape)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(segment.value) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    segment.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) palette.onBrand else palette.inkMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
