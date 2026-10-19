package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

private val PageShape = RoundedCornerShape(22.dp)

@Composable
fun PageTile(
    title: String,
    thumbnail: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Zekke.palette
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(PageShape)
            .background(palette.surface, PageShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (thumbnail.isNotBlank()) {
                Text(thumbnail, style = MaterialTheme.typography.bodySmall, color = palette.inkSoft, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(caption, style = MaterialTheme.typography.labelSmall, color = palette.inkMuted, maxLines = 1)
    }
}
