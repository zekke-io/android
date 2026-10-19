package zekke.android.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ActivityRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.PillTab
import zekke.android.ui.components.PillTabs
import zekke.android.ui.theme.Zekke

enum class ActivityTab { Alerts, Recent }

@DrawableRes
private fun iconOf(kind: RecentKind): Int = when (kind) {
    RecentKind.Note -> zekke.android.ui.R.drawable.ic_app_notes
    RecentKind.Password -> zekke.android.ui.R.drawable.ic_app_passwords
    RecentKind.Secret -> zekke.android.ui.R.drawable.ic_app_secrets
    RecentKind.File -> zekke.android.ui.R.drawable.ic_app_drive
}

@Composable
private fun kindLabel(kind: RecentKind): String = stringResource(
    when (kind) {
        RecentKind.Note -> R.string.recent_kind_note
        RecentKind.Password -> R.string.recent_kind_password
        RecentKind.Secret -> R.string.recent_kind_secret
        RecentKind.File -> R.string.recent_kind_file
    },
)

@Composable
fun ActivityScreen(
    unreadAlerts: Int,
    alerts: @Composable () -> Unit,
    recent: RecentState,
    onRecentShown: () -> Unit,
    onOpenRecent: (RecentKind) -> Unit,
    clock: NotificationClock = NotificationClock(),
) {
    var tab by rememberSaveable { mutableStateOf(ActivityTab.Alerts) }
    val resources = LocalResources.current
    LaunchedEffect(tab) { if (tab == ActivityTab.Recent) onRecentShown() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PillTabs(
            tabs = listOf(
                PillTab(ActivityTab.Alerts, stringResource(R.string.activity_alerts), unreadAlerts),
                PillTab(ActivityTab.Recent, stringResource(R.string.activity_recent)),
            ),
            selected = tab,
            onSelect = { tab = it },
            label = stringResource(R.string.activity_title),
        )
        when (tab) {
            ActivityTab.Alerts -> alerts()
            ActivityTab.Recent -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !recent.loaded -> LoadingIndicator()
                    recent.entries.isEmpty() -> Text(
                        stringResource(R.string.recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Zekke.palette.inkMuted,
                    )
                    else -> recent.entries.forEach { entry ->
                        ActivityRow(
                            icon = iconOf(entry.kind),
                            title = entry.title,
                            kind = kindLabel(entry.kind),
                            time = activityAge(entry.updatedAt, resources, clock),
                            onClick = { onOpenRecent(entry.kind) },
                        )
                    }
                }
            }
        }
    }
}
