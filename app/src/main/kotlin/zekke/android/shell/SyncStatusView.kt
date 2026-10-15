package zekke.android.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.R
import zekke.android.sync.SyncFailure
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke

@Composable
fun SyncStatusView(status: SyncStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        status.failure?.let { failure ->
            Notice(
                when (failure) {
                    SyncFailure.Offline -> stringResource(R.string.sync_offline)
                    is SyncFailure.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
                    SyncFailure.SessionOver -> stringResource(R.string.sync_session_over)
                    SyncFailure.Failed -> stringResource(R.string.sync_failed)
                },
                tone = if (failure == SyncFailure.Failed || failure == SyncFailure.SessionOver) NoticeTone.Danger else NoticeTone.Warning,
            )
        }
        Text(
            when {
                status.syncing -> stringResource(R.string.sync_syncing)
                status.lastSyncEpochSeconds != null -> stringResource(R.string.sync_up_to_date)
                else -> stringResource(R.string.sync_not_yet)
            },
            style = MaterialTheme.typography.bodySmall,
            color = Zekke.palette.inkMuted,
        )
        if (status.outboxPending > 0) {
            Notice(pluralStringResource(R.plurals.outbox_waiting, status.outboxPending, status.outboxPending), tone = NoticeTone.Info)
        }
        if (status.outboxWaiting > 0) {
            Notice(pluralStringResource(R.plurals.outbox_rewrap, status.outboxWaiting, status.outboxWaiting), tone = NoticeTone.Info)
        }
    }
}
