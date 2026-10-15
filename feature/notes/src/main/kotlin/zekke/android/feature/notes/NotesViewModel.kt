package zekke.android.feature.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zekke.android.feature.tabs.FolderTabsController
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.HOME_FOLDER_ID
import zekke.core.folders.ManifestScope
import zekke.core.items.newItemId
import zekke.core.notes.InlineStyle
import zekke.core.notes.NoteDocument
import zekke.core.notes.NoteLineCommand
import zekke.core.notes.NoteTile
import zekke.core.notes.documentOfNote
import zekke.core.notes.isNoteEmpty
import zekke.core.notes.isNoteSavable
import zekke.core.notes.isNoteWithinLimit
import zekke.core.notes.noteCharactersLeft

const val NOTE_AUTOSAVE_DELAY_MS = 2000L

enum class NoteSaveState { Blank, Emptied, OverLimit, Saving, Saved, Editing }

data class NoteEditorState(
    val id: String,
    val document: NoteDocument,
    val selection: TextRange = TextRange.Zero,
    val saved: String? = null,
    val saving: Boolean = false,
    val unreadable: Boolean = false,
    val failure: ItemFailure? = null,
    val confirmingDelete: Boolean = false,
    val busy: Boolean = false,
) {
    val draft: String get() = document.toNote()

    val value: TextFieldValue get() = TextFieldValue(document.plain, selection)

    val charactersLeft: Int get() = noteCharactersLeft(draft)

    val saveState: NoteSaveState
        get() = when {
            !isNoteWithinLimit(draft) -> NoteSaveState.OverLimit
            saving -> NoteSaveState.Saving
            isNoteEmpty(draft) -> if (saved == null) NoteSaveState.Blank else NoteSaveState.Emptied
            draft == saved -> NoteSaveState.Saved
            else -> NoteSaveState.Editing
        }
}

data class NotesState(
    val loaded: Boolean = false,
    val synced: Boolean = false,
    val tiles: List<NoteTile> = emptyList(),
    val failure: ItemFailure? = null,
    val editor: NoteEditorState? = null,
    val canDelete: Boolean = false,
)

