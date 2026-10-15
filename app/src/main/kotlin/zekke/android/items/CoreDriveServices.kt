package zekke.android.items

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import zekke.android.feature.drive.DriveEntry
import zekke.android.feature.drive.ExportTarget
import zekke.android.feature.drive.MAX_IMAGE_PREVIEW_BYTES
import zekke.android.feature.drive.PreviewKind
import zekke.android.feature.drive.PreviewLoad
import zekke.android.feature.drive.ShareLoad
import zekke.android.feature.drive.previewKindOf
import zekke.android.session.SessionGuard
import zekke.core.files.downloadBytes
import zekke.core.files.downloadFile
import zekke.core.files.openFile
import zekke.android.feature.drive.DriveLoad
import zekke.android.feature.drive.DriveServices
import zekke.android.feature.drive.DriveWrite
import zekke.android.feature.drive.TrashLoad
import zekke.android.feature.drive.UsageLoad
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.feed.FeedScope
import zekke.core.feed.ItemTypes
import zekke.core.files.DriveFile
import zekke.core.files.ObjectStore
import zekke.core.files.PlaintextSink
import zekke.core.files.SealedObjectCache
import zekke.core.files.abandonUpload
import zekke.core.files.deleteFiles
import zekke.core.files.driveView
import zekke.core.files.fileRecords
import zekke.core.files.getStorageUsage
import zekke.core.files.openPreview
import zekke.core.files.renameFile
import zekke.core.folders.TreeScope
import zekke.core.folders.createTreeFolder
import zekke.core.folders.deleteTreeFolder
import zekke.core.folders.moveItemsToFolder
import zekke.core.folders.moveTreeFolder
import zekke.core.folders.renameTreeFolder
import zekke.core.folders.treeFoldersFromReplica
import zekke.core.items.ItemContext
import zekke.core.trash.TrashEntry
import zekke.core.trash.listFileTrash
import zekke.core.trash.purgeEntries
import zekke.core.trash.restoreEntries
import zekke.core.users.getMe

