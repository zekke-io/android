package zekke.android.feature.sharing

import java.io.OutputStream
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.Folder
import zekke.core.folders.FolderEditProblem
import zekke.core.folders.FolderManifest
import zekke.core.folders.childFolders
import zekke.core.folders.folderOf
import zekke.core.folders.liveFolders
import zekke.core.sharing.SHARED_FOLDER_RULES

enum class SharedType(val wire: String) {
    Secret("secret"),
    Note("note"),
    Document("document"),
    File("file"),
}

fun sharedTypeOf(wire: String): SharedType? = SharedType.entries.firstOrNull { it.wire == wire }

data class SharedItem(
    val shareId: String,
    val itemId: String,
    val type: SharedType?,
    val inbound: Boolean,
    val name: String?,
    val readable: Boolean,
    val problem: String?,
    val sizeBytes: Long?,
    val mime: String?,
    val text: String?,
)

class SaveTarget(val open: () -> OutputStream, val discard: () -> Unit)

data class FolderChoice(val id: String, val name: String, val depth: Int)

sealed interface SpaceLoad {
    data class Loaded(
        val items: List<SharedItem>,
        val manifest: FolderManifest?,
        val organised: Boolean,
        val invalid: Boolean,
    ) : SpaceLoad

    data class Failed(val failure: ItemFailure) : SpaceLoad
}

sealed interface FolderChange {
    data class Done(val manifest: FolderManifest) : FolderChange

    data class Refused(val problem: FolderEditProblem) : FolderChange

    data class Failed(val failure: ItemFailure) : FolderChange
}

data class Recipient(val shareId: String, val username: String)

sealed interface SendTargets {
    data class Loaded(val connections: List<ConnectionItem>, val recipients: List<Recipient>) : SendTargets

    data class Failed(val failure: ItemFailure) : SendTargets
}

sealed interface SendOutcome {
    data object Sent : SendOutcome

    data object SentNotFiled : SendOutcome

    data class NotTrusted(val alarm: TrustAlarm?) : SendOutcome

    data object NotShared : SendOutcome

    data class Failed(val failure: ItemFailure) : SendOutcome
}

sealed interface CopyOutcome {
    data object Copied : CopyOutcome

    data object TooLarge : CopyOutcome

    data object NoRoom : CopyOutcome

    data class Failed(val failure: ItemFailure) : CopyOutcome
}

fun canCopy(item: SharedItem, copyable: Set<SharedType>): Boolean =
    item.inbound && item.readable && item.type != null && item.type != SharedType.Document && item.type in copyable

fun canDownload(item: SharedItem): Boolean = item.type == SharedType.File && item.readable

fun itemsInFolder(items: List<SharedItem>, manifest: FolderManifest?, folderId: String?): List<SharedItem> =
    if (manifest == null) {
        if (folderId == null) items else emptyList()
    } else {
        items.filter { folderOf(manifest, it.shareId, SHARED_FOLDER_RULES) == folderId }
    }

fun foldersIn(manifest: FolderManifest?, parentId: String?): List<Folder> =
    if (manifest == null) emptyList() else childFolders(manifest, parentId)

fun folderChoices(manifest: FolderManifest): List<FolderChoice> {
    val folders = liveFolders(manifest)
    val choices = ArrayList<FolderChoice>()
    fun walk(parentId: String?, depth: Int) {
        folders.filter { it.parentId == parentId }.forEach { folder ->
            choices += FolderChoice(folder.id, folder.name, depth)
            walk(folder.id, depth + 1)
        }
    }
    walk(null, 0)
    return choices
}

fun friendships(items: List<ConnectionItem>): List<ConnectionItem> =
    items.filter { it.standing == ConnectionStanding.Accepted }.sortedBy { it.label.lowercase() }
