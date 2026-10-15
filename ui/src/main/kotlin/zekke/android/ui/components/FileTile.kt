package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

private val TileShape = RoundedCornerShape(8.dp)

@Composable
fun FileTile(
    title: String,
    caption: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    more: (@Composable () -> Unit)? = null,
) {
    val palette = Zekke.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(TileShape)
                .background(palette.surface)
                .border(1.dp, palette.line, TileShape),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(painterResource(icon), contentDescription = null, tint = palette.brandText, modifier = Modifier.size(40.dp))
            }
            if (more != null) Box(Modifier.align(Alignment.TopEnd)) { more() }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
