package zekke.android.feature.drive

import kotlinx.coroutines.flow.Flow
import zekke.android.ui.components.ItemFailure
import zekke.core.files.StorageUsage
import zekke.core.folders.TreeFolder
import zekke.core.trash.TrashEntry

data class DriveEntry(
    val id: String,
    val name: String?,
    val mime: String?,
    val sizeBytes: Long,
    val folderId: String?,
    val thumbnailId: String?,
    val r2State: String,
    val gcsState: String,
) {
    val readable: Boolean get() = name != null
}

sealed interface DriveLoad {
    data class Loaded(val files: List<DriveEntry>, val folders: List<TreeFolder>, val synced: Boolean) : DriveLoad

    data class Failed(val failure: ItemFailure) : DriveLoad
}

sealed interface UsageLoad {
    data class Loaded(val usage: StorageUsage, val retentionDays: Int) : UsageLoad

    data class Failed(val failure: ItemFailure) : UsageLoad
}

sealed interface TrashLoad {
    data class Loaded(val entries: List<TrashEntry>) : TrashLoad

    data class Failed(val failure: ItemFailure) : TrashLoad
}

sealed interface DriveWrite {
    data object Done : DriveWrite

    data class Failed(val failure: ItemFailure) : DriveWrite
}

interface DriveServices {
    val canDelete: Boolean

    val changes: Flow<Unit>

    suspend fun load(): DriveLoad

    suspend fun usage(): UsageLoad

    suspend fun thumbnail(thumbnailId: String): ByteArray?

    suspend fun createFolder(name: String, parentId: String?): DriveWrite

    suspend fun renameFolder(id: String, name: String): DriveWrite

    suspend fun renameFile(id: String, name: String): DriveWrite

    suspend fun moveFiles(ids: List<String>, folderId: String?): DriveWrite

    suspend fun moveFolder(id: String, parentId: String?): DriveWrite

    suspend fun deleteFiles(ids: List<String>): DriveWrite

    suspend fun deleteFolder(id: String): DriveWrite

    suspend fun trash(): TrashLoad

    suspend fun restore(entries: List<TrashEntry>): DriveWrite

    suspend fun purge(entries: List<TrashEntry>): DriveWrite
}
