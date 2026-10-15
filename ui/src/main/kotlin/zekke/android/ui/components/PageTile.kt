package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zekke.android.ui.theme.Zekke

private val PageShape = RoundedCornerShape(8.dp)

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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .shadow(if (palette.isDark) 0.dp else 1.dp, PageShape)
                .clip(PageShape)
                .background(palette.surface)
                .border(1.dp, palette.line, PageShape)
                .padding(10.dp),
        ) {
            Text(
                thumbnail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
                color = palette.inkSoft,
                overflow = TextOverflow.Clip,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 1)
    }
}
