package zekke.android.feature.tabs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.FolderManifestProblem
import zekke.core.folders.FolderRules
import zekke.core.folders.FolderTab
import zekke.core.folders.HOME_FOLDER_ID
import zekke.core.folders.MAX_FOLDER_NAME_LENGTH
import zekke.core.folders.activeTabOf
import zekke.core.folders.createFolder
import zekke.core.folders.deleteFolder
import zekke.core.folders.folderTabs
import zekke.core.folders.forgetItems
import zekke.core.folders.itemsInTab
import zekke.core.folders.placeItem
import zekke.core.folders.renameFolder
import zekke.core.items.newItemId

sealed interface TabNameProblem {
    data object Empty : TabNameProblem

    data class TooLong(val limit: Int) : TabNameProblem

    data class Clash(val existing: String) : TabNameProblem
}

sealed interface TabDeleteRefusal {
    data object Home : TabDeleteRefusal

    data class NeedsFullDevice(val name: String) : TabDeleteRefusal
}

data class TabNaming(val renaming: String?, val name: String)

data class TabsState(
    val manifest: FolderManifest? = null,
    val invalid: FolderManifestProblem? = null,
    val failure: ItemFailure? = null,
    val busy: Boolean = false,
    val wanted: String? = null,
    val managing: String? = null,
    val naming: TabNaming? = null,
    val deleting: String? = null,
)

class FolderTabsController(
    private val services: FolderTabsServices,
    private val rules: FolderRules,
    private val scope: CoroutineScope,
    private val newId: () -> String = ::newItemId,
) {
    private val mutableState = MutableStateFlow(TabsState())

    val state: StateFlow<TabsState> = mutableState.asStateFlow()

    val canDeleteItems: Boolean get() = services.canDeleteItems

    init {
        scope.launch { services.changes.collect { reload() } }
        reload()
    }

    fun reload() {
        scope.launch { received(attempt { services.load() }) }
    }

    fun tabs(itemIds: Collection<String>): List<FolderTab> =
        mutableState.value.manifest?.let { folderTabs(it, itemIds, rules) } ?: emptyList()

    fun active(itemIds: Collection<String>): String = activeTabOf(tabs(itemIds), mutableState.value.wanted)

    fun <T> filter(items: List<T>, idOf: (T) -> String): List<T> {
        val manifest = mutableState.value.manifest ?: return items
        val active = active(items.map(idOf))
        val ids = itemsInTab(manifest, active, items.map(idOf), rules).toSet()
        return items.filter { idOf(it) in ids }
    }

    fun select(id: String) = mutableState.update { it.copy(wanted = id) }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun startNew() = mutableState.update { it.copy(naming = TabNaming(null, ""), managing = null) }

    fun manage(id: String) = mutableState.update { it.copy(managing = id) }

    fun stopManaging() = mutableState.update { it.copy(managing = null) }

    fun startRename(tab: FolderTab) = mutableState.update { it.copy(naming = TabNaming(tab.id, tab.name), managing = null) }

    fun startDelete(tab: FolderTab) = mutableState.update { it.copy(deleting = tab.id, managing = null) }

    fun nameChanged(name: String) = mutableState.update { state -> state.naming?.let { state.copy(naming = it.copy(name = name)) } ?: state }

    fun cancelNaming() = mutableState.update { if (it.busy) it else it.copy(naming = null) }

    fun cancelDelete() = mutableState.update { if (it.busy) it else it.copy(deleting = null) }

    fun nameProblem(tabs: List<FolderTab>): TabNameProblem? {
        val naming = mutableState.value.naming ?: return null
        val trimmed = naming.name.trim()
        if (trimmed.isEmpty()) return TabNameProblem.Empty
        if (trimmed.length > MAX_FOLDER_NAME_LENGTH) return TabNameProblem.TooLong(MAX_FOLDER_NAME_LENGTH)
        val clash = tabs.firstOrNull { it.id != naming.renaming && it.name.trim().lowercase() == trimmed.lowercase() }
        return clash?.let { TabNameProblem.Clash(it.name) }
    }

    fun deleteRefusal(tab: FolderTab): TabDeleteRefusal? = when {
        tab.home -> TabDeleteRefusal.Home
        tab.count > 0 && !services.canDeleteItems -> TabDeleteRefusal.NeedsFullDevice(tab.name)
        else -> null
    }

    fun commitName(tabs: List<FolderTab>) {
        val naming = mutableState.value.naming ?: return
        if (mutableState.value.busy || nameProblem(tabs) != null) return
        val name = naming.name.trim()
        val created = newId()
        val edit = if (naming.renaming == null) createFolder(name, id = created) else renameFolder(naming.renaming, name)
        scope.launch {
            if (apply(edit)) {
                mutableState.update { it.copy(naming = null, wanted = if (naming.renaming == null) created else it.wanted) }
            }
        }
    }

    fun confirmDelete(tabs: List<FolderTab>, itemIds: Collection<String>, deleteItems: suspend (List<String>) -> ItemFailure?) {
        val id = mutableState.value.deleting ?: return
        val tab = tabs.firstOrNull { it.id == id } ?: return
        if (mutableState.value.busy || deleteRefusal(tab) != null) return
        val manifest = mutableState.value.manifest ?: return
        val contained = itemsInTab(manifest, id, itemIds, rules)
        scope.launch {
            mutableState.update { it.copy(busy = true, failure = null) }
            val failure = if (contained.isEmpty()) null else deleteItems(contained)
            if (failure != null) {
                mutableState.update { it.copy(busy = false, failure = failure, deleting = null) }
                return@launch
            }
            mutableState.update { it.copy(busy = false) }
            val removed = apply { current, currentRules -> forgetItems(contained).apply(deleteFolder(id).apply(current, currentRules), currentRules) }
            if (removed) mutableState.update { it.copy(deleting = null, wanted = null) } else mutableState.update { it.copy(deleting = null) }
        }
    }

    fun move(itemIds: List<String>, tabId: String) {
        scope.launch {
            apply { current, currentRules -> itemIds.fold(current) { next, id -> placeItem(id, tabId).apply(next, currentRules) } }
        }
    }

    suspend fun file(itemId: String, tabId: String): Boolean =
        if (tabId == HOME_FOLDER_ID) true else apply(placeItem(itemId, tabId))

    fun forget(itemIds: List<String>) {
        scope.launch { apply(forgetItems(itemIds)) }
    }

    fun reset() {
        scope.launch {
            mutableState.update { it.copy(busy = true) }
            received(attempt { services.reset() })
            mutableState.update { it.copy(busy = false, wanted = null) }
        }
    }

    private suspend fun apply(edit: FolderEdit): Boolean {
        mutableState.update { it.copy(busy = true, failure = null) }
        val result = attempt { services.edit(edit) }
        mutableState.update { it.copy(busy = false) }
        received(result)
        return result is TabsLoad.Loaded
    }

    private suspend fun attempt(call: suspend () -> TabsLoad): TabsLoad = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        TabsLoad.Failed(ItemFailure.Failed)
    }

    private fun received(result: TabsLoad) = mutableState.update {
        when (result) {
            is TabsLoad.Loaded -> it.copy(manifest = result.manifest, invalid = null)
            is TabsLoad.Invalid -> it.copy(manifest = null, invalid = result.problem)
            is TabsLoad.Failed -> it.copy(failure = result.failure)
        }
    }
}
