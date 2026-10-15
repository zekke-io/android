package zekke.android.feature.passwords

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
import zekke.core.credentials.siteLabel

@Composable
fun DeletedPasswords(state: PasswordsState, viewModel: PasswordsViewModel) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.deleted_title), style = MaterialTheme.typography.titleLarge, color = palette.ink)
        Text(stringResource(R.string.deleted_subtitle), style = MaterialTheme.typography.bodyMedium, color = palette.inkSoft)
        state.failure?.let {
            Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure)
        }
        ZekkeButton(stringResource(R.string.deleted_close), viewModel::hideDeleted, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        if (state.deleted.isEmpty()) {
            Text(stringResource(R.string.deleted_none), style = MaterialTheme.typography.bodyMedium, color = palette.inkMuted)
        }
        state.deleted.forEach { row ->
            val title = if (row.readable) siteLabel(row.site) else stringResource(R.string.passwords_unreadable)
            Column {
                ItemRow(
                    title = title,
                    meta = listOf(row.username, stringResource(R.string.deleted_when, formatMoment(row.deletedAt))).filter { it.isNotEmpty() }.joinToString(" · "),
                    divider = false,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZekkeButton(stringResource(R.string.deleted_restore), { viewModel.restore(row) }, Modifier.fillMaxWidth(), ButtonVariant.Secondary, enabled = !state.busy)
                    if (state.canPurge) {
                        ZekkeButton(
                            stringResource(R.string.deleted_purge_one),
                            { viewModel.askPurge(listOf(row)) },
                            Modifier.fillMaxWidth(),
                            ButtonVariant.Danger,
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
        if (state.canPurge && state.deleted.size > 1) {
            ZekkeButton(
                stringResource(R.string.deleted_purge_all),
                { viewModel.askPurge(state.deleted) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Danger,
                enabled = !state.busy,
            )
        }
    }
    state.confirmingPurge?.let { rows ->
        ConfirmDeleteDialog(
            title = if (rows.size == 1) stringResource(R.string.purge_title_one) else pluralStringResource(R.plurals.purge_title_many, rows.size, rows.size),
            message = stringResource(R.string.purge_message),
            onDismiss = viewModel::cancelPurge,
            onConfirm = viewModel::confirmPurge,
            confirmLabel = stringResource(if (state.busy) R.string.purge_working else R.string.purge_confirm),
            busy = state.busy,
        )
    }
}
