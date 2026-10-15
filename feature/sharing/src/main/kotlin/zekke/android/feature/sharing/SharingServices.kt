package zekke.android.feature.sharing

import zekke.android.ui.components.ItemFailure
import zekke.core.sharing.ConnectionDirection
import zekke.core.sharing.ConnectionRecord
import zekke.core.sharing.ConnectionStatus
import zekke.core.sharing.ConnectionTrust

enum class ConnectionStanding { Accepted, WaitingForMe, WaitingForThem }

data class ConnectionItem(
    val id: String,
    val username: String,
    val nickname: String?,
    val standing: ConnectionStanding,
) {
    val label: String get() = nickname ?: username

    val shouldBeChecked: Boolean get() = standing != ConnectionStanding.WaitingForMe
}

data class ConnectionGroups(
    val awaitingMe: List<ConnectionItem>,
    val awaitingThem: List<ConnectionItem>,
    val accepted: List<ConnectionItem>,
)

enum class TrustAlarm(val danger: Boolean) {
    RootChanged(true),
    ProofInvalid(true),
    AccountChanged(true),
    Unresolvable(false),
}

fun alarmOf(trust: ConnectionTrust): TrustAlarm? = when (trust) {
    is ConnectionTrust.Trusted, is ConnectionTrust.Unpinned -> null
    is ConnectionTrust.RootChanged -> TrustAlarm.RootChanged
    is ConnectionTrust.ProofInvalid -> TrustAlarm.ProofInvalid
    ConnectionTrust.AccountChanged -> TrustAlarm.AccountChanged
    ConnectionTrust.Unresolvable -> TrustAlarm.Unresolvable
}

fun fingerprintOf(trust: ConnectionTrust): String? = when (trust) {
    is ConnectionTrust.Trusted -> trust.fingerprint
    is ConnectionTrust.Unpinned -> trust.fingerprint
    is ConnectionTrust.RootChanged -> trust.fingerprint
    is ConnectionTrust.ProofInvalid -> trust.fingerprint
    ConnectionTrust.AccountChanged, ConnectionTrust.Unresolvable -> null
}

fun standingOf(record: ConnectionRecord): ConnectionStanding = when {
    record.status == ConnectionStatus.ACCEPTED -> ConnectionStanding.Accepted
    record.direction == ConnectionDirection.INBOUND -> ConnectionStanding.WaitingForMe
    else -> ConnectionStanding.WaitingForThem
}

fun groupConnections(items: List<ConnectionItem>): ConnectionGroups = ConnectionGroups(
    awaitingMe = items.filter { it.standing == ConnectionStanding.WaitingForMe },
    awaitingThem = items.filter { it.standing == ConnectionStanding.WaitingForThem },
    accepted = items.filter { it.standing == ConnectionStanding.Accepted },
)

sealed interface ConnectionsLoad {
    data class Loaded(val items: List<ConnectionItem>) : ConnectionsLoad

    data class Failed(val failure: ItemFailure) : ConnectionsLoad
}

sealed interface InviteOutcome {
    data class Sent(val username: String) : InviteOutcome

    data object UnknownUsername : InviteOutcome

    data object AlreadyConnected : InviteOutcome

    data class NotTrusted(val alarm: TrustAlarm?) : InviteOutcome

    data class Failed(val failure: ItemFailure) : InviteOutcome
}

sealed interface ReviewLoad {
    data class Ready(val theirs: String?, val mine: String, val alarm: TrustAlarm?) : ReviewLoad

    data class Failed(val failure: ItemFailure) : ReviewLoad
}

sealed interface SharingWrite {
    data object Done : SharingWrite

    data class Failed(val failure: ItemFailure) : SharingWrite
}

interface SharingServices {
    val canDisconnect: Boolean

    val holdsSharing: Boolean

    val copyable: Set<SharedType>

    suspend fun loadConnections(): ConnectionsLoad

    suspend fun checkTrust(ids: List<String>): Map<String, TrustAlarm?>

    suspend fun catchUp(ids: List<String>)

    suspend fun invite(username: String): InviteOutcome

    suspend fun review(id: String): ReviewLoad

    suspend fun accept(id: String): SharingWrite

    suspend fun disconnect(id: String): SharingWrite

    suspend fun rename(id: String, name: String): SharingWrite

    suspend fun loadSpace(connectionId: String): SpaceLoad

    suspend fun editFolders(connectionId: String, edit: zekke.core.folders.FolderEdit): FolderChange

    suspend fun resetFolders(connectionId: String): FolderChange

    suspend fun copyToMyAccount(connectionId: String, item: SharedItem): CopyOutcome

    suspend fun saveTo(connectionId: String, item: SharedItem, target: SaveTarget): SharingWrite

    suspend fun sendTargets(type: SharedType, itemId: String): SendTargets

    suspend fun folderChoicesOf(connectionId: String): List<FolderChoice>

    suspend fun send(connectionId: String, type: SharedType, itemId: String, folderId: String?): SendOutcome

    suspend fun unshare(shareId: String): SharingWrite

    fun systemScreenWillOpen()

    fun systemScreenReturned()
}
