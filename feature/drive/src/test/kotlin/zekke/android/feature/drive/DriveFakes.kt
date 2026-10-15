package zekke.android.feature.drive

import kotlinx.coroutines.flow.MutableSharedFlow
import zekke.android.ui.components.ItemFailure
import zekke.core.files.StorageUsage
import zekke.core.folders.TreeFolder
import zekke.core.trash.TrashEntry
import zekke.core.trash.TrashKind
import zekke.core.folders.TreeScope

fun entry(
    id: String,
    name: String? = "file-$id.txt",
    folderId: String? = null,
    thumbnailId: String? = null,
    r2State: String = "ok",
    size: Long = 1234,
    gcs: String = "ok",
) = DriveEntry(id, name, "text/plain", size, folderId, thumbnailId, r2State, gcs)

fun folder(id: String, name: String?, parent: String? = null) = TreeFolder(id, parent, name, 0.0, "2026-10-01T10:00:00Z", "2026-10-01T10:00:00Z")

fun trashed(id: String, name: String? = "gone-$id", kind: TrashKind = TrashKind.FILE) =
    TrashEntry(TreeScope.FILES, kind, id, emptyList(), name, "2026-10-10T10:00:00Z", if (kind == TrashKind.FOLDER) 3 else null, 500, "text/plain")

class FakeDriveServices(override var canDelete: Boolean = true) : DriveServices {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    var files: List<DriveEntry> = emptyList()
    var folders: List<TreeFolder> = emptyList()
    var synced = true
    var loadFailure: ItemFailure? = null
    var usage = StorageUsage(usedBytes = 3_000, storedBytes = 2_000, quotaBytes = 10_000)
    var retentionDays = 30
    var trashEntries: List<TrashEntry> = emptyList()
    var result: DriveWrite = DriveWrite.Done
    val calls = mutableListOf<String>()
    val thumbnailsAsked = mutableListOf<String>()
    var thumbnailBytes: ByteArray? = byteArrayOf(1, 2, 3)

    override suspend fun load(): DriveLoad = loadFailure?.let { DriveLoad.Failed(it) } ?: DriveLoad.Loaded(files, folders, synced)

    override suspend fun usage(): UsageLoad = UsageLoad.Loaded(usage, retentionDays)

    override suspend fun thumbnail(thumbnailId: String): ByteArray? {
        thumbnailsAsked += thumbnailId
        return thumbnailBytes
    }

    override suspend fun createFolder(name: String, parentId: String?): DriveWrite = record("create:$name:$parentId")

    override suspend fun renameFolder(id: String, name: String): DriveWrite = record("renameFolder:$id:$name")

    override suspend fun renameFile(id: String, name: String): DriveWrite = record("renameFile:$id:$name")

    override suspend fun moveFiles(ids: List<String>, folderId: String?): DriveWrite = record("moveFiles:${ids.joinToString(",")}:$folderId")

    override suspend fun moveFolder(id: String, parentId: String?): DriveWrite = record("moveFolder:$id:$parentId")

    override suspend fun deleteFiles(ids: List<String>): DriveWrite = record("deleteFiles:${ids.joinToString(",")}")

    override suspend fun deleteFolder(id: String): DriveWrite = record("deleteFolder:$id")

    override suspend fun trash(): TrashLoad = TrashLoad.Loaded(trashEntries)

    override suspend fun restore(entries: List<TrashEntry>): DriveWrite = record("restore:${entries.joinToString(",") { it.id }}")

    override suspend fun purge(entries: List<TrashEntry>): DriveWrite = record("purge:${entries.joinToString(",") { it.id }}")

    private fun record(call: String): DriveWrite {
        calls += call
        return result
    }
}
