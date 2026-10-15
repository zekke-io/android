package zekke.android.items

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.feature.tabs.TabsLoad
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.FolderManifestInvalidException
import zekke.core.folders.FolderStore
import zekke.core.folders.ManifestScope
import zekke.core.items.ItemContext

class CoreFolderTabsServices(
    private val manifestScope: ManifestScope,
    private val sync: SyncService,
    private val calls: ItemCalls,
    private val context: ItemContext,
) : FolderTabsServices {
    override val canDeleteItems: Boolean get() = context.session.isFullDevice

    override val changes: Flow<Unit> = sync.changes.filter { it == manifestScope.feedScope }.map { }

    override suspend fun load(): TabsLoad {
        val store = sync.folderStore() ?: return TabsLoad.Failed(ItemFailure.Failed)
        val replica = sync.replica() ?: return TabsLoad.Failed(ItemFailure.Failed)
        return outcome(calls.read { checked { store.loadFromReplica(manifestScope, replica) } })
    }

    override suspend fun edit(edit: FolderEdit): TabsLoad {
        val store = sync.folderStore() ?: return TabsLoad.Failed(ItemFailure.Failed)
        return outcome(calls.write { checked { store.edit(manifestScope, edit) } }).also { if (it is TabsLoad.Loaded) sync.syncNow() }
    }

    override suspend fun reset(): TabsLoad {
        val store = sync.folderStore() ?: return TabsLoad.Failed(ItemFailure.Failed)
        return outcome(calls.write { checked { store.reset(manifestScope) } }).also { if (it is TabsLoad.Loaded) sync.syncNow() }
    }

    private suspend fun checked(load: suspend () -> FolderManifest): TabsLoad = try {
        TabsLoad.Loaded(load())
    } catch (error: FolderManifestInvalidException) {
        TabsLoad.Invalid(error.problem)
    }

    private fun outcome(result: ItemResult<TabsLoad>): TabsLoad = when (result) {
        is ItemResult.Done -> result.value
        is ItemResult.Failed -> TabsLoad.Failed(result.failure)
    }
}
