package zekke.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.R
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.Zekke

@Composable
fun SettingsContent(
    status: SyncStatus,
    onOpenNotifications: () -> Unit,
    onDiscard: (Long) -> Unit,
    onOpen: (Destination) -> Unit,
    notices: @Composable () -> Unit,
) {
    val palette = Zekke.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        notices()
        ZekkeButton(
            if (status.unreadNotifications > 0) {
                stringResource(R.string.notifications_open) + " · " +
                    pluralStringResource(R.plurals.notifications_unread, status.unreadNotifications, status.unreadNotifications)
            } else {
                stringResource(R.string.notifications_open)
            },
            onOpenNotifications,
            Modifier.fillMaxWidth(),
            ButtonVariant.Secondary,
        )
        if (status.outboxRefused.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.outbox_refused_title), style = MaterialTheme.typography.titleMedium, color = palette.ink)
                Notice(stringResource(R.string.outbox_refused_summary), tone = NoticeTone.Danger)
                status.outboxRefused.forEach { entry ->
                    ZekkeButton(
                        stringResource(R.string.outbox_discard) + " · " + entry.path,
                        { onDiscard(entry.position) },
                        Modifier.fillMaxWidth(),
                        ButtonVariant.Secondary,
                    )
                }
            }
        }
        ItemRow(
            title = stringResource(R.string.settings_devices),
            meta = stringResource(R.string.settings_devices_meta),
            icon = zekke.android.ui.R.drawable.ic_security,
            onClick = { onOpen(Destination.SettingsDevices) },
        )
        ItemRow(
            title = stringResource(R.string.settings_account),
            meta = stringResource(R.string.settings_account_meta),
            icon = zekke.android.ui.R.drawable.ic_settings,
            onClick = { onOpen(Destination.SettingsAccount) },
        )
    }
}
