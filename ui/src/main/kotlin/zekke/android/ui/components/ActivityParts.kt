package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

class PillTab<T>(val value: T, val label: String, val count: Int = 0)

@Composable
fun <T> PillTabs(tabs: List<PillTab<T>>, selected: T, onSelect: (T) -> Unit, label: String, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    val inkFill = if (palette.isDark) palette.accent else palette.ink
    val inkText = if (palette.isDark) Color.White else palette.ground
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .selectableGroup()
            .background(palette.raised, RoundedCornerShape(28.dp))
            .padding(4.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.value == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) inkFill else Color.Transparent, CircleShape)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab.value) },
            ) {
                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = if (isSelected) inkText else palette.ink)
                if (tab.count > 0) {
                    Text(
                        tab.count.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = palette.onBrand,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .background(palette.brand, CircleShape)
                            .padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    title: String,
    body: String,
    age: String,
    tone: NoticeTone,
    unread: Boolean,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = if (tone == NoticeTone.Success) R.drawable.ic_check else R.drawable.ic_bell,
) {
    val palette = Zekke.palette
    val unreadLabel = stringResource(R.string.alert_unread)
    val shape = RoundedCornerShape(24.dp)
    val loud = tone == NoticeTone.Warning || tone == NoticeTone.Danger
    if (loud) {
        val colors = if (tone == NoticeTone.Danger) palette.danger else palette.warning
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxWidth().background(colors.background, shape).padding(18.dp),
        ) {
            Icon(painterResource(R.drawable.ic_warn), contentDescription = null, tint = colors.text, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Black), color = colors.text)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodySmall, color = colors.text)
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = modifier.fillMaxWidth().background(palette.surface, shape).padding(16.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(palette.raised, RoundedCornerShape(14.dp))) {
                Icon(painterResource(icon), contentDescription = null, tint = palette.brandText, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Black), color = palette.ink)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
                if (age.isNotEmpty()) Text(age, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
            }
            if (unread) {
                Box(Modifier.padding(top = 6.dp).size(10.dp).background(palette.brand, CircleShape).semantics { contentDescription = unreadLabel })
            }
        }
    }
}

@Composable
fun ActivityRow(
    @DrawableRes icon: Int,
    title: String,
    kind: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Zekke.palette
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface, shape)
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).background(palette.raised, RoundedCornerShape(14.dp))) {
            Icon(painterResource(icon), contentDescription = null, tint = palette.brandText, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(kind, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted, maxLines = 1)
        }
        Text(time, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = palette.inkMuted, maxLines = 1)
    }
}
