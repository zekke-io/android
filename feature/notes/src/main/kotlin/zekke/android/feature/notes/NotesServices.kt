package zekke.android.feature.notes

import kotlinx.coroutines.flow.Flow
import zekke.android.ui.components.ItemFailure
import zekke.core.notes.NoteTile

sealed interface NotesLoad {
    data class Loaded(val tiles: List<NoteTile>, val synced: Boolean) : NotesLoad

    data class Failed(val failure: ItemFailure) : NotesLoad
}

sealed interface NoteOpen {
    data class Opened(val text: String?) : NoteOpen

    data class Failed(val failure: ItemFailure) : NoteOpen
}

sealed interface NoteSave {
    data object Saved : NoteSave

    data class Failed(val failure: ItemFailure) : NoteSave
}

sealed interface NotesWrite {
    data object Done : NotesWrite

    data class Failed(val failure: ItemFailure) : NotesWrite
}

class PendingNote(val id: String, val text: String)

interface NotesServices {
    val canDelete: Boolean

    val changes: Flow<Unit>

    fun newId(): String

    suspend fun load(): NotesLoad

    suspend fun open(id: String): NoteOpen

    suspend fun save(id: String, text: String): NoteSave

    suspend fun delete(ids: List<String>): NotesWrite

    fun keepUnsavedWhenLocking(pending: () -> PendingNote?): () -> Unit
}
