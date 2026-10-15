package zekke.android.feature.drive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure
import zekke.core.files.StorageUsage
import zekke.core.folders.MAX_FOLDER_NAME_LENGTH
import zekke.core.folders.TreeFolder
import zekke.core.folders.canCreateIn
import zekke.core.folders.canMoveFolder
import zekke.core.folders.childrenOf
import zekke.core.folders.pathTo
import zekke.core.trash.TrashEntry

sealed interface DriveItem {
    data class OfFile(val file: DriveEntry) : DriveItem

    data class OfFolder(val folder: TreeFolder) : DriveItem
}

sealed interface Viewing {
    val entry: DriveEntry

    data class Loading(override val entry: DriveEntry) : Viewing

    class Image(override val entry: DriveEntry, val bytes: ByteArray) : Viewing

    class Pdf(override val entry: DriveEntry, val file: java.io.File) : Viewing

    data class TooLarge(override val entry: DriveEntry) : Viewing

    data class Failed(override val entry: DriveEntry, val failure: ItemFailure) : Viewing
}

sealed interface DriveNaming {
    val name: String

    fun renamed(name: String): DriveNaming

    data class NewFolder(val parentId: String?, override val name: String = "") : DriveNaming {
        override fun renamed(name: String) = copy(name = name)
    }

    data class RenameFolder(val id: String, override val name: String) : DriveNaming {
        override fun renamed(name: String) = copy(name = name)
    }

    data class RenameFile(val id: String, val original: String, override val name: String) : DriveNaming {
        override fun renamed(name: String) = copy(name = name)
    }
}

sealed interface NamingProblem {
    data class File(val problem: FileNameProblem) : NamingProblem

    data object FolderEmpty : NamingProblem

    data class FolderTooLong(val limit: Int) : NamingProblem

    data class FolderClash(val existing: String) : NamingProblem
}

data class DriveState(
    val loaded: Boolean = false,
    val synced: Boolean = false,
    val files: List<DriveEntry> = emptyList(),
    val folders: List<TreeFolder> = emptyList(),
    val usage: StorageUsage? = null,
    val retentionDays: Int? = null,
    val folderId: String? = null,
    val grid: Boolean = true,
    val failure: ItemFailure? = null,
    val busy: Boolean = false,
    val acting: DriveItem? = null,
    val naming: DriveNaming? = null,
    val moving: DriveItem? = null,
    val deleting: DriveItem? = null,
    val showingTrash: Boolean = false,
    val trash: List<TrashEntry>? = null,
    val confirmingPurge: List<TrashEntry>? = null,
    val thumbnails: Map<String, ByteArray> = emptyMap(),
    val canDelete: Boolean = false,
    val viewing: Viewing? = null,
    val saved: String? = null,
    val share: ShareLoad.Ready? = null,
    val uploads: List<UploadItem> = emptyList(),
    val cancelling: UploadItem? = null,
    val choosing: UploadItem? = null,
) {
    val path: List<TreeFolder> get() = pathTo(folders, folderId)

    val subfolders: List<TreeFolder> get() = childrenOf(folders, folderId).sortedBy { it.name?.lowercase() ?: "" }

    val filesHere: List<DriveEntry> get() = files.filter { it.folderId == folderId }

    val uploadsHere: List<UploadItem> get() = uploads.filter { it.folderId == folderId }

    val canCreateFolder: Boolean get() = canCreateIn(folders, folderId)

    fun itemsInside(folderId: String): Int = files.count { it.folderId == folderId }

    fun foldersInside(folderId: String): Int = folders.count { it.parentId == folderId }
}

class DriveViewModel(private val services: DriveServices) : ViewModel() {
    private val mutableState = MutableStateFlow(DriveState(canDelete = services.canDelete))
    private val requested = HashSet<String>()

