package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zekke.android.feature.sharing.ConnectionItem
import zekke.android.feature.sharing.ConnectionsLoad
import zekke.android.feature.sharing.InviteOutcome
import zekke.android.feature.sharing.ReviewLoad
import zekke.android.feature.sharing.SharingServices
import zekke.android.feature.sharing.SharingWrite
import zekke.android.feature.sharing.TrustAlarm
import zekke.android.feature.sharing.alarmOf
import zekke.android.feature.sharing.fingerprintOf
import zekke.android.feature.sharing.standingOf
import zekke.android.ui.components.ItemFailure
import zekke.core.items.ItemContext
import zekke.core.sharing.AlreadyConnectedException
import zekke.core.sharing.ConnectionNotTrustedException
import zekke.core.sharing.ConnectionRecord
import zekke.core.sharing.SharingService
import zekke.core.sharing.UnknownRecipientException
import zekke.core.sharing.deleteConnection
import zekke.core.sharing.displayName
import zekke.core.sharing.setNickname
import zekke.core.users.MalformedUsernameException

fun inviteOutcomeOf(error: Exception): InviteOutcome? = when (error) {
    is UnknownRecipientException, is MalformedUsernameException -> InviteOutcome.UnknownUsername
    is AlreadyConnectedException -> InviteOutcome.AlreadyConnected
    is ConnectionNotTrustedException -> InviteOutcome.NotTrusted(alarmOf(error.trust))
    else -> null
}

class CoreSharingServices(
    private val sharing: SharingService,
    private val calls: ItemCalls,
    private val context: ItemContext,
) : SharingServices {
    private val records = HashMap<String, ConnectionRecord>()

    override val canDisconnect: Boolean get() = context.session.isFullDevice

    override suspend fun loadConnections(): ConnectionsLoad = calls.read {
        val loaded = sharing.connections()
        val book = sharing.loadAddressBook()
        synchronized(records) {
            records.clear()
            loaded.forEach { records[it.id] = it }
        }
        loaded.map { ConnectionItem(it.id, it.username, displayName(book.nicknames[it.id]), standingOf(it)) }
    }.fold({ ConnectionsLoad.Loaded(it) }, { ConnectionsLoad.Failed(it) })

    private fun record(id: String): ConnectionRecord =
        synchronized(records) { records[id] }?.let(sharing::current) ?: throw NoSuchElementException(id)

    override suspend fun checkTrust(ids: List<String>): Map<String, TrustAlarm?> = calls.read {
        coroutineScope {
            ids.map { id ->
                async {
                    try {
                        id to alarmOf(sharing.verifyConnection(record(id)))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }.fold({ it }, { emptyMap() })

    override suspend fun catchUp(ids: List<String>) {
        calls.write {
            ids.forEach { id ->
                try {
                    sharing.reestablishConnection(record(id))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun invite(username: String): InviteOutcome = calls.write {
        val existing = synchronized(records) { records.values.toList() }
        try {
            InviteOutcome.Sent(sharing.inviteByUsername(username, existing).connection.username)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            inviteOutcomeOf(error) ?: throw error
        }
    }.fold({ it }, { InviteOutcome.Failed(it) })

    override suspend fun review(id: String): ReviewLoad = calls.read {
        val trust = sharing.verifyConnection(record(id))
        ReviewLoad.Ready(fingerprintOf(trust), sharing.ownRootFingerprint(), alarmOf(trust))
    }.fold({ it }, { ReviewLoad.Failed(it) })

    private fun <T> ItemResult<T>.written(): SharingWrite = fold({ SharingWrite.Done }, { SharingWrite.Failed(it) })

    override suspend fun accept(id: String): SharingWrite = calls.write { sharing.acceptInvitation(record(id)) }.written()

    override suspend fun disconnect(id: String): SharingWrite = calls.write { deleteConnection(context, id) }.written()

    override suspend fun rename(id: String, name: String): SharingWrite =
        calls.write { sharing.editAddressBook(setNickname(id, name)) }.written()
}
