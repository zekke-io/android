package zekke.android.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.Folder
import zekke.core.folders.FolderEditProblem
import zekke.core.folders.FolderManifest
import zekke.core.folders.createFolder
import zekke.core.folders.deleteFolder
import zekke.core.folders.folderPath
import zekke.core.folders.placeItem
import zekke.core.folders.renameFolder
import zekke.core.items.newItemId

sealed interface FolderForm {
    val draft: String

    data class Create(override val draft: String = "") : FolderForm

    data class Rename(val folderId: String, override val draft: String) : FolderForm
}

sealed interface SpaceNotice {
    data class Copied(val name: String) : SpaceNotice

    data class Saved(val name: String) : SpaceNotice
}

sealed interface SpaceProblem {
    data class Failure(val failure: ItemFailure) : SpaceProblem

    data class Folders(val problem: FolderEditProblem) : SpaceProblem

    data object TooLarge : SpaceProblem

    data object NoRoom : SpaceProblem
}

data class SpaceState(
    val loading: Boolean = true,
    val items: List<SharedItem> = emptyList(),
    val manifest: FolderManifest? = null,
    val organised: Boolean = true,
    val invalid: Boolean = false,
    val folderId: String? = null,
    val detail: String? = null,
    val revealed: Boolean = false,
    val form: FolderForm? = null,
    val folderMenu: String? = null,
    val confirmingFolderDelete: Folder? = null,
    val moving: SharedItem? = null,
    val busy: Boolean = false,
    val notice: SpaceNotice? = null,
    val problem: SpaceProblem? = null,
) {
    val visibleItems: List<SharedItem> get() = itemsInFolder(items, if (invalid) null else manifest, folderId)

    val visibleFolders: List<Folder> get() = if (invalid) emptyList() else foldersIn(manifest, folderId)

    val path: List<Folder> get() = if (manifest == null || folderId == null) emptyList() else folderPath(manifest, folderId)

    val opened: SharedItem? get() = detail?.let { id -> items.firstOrNull { it.shareId == id } }

    val menuFolder: Folder? get() = folderMenu?.let { id -> manifest?.let { m -> folderPath(m, id).lastOrNull() } }
}

data class SharedState(
    val loaded: Boolean = false,
    val friendships: List<ConnectionItem> = emptyList(),
    val failure: ItemFailure? = null,
    val openId: String? = null,
    val space: SpaceState? = null,
    val organises: Boolean = true,
    val copyable: Set<SharedType> = emptySet(),
) {
    val openFriendship: ConnectionItem? get() = openId?.let { id -> friendships.firstOrNull { it.id == id } }
}

