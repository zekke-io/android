package zekke.android.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import zekke.android.ui.clipboard.SensitiveClipboard
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.Notice
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.SecretField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.folders.HOME_FOLDER_NAME

@Composable
fun SecretSheets(state: VaultState, viewModel: VaultViewModel, itemIds: List<String>, onShare: ((String) -> Unit)? = null) {
    AddSecretSheet(state, viewModel)
    DetailSheet(state, viewModel, itemIds, onShare)
    if (state.confirmingDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.vault_delete_title),
            message = stringResource(R.string.vault_delete_message),
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete,
            confirmLabel = stringResource(R.string.vault_delete_confirm),
            busy = state.busy,
        )
    }
}

@Composable
private fun AddSecretSheet(state: VaultState, viewModel: VaultViewModel) {
    val form = state.adding ?: return
    val value = remember { FocusRequester() }
    FormSheet(
        title = stringResource(R.string.vault_add_title),
        submitLabel = stringResource(if (state.busy) R.string.vault_saving else R.string.vault_add_submit),
        onSubmit = viewModel::add,
        onDismiss = viewModel::closeAdd,
        canSubmit = form.complete,
        busy = state.busy,
    ) {
        Text(stringResource(R.string.vault_add_subtitle), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        PrivateField(
            value = form.name,
            onValueChange = viewModel::nameChanged,
            label = stringResource(R.string.vault_field_name),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = { value.requestFocus() },
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = form.value,
            onValueChange = viewModel::valueChanged,
            label = stringResource(R.string.vault_field_value),
            singleLine = false,
            modifier = Modifier.fillMaxWidth().focusRequester(value),
        )
    }
}

@Composable
private fun DetailSheet(state: VaultState, viewModel: VaultViewModel, itemIds: List<String>, onShare: ((String) -> Unit)?) {
    val row = state.rows.firstOrNull { it.id == state.detail } ?: return
    val context = LocalContext.current
    val clipboard = remember(context) { SensitiveClipboard(context.applicationContext) }
    val tabs = viewModel.tabs.tabs(itemIds)
    val active = viewModel.tabs.active(itemIds)
    FormSheet(
        title = row.name,
        submitLabel = stringResource(R.string.vault_copy),
        onSubmit = {
            clipboard.copy(row.name, row.value)
            viewModel.closeDetail()
        },
        onDismiss = viewModel::closeDetail,
    ) {
        if (tabs.size >= 2) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.vault_detail_move), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.ink)
                tabs.filter { it.id != active }.forEach { tab ->
                    val name = if (tab.home && tab.name == HOME_FOLDER_NAME) stringResource(zekke.android.feature.tabs.R.string.tabs_home) else tab.name
                    ZekkeButton(
                        stringResource(R.string.vault_detail_move_to, name),
                        { viewModel.moveTo(row, tab.id) },
                        Modifier.fillMaxWidth(),
                        ButtonVariant.Secondary,
                    )
                }
            }
        }
        if (onShare != null) {
            ZekkeButton(
                stringResource(R.string.vault_detail_share),
                {
                    viewModel.closeDetail()
                    onShare(row.id)
                },
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
            )
        }
        if (state.canDelete) {
            ZekkeButton(stringResource(R.string.vault_detail_delete), { viewModel.askDelete(row) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
        }
    }
}
