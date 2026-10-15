package zekke.android.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.core.api.Method
import zekke.core.feed.FeedScope
import zekke.core.feed.OutboxEntry
import zekke.core.feed.OutboxState
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SyncServiceTest {
    private val scope = TestScope()
    private val sessionState = MutableStateFlow(SessionState.LOCKED)
    private val session = object : SyncSession {
        override val state = sessionState
        override fun userAddress() = "a".repeat(64)
        override fun heldFeedScopes() = listOf(FeedScope.PASSWORDS, FeedScope.NOTES)
    }
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private val backends = mutableListOf<FakeBackend>()
    private val wipes = mutableListOf<Boolean>()
    private var clock = 100L

    private inner class FakeBackend(val scopes: List<FeedScope>) : SyncBackend {
        val changed = MutableSharedFlow<FeedScope>(extraBufferCapacity = 8)
        var pulses = 0
        var deepPulses = 0
        var closed = false
        var result = PulseResult(changed = false)
        var unread: Int? = 0
        var counts = SectionCounts()
        var entries = listOf<OutboxEntry>()
        val discarded = mutableListOf<Long>()
        override val changes: Flow<FeedScope> = changed
        override suspend fun pulse(deep: Boolean): PulseResult {
            pulses++
            if (deep) deepPulses++
            return result
        }
        override suspend fun unreadNotifications() = unread
        override fun counts() = counts
        override fun outbox() = entries
        override fun discard(position: Long) {
            discarded += position
            entries = entries.filterNot { it.position == position }
        }
        override fun close() {
            closed = true
        }
    }

    private val service = SyncService(
        session = session,
        scope = scope,
        factory = { _, scopes -> FakeBackend(scopes).also { backends += it } },
        wipe = { wipes += it },
        nowEpochSeconds = { clock },
    )

    private fun entry(position: Long, state: OutboxState) =
        OutboxEntry(position, "item", FeedScope.NOTES, Method.POST, "/notes", JsonObject(emptyMap()), 1, state, 0, 0)

    private fun foreground() {
        (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED
        service.onStart(owner)
    }

    private fun background() = service.onStop(owner)

    private fun unlock() {
        service.start()
        sessionState.value = SessionState.UNLOCKED
        scope.runCurrent()
    }

    @Test
    fun unlockingOpensTheBackendForTheHeldScopesAndSyncsDeeply() {
        unlock()
        assertEquals(1, backends.size)
        assertEquals(listOf(FeedScope.PASSWORDS, FeedScope.NOTES), backends[0].scopes)
        assertEquals(1, backends[0].deepPulses)
        assertTrue(service.status.value.active)
        assertEquals(100L, service.status.value.lastSyncEpochSeconds)
    }

    @Test
    fun nothingIsOpenWhileTheSessionIsLocked() {
        service.start()
        scope.runCurrent()
        assertTrue(backends.isEmpty())
        assertFalse(service.status.value.active)
    }

    @Test
    fun lockingClosesTheBackendAndClearsTheStatus() {
        unlock()
        sessionState.value = SessionState.LOCKED
        scope.runCurrent()
        assertTrue(backends[0].closed)
        assertEquals(SyncStatus(), service.status.value)
    }

    @Test
    fun pollingRunsEvery30SecondsWhileVisibleAndDoublesWhenNothingChanges() {
        foreground()
        unlock()
        val backend = backends[0]
        scope.advanceTimeBy(29_000)
        assertEquals(1, backend.pulses)
        scope.advanceTimeBy(2_000)
        assertEquals(2, backend.pulses)
        scope.advanceTimeBy(59_000)
        assertEquals(2, backend.pulses)
        scope.advanceTimeBy(2_000)
        assertEquals(3, backend.pulses)
        scope.advanceTimeBy(121_000)
        assertEquals(4, backend.pulses)
        scope.advanceTimeBy(121_000)
        assertEquals(5, backend.pulses)
    }

    @Test
    fun aPulseThatChangedSomethingKeepsTheShortInterval() {
        foreground()
        unlock()
        val backend = backends[0]
        backend.result = PulseResult(changed = true)
        scope.advanceTimeBy(31_000)
        scope.advanceTimeBy(31_000)
        assertEquals(3, backend.pulses)
    }

    @Test
    fun pollingStopsInTheBackgroundAndOnALock() {
        foreground()
        unlock()
        background()
        scope.advanceTimeBy(300_000)
        assertEquals(1, backends[0].pulses)
        foreground()
        scope.runCurrent()
        assertEquals(2, backends[0].pulses)
        sessionState.value = SessionState.LOCKED
        scope.runCurrent()
        scope.advanceTimeBy(300_000)
        assertEquals(2, backends[0].pulses)
    }

    @Test
    fun returningToTheForegroundWhileLockedSyncsNothing() {
        foreground()
        scope.runCurrent()
        assertTrue(backends.isEmpty())
    }

    @Test
    fun noPollingStartsWhileInTheBackground() {
        unlock()
        scope.advanceTimeBy(300_000)
        assertEquals(1, backends[0].pulses)
    }

    @Test
    fun syncNowPullsOnce() {
        unlock()
        service.syncNow()
        scope.runCurrent()
        assertEquals(2, backends[0].pulses)
    }

    @Test
    fun resettingAScopePullsAgain() {
        unlock()
        service.resetScope(FeedScope.PASSWORDS)
        scope.runCurrent()
        assertEquals(2, backends[0].pulses)
    }

    @Test
    fun aFailureIsShownAndKeepsTheLastGoodTime() {
        unlock()
        clock = 200L
        backends[0].result = PulseResult(changed = false, failure = SyncFailure.Offline)
        service.syncNow()
        scope.runCurrent()
        assertEquals(SyncFailure.Offline, service.status.value.failure)
        assertEquals(100L, service.status.value.lastSyncEpochSeconds)
        backends[0].result = PulseResult(changed = false)
        service.syncNow()
        scope.runCurrent()
        assertNull(service.status.value.failure)
        assertEquals(200L, service.status.value.lastSyncEpochSeconds)
    }

    @Test
    fun theUnreadCountIsKeptWhenTheNotificationsCannotBeRead() {
        unlock()
        backends[0].unread = 3
        service.syncNow()
        scope.runCurrent()
        assertEquals(3, service.status.value.unreadNotifications)
        backends[0].unread = null
        service.syncNow()
        scope.runCurrent()
        assertEquals(3, service.status.value.unreadNotifications)
    }

    @Test
    fun theOutboxIsCountedByStateAndRefusalsCanBeDiscarded() {
        unlock()
        backends[0].entries = listOf(entry(1, OutboxState.PENDING), entry(2, OutboxState.NEEDS_REWRAP), entry(3, OutboxState.REFUSED))
        service.syncNow()
        scope.runCurrent()
        val status = service.status.value
        assertEquals(1, status.outboxPending)
        assertEquals(1, status.outboxWaiting)
        assertEquals(listOf(3L), status.outboxRefused.map { it.position })
        service.discardRefused(3)
        assertEquals(emptyList<OutboxEntry>(), service.status.value.outboxRefused)
        assertEquals(listOf(3L), backends[0].discarded)
    }

    @Test
    fun aChangeInAScopeRefreshesTheCountsAndIsRelayed() {
        unlock()
        val relayed = mutableListOf<FeedScope>()
        val collecting = scope.launch { service.changes.collect { relayed += it } }
        scope.runCurrent()
        backends[0].counts = SectionCounts(passwords = 4, notes = 2)
        backends[0].changed.tryEmit(FeedScope.PASSWORDS)
        scope.runCurrent()
        assertEquals(4, service.status.value.counts.passwords)
        assertEquals(listOf(FeedScope.PASSWORDS), relayed)
        collecting.cancel()
    }

    @Test
    fun leavingTheAccountClosesTheBackendAndWipes() {
        unlock()
        service.leaveAccount(includeOutbox = false)
        scope.runCurrent()
        assertTrue(backends[0].closed)
        assertEquals(listOf(false), wipes)
        service.leaveAccount(includeOutbox = true)
        scope.runCurrent()
        assertEquals(listOf(false, true), wipes)
    }

    @Test
    fun unlockingAgainOpensAFreshBackend() {
        unlock()
        sessionState.value = SessionState.LOCKED
        scope.runCurrent()
        sessionState.value = SessionState.UNLOCKED
        scope.runCurrent()
        assertEquals(2, backends.size)
        assertFalse(backends[1].closed)
    }
}
