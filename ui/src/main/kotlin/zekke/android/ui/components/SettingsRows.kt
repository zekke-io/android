package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

private val GroupShape = RoundedCornerShape(24.dp)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.em),
        color = Zekke.palette.brandText,
        modifier = modifier.padding(top = 10.dp).semantics { heading() },
    )
}

@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(GroupShape).background(Zekke.palette.surface, GroupShape), content = content)
}

@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    divider: Boolean = true,
) {
    val palette = Zekke.palette
    Column(modifier) {
        if (divider) HorizontalDivider(color = palette.line, thickness = 2.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(palette.raised, RoundedCornerShape(14.dp)),
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = when {
                        danger -> palette.danger.text
                        enabled -> palette.brandText
                        else -> palette.inkMuted
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = if (danger) FontWeight.Black else FontWeight.ExtraBold),
                color = when {
                    danger -> palette.danger.text
                    enabled -> palette.ink
                    else -> palette.inkMuted
                },
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = palette.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onClick != null && enabled && !danger) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun SettingsProfileCard(initial: String, name: String, tags: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(palette.surface, GroupShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp).background(palette.brand, CircleShape)) {
            Text(initial.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = palette.onBrand)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black), color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEachIndexed { index, tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.em, fontWeight = FontWeight.ExtraBold),
                        color = if (index == 0) palette.brandText else palette.ink,
                        modifier = Modifier.background(palette.raised, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PlanStorageCard(
    reading: StorageReading,
    planLabel: String,
    linkLabel: String,
    onLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Zekke.palette
    val used = formatBytes(reading.usedBytes)
    val quota = formatBytes(reading.quotaBytes)
    val free = androidx.compose.ui.res.stringResource(R.string.storage_free, formatBytes(reading.freeBytes))
    val summary = androidx.compose.ui.res.stringResource(R.string.storage_of_used, used, quota)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().clip(GroupShape).background(palette.surface, GroupShape).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = summary }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(used, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 28.sp), color = palette.ink, modifier = Modifier.alignByBaseline())
                Text(
                    androidx.compose.ui.res.stringResource(R.string.storage_of_quota, quota),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.inkMuted,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                planLabel,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.em, fontWeight = FontWeight.ExtraBold),
                color = palette.brandText,
                modifier = Modifier.background(palette.raised, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 12.dp).clip(CircleShape).background(palette.raised)) {
            Box(Modifier.fillMaxWidth(reading.usedShare.coerceIn(0f, 1f)).heightIn(min = 12.dp).clip(CircleShape).background(if (reading.isAlmostFull) palette.danger.text else palette.brand))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(free, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = palette.inkMuted)
            Text(
                linkLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                color = palette.brandText,
                modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onLink).padding(vertical = 12.dp),
            )
        }
    }
}
