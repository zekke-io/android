package zekke.android.feature.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zekke.android.ui.clipboard.CLIPBOARD_CLEAR_AFTER_MILLIS
import zekke.android.ui.clipboard.SensitiveClipboard
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FloatingAddButton
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.core.credentials.PasswordRow
import zekke.core.credentials.siteLabel

private const val COPIED_NOTICE_MILLIS = 3_000L

@Composable
fun PasswordsScreen(state: PasswordsState, viewModel: PasswordsViewModel) {
    val context = LocalContext.current
    val clipboard = remember(context) { SensitiveClipboard(context.applicationContext) }
    var copiedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedFor) {
        if (copiedFor != null) {
            delay(COPIED_NOTICE_MILLIS)
            copiedFor = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (state.showingDeleted) {
            DeletedPasswords(state, viewModel)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.failure?.let {
                            Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure)
                        }
                        if (copiedFor != null) {
                            Notice(
                                stringResource(zekke.android.ui.R.string.copy_done, CLIPBOARD_CLEAR_AFTER_MILLIS / 1000),
                                tone = NoticeTone.Success,
                            )
                        }
                        PrivateField(
                            value = state.query,
                            onValueChange = viewModel::queryChanged,
                            label = stringResource(R.string.passwords_search),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZekkeButton(
                            stringResource(R.string.passwords_recently_deleted),
                            viewModel::toggleDeleted,
                            Modifier.fillMaxWidth(),
                            ButtonVariant.Secondary,
                        )
                    }
                }
                when {
                    !state.loaded || (!state.synced && state.rows.isEmpty()) -> item { LoadingIndicator() }
                    state.rows.isEmpty() -> item {
                        EmptyState(stringResource(R.string.passwords_empty), icon = zekke.android.ui.R.drawable.ic_passwords)
                    }
                    state.visible.isEmpty() -> item { EmptyState(stringResource(R.string.passwords_no_match)) }
                    else -> items(state.visible, key = { it.id }) { row ->
                        PasswordItem(row, onOpen = { viewModel.startEdit(row) }, onCopy = {
                            clipboard.copy(row.site, row.password)
                            copiedFor = row.id
                        })
                    }
                }
            }
            FloatingAddButton(
                stringResource(R.string.passwords_add),
                viewModel::startAdd,
                Modifier.align(Alignment.BottomEnd).padding(24.dp),
                enabled = !state.busy,
            )
        }
    }
    PasswordFormSheet(state, viewModel)
    if (state.confirmingDelete != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.password_delete_title),
            message = stringResource(if (state.canPurge) R.string.password_delete_message else R.string.password_delete_message_not_full),
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete,
            confirmLabel = stringResource(R.string.password_delete_confirm),
            busy = state.busy,
        )
    }
}

@Composable
private fun PasswordItem(row: PasswordRow, onOpen: () -> Unit, onCopy: () -> Unit) {
    val title = if (row.readable) siteLabel(row.site) else stringResource(R.string.passwords_unreadable)
    ItemRow(
        title = title,
        meta = row.username.ifEmpty { null },
        icon = zekke.android.ui.R.drawable.ic_passwords,
        onClick = if (row.readable) onOpen else null,
        trailing = {
            if (row.readable) {
                ZekkeIconButton(
                    zekke.android.ui.R.drawable.ic_clipboard,
                    hint = stringResource(R.string.passwords_copy),
                    label = stringResource(R.string.passwords_copy) + " · " + title,
                    onClick = onCopy,
                )
            }
        },
    )
}
