package zekke.android.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import zekke.android.feature.tabs.TabNouns
import zekke.android.feature.tabs.TabsBar
import zekke.android.ui.clipboard.CLIPBOARD_CLEAR_AFTER_MILLIS
import zekke.android.ui.clipboard.SensitiveClipboard
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FloatingAddButton
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.ZekkeIconToggleButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.core.secrets.VaultRow

private const val COPIED_NOTICE_MILLIS = 3_000L

@Composable
fun VaultScreen(state: VaultState, viewModel: VaultViewModel) {
    val context = LocalContext.current
    val clipboard = remember(context) { SensitiveClipboard(context.applicationContext) }
    var copiedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedFor) {
        if (copiedFor != null) {
            delay(COPIED_NOTICE_MILLIS)
            copiedFor = null
        }
    }
    val itemIds = state.rows.map { it.id }
    val tabState by viewModel.tabs.state.collectAsState()
    val visible = if (tabState.manifest == null) state.rows else viewModel.tabs.filter(state.rows) { it.id }
    val nouns = TabNouns(stringResource(R.string.vault_nouns_one), stringResource(R.string.vault_nouns_many))
    Box(Modifier.fillMaxSize()) {
        if (state.showingDeleted) {
            DeletedSecrets(state, viewModel)
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
                        TabsBar(viewModel.tabs, itemIds, nouns, viewModel::deleteTabItems)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ZekkeButton(
                                stringResource(R.string.vault_recently_deleted),
                                viewModel::toggleDeleted,
                                Modifier.weight(1f),
                                ButtonVariant.Secondary,
                            )
                            ZekkeIconToggleButton(
                                icon = if (state.revealed) zekke.android.ui.R.drawable.ic_eye_off else zekke.android.ui.R.drawable.ic_eye,
                                hint = stringResource(if (state.revealed) R.string.vault_hide_values else R.string.vault_show_values),
                                checked = state.revealed,
                                onCheckedChange = { viewModel.toggleRevealed() },
                            )
                        }
                    }
                }
                when {
                    !state.loaded || (!state.synced && state.rows.isEmpty()) -> item { LoadingIndicator() }
                    state.rows.isEmpty() -> item {
                        EmptyState(stringResource(R.string.vault_empty_all), icon = zekke.android.ui.R.drawable.ic_vault)
                    }
                    visible.isEmpty() -> item { EmptyState(stringResource(R.string.vault_empty_tab)) }
                    else -> items(visible, key = { it.id }) { row ->
                        SecretItem(row, state.revealed, onOpen = { viewModel.open(row) }, onCopy = {
                            clipboard.copy(row.name, row.value)
                            copiedFor = row.id
                        })
                    }
                }
            }
            FloatingAddButton(
                stringResource(R.string.vault_add),
                viewModel::startAdd,
                Modifier.align(Alignment.BottomEnd).padding(24.dp),
                enabled = !state.busy,
            )
        }
    }
    SecretSheets(state, viewModel, itemIds)
}

@Composable
private fun SecretItem(row: VaultRow, revealed: Boolean, onOpen: () -> Unit, onCopy: () -> Unit) {
    val title = if (row.readable) row.name else stringResource(R.string.vault_unreadable)
    ItemRow(
        title = title,
        meta = if (!row.readable) null else if (revealed) row.value else stringResource(R.string.vault_masked),
        metaIsValue = true,
        icon = zekke.android.ui.R.drawable.ic_vault,
        onClick = if (row.readable) onOpen else null,
        trailing = {
            if (row.readable) {
                ZekkeIconButton(
                    zekke.android.ui.R.drawable.ic_clipboard,
                    hint = stringResource(R.string.vault_copy),
                    label = stringResource(R.string.vault_copy) + " · " + title,
                    onClick = onCopy,
                )
            }
        },
    )
}
