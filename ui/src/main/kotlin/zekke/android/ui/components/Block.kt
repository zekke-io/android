package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke

@Composable
fun Block(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    Column(modifier.fillMaxWidth()) {
        if (title != null || subtitle != null || actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.ink, modifier = Modifier.semantics { heading() })
                    }
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (actions != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
        content()
    }
}

@Composable
fun ItemRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    metaIsValue: Boolean = false,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    card: Boolean = false,
    avatar: String? = null,
    inkIcon: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = Zekke.palette
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (card) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (card) Modifier.clip(shape).background(palette.surface, shape) else Modifier)
                .heightIn(min = if (card) 72.dp else 64.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = if (card) 16.dp else 0.dp, vertical = 12.dp),
        ) {
            if (leading != null) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { leading() }
            } else if (avatar != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).background(palette.brandTint, CircleShape),
                ) {
                    Text(avatar.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = palette.brandText)
                }
            } else if (icon != null) {
                val inkFill = if (palette.isDark) palette.accent else palette.ink
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(if (card) 44.dp else 40.dp).background(
                        if (inkIcon) inkFill else palette.brandTint,
                        if (inkIcon) RoundedCornerShape(14.dp) else if (card) CircleShape else MaterialTheme.shapes.small,
                    ),
                ) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = if (inkIcon) (if (palette.isDark) Color.White else palette.ground) else palette.brandText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta != null) {
                    Text(
                        meta,
                        style = if (metaIsValue) MonoStyle else MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }
        if (divider && !card) HorizontalDivider(color = palette.line)
    }
}
