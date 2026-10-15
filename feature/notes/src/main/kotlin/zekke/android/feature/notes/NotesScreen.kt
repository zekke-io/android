package zekke.android.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.feature.tabs.TabNouns
import zekke.android.feature.tabs.TabsBar
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FloatingAddButton
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.NoteSurface
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PageTile
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.notes.NoteLineCommand
import zekke.core.notes.NoteTile
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatNoteDate(iso: String, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val moment = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(moment)
}

@Composable
fun NotesScreen(state: NotesState, viewModel: NotesViewModel, onShare: ((String) -> Unit)? = null) {
    val editor = state.editor
    if (editor != null) {
        BackHandler(onBack = viewModel::close)
        NoteEditor(state, editor, viewModel, onShare)
    } else {
        NotesList(state, viewModel)
    }
}

@Composable
private fun NotesList(state: NotesState, viewModel: NotesViewModel) {
    val tabState by viewModel.tabs.state.collectAsState()
    val itemIds = state.tiles.map { it.id }
    val visible = if (tabState.manifest == null) state.tiles else viewModel.tabs.filter(state.tiles) { it.id }
    val nouns = TabNouns(stringResource(R.string.notes_nouns_one), stringResource(R.string.notes_nouns_many))
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
                    TabsBar(viewModel.tabs, itemIds, nouns, viewModel::deleteSpaceItems)
                    if (state.tiles.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.notes_count, visible.size, visible.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Zekke.palette.inkMuted,
                        )
                    }
                }
            }
            when {
                !state.loaded || (!state.synced && state.tiles.isEmpty()) -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingIndicator() }
                state.tiles.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.notes_empty_all), icon = zekke.android.ui.R.drawable.ic_notes)
                }
                visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.notes_empty_tab)) }
                else -> items(visible, key = { it.id }) { tile -> Tile(tile) { viewModel.open(tile) } }
            }
        }
        FloatingAddButton(
            stringResource(R.string.notes_add),
            viewModel::startNew,
            Modifier.align(Alignment.BottomEnd).padding(24.dp),
        )
    }
}

@Composable
private fun Tile(tile: NoteTile, onOpen: () -> Unit) {
    PageTile(
        title = when {
            !tile.readable -> stringResource(R.string.notes_unreadable)
            else -> tile.title ?: stringResource(R.string.notes_untitled)
        },
        thumbnail = tile.thumbnail,
        caption = formatNoteDate(tile.updatedAt),
        onClick = { if (tile.readable) onOpen() },
    )
}

@Composable
private fun NoteEditor(state: NotesState, editor: NoteEditorState, viewModel: NotesViewModel, onShare: ((String) -> Unit)?) {
    val palette = Zekke.palette
    val model = surfaceModelOf(editor.document)
    val title = zekke.core.notes.noteTitle(editor.draft) ?: stringResource(R.string.notes_untitled)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZekkeIconButton(
                zekke.android.ui.R.drawable.ic_arrow_left,
                hint = stringResource(R.string.notes_back),
                onClick = viewModel::close,
                enabled = !editor.busy,
            )
            Text(title, style = MaterialTheme.typography.titleLarge, color = palette.ink, maxLines = 1, modifier = Modifier.weight(1f))
            if (onShare != null && editor.saved != null && editor.saveState == NoteSaveState.Saved && !editor.unreadable) {
                ZekkeButton(stringResource(R.string.notes_share), { onShare(editor.id) }, variant = ButtonVariant.Secondary, enabled = !editor.busy && !editor.saving)
            }
            if (editor.saved != null && state.canDelete) {
                ZekkeButton(stringResource(R.string.notes_delete), viewModel::askDelete, variant = ButtonVariant.Danger, enabled = !editor.busy && !editor.saving)
            }
        }
        val label = saveLabel(editor.saveState)
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (editor.saveState == NoteSaveState.OverLimit) palette.danger.text else palette.inkMuted)
        }
        editor.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it)) }
        if (editor.unreadable) Notice(stringResource(R.string.notes_unreadable_notice), tone = NoticeTone.Warning)
        Toolbar(editor, viewModel)
        NoteSurface(
            value = editor.value,
            onValueChange = viewModel::valueChanged,
            lines = model.lines,
            spans = model.spans,
            onToggleTask = viewModel::toggleTask,
            enabled = !editor.unreadable && !editor.busy,
            label = stringResource(R.string.notes_body),
        )
        val left = editor.charactersLeft
        Text(
            if (left >= 0) stringResource(R.string.notes_chars_left, NumberFormat.getIntegerInstance().format(left))
            else stringResource(R.string.notes_chars_over, NumberFormat.getIntegerInstance().format(-left)),
            style = MaterialTheme.typography.bodySmall,
            color = if (left >= 0) palette.inkMuted else palette.danger.text,
        )
    }
    if (editor.confirmingDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.notes_delete_title),
            message = stringResource(R.string.notes_delete_message),
            onDismiss = viewModel::cancelDelete,
            onConfirm = viewModel::confirmDelete,
            confirmLabel = stringResource(R.string.notes_delete_confirm),
            busy = editor.busy,
        )
    }
}

@Composable
private fun saveLabel(state: NoteSaveState): String = when (state) {
    NoteSaveState.Blank -> ""
    NoteSaveState.Emptied -> stringResource(R.string.notes_state_emptied)
    NoteSaveState.OverLimit -> stringResource(R.string.notes_state_over_limit)
    NoteSaveState.Saving -> stringResource(R.string.notes_state_saving)
    NoteSaveState.Saved -> stringResource(R.string.notes_state_saved)
    NoteSaveState.Editing -> stringResource(R.string.notes_state_editing)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(editor: NoteEditorState, viewModel: NotesViewModel) {
    val enabled = !editor.unreadable && !editor.busy
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        ZekkeIconButton(zekke.android.ui.R.drawable.ic_title, stringResource(R.string.notes_tool_title), { viewModel.lineType(NoteLineCommand.TITLE) }, enabled = enabled)
        ZekkeIconButton(zekke.android.ui.R.drawable.ic_topic, stringResource(R.string.notes_tool_topic), { viewModel.lineType(NoteLineCommand.TOPIC) }, enabled = enabled)
        ZekkeIconButton(zekke.android.ui.R.drawable.ic_task_list, stringResource(R.string.notes_tool_task), { viewModel.lineType(NoteLineCommand.TASK) }, enabled = enabled)
        ZekkeButton(stringResource(R.string.notes_tool_bold), viewModel::toggleBold, variant = ButtonVariant.Ghost, enabled = enabled)
        ZekkeButton(stringResource(R.string.notes_tool_italic), viewModel::toggleItalic, variant = ButtonVariant.Ghost, enabled = enabled)
        ZekkeButton(stringResource(R.string.notes_tool_smaller), viewModel::smallerText, variant = ButtonVariant.Ghost, enabled = enabled)
        ZekkeButton(stringResource(R.string.notes_tool_larger), viewModel::largerText, variant = ButtonVariant.Ghost, enabled = enabled)
    }
}