class SharedViewModel(
    private val services: SharingServices,
    private val newId: () -> String = ::newItemId,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SharedState(organises = services.holdsSharing, copyable = services.copyable))

    val state: StateFlow<SharedState> = mutableState.asStateFlow()

    private var attempts = 0

    fun opened() {
        mutableState.update { it.copy(failure = null) }
        viewModelScope.launch {
            val result = guarded({ ConnectionsLoad.Failed(it) }) { services.loadConnections() }
            when (result) {
                is ConnectionsLoad.Failed -> mutableState.update { it.copy(loaded = true, failure = result.failure) }
                is ConnectionsLoad.Loaded -> {
                    val accepted = friendships(result.items)
                    mutableState.update { state ->
                        val stillThere = state.openId != null && accepted.any { it.id == state.openId }
                        if (stillThere) {
                            state.copy(loaded = true, friendships = accepted)
                        } else {
                            state.copy(loaded = true, friendships = accepted, openId = null, space = null)
                        }
                    }
                    mutableState.value.openId?.let { loadSpace(it) }
                    catchUp(result.items)
                }
            }
        }
    }

    private fun catchUp(items: List<ConnectionItem>) {
        val ids = items.filter { it.standing != ConnectionStanding.WaitingForMe }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                services.catchUp(ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun openFriendship(item: ConnectionItem) {
        mutableState.update { it.copy(openId = item.id, space = SpaceState()) }
        loadSpace(item.id)
    }

    private fun loadSpace(connectionId: String) {
        val attempt = ++attempts
        viewModelScope.launch {
            val result = guarded({ SpaceLoad.Failed(it) }) { services.loadSpace(connectionId) }
            if (attempt != attempts || mutableState.value.openId != connectionId) return@launch
            mutableState.update { state ->
                val space = state.space ?: SpaceState()
                when (result) {
                    is SpaceLoad.Failed -> state.copy(space = space.copy(loading = false, problem = SpaceProblem.Failure(result.failure)))
                    is SpaceLoad.Loaded -> {
                        val folderId = space.folderId?.takeIf { id -> result.manifest != null && folderPath(result.manifest, id).isNotEmpty() }
                        state.copy(
                            space = space.copy(
                                loading = false,
                                items = result.items,
                                manifest = result.manifest,
                                organised = result.organised,
                                invalid = result.invalid,
                                folderId = folderId,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun closeFriendship() {
        attempts++
        mutableState.update { it.copy(openId = null, space = null) }
    }

    fun back(): Boolean {
        val state = mutableState.value
        val space = state.space ?: return false
        when {
            space.busy -> Unit
            space.detail != null -> closeDetail()
            space.moving != null -> cancelMove()
            space.form != null -> cancelForm()
            space.folderId != null -> goUp()
            else -> closeFriendship()
        }
        return true
    }

    private fun updateSpace(change: (SpaceState) -> SpaceState) = mutableState.update { state ->
        state.space?.let { state.copy(space = change(it)) } ?: state
    }

    fun openFolder(id: String) = updateSpace { it.copy(folderId = id, notice = null, problem = null) }

    fun openTop() = updateSpace { it.copy(folderId = null, notice = null, problem = null) }

    private fun goUp() = updateSpace { space ->
        val parent = space.manifest?.let { m -> space.folderId?.let { id -> folderPath(m, id).lastOrNull()?.parentId } }
        space.copy(folderId = parent)
    }

    fun dismissNotice() = updateSpace { it.copy(notice = null) }

    fun dismissProblem() = updateSpace { it.copy(problem = null) }

    fun openItem(item: SharedItem) {
        if (!item.readable) return
        updateSpace { it.copy(detail = item.shareId, revealed = false, notice = null, problem = null) }
    }

    fun closeDetail() = updateSpace { if (it.busy) it else it.copy(detail = null, revealed = false) }

    fun toggleRevealed() = updateSpace { it.copy(revealed = !it.revealed) }

    fun startCreateFolder() = updateSpace { if (it.invalid || !it.organised) it else it.copy(form = FolderForm.Create(), problem = null) }

    fun startRenameFolder(folder: Folder) = updateSpace {
        it.copy(form = FolderForm.Rename(folder.id, folder.name), folderMenu = null, problem = null)
    }

    fun draftChanged(value: String) = updateSpace { space ->
        val form = when (val current = space.form) {
            is FolderForm.Create -> current.copy(draft = value)
            is FolderForm.Rename -> current.copy(draft = value)
            null -> null
        }
        space.copy(form = form)
    }

    fun cancelForm() = updateSpace { if (it.busy) it else it.copy(form = null) }

    fun submitForm() {
        val space = mutableState.value.space ?: return
        val form = space.form ?: return
        if (space.busy || form.draft.trim().isEmpty()) return
        val edit = when (form) {
            is FolderForm.Create -> createFolder(form.draft, space.folderId, newId())
            is FolderForm.Rename -> renameFolder(form.folderId, form.draft)
        }
        editFolders(edit) { it.copy(form = null) }
    }

    fun openFolderMenu(folder: Folder) = updateSpace { it.copy(folderMenu = folder.id) }

    fun closeFolderMenu() = updateSpace { it.copy(folderMenu = null) }

    fun askDeleteFolder(folder: Folder) = updateSpace { it.copy(confirmingFolderDelete = folder, folderMenu = null) }

    fun cancelDeleteFolder() = updateSpace { if (it.busy) it else it.copy(confirmingFolderDelete = null) }

    fun confirmDeleteFolder() {
        val space = mutableState.value.space ?: return
        val folder = space.confirmingFolderDelete ?: return
        if (space.busy) return
        editFolders(deleteFolder(folder.id)) { it.copy(confirmingFolderDelete = null) }
    }

    fun startMove(item: SharedItem) = updateSpace { if (it.invalid || !it.organised) it else it.copy(moving = item, detail = null, problem = null) }

    fun cancelMove() = updateSpace { if (it.busy) it else it.copy(moving = null) }

    fun moveTo(folderId: String?) {
        val space = mutableState.value.space ?: return
        val item = space.moving ?: return
        if (space.busy) return
        editFolders(placeItem(item.shareId, folderId)) { it.copy(moving = null) }
    }

    fun resetFolders() {
        val connectionId = mutableState.value.openId ?: return
        val space = mutableState.value.space ?: return
        if (space.busy) return
        updateSpace { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            val result = guarded({ FolderChange.Failed(it) }) { services.resetFolders(connectionId) }
            applyFolderChange(connectionId, result) { it.copy(invalid = false) }
        }
    }

    private fun editFolders(edit: zekke.core.folders.FolderEdit, onDone: (SpaceState) -> SpaceState) {
        val connectionId = mutableState.value.openId ?: return
        updateSpace { it.copy(busy = true, problem = null) }
        viewModelScope.launch {
            val result = guarded({ FolderChange.Failed(it) }) { services.editFolders(connectionId, edit) }
            applyFolderChange(connectionId, result, onDone)
        }
    }

    private fun applyFolderChange(connectionId: String, result: FolderChange, onDone: (SpaceState) -> SpaceState) {
        if (mutableState.value.openId != connectionId) return
        updateSpace { space ->
            when (result) {
                is FolderChange.Done -> {
                    val reachable = space.folderId == null || folderPath(result.manifest, space.folderId).isNotEmpty()
                    onDone(space).copy(busy = false, manifest = result.manifest, folderId = if (reachable) space.folderId else null)
                }
                is FolderChange.Refused -> space.copy(busy = false, problem = SpaceProblem.Folders(result.problem))
                is FolderChange.Failed -> space.copy(busy = false, problem = SpaceProblem.Failure(result.failure))
            }
        }
    }

    fun copy(item: SharedItem) {
        val connectionId = mutableState.value.openId ?: return
        val space = mutableState.value.space ?: return
        if (space.busy || !canCopy(item, mutableState.value.copyable)) return
        updateSpace { it.copy(busy = true, notice = null, problem = null) }
        viewModelScope.launch {
            val result = guarded({ CopyOutcome.Failed(it) }) { services.copyToMyAccount(connectionId, item) }
            if (mutableState.value.openId != connectionId) return@launch
            updateSpace { space ->
                when (result) {
                    CopyOutcome.Copied -> space.copy(busy = false, detail = null, notice = SpaceNotice.Copied(item.name.orEmpty()))
                    CopyOutcome.TooLarge -> space.copy(busy = false, problem = SpaceProblem.TooLarge)
                    CopyOutcome.NoRoom -> space.copy(busy = false, problem = SpaceProblem.NoRoom)
                    is CopyOutcome.Failed -> space.copy(busy = false, problem = SpaceProblem.Failure(result.failure))
                }
            }
        }
    }

    fun save(item: SharedItem, target: SaveTarget) {
        val connectionId = mutableState.value.openId ?: return
        val space = mutableState.value.space ?: return
        if (space.busy || !canDownload(item)) {
            target.discard()
            return
        }
        updateSpace { it.copy(busy = true, detail = null, notice = null, problem = null) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.saveTo(connectionId, item, target) }
            if (mutableState.value.openId != connectionId) return@launch
            updateSpace { space ->
                when (result) {
                    SharingWrite.Done -> space.copy(busy = false, notice = SpaceNotice.Saved(item.name.orEmpty()))
                    is SharingWrite.Failed -> space.copy(busy = false, problem = SpaceProblem.Failure(result.failure))
                }
            }
        }
    }

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    private suspend fun <T> guarded(failed: (ItemFailure) -> T, call: suspend () -> T): T = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(ItemFailure.Failed)
    }
}
