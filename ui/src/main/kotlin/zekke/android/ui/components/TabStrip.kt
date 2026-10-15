package zekke.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

data class TabItem(val id: String, val label: String, val count: Int? = null, val manageable: Boolean = true)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabStrip(
    tabs: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: (String) -> Unit,
    modifier: Modifier = Modifier,
    addLabel: String = stringResource(R.string.tab_strip_new),
    manageLabel: String = stringResource(R.string.tab_strip_manage),
    enabled: Boolean = true,
) {
    val palette = Zekke.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .background(if (isSelected) palette.brandTint else palette.surface, CircleShape)
                    .border(1.dp, if (isSelected) palette.brandTintLine else palette.line, CircleShape)
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClickLabel = null,
                        onLongClickLabel = manageLabel,
                        onLongClick = if (tab.manageable) ({ onManage(tab.id) }) else null,
                        onClick = { onSelect(tab.id) },
                    )
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) palette.brandText else palette.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tab.count != null) {
                    Text(tab.count.toString(), style = MaterialTheme.typography.labelSmall, color = palette.inkMuted)
                }
            }
        }
        ZekkeIconButton(R.drawable.ic_plus, hint = addLabel, onClick = onAdd, enabled = enabled)
    }
}