    val state: StateFlow<DriveState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { services.changes.collect { refresh() } }
        viewModelScope.launch { services.uploads.collect { items -> mutableState.update { it.copy(uploads = items) } } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = attemptLoad { services.load() }
            when (loaded) {
                is DriveLoad.Loaded -> mutableState.update { state ->
                    val here = state.folderId?.takeIf { id -> loaded.folders.any { it.id == id } }
                    state.copy(loaded = true, synced = loaded.synced, files = loaded.files, folders = loaded.folders, folderId = here, failure = null)
                }
                is DriveLoad.Failed -> mutableState.update { it.copy(failure = loaded.failure) }
            }
            when (val usage = attemptUsage { services.usage() }) {
                is UsageLoad.Loaded -> mutableState.update { it.copy(usage = usage.usage, retentionDays = usage.retentionDays) }
                is UsageLoad.Failed -> Unit
            }
        }
    }

    private suspend fun attemptLoad(call: suspend () -> DriveLoad): DriveLoad = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DriveLoad.Failed(ItemFailure.Failed)
    }

    private suspend fun attemptUsage(call: suspend () -> UsageLoad): UsageLoad = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        UsageLoad.Failed(ItemFailure.Failed)
    }

    override fun onCleared() {
        (mutableState.value.viewing as? Viewing.Pdf)?.let { services.discardPreview(it.file) }
    }

    fun preview(entry: DriveEntry) {
        if (previewKindOf(entry.mime) == PreviewKind.None || !entry.readable || entry.r2State != "ok") return
        mutableState.update { it.copy(viewing = Viewing.Loading(entry), acting = null) }
        viewModelScope.launch {
            val result = try {
                services.preview(entry)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PreviewLoad.Failed(ItemFailure.Failed)
            }
            mutableState.update { state ->
                if ((state.viewing as? Viewing.Loading)?.entry?.id != entry.id) {
                    (result as? PreviewLoad.Pdf)?.let { services.discardPreview(it.file) }
                    state
                } else {
                    state.copy(
                        viewing = when (result) {
                            is PreviewLoad.Image -> Viewing.Image(entry, result.bytes)
                            is PreviewLoad.Pdf -> Viewing.Pdf(entry, result.file)
                            PreviewLoad.TooLarge -> Viewing.TooLarge(entry)
                            is PreviewLoad.Failed -> Viewing.Failed(entry, result.failure)
                        },
                    )
                }
            }
        }
    }

    fun closePreview() {
        val viewing = mutableState.value.viewing
        (viewing as? Viewing.Pdf)?.let { services.discardPreview(it.file) }
        mutableState.update { it.copy(viewing = null) }
    }

    fun saveTo(entry: DriveEntry, target: ExportTarget) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(acting = null, saved = null) }
        viewModelScope.launch {
            if (write { services.saveTo(entry.id, target) }) mutableState.update { it.copy(saved = entry.name) }
        }
    }

    fun filesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        services.enqueueUploads(uris, mutableState.value.folderId)
    }

    fun retryUpload(id: String) = services.retryUpload(id)

    fun askChooseAgain(item: UploadItem) = mutableState.update { it.copy(choosing = item) }

    fun cancelChoosing() = mutableState.update { it.copy(choosing = null) }

    fun chosenAgain(uri: String) {
        val item = mutableState.value.choosing ?: return
        mutableState.update { it.copy(choosing = null) }
        services.chooseFileAgain(item.id, uri)
    }

    fun askCancelUpload(item: UploadItem) = mutableState.update { it.copy(cancelling = item) }

    fun keepUpload() = mutableState.update { it.copy(cancelling = null) }

    fun confirmCancelUpload() {
        val item = mutableState.value.cancelling ?: return
        mutableState.update { it.copy(cancelling = null) }
        viewModelScope.launch {
            try {
                services.cancelUpload(item.id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
            refresh()
        }
    }

    fun dismissSaved() = mutableState.update { it.copy(saved = null) }

    fun shareFile(entry: DriveEntry) {
        if (mutableState.value.busy || !entry.readable || entry.r2State != "ok") return
        mutableState.update { it.copy(acting = null, saved = null, busy = true, failure = null) }
        viewModelScope.launch {
            val result = try {
                services.shareFile(entry.id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ShareLoad.Failed(ItemFailure.Failed)
            }
            when (result) {
                is ShareLoad.Ready -> mutableState.update { it.copy(busy = false, share = result) }
                is ShareLoad.Failed -> mutableState.update { it.copy(busy = false, failure = result.failure) }
            }
        }
    }

    fun shareHandled() = mutableState.update { it.copy(share = null) }

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun openFolder(id: String?) = mutableState.update { it.copy(folderId = id, acting = null) }

    fun up() = mutableState.update { state -> state.copy(folderId = state.path.dropLast(1).lastOrNull()?.id) }

    fun toggleGrid() = mutableState.update { it.copy(grid = !it.grid) }

    fun requestThumbnail(file: DriveEntry) {
        val id = file.thumbnailId ?: return
        if (!requested.add(id)) return
        viewModelScope.launch {
            val bytes = try {
                services.thumbnail(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (bytes != null) mutableState.update { it.copy(thumbnails = it.thumbnails + (id to bytes)) }
        }
    }

    fun act(item: DriveItem) = mutableState.update { it.copy(acting = item) }

    fun stopActing() = mutableState.update { it.copy(acting = null) }

    fun startNewFolder() {
        val state = mutableState.value
        if (!state.canCreateFolder) return
        mutableState.update { it.copy(naming = DriveNaming.NewFolder(it.folderId), acting = null) }
    }

    fun startRename(item: DriveItem) = mutableState.update {
        it.copy(
            acting = null,
            naming = when (item) {
                is DriveItem.OfFolder -> DriveNaming.RenameFolder(item.folder.id, item.folder.name.orEmpty())
                is DriveItem.OfFile -> DriveNaming.RenameFile(item.file.id, item.file.name.orEmpty(), item.file.name.orEmpty())
            },
        )
    }

    fun nameChanged(name: String) = mutableState.update { state -> state.naming?.let { state.copy(naming = it.renamed(name)) } ?: state }

    fun cancelNaming() = mutableState.update { if (it.busy) it else it.copy(naming = null) }

    fun namingProblem(): NamingProblem? {
        val state = mutableState.value
        val naming = state.naming ?: return null
        return when (naming) {
            is DriveNaming.RenameFile -> fileNameProblem(naming.name)?.let { NamingProblem.File(it) }
            is DriveNaming.NewFolder -> folderProblem(naming.name, state.folders.filter { it.parentId == naming.parentId }, null)
            is DriveNaming.RenameFolder -> {
                val parent = state.folders.firstOrNull { it.id == naming.id }?.parentId
                folderProblem(naming.name, state.folders.filter { it.parentId == parent }, naming.id)
            }
        }
    }

    private fun folderProblem(name: String, siblings: List<TreeFolder>, renaming: String?): NamingProblem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NamingProblem.FolderEmpty
        if (trimmed.length > MAX_FOLDER_NAME_LENGTH) return NamingProblem.FolderTooLong(MAX_FOLDER_NAME_LENGTH)
        val clash = siblings.firstOrNull { it.id != renaming && it.name?.trim()?.lowercase() == trimmed.lowercase() }
        return clash?.let { NamingProblem.FolderClash(it.name.orEmpty()) }
    }

    fun commitNaming() {
        val naming = mutableState.value.naming ?: return
        if (mutableState.value.busy || namingProblem() != null) return
        val name = naming.name.trim()
        viewModelScope.launch {
            val done = write {
                when (naming) {
                    is DriveNaming.NewFolder -> services.createFolder(name, naming.parentId)
                    is DriveNaming.RenameFolder -> services.renameFolder(naming.id, name)
                    is DriveNaming.RenameFile -> services.renameFile(naming.id, name)
                }
            }
            if (done) mutableState.update { it.copy(naming = null) }
        }
    }

    fun askMove(item: DriveItem) = mutableState.update { it.copy(moving = item, acting = null) }

    fun cancelMove() = mutableState.update { if (it.busy) it else it.copy(moving = null) }

    fun destinations(): List<Pair<TreeFolder?, Int>> {
        val state = mutableState.value
        val moving = state.moving ?: return emptyList()
        val result = ArrayList<Pair<TreeFolder?, Int>>()
        fun walk(parent: String?, depth: Int) {
            for (folder in childrenOf(state.folders, parent).sortedBy { it.name?.lowercase() ?: "" }) {
                val allowed = when (moving) {
                    is DriveItem.OfFolder -> canMoveFolder(state.folders, moving.folder.id, folder.id)
                    is DriveItem.OfFile -> true
                }
                if (moving is DriveItem.OfFolder && folder.id == moving.folder.id) continue
                result += folder to depth
                if (allowed || moving is DriveItem.OfFile) walk(folder.id, depth + 1)
            }
        }
        result += null to 0
        walk(null, 1)
        return result
    }

    fun canMoveTo(folderId: String?): Boolean {
        val state = mutableState.value
        return when (val moving = state.moving) {
            null -> false
            is DriveItem.OfFile -> moving.file.folderId != folderId
            is DriveItem.OfFolder -> moving.folder.parentId != folderId && canMoveFolder(state.folders, moving.folder.id, folderId)
        }
    }

    fun moveTo(folderId: String?) {
        val moving = mutableState.value.moving ?: return
        if (mutableState.value.busy || !canMoveTo(folderId)) return
        viewModelScope.launch {
            val done = write {
                when (moving) {
                    is DriveItem.OfFile -> services.moveFiles(listOf(moving.file.id), folderId)
                    is DriveItem.OfFolder -> services.moveFolder(moving.folder.id, folderId)
                }
            }
            if (done) mutableState.update { it.copy(moving = null) }
        }
    }

    fun canDeleteItem(item: DriveItem): Boolean {
        val state = mutableState.value
        return when {
            item is DriveItem.OfFile && item.file.isUnfinished -> true
            else -> state.canDelete && state.retentionDays != null
        }
    }

    fun askDelete(item: DriveItem) {
        if (!canDeleteItem(item)) {
            if (mutableState.value.retentionDays == null) refresh()
            return
        }
        mutableState.update { it.copy(deleting = item, acting = null) }
    }

    fun cancelDelete() = mutableState.update { if (it.busy) it else it.copy(deleting = null) }

    fun confirmDelete() {
        val deleting = mutableState.value.deleting ?: return
        if (mutableState.value.busy) return
        viewModelScope.launch {
            write {
                when (deleting) {
                    is DriveItem.OfFile -> if (deleting.file.isUnfinished) services.discardUpload(deleting.file.id) else services.deleteFiles(listOf(deleting.file.id))
                    is DriveItem.OfFolder -> services.deleteFolder(deleting.folder.id)
                }
            }
            mutableState.update { it.copy(deleting = null) }
        }
    }

    fun showTrash() {
        mutableState.update { it.copy(showingTrash = true, trash = null, acting = null) }
        loadTrash()
    }

    fun hideTrash() = mutableState.update { it.copy(showingTrash = false) }

    private fun loadTrash() {
        viewModelScope.launch {
            val result = try {
                services.trash()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                TrashLoad.Failed(ItemFailure.Failed)
            }
            when (result) {
                is TrashLoad.Loaded -> mutableState.update { it.copy(trash = result.entries, failure = null) }
                is TrashLoad.Failed -> mutableState.update { it.copy(failure = result.failure, trash = it.trash ?: emptyList()) }
            }
        }
    }

    fun restore(entries: List<TrashEntry>) {
        if (mutableState.value.busy || entries.isEmpty()) return
        viewModelScope.launch {
            if (write { services.restore(entries) }) loadTrash()
        }
    }

    fun askPurge(entries: List<TrashEntry>) {
        if (!mutableState.value.canDelete || entries.isEmpty()) return
        mutableState.update { it.copy(confirmingPurge = entries) }
    }

    fun cancelPurge() = mutableState.update { if (it.busy) it else it.copy(confirmingPurge = null) }

    fun confirmPurge() {
        val entries = mutableState.value.confirmingPurge ?: return
        if (mutableState.value.busy) return
        viewModelScope.launch {
            if (write { services.purge(entries) }) loadTrash()
            mutableState.update { it.copy(confirmingPurge = null) }
        }
    }

    private suspend fun write(call: suspend () -> DriveWrite): Boolean {
        mutableState.update { it.copy(busy = true, failure = null) }
        val result = try {
            call()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DriveWrite.Failed(ItemFailure.Failed)
        }
        return when (result) {
            DriveWrite.Done -> {
                mutableState.update { it.copy(busy = false) }
                refresh()
                true
            }
            is DriveWrite.Failed -> {
                mutableState.update { it.copy(busy = false, failure = result.failure) }
                false
            }
        }
    }
}
