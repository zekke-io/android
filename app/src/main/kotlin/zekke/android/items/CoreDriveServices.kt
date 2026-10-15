package zekke.android.items

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import zekke.android.feature.drive.DriveEntry
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
import zekke.core.files.SealedObjectCache
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

    override suspend fun deleteFolder(id: String): DriveWrite = written { deleteTreeFolder(context, TreeScope.FILES, id) }

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
