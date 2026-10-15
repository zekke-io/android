package zekke.android.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zekke.core.feed.FeedScope
import zekke.core.feed.OutboxEntry
import zekke.core.feed.OutboxState
import zekke.core.session.SessionState

data class SyncStatus(
    val active: Boolean = false,
    val heldScopes: List<FeedScope> = emptyList(),
    val syncing: Boolean = false,
    val lastSyncEpochSeconds: Long? = null,
    val failure: SyncFailure? = null,
    val counts: SectionCounts = SectionCounts(),
    val outboxPending: Int = 0,
    val outboxWaiting: Int = 0,
    val outboxRefused: List<OutboxEntry> = emptyList(),
    val unreadNotifications: Int = 0,
)

const val POLL_SECONDS = 30L
const val MAX_POLL_SECONDS = 120L

class SyncService(
    private val session: SyncSession,
    private val scope: CoroutineScope,
    private val factory: SyncBackendFactory,
    private val wipe: (includeOutbox: Boolean) -> Unit,
    private val nowEpochSeconds: () -> Long,
) : DefaultLifecycleObserver {
    private val mutableStatus = MutableStateFlow(SyncStatus())
    private val mutableChanges = MutableSharedFlow<FeedScope>(extraBufferCapacity = 64)
    private val lock = Mutex()
    private val pulses = Mutex()
    private var backend: SyncBackend? = null
    private var changesJob: Job? = null
    private var pollJob: Job? = null
    private var foreground = false
    private var lastChanged = false

    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    val changes: SharedFlow<FeedScope> = mutableChanges.asSharedFlow()

    fun start() {
        scope.launch {
            session.state.collect { state ->
                if (state == SessionState.UNLOCKED) open() else close()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        scope.launch {
            if (backend != null) {
                pulse(deep = false)
                startPolling()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        stopPolling()
    }

    fun syncNow() {
        scope.launch { pulse(deep = false) }
    }

    fun unreadChanged(count: Int) {
        mutableStatus.update { it.copy(unreadNotifications = count) }
    }

    fun discardRefused(position: Long) {
        backend?.discard(position)
        refreshOutbox()
    }

    fun leaveAccount(includeOutbox: Boolean) {
        scope.launch {
            lock.withLock { closeLocked() }
            wipe(includeOutbox)
        }
    }

    private suspend fun open() {
        val opened = lock.withLock {
            if (backend != null) return@withLock false
            val next = factory.open(session.userAddress(), session.heldFeedScopes())
            backend = next
            changesJob = scope.launch {
                next.changes.collect { changedScope ->
                    mutableChanges.emit(changedScope)
                    refreshCounts()
                }
            }
            mutableStatus.value = SyncStatus(active = true, heldScopes = session.heldFeedScopes(), counts = next.counts())
            true
        }
        if (!opened) return
        refreshOutbox()
        pulse(deep = true)
        if (foreground) startPolling()
    }

    private suspend fun close() = lock.withLock { closeLocked() }

    private fun closeLocked() {
        stopPolling()
        changesJob?.cancel()
        changesJob = null
        backend?.close()
        backend = null
        mutableStatus.value = SyncStatus()
    }

    private suspend fun pulse(deep: Boolean) = pulses.withLock { pulseExclusive(deep) }

    private suspend fun pulseExclusive(deep: Boolean) {
        val current = backend ?: return
        mutableStatus.update { it.copy(syncing = true) }
        val result = current.pulse(deep)
        val unread = current.unreadNotifications()
        if (backend !== current) return
        mutableStatus.update {
            it.copy(
                syncing = false,
                failure = result.failure,
                lastSyncEpochSeconds = if (result.failure == null) nowEpochSeconds() else it.lastSyncEpochSeconds,
                unreadNotifications = unread ?: it.unreadNotifications,
            )
        }
        refreshCounts()
        refreshOutbox()
        lastChanged = result.changed
    }

    private fun startPolling() {
        stopPolling()
        pollJob = scope.launch {
            var wait = POLL_SECONDS
            while (true) {
                delay(wait * 1000)
                pulse(deep = false)
                wait = if (lastChanged) POLL_SECONDS else minOf(MAX_POLL_SECONDS, wait * 2)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun refreshCounts() {
        val current = backend ?: return
        mutableStatus.update { it.copy(counts = current.counts()) }
    }

    private fun refreshOutbox() {
        val entries = backend?.outbox() ?: return
        mutableStatus.update {
            it.copy(
                outboxPending = entries.count { entry -> entry.state == OutboxState.PENDING },
                outboxWaiting = entries.count { entry -> entry.state == OutboxState.NEEDS_REWRAP },
                outboxRefused = entries.filter { entry -> entry.state == OutboxState.REFUSED },
            )
        }
    }
}