class NotesViewModel(
    private val services: NotesServices,
    tabsServices: FolderTabsServices,
    private val autosaveDelayMillis: Long = NOTE_AUTOSAVE_DELAY_MS,
    newId: () -> String = ::newItemId,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotesState(canDelete = services.canDelete))
    private var autosave: Job? = null
    private val saving = Mutex()
    private val stopKeeping: () -> Unit

    val state: StateFlow<NotesState> = mutableState.asStateFlow()

    val tabs = FolderTabsController(tabsServices, ManifestScope.NOTES.rules, viewModelScope, newId)

    init {
        viewModelScope.launch { services.changes.collect { refresh() } }
        stopKeeping = services.keepUnsavedWhenLocking {
            mutableState.value.editor?.takeIf { !it.unreadable && isNoteSavable(it.draft, it.saved) }?.let { PendingNote(it.id, it.draft) }
        }
        refresh()
    }

    override fun onCleared() {
        stopKeeping()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = try {
                services.load()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NotesLoad.Failed(ItemFailure.Failed)
            }
            when (result) {
                is NotesLoad.Loaded -> mutableState.update { it.copy(loaded = true, synced = result.synced, tiles = result.tiles, failure = null) }
                is NotesLoad.Failed -> mutableState.update { it.copy(failure = result.failure) }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun startNew() {
        mutableState.update { it.copy(editor = NoteEditorState(services.newId(), documentOfNote(""))) }
    }

    fun open(tile: NoteTile) {
        if (!tile.readable) return
        viewModelScope.launch {
            val result = try {
                services.open(tile.id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NoteOpen.Failed(ItemFailure.Failed)
            }
            when (result) {
                is NoteOpen.Opened -> mutableState.update {
                    val text = result.text
                    it.copy(editor = NoteEditorState(tile.id, documentOfNote(text ?: ""), saved = text, unreadable = text == null))
                }
                is NoteOpen.Failed -> mutableState.update { it.copy(failure = result.failure) }
            }
        }
    }

    fun valueChanged(value: TextFieldValue) = editor { current ->
        if (current.unreadable) return@editor current
        val next = current.document.edit(value.text)
        val limit = next.plain.length
        current.copy(document = next, selection = TextRange(value.selection.start.coerceIn(0, limit), value.selection.end.coerceIn(0, limit)))
    }

    fun toggleBold() = format { doc, range -> doc.toggleStyle(range.min, range.max, InlineStyle.BOLD) }

    fun toggleItalic() = format { doc, range -> doc.toggleStyle(range.min, range.max, InlineStyle.ITALIC) }

    fun largerText() = format { doc, range -> doc.resize(range.min, range.max, 1) }

    fun smallerText() = format { doc, range -> doc.resize(range.min, range.max, -1) }

    fun lineType(command: NoteLineCommand) = format { doc, range -> doc.cycleLines(range.min, range.max, command) }

    fun toggleTask(lineIndex: Int) = format { doc, _ -> doc.toggleTask(lineIndex) }

    private fun format(change: (NoteDocument, TextRange) -> NoteDocument) = editor { current ->
        if (current.unreadable) current else current.copy(document = change(current.document, current.selection))
    }

    private fun editor(change: (NoteEditorState) -> NoteEditorState) {
        var changed = false
        mutableState.update { state ->
            val current = state.editor ?: return@update state
            val next = change(current)
            changed = next.draft != current.draft
            state.copy(editor = next)
        }
        if (changed) scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosave?.cancel()
        autosave = viewModelScope.launch {
            delay(autosaveDelayMillis)
            viewModelScope.launch { save() }
        }
    }

    suspend fun save() = saving.withLock {
        val current = mutableState.value.editor ?: return@withLock
        if (current.unreadable || !isNoteSavable(current.draft, current.saved)) return@withLock
        val text = current.draft
        val first = current.saved == null
        mutableState.update { state -> state.editor?.let { state.copy(editor = it.copy(saving = true)) } ?: state }
        val result = try {
            services.save(current.id, text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            NoteSave.Failed(ItemFailure.Failed)
        }
        when (result) {
            NoteSave.Saved -> {
                if (first) fileInOpenTab(current.id)
                mutableState.update { state -> state.editor?.let { state.copy(editor = it.copy(saving = false, saved = text, failure = null)) } ?: state }
                refresh()
            }
            is NoteSave.Failed -> mutableState.update { state ->
                state.editor?.let { state.copy(editor = it.copy(saving = false, failure = result.failure)) } ?: state
            }
        }
        val after = mutableState.value.editor
        if (after != null && isNoteSavable(after.draft, after.saved) && after.failure == null) scheduleAutosave()
    }

    private suspend fun fileInOpenTab(id: String) {
        val active = tabs.active(mutableState.value.tiles.map { it.id })
        if (active == HOME_FOLDER_ID) return
        if (!tabs.file(id, active)) mutableState.update { state -> state.copy(failure = tabs.state.value.failure) }
    }

    fun close() {
        autosave?.cancel()
        viewModelScope.launch {
            save()
            mutableState.update { it.copy(editor = null) }
            refresh()
        }
    }

    fun askDelete() = editor { it.copy(confirmingDelete = true) }

    fun cancelDelete() = editor { if (it.busy) it else it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val current = mutableState.value.editor ?: return
        if (current.busy || !mutableState.value.canDelete) return
        autosave?.cancel()
        viewModelScope.launch {
            if (current.saved == null) {
                mutableState.update { it.copy(editor = null) }
                return@launch
            }
            mutableState.update { state -> state.copy(editor = state.editor?.copy(busy = true)) }
            val result = try {
                services.delete(listOf(current.id))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NotesWrite.Failed(ItemFailure.Failed)
            }
            when (result) {
                NotesWrite.Done -> {
                    tabs.forget(listOf(current.id))
                    mutableState.update { it.copy(editor = null) }
                    refresh()
                }
                is NotesWrite.Failed -> mutableState.update { state ->
                    state.copy(editor = state.editor?.copy(busy = false, confirmingDelete = false, failure = result.failure))
                }
            }
        }
    }

    suspend fun deleteSpaceItems(ids: List<String>): ItemFailure? {
        val result = try {
            services.delete(ids)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            NotesWrite.Failed(ItemFailure.Failed)
        }
        return (result as? NotesWrite.Failed)?.failure
    }
}
