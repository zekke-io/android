package zekke.android.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.Notice
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatDeletedMoment(iso: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val moment = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(moment)
}

@Composable
fun DeletedSecrets(state: VaultState, viewModel: VaultViewModel) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.vault_deleted_title), style = MaterialTheme.typography.titleLarge, color = palette.ink)
        Text(stringResource(R.string.vault_deleted_subtitle), style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        ZekkeButton(stringResource(R.string.vault_deleted_close), viewModel::hideDeleted, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        if (state.deleted.isEmpty()) {
            Text(stringResource(R.string.vault_deleted_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        state.deleted.forEach { row ->
            Column {
                ItemRow(
                    title = if (row.readable) row.name else stringResource(R.string.vault_unreadable),
                    meta = stringResource(R.string.vault_deleted_when, formatDeletedMoment(row.deletedAt)),
                    divider = false,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZekkeButton(stringResource(R.string.vault_deleted_restore), { viewModel.restore(row) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !state.busy)
                    if (state.canDelete) {
                        ZekkeButton(
                            stringResource(R.string.vault_deleted_purge_one),
                            { viewModel.askPurge(listOf(row)) },
                            Modifier.fillMaxWidth(),
                            ButtonVariant.Danger,
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
        if (state.canDelete && state.deleted.size > 1) {
            ZekkeButton(
                stringResource(R.string.vault_deleted_purge_all),
                { viewModel.askPurge(state.deleted) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Danger,
                enabled = !state.busy,
            )
        }
    }
    state.confirmingPurge?.let { rows ->
        ConfirmDeleteDialog(
            title = if (rows.size == 1) stringResource(R.string.vault_purge_title_one) else pluralStringResource(R.plurals.vault_purge_title_many, rows.size, rows.size),
            message = stringResource(R.string.vault_purge_message),
            onDismiss = viewModel::cancelPurge,
            onConfirm = viewModel::confirmPurge,
            confirmLabel = stringResource(if (state.busy) R.string.vault_purge_working else R.string.vault_purge_confirm),
            busy = state.busy,
        )
    }
}
