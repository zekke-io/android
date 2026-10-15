package zekke.android.feature.sharing

import kotlinx.coroutines.CompletableDeferred
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.emptyFolderManifest
import zekke.core.sharing.SHARED_FOLDER_RULES

const val AT = "2026-10-01T00:00:00.000Z"

fun connection(
    id: String,
    username: String,
    standing: ConnectionStanding = ConnectionStanding.Accepted,
    nickname: String? = null,
) = ConnectionItem(id, username, nickname, standing)

fun sharedItem(
    shareId: String,
    name: String?,
    type: SharedType? = SharedType.Note,
    inbound: Boolean = true,
    readable: Boolean = true,
    text: String? = "body of $shareId",
    sizeBytes: Long? = null,
) = SharedItem(shareId, "item-$shareId", type, inbound, name, readable, null, sizeBytes, null, text)

class FakeSharingServices(
    override var canDisconnect: Boolean = true,
    override var holdsSharing: Boolean = true,
    override var copyable: Set<SharedType> = setOf(SharedType.Secret, SharedType.Note, SharedType.File),
) : SharingServices {
    var items: List<ConnectionItem> = emptyList()
    var loadFailure: ItemFailure? = null
    var loads = 0
    var alarms: Map<String, TrustAlarm?> = emptyMap()
    var checkFailure: Exception? = null
    var checked = mutableListOf<List<String>>()
    var caughtUp = mutableListOf<List<String>>()
    var catchUpFailure: Exception? = null
    var inviteOutcome: InviteOutcome? = null
    var invited = mutableListOf<String>()
    var reviewResult: ReviewLoad = ReviewLoad.Ready("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", "1111-2222-3333-4444-5555-6666", null)
    var accepted = mutableListOf<String>()
    var disconnected = mutableListOf<String>()
    var renamed = mutableListOf<Pair<String, String>>()
    var writeResult: SharingWrite = SharingWrite.Done
    var gate: CompletableDeferred<Unit>? = null
    var reviewGate: CompletableDeferred<Unit>? = null
    var loadGate: CompletableDeferred<Unit>? = null

    override suspend fun loadConnections(): ConnectionsLoad {
        loads++
        loadGate?.await()
        return loadFailure?.let { ConnectionsLoad.Failed(it) } ?: ConnectionsLoad.Loaded(items)
    }

    override suspend fun checkTrust(ids: List<String>): Map<String, TrustAlarm?> {
        checked += ids
        checkFailure?.let { throw it }
        return alarms
    }

    override suspend fun catchUp(ids: List<String>) {
        caughtUp += ids
        catchUpFailure?.let { throw it }
    }

    override suspend fun invite(username: String): InviteOutcome {
        invited += username
        gate?.await()
        return inviteOutcome ?: InviteOutcome.Sent(username)
    }

    override suspend fun review(id: String): ReviewLoad {
        reviewGate?.await()
        return reviewResult
    }

    private suspend fun outcome(): SharingWrite {
        gate?.await()
        return writeResult
    }

    override suspend fun accept(id: String): SharingWrite {
        accepted += id
        return outcome()
    }

    override suspend fun disconnect(id: String): SharingWrite {
        disconnected += id
        return outcome()
    }

    override suspend fun rename(id: String, name: String): SharingWrite {
        renamed += id to name
        return outcome()
    }

    var space: SpaceLoad = SpaceLoad.Loaded(emptyList(), emptyFolderManifest(SHARED_FOLDER_RULES, AT), true, false)
    var spaceLoads = mutableListOf<String>()
    var spaceGate: CompletableDeferred<Unit>? = null
    var folderResult: FolderChange? = null
    var edits = mutableListOf<Pair<String, FolderEdit>>()
    var resets = mutableListOf<String>()
    var copied = mutableListOf<Pair<String, SharedItem>>()
    var copyResult: CopyOutcome = CopyOutcome.Copied
    var saved = mutableListOf<Pair<String, SharedItem>>()
    var saveResult: SharingWrite = SharingWrite.Done
    var pickerOpened = 0
    var pickerReturned = 0
    private var current: FolderManifest? = null

    override suspend fun loadSpace(connectionId: String): SpaceLoad {
        spaceLoads += connectionId
        val result = space
        spaceGate?.await()
        return result
    }

    override suspend fun editFolders(connectionId: String, edit: FolderEdit): FolderChange {
        edits += connectionId to edit
        gate?.await()
        folderResult?.let { return it }
        val base = current ?: (space as? SpaceLoad.Loaded)?.manifest ?: emptyFolderManifest(SHARED_FOLDER_RULES, AT)
        val next = edit.apply(base, SHARED_FOLDER_RULES)
        current = next
        return FolderChange.Done(next)
    }

    override suspend fun resetFolders(connectionId: String): FolderChange {
        resets += connectionId
        gate?.await()
        return folderResult ?: FolderChange.Done(emptyFolderManifest(SHARED_FOLDER_RULES, AT))
    }

    override suspend fun copyToMyAccount(connectionId: String, item: SharedItem): CopyOutcome {
        copied += connectionId to item
        gate?.await()
        return copyResult
    }

    override suspend fun saveTo(connectionId: String, item: SharedItem, target: SaveTarget): SharingWrite {
        saved += connectionId to item
        gate?.await()
        return saveResult
    }

    override fun systemScreenWillOpen() {
        pickerOpened++
    }

    override fun systemScreenReturned() {
        pickerReturned++
    }

    var targets: SendTargets = SendTargets.Loaded(emptyList(), emptyList())
    var targetsLoads = 0
    var choicesFor: List<FolderChoice> = emptyList()
    var sendOutcome: SendOutcome = SendOutcome.Sent
    var sent = mutableListOf<List<Any?>>()
    var unshared = mutableListOf<String>()
    var unshareResult: SharingWrite = SharingWrite.Done

    override suspend fun sendTargets(type: SharedType, itemId: String): SendTargets {
        targetsLoads++
        return targets
    }

    override suspend fun folderChoicesOf(connectionId: String): List<FolderChoice> = choicesFor

    override suspend fun send(connectionId: String, type: SharedType, itemId: String, folderId: String?): SendOutcome {
        sent += listOf(connectionId, type, itemId, folderId)
        gate?.await()
        return sendOutcome
    }

    override suspend fun unshare(shareId: String): SharingWrite {
        unshared += shareId
        gate?.await()
        return unshareResult
    }
}
