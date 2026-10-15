package zekke.android.items

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zekke.android.feature.notes.NoteOpen
import zekke.android.feature.notes.NoteSave
import zekke.android.feature.notes.NotesLoad
import zekke.android.feature.notes.NotesServices
import zekke.android.feature.notes.NotesWrite
import zekke.android.feature.notes.PendingNote
import zekke.android.session.SessionGuard
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.feed.FeedScope
import zekke.core.feed.ItemTypes
import zekke.core.items.ItemContext
import zekke.core.items.newItemId
import zekke.core.notes.NoteRecord
import zekke.core.notes.deleteNotes
import zekke.core.notes.noteRecords
import zekke.core.notes.noteTiles
import zekke.core.notes.openNoteRecords
import zekke.core.notes.queueNewNote
import zekke.core.notes.queueNoteEdit
import zekke.core.notes.saveNote

class CoreNotesServices(
    private val sync: SyncService,
    private val calls: ItemCalls,
    private val context: ItemContext,
    private val guard: SessionGuard,
    private val scope: CoroutineScope,
) : NotesServices {
    private val records = HashMap<String, NoteRecord>()

    override val canDelete: Boolean get() = context.session.isFullDevice

    override val changes: Flow<Unit> = sync.changes.filter { it == FeedScope.NOTES }.map { }

    override fun newId(): String = newItemId()

    override suspend fun load(): NotesLoad {
        val replica = sync.replica() ?: return NotesLoad.Failed(ItemFailure.Failed)
        return calls.read { noteTiles(context, replica) to replica.isSynced(FeedScope.NOTES) }
            .fold({ (tiles, synced) -> NotesLoad.Loaded(tiles, synced) }, { NotesLoad.Failed(it) })
    }

    override suspend fun open(id: String): NoteOpen {
        val replica = sync.replica() ?: return NoteOpen.Failed(ItemFailure.Failed)
        return calls.read {
            val record = noteRecords(replica.items(FeedScope.NOTES, ItemTypes.NOTE)).firstOrNull { it.id == id }
                ?: throw NoSuchElementException(id)
            records[id] = record
            openNoteRecords(context, listOf(record)).single().plaintext
        }.fold({ NoteOpen.Opened(it) }, { NoteOpen.Failed(it) })
    }

    override suspend fun save(id: String, text: String): NoteSave =
        calls.write { saveNote(context, text, id, records[id]) }.fold(
            {
                records[id] = it
                sync.syncNow()
                NoteSave.Saved
            },
            { NoteSave.Failed(it) },
        )

    override suspend fun delete(ids: List<String>): NotesWrite =
        calls.write { deleteNotes(context, ids) }.fold(
            {
                ids.forEach(records::remove)
                sync.syncNow()
                NotesWrite.Done
            },
            { NotesWrite.Failed(it) },
        )

    override fun keepUnsavedWhenLocking(pending: () -> PendingNote?): () -> Unit = guard.onBeforeLock {
        val note = pending() ?: return@onBeforeLock
        val outbox = sync.outboxQueue() ?: return@onBeforeLock
        val release = guard.holdProcess()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val record = records[note.id]
                if (record == null) {
                    queueNewNote(context, outbox, note.text, note.id)
                } else {
                    queueNoteEdit(context, outbox, note.id, record.wrappedDek, record.keyGeneration, record.version, note.text)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                release()
                throw error
            } catch (_: Exception) {
                release()
                return@launch
            }
            scope.launch {
                try {
                    outbox.flush(context.api)
                } catch (_: Exception) {
                } finally {
                    release()
                }
            }
        }
    }
}
