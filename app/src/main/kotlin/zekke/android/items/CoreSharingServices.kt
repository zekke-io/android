package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zekke.android.feature.sharing.CopyOutcome
import zekke.android.feature.sharing.ConnectionItem
import zekke.android.feature.sharing.FolderChange
import zekke.android.feature.sharing.SaveTarget
import zekke.android.feature.sharing.SharedItem
import zekke.android.feature.sharing.SharedType
import zekke.android.feature.sharing.SpaceLoad
import zekke.android.feature.sharing.sharedTypeOf
import zekke.android.feature.sharing.ConnectionsLoad
import zekke.android.feature.sharing.InviteOutcome
import zekke.android.feature.sharing.ReviewLoad
import zekke.android.feature.sharing.SharingServices
import zekke.android.feature.sharing.SharingWrite
import zekke.android.feature.sharing.TrustAlarm
import zekke.android.feature.sharing.alarmOf
import zekke.android.feature.sharing.fingerprintOf
import zekke.android.feature.sharing.standingOf
import zekke.android.session.SessionGuard
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.api.ApiError
import zekke.core.files.DownloadBufferExceededException
import zekke.core.files.ObjectStore
import zekke.core.files.PlaintextSink
import zekke.core.files.downloadFile
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderEditException
import zekke.core.folders.FolderManifest
import zekke.core.folders.FolderManifestInvalidException
import zekke.core.scopes.Scope
import zekke.core.scopes.ScopedItemType
import zekke.core.sharing.ConnectionDirection
import zekke.core.sharing.ReceivedItem
import zekke.core.sharing.ShareRecord
import zekke.core.sharing.listConnectionShares
import zekke.core.sharing.listInbox
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

private const val INSUFFICIENT_STORAGE = 507

fun inviteOutcomeOf(error: Exception): InviteOutcome? = when (error) {
    is UnknownRecipientException, is MalformedUsernameException -> InviteOutcome.UnknownUsername
    is AlreadyConnectedException -> InviteOutcome.AlreadyConnected
    is ConnectionNotTrustedException -> InviteOutcome.NotTrusted(alarmOf(error.trust))
    else -> null
}

fun sharedItemOf(received: ReceivedItem): SharedItem = SharedItem(
    shareId = received.shareId,
    itemId = received.itemId,
    type = sharedTypeOf(received.itemType),
    inbound = received.direction == ConnectionDirection.INBOUND,
    name = received.name,
    readable = received.readable,
    problem = received.problem,
    sizeBytes = received.sizeBytes,
    mime = received.mime,
    text = received.text,
)

const val MAX_COPY_FILE_BYTES = 64L shl 20

class CoreSharingServices(
    private val sharing: SharingService,
    private val calls: ItemCalls,
    private val context: ItemContext,
    private val store: ObjectStore,
    private val sync: SyncService,
    private val guard: SessionGuard,
) : SharingServices {
    private val records = HashMap<String, ConnectionRecord>()

    override val canDisconnect: Boolean get() = context.session.isFullDevice

    override val holdsSharing: Boolean get() = context.session.holds(Scope.SHARING)

    override val copyable: Set<SharedType> get() = sharing.sharableItemTypes().mapNotNull { sharedTypeOf(it.wire) }.toSet()

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

    private fun heldWires(): Set<String> = ScopedItemType.entries.filter { context.session.holds(it.scope) }.map { it.wire }.toSet()

    override suspend fun loadSpace(connectionId: String): SpaceLoad = calls.read {
        val connection = record(connectionId)
        val held = heldWires()
        val organised = holdsSharing
        val described: List<ReceivedItem> = coroutineScope {
            if (organised) {
                listConnectionShares(context, connection.id).filter { it.itemType in held }.map { share ->
                    async {
                        if (share.direction == ConnectionDirection.OUTBOUND) {
                            sharing.describeSent(share, connection)
                        } else {
                            sharing.describeReceived(ShareRecord(share.id, connection.id, share.itemType, share.itemId, share.wrappedDek, share.createdAt, connection.username), connection)
                        }
                    }
                }
            } else {
                listInbox(context).filter { it.connectionId == connection.id && it.itemType in held }.map { share ->
                    async { sharing.describeReceived(share, connection) }
                }
            }.awaitAll()
        }
        var invalid = false
        val manifest = if (organised) {
            try {
                sharing.loadSharedFolders(connection, fresh = true)
            } catch (_: FolderManifestInvalidException) {
                invalid = true
                null
            }
        } else {
            null
        }
        SpaceLoad.Loaded(described.map(::sharedItemOf), manifest, organised, invalid)
    }.fold({ it }, { SpaceLoad.Failed(it) })

    private suspend fun changed(connectionId: String, run: suspend (ConnectionRecord) -> FolderManifest): FolderChange = calls.write {
        try {
            FolderChange.Done(run(record(connectionId)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: FolderEditException) {
            FolderChange.Refused(error.problem)
        }
    }.fold({ it }, { FolderChange.Failed(it) })

    override suspend fun editFolders(connectionId: String, edit: FolderEdit): FolderChange =
        changed(connectionId) { sharing.editSharedFolders(it, edit) }

    override suspend fun resetFolders(connectionId: String): FolderChange = changed(connectionId) { sharing.resetSharedFolders(it) }

    override suspend fun copyToMyAccount(connectionId: String, item: SharedItem): CopyOutcome {
        if (item.type == SharedType.File && (item.sizeBytes ?: 0) > MAX_COPY_FILE_BYTES) return CopyOutcome.TooLarge
        val result = calls.write {
            try {
                val connection = record(connectionId)
                val share = ShareRecord(item.shareId, connectionId, item.type?.wire ?: throw IllegalStateException(), item.itemId)
                sharing.copySharedItem(connection, share, store)
                CopyOutcome.Copied
            } catch (error: CancellationException) {
                throw error
            } catch (error: DownloadBufferExceededException) {
                CopyOutcome.TooLarge
            } catch (error: ApiError) {
                if (error.status == INSUFFICIENT_STORAGE) CopyOutcome.NoRoom else throw error
            }
        }
        return result.fold(
            {
                if (it == CopyOutcome.Copied) sync.syncNow()
                it
            },
            { CopyOutcome.Failed(it) },
        )
    }

    override suspend fun saveTo(connectionId: String, item: SharedItem, target: SaveTarget): SharingWrite {
        val result = calls.write {
            target.open().use { out ->
                val sink = PlaintextSink { bytes, offset, length -> out.write(bytes, offset, length) }
                if (item.inbound) {
                    sharing.openSharedFile(record(connectionId), item.shareId, store, sink)
                } else {
                    downloadFile(context, item.itemId, store, sink)
                }
            }
        }
        return result.fold(
            { SharingWrite.Done },
            {
                target.discard()
                SharingWrite.Failed(it)
            },
        )
    }

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()
}