class CoreDriveServices(
    private val sync: SyncService,
    private val calls: ItemCalls,
    private val context: ItemContext,
    private val store: ObjectStore,
    private val cache: SealedObjectCache,
    private val plaintext: PlaintextFiles,
    private val guard: SessionGuard,
) : DriveServices {
    override val canDelete: Boolean get() = context.session.isFullDevice

    override val changes: Flow<Unit> = sync.changes.filter { it == FeedScope.FILES }.map { }

    override suspend fun load(): DriveLoad {
        val replica = sync.replica() ?: return DriveLoad.Failed(ItemFailure.Failed)
        return calls.read {
            val view = driveView(context, replica)
            val folders = treeFoldersFromReplica(context, TreeScope.FILES, replica.items(FeedScope.FILES, ItemTypes.FILE_FOLDER))
            val ids = fileRecords(replica.items(FeedScope.FILES, ItemTypes.FILE)).map { it.id }.toSet()
            cache.prune(ids)
            Triple(view.live.map(::entryOf), folders, replica.isSynced(FeedScope.FILES))
        }.fold({ (files, folders, synced) -> DriveLoad.Loaded(files, folders, synced) }, { DriveLoad.Failed(it) })
    }

    override suspend fun usage(): UsageLoad = calls.read { getStorageUsage(context) to getMe(context.api).retentionDays }
        .fold({ (usage, days) -> UsageLoad.Loaded(usage, days) }, { UsageLoad.Failed(it) })

    override suspend fun thumbnail(thumbnailId: String): ByteArray? {
        val replica = sync.replica() ?: return null
        return calls.read {
            val record = fileRecords(replica.items(FeedScope.FILES, ItemTypes.FILE)).firstOrNull { it.id == thumbnailId }
            record?.let { openPreview(context, it, store, cache).bytes }
        }.fold({ it }, { null })
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveWrite =
        written { createTreeFolder(context, TreeScope.FILES, name, parentId) }

    override suspend fun renameFolder(id: String, name: String): DriveWrite = written { renameTreeFolder(context, TreeScope.FILES, id, name) }

    override suspend fun renameFile(id: String, name: String): DriveWrite = written { renameFile(context, id, name) }

    override suspend fun moveFiles(ids: List<String>, folderId: String?): DriveWrite =
        written { moveItemsToFolder(context, TreeScope.FILES, ids, folderId) }

    override suspend fun moveFolder(id: String, parentId: String?): DriveWrite = written { moveTreeFolder(context, TreeScope.FILES, id, parentId) }

    override suspend fun deleteFiles(ids: List<String>): DriveWrite = written { deleteFiles(context, ids) }

    override suspend fun discardUpload(id: String): DriveWrite = written { abandonUpload(context, id) }

    override suspend fun deleteFolder(id: String): DriveWrite = written { deleteTreeFolder(context, TreeScope.FILES, id) }

    override suspend fun preview(file: DriveEntry): PreviewLoad = when (previewKindOf(file.mime)) {
        PreviewKind.None -> PreviewLoad.Failed(ItemFailure.Failed)
        PreviewKind.Image -> if (file.sizeBytes > MAX_IMAGE_PREVIEW_BYTES) {
            PreviewLoad.TooLarge
        } else {
            calls.read { downloadBytes(context, file.id, store, MAX_IMAGE_PREVIEW_BYTES).bytes }
                .fold({ PreviewLoad.Image(it) }, { PreviewLoad.Failed(it) })
        }
        PreviewKind.Pdf -> {
            val target = plaintext.newPreview()
            calls.read {
                FileOutputStream(target).use { out ->
                    downloadFile(context, file.id, store, PlaintextSink { bytes, offset, length -> out.write(bytes, offset, length) })
                }
            }.fold(
                { PreviewLoad.Pdf(target) },
                {
                    plaintext.discard(target)
                    PreviewLoad.Failed(it)
                },
            )
        }
    }

    override fun discardPreview(file: File) = plaintext.discard(file)

    override suspend fun saveTo(id: String, target: ExportTarget): DriveWrite {
        val result = calls.write {
            target.open().use { out -> downloadFile(context, id, store, PlaintextSink { bytes, offset, length -> out.write(bytes, offset, length) }) }
        }
        return result.fold(
            { DriveWrite.Done },
            {
                target.discard()
                DriveWrite.Failed(it)
            },
        )
    }

    override suspend fun shareFile(id: String): ShareLoad {
        var file: File? = null
        val result = calls.write {
            val manifest = openFile(context, id).use { it.manifest }
            val target = plaintext.newShared(manifest.name)
            file = target
            FileOutputStream(target).use { out -> downloadFile(context, id, store, PlaintextSink { bytes, offset, length -> out.write(bytes, offset, length) }) }
            plaintext.uriOf(target).toString() to manifest.mime
        }
        return result.fold(
            { (uri, mime) -> ShareLoad.Ready(uri, mime) },
            {
                file?.let(plaintext::discard)
                ShareLoad.Failed(it)
            },
        )
    }

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()

    override suspend fun trash(): TrashLoad = calls.read { listFileTrash(context) }.fold({ TrashLoad.Loaded(it) }, { TrashLoad.Failed(it) })

    override suspend fun restore(entries: List<TrashEntry>): DriveWrite = written { restoreEntries(context, entries) }

    override suspend fun purge(entries: List<TrashEntry>): DriveWrite = written { purgeEntries(context, entries) }

    private suspend fun written(call: suspend () -> Unit): DriveWrite =
        calls.write { call() }.fold(
            {
                sync.syncNow()
                DriveWrite.Done
            },
            { DriveWrite.Failed(it) },
        )

    private fun entryOf(file: DriveFile): DriveEntry = DriveEntry(
        id = file.id,
        name = file.name,
        mime = file.mime,
        sizeBytes = file.size ?: file.record.sizeBytes,
        folderId = file.record.folderId,
        thumbnailId = file.thumbnailId,
        r2State = file.record.r2State,
        gcsState = file.record.gcsState,
    )
}
