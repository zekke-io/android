package zekke.android.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.core.notifications.NotificationPage
import zekke.core.notifications.NotificationRecord

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private class FakeServices : NotificationsServices {
        val cursors = mutableListOf<String?>()
        val marked = mutableListOf<List<String>>()
        var next = CompletableDeferred<NotificationsLoad>()
        var markFails = false

        override suspend fun load(cursor: String?): NotificationsLoad {
            cursors += cursor
            return next.await().also { next = CompletableDeferred() }
        }

        override suspend fun markRead(ids: List<String>) {
            if (markFails) error("refused")
            marked += ids
        }
    }

    private val services = FakeServices()
    private val unread = mutableListOf<Int>()
    private val store = ViewModelStore()
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ViewModelProvider.create(
            store,
            viewModelFactory { initializer { NotificationsViewModel(services) { unread += it } } },
        )[NotificationsViewModel::class]
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun record(id: String, read: Boolean = false) =
        NotificationRecord(id, "refunded", JsonObject(emptyMap()), "2026-10-01T10:00:00Z", if (read) "2026-10-02T10:00:00Z" else null)

    private fun page(vararg records: NotificationRecord, unreadCount: Int = records.count { !it.isRead }, next: String? = null) =
        NotificationsLoad.Loaded(NotificationPage(records.toList(), unreadCount, next))

    private val state get() = viewModel.state.value

    @Test
    fun openingLoadsTheFirstPageAndMarksTheShownUnreadAsRead() {
        viewModel.open()
        assertTrue(state.loading)
        services.next.complete(page(record("a"), record("b", read = true), record("c")))
        assertFalse(state.loading)
        assertEquals(3, state.records.size)
        assertEquals(setOf("a", "c"), state.unreadIds)
        assertEquals(listOf(listOf("a", "c")), services.marked)
        assertEquals(0, state.unreadCount)
        assertEquals(listOf(0), unread)
    }

    @Test
    fun theUnreadCountKeepsWhatIsNotOnThePage() {
        viewModel.open()
        services.next.complete(page(record("a"), unreadCount = 5))
        assertEquals(4, state.unreadCount)
        assertEquals(listOf(4), unread)
    }

    @Test
    fun openingWhileLoadingLoadsOnce() {
        viewModel.open()
        viewModel.open()
        assertEquals(1, services.cursors.size)
    }

    @Test
    fun openingAgainReloadsTheFirstPageAndMarksWhatArrivedSince() {
        viewModel.open()
        services.next.complete(page(record("a")))
        assertEquals(setOf("a"), state.unreadIds)
        viewModel.open()
        services.next.complete(page(record("b"), record("a", read = true)))
        assertEquals(listOf(null, null), services.cursors)
        assertEquals(listOf("b", "a"), state.records.map { it.id })
        assertEquals(setOf("b"), state.unreadIds)
        assertEquals(listOf(listOf("a"), listOf("b")), services.marked)
        assertEquals(listOf(0, 0), unread)
    }

    @Test
    fun anEmptyListIsLoadedAndEmpty() {
        viewModel.open()
        services.next.complete(page())
        assertTrue(state.loadedOnce)
        assertTrue(state.records.isEmpty())
        assertTrue(services.marked.isEmpty())
    }

    @Test
    fun showOlderAppendsTheNextPageWithItsCursor() {
        viewModel.open()
        services.next.complete(page(record("a", read = true), next = "cursor-1"))
        viewModel.showOlder()
        services.next.complete(page(record("b"), next = null))
        assertEquals(listOf(null, "cursor-1"), services.cursors)
        assertEquals(listOf("a", "b"), state.records.map { it.id })
        assertNull(state.nextCursor)
    }

    @Test
    fun showOlderWithoutACursorOrWhileLoadingDoesNothing() {
        viewModel.showOlder()
        assertTrue(services.cursors.isEmpty())
        viewModel.open()
        viewModel.showOlder()
        assertEquals(1, services.cursors.size)
    }

    @Test
    fun eachFailureIsKeptApartAndRetryLoadsAgain() {
        viewModel.open()
        services.next.complete(NotificationsLoad.Offline)
        assertEquals(NotificationsProblem.Offline, state.problem)
        assertFalse(state.loadedOnce)
        viewModel.retry()
        services.next.complete(NotificationsLoad.RateLimited(90))
        assertEquals(NotificationsProblem.RateLimited, state.problem)
        assertEquals(90L, state.retryAfterSeconds)
        viewModel.retry()
        services.next.complete(NotificationsLoad.Failed)
        assertEquals(NotificationsProblem.Failed, state.problem)
        viewModel.retry()
        services.next.complete(page(record("a", read = true)))
        assertNull(state.problem)
        assertEquals(4, services.cursors.size)
    }

    @Test
    fun aThrowingServiceIsAFailure() {
        viewModel.open()
        services.next.completeExceptionally(IllegalStateException("boom"))
        assertEquals(NotificationsProblem.Failed, state.problem)
        assertFalse(state.loading)
    }

    @Test
    fun aFailedMarkLeavesTheNoticesShownAndUnread() {
        services.markFails = true
        viewModel.open()
        services.next.complete(page(record("a"), unreadCount = 1))
        assertEquals(1, state.records.size)
        assertEquals(1, state.unreadCount)
        assertTrue(unread.isEmpty())
    }
}
