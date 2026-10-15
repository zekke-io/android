package zekke.android.sync

import kotlinx.coroutines.flow.Flow
import zekke.core.feed.FeedScope
import zekke.core.feed.Outbox
import zekke.core.feed.OutboxEntry
import zekke.core.feed.Replica

data class SectionCounts(val passwords: Int = 0, val vault: Int = 0, val notes: Int = 0)

sealed interface SyncFailure {
    data object Offline : SyncFailure

    data class RateLimited(val retryAfterSeconds: Long?) : SyncFailure

    data object SessionOver : SyncFailure

    data object Failed : SyncFailure
}

data class PulseResult(val changed: Boolean, val failure: SyncFailure? = null)

interface SyncBackend {
    val changes: Flow<FeedScope>

    val replica: Replica? get() = null

    val outboxQueue: Outbox? get() = null

    suspend fun pulse(deep: Boolean): PulseResult

    suspend fun unreadNotifications(): Int?

    fun counts(): SectionCounts

    fun outbox(): List<OutboxEntry>

    fun discard(position: Long)

    fun close()
}

fun interface SyncBackendFactory {
    fun open(userAddress: String, scopes: List<FeedScope>): SyncBackend
}
