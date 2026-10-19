package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zekke.android.ui.theme.Zekke

class BottomBarItem(
    @DrawableRes val icon: Int,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val description: String = label,
    val badge: String? = null,
)

@Composable
fun BottomBar(items: List<BottomBarItem>, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    Column(modifier.fillMaxWidth().background(palette.surface)) {
        HorizontalDivider(color = palette.line, thickness = 2.dp)
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            items.forEach { item ->
                val color = if (item.selected) palette.brandMark else palette.inkSoft
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .clickable(role = Role.Tab, onClick = item.onClick)
                        .semantics(mergeDescendants = true) {
                            contentDescription = item.description
                            selected = item.selected
                        },
                ) {
                    Box {
                        Icon(painterResource(item.icon), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
                        if (item.badge != null) {
                            Text(
                                item.badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.onBrand,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 10.dp, y = (-6).dp)
                                    .background(palette.brand, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (item.selected) FontWeight.Black else FontWeight.ExtraBold),
                        color = color,
                    )
                }
            }
        }
    }
}
