package zekke.android.feature.notes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.feature.tabs.TabsLoad
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.ManifestScope
import zekke.core.folders.emptyFolderManifest
import zekke.core.notes.NoteTile

fun tile(id: String, title: String? = "Note $id", thumbnail: String = "t-$id", readable: Boolean = true) =
    NoteTile(id, title, thumbnail, 10, "v1", "2026-10-01T10:00:00Z", "2026-10-02T10:00:00Z", readable)

class FakeNotesServices(override var canDelete: Boolean = true) : NotesServices {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    var tiles: List<NoteTile> = emptyList()
    var synced = true
    var loadFailure: zekke.android.ui.components.ItemFailure? = null
    var texts = mutableMapOf<String, String?>()
    val saves = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<List<String>>()
    var saveResult: NoteSave = NoteSave.Saved
    var gate: CompletableDeferred<Unit>? = null
    var provider: (() -> PendingNote?)? = null
    var ids = 0

    override fun newId(): String = "note-${++ids}"

    override suspend fun load(): NotesLoad = loadFailure?.let { NotesLoad.Failed(it) } ?: NotesLoad.Loaded(tiles, synced)

    override suspend fun open(id: String): NoteOpen = NoteOpen.Opened(texts[id])

    override suspend fun save(id: String, text: String): NoteSave {
        saves += id to text
        gate?.await()
        return saveResult
    }

    override suspend fun delete(ids: List<String>): NotesWrite {
        deleted += ids
        return NotesWrite.Done
    }

    override fun keepUnsavedWhenLocking(pending: () -> PendingNote?): () -> Unit {
        provider = pending
        return { provider = null }
    }
}

class FakeNoteTabs : FolderTabsServices {
    private val rules = ManifestScope.NOTES.rules
    override val canDeleteItems = true
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var manifest: FolderManifest = emptyFolderManifest(rules, "2026-10-01T00:00:00.000Z")
    override suspend fun load() = TabsLoad.Loaded(manifest)
    override suspend fun edit(edit: FolderEdit): TabsLoad {
        manifest = edit.apply(manifest, rules)
        return TabsLoad.Loaded(manifest)
    }
    override suspend fun reset() = TabsLoad.Loaded(manifest)
}
