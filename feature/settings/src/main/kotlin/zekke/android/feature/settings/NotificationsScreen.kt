package zekke.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke

@Composable
fun NotificationsScreen(state: NotificationsState, viewModel: NotificationsViewModel, clock: NotificationClock = NotificationClock()) {
    LaunchedEffect(Unit) { viewModel.open() }
    val resources = LocalResources.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.problem?.let { problem ->
            Notice(
                when (problem) {
                    NotificationsProblem.Offline -> stringResource(R.string.notif_offline)
                    NotificationsProblem.RateLimited -> rateLimitedMessage(state.retryAfterSeconds)
                    NotificationsProblem.Failed -> stringResource(R.string.notif_failed)
                },
                tone = if (problem == NotificationsProblem.Failed) NoticeTone.Danger else NoticeTone.Warning,
            )
            ZekkeButton(stringResource(R.string.notif_try_again), viewModel::retry, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        }
        if (state.loadedOnce && state.records.isEmpty()) {
            Text(stringResource(R.string.notif_empty), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
        }
        state.records.forEach { record ->
            val view = notificationView(record, resources, clock).copy(unread = record.id in state.unreadIds)
            NotificationItem(view)
        }
        if (state.loading) LoadingIndicator()
        if (state.nextCursor != null && !state.loading) {
            ZekkeButton(stringResource(R.string.notif_load_more), viewModel::showOlder, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        }
    }
}

@Composable
private fun NotificationItem(view: NotificationView) {
    val palette = Zekke.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Notice(view.title + if (view.unread) " · " + stringResource(R.string.notif_unread_marker) else "", tone = view.tone)
        Text(view.body, style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
        if (view.age.isNotEmpty()) Text(view.age, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}
