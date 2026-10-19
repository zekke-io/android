package zekke.android.feature.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import zekke.android.ui.components.FileKind
import zekke.android.ui.components.FileKindIcon
import zekke.android.ui.components.fileKindOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.formatBytes
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.trash.TrashEntry
import zekke.core.trash.TrashKind

fun daysLeft(deletedAt: String, retentionDays: Int, now: Instant = Instant.now()): Int {
    val moment = runCatching { Instant.parse(deletedAt) }.getOrNull() ?: return 0
    val expires = moment.plus(Duration.ofDays(retentionDays.toLong()))
    val seconds = Duration.between(now, expires).seconds
    return if (seconds <= 0) 0 else ((seconds + 86_399) / 86_400).toInt()
}

@Composable
fun TrashPage(state: DriveState, viewModel: DriveViewModel) {
    val palette = Zekke.palette
    val entries = state.trash
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.titleLarge, color = palette.ink)
        val retention = state.retentionDays
        if (retention != null) {
            Notice(
                if (retention <= 0) stringResource(R.string.trash_notice_none) else pluralStringResource(R.plurals.trash_notice, retention, retention),
                tone = NoticeTone.Info,
            )
        }
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        ZekkeButton(stringResource(R.string.trash_back), viewModel::hideTrash, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        when {
            entries == null -> LoadingIndicator()
            entries.isEmpty() -> Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
            else -> {
                ZekkeButton(stringResource(R.string.trash_restore_all), { viewModel.restore(entries) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !state.busy)
                if (state.canDelete) {
                    ZekkeButton(stringResource(R.string.trash_purge_all), { viewModel.askPurge(entries) }, Modifier.fillMaxWidth(), ButtonVariant.Danger, enabled = !state.busy)
                } else {
                    Text(stringResource(R.string.trash_full_device_only), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                }
                entries.forEach { entry -> TrashRow(entry, state, viewModel) }
            }
        }
    }
    state.confirmingPurge?.let { chosen ->
        ConfirmDeleteDialog(
            title = if (chosen.size == 1) stringResource(R.string.trash_purge_title_one) else pluralStringResource(R.plurals.trash_purge_title_many, chosen.size, chosen.size),
            message = stringResource(R.string.trash_purge_warning),
            onDismiss = viewModel::cancelPurge,
            onConfirm = viewModel::confirmPurge,
            confirmLabel = stringResource(R.string.trash_purge),
            busy = state.busy,
        )
    }
}

@Composable
private fun TrashRow(entry: TrashEntry, state: DriveState, viewModel: DriveViewModel) {
    val retention = state.retentionDays
    val days = if (retention != null && retention > 0) daysLeft(entry.deletedAt, retention) else null
    val detail = when {
        entry.kind == TrashKind.FOLDER -> stringResource(R.string.trash_folder_detail, pluralStringResource(R.plurals.drive_files, entry.itemCount ?: 0, entry.itemCount ?: 0))
        else -> stringResource(R.string.trash_detail, stringResource(R.string.trash_kind_file), entry.sizeBytes?.let { formatBytes(it) } ?: "")
    }
    val expiry = when {
        days == null -> null
        days <= 0 -> stringResource(R.string.trash_left_today)
        else -> pluralStringResource(R.plurals.trash_left, days, days)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemRow(
            title = entry.name ?: stringResource(R.string.trash_unnamed),
            meta = listOfNotNull(detail, expiry).joinToString(" · "),
            leading = {
                FileKindIcon(if (entry.kind == TrashKind.FOLDER) FileKind.Folder else fileKindOf(entry.name), height = 36.dp)
            },
            divider = false,
        )
        ZekkeButton(stringResource(R.string.trash_restore), { viewModel.restore(listOf(entry)) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !state.busy)
        if (state.canDelete) {
            ZekkeButton(stringResource(R.string.trash_purge), { viewModel.askPurge(listOf(entry)) }, Modifier.fillMaxWidth(), ButtonVariant.Danger, enabled = !state.busy)
        }
    }
}
