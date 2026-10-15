package zekke.android.feature.sharing

import kotlinx.coroutines.CompletableDeferred
import zekke.android.ui.components.ItemFailure

fun connection(
    id: String,
    username: String,
    standing: ConnectionStanding = ConnectionStanding.Accepted,
    nickname: String? = null,
) = ConnectionItem(id, username, nickname, standing)

class FakeSharingServices(override var canDisconnect: Boolean = true) : SharingServices {
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
}
