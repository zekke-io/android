package zekke.android.sync

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import zekke.android.platform.LocalDatabases
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.api.ZekkeApi
import zekke.core.credentials.credentialRevisions
import zekke.core.credentials.currentRevisions
import zekke.core.feed.Feed
import zekke.core.items.ItemContext
import zekke.core.items.rewrapStaleEntries
import zekke.core.feed.FeedScope
import zekke.core.feed.ItemTypes
import zekke.core.feed.Outbox
import zekke.core.feed.OutboxEntry
import zekke.core.feed.OutboxStop
import zekke.core.feed.Replica
import zekke.core.notifications.unreadNotificationCount
import zekke.core.notes.noteRecords
import zekke.core.secrets.secretRecords
import zekke.core.session.SessionKeystore

class CoreSyncBackend(
    private val api: ZekkeApi,
    private val replicaStore: Replica,
    private val outbox: Outbox,
    private val feed: Feed,
    private val scopes: List<FeedScope>,
    private val drivers: List<Closeable> = emptyList(),
    private val items: ItemContext? = null,
) : SyncBackend {
    override val changes: Flow<FeedScope> get() = feed.changes

    override val replica: Replica get() = replicaStore

    override val outboxQueue: Outbox get() = outbox

    override suspend fun pulse(deep: Boolean): PulseResult {
        var failure: SyncFailure? = null
        var changed = false
        if (deep && items != null) {
            try {
                rewrapStaleEntries(items, outbox)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failureOf(error)
            }
        }
        val flush = try {
            outbox.flush(api)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failure = failure ?: failureOf(error)
            null
        }
        if (failure == null && flush != null) failure = failureOf(flush.stop)
        for (scope in scopes) {
            try {
                changed = feed.sync(scope) || changed
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failure = failure ?: failureOf(error)
            }
        }
        return PulseResult(changed, failure)
    }

    override suspend fun unreadNotifications(): Int? = try {
        unreadNotificationCount(api)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override fun counts(): SectionCounts = SectionCounts(
        passwords = if (FeedScope.PASSWORDS in scopes) {
            currentRevisions(credentialRevisions(replicaStore.items(FeedScope.PASSWORDS, ItemTypes.CREDENTIAL))).count { !it.deleted }
        } else {
            0
        },
        vault = if (FeedScope.SECRETS in scopes) {
            secretRecords(replicaStore.items(FeedScope.SECRETS, ItemTypes.SECRET)).count { it.deletedAt == null }
        } else {
            0
        },
        notes = if (FeedScope.NOTES in scopes) noteRecords(replicaStore.items(FeedScope.NOTES, ItemTypes.NOTE)).size else 0,
    )

    override fun outbox(): List<OutboxEntry> = outbox.entries()

    override fun discard(position: Long) {
        outbox.discard(position)
    }

    override fun close() {
        feed.close()
        drivers.forEach { it.close() }
    }

    private fun failureOf(stop: OutboxStop): SyncFailure? = when (stop) {
        OutboxStop.Drained -> null
        OutboxStop.Offline -> SyncFailure.Offline
        is OutboxStop.RateLimited -> SyncFailure.RateLimited(stop.retryAfterSeconds)
        OutboxStop.SessionOver -> SyncFailure.SessionOver
        is OutboxStop.ServerUnavailable -> SyncFailure.Failed
    }

    private fun failureOf(error: Exception): SyncFailure = when {
        error is NetworkError -> SyncFailure.Offline
        error is ApiError && error.isRateLimited -> SyncFailure.RateLimited(error.retryAfterSeconds)
        error is ApiError && error.isSessionOver -> SyncFailure.SessionOver
        else -> SyncFailure.Failed
    }
}

class CoreSyncBackendFactory(
    private val api: ZekkeApi,
    private val keystore: SessionKeystore,
    private val databases: LocalDatabases,
    private val scope: CoroutineScope,
    private val items: ItemContext,
) : SyncBackendFactory {
    override fun open(userAddress: String, scopes: List<FeedScope>): SyncBackend {
        val replicaDriver = databases.replicaDriver()
        val outboxDriver = databases.outboxDriver()
        val replica = Replica(replicaDriver, userAddress)
        val outbox = Outbox(outboxDriver, userAddress)
        return CoreSyncBackend(api, replica, outbox, Feed(replica, keystore, api, scope), scopes, listOf(replicaDriver, outboxDriver), items)
    }
}
