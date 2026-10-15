package zekke.android.feature.tabs

import kotlinx.coroutines.flow.Flow
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.FolderManifestProblem

sealed interface TabsLoad {
    data class Loaded(val manifest: FolderManifest) : TabsLoad

    data class Invalid(val problem: FolderManifestProblem) : TabsLoad

    data class Failed(val failure: ItemFailure) : TabsLoad
}

interface FolderTabsServices {
    val canDeleteItems: Boolean

    val changes: Flow<Unit>

    suspend fun load(): TabsLoad

    suspend fun edit(edit: FolderEdit): TabsLoad

    suspend fun reset(): TabsLoad
}
