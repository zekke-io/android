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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentViewModelTest {
    private class FakeServices : RecentServices {
        var next = CompletableDeferred<List<RecentEntry>>()
        var calls = 0

        override suspend fun load(): List<RecentEntry> {
            calls++
            return next.await().also { next = CompletableDeferred() }
        }
    }

    private val services = FakeServices()
    private val store = ViewModelStore()
    private lateinit var viewModel: RecentViewModel

    private fun entry(id: String, at: String, kind: RecentKind = RecentKind.Note) = RecentEntry(kind, id, "title $id", at)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ViewModelProvider(store, viewModelFactory { initializer { RecentViewModel(services) } })[RecentViewModel::class.java]
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun theStateStartsNotLoaded() {
        assertFalse(viewModel.state.value.loaded)
        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    @Test
    fun entriesComeNewestFirst() {
        viewModel.opened()
        services.next.complete(
            listOf(
                entry("old", "2026-10-01T10:00:00Z"),
                entry("new", "2026-10-18T10:00:00Z", RecentKind.Password),
                entry("middle", "2026-10-10T10:00:00Z", RecentKind.Secret),
            ),
        )
        assertTrue(viewModel.state.value.loaded)
        assertEquals(listOf("new", "middle", "old"), viewModel.state.value.entries.map { it.id })
    }

    @Test
    fun anEntryWithAnUnreadableTimeIsLeftOut() {
        viewModel.opened()
        services.next.complete(listOf(entry("ok", "2026-10-18T10:00:00Z"), entry("bad", ""), entry("worse", "yesterday")))
        assertEquals(listOf("ok"), viewModel.state.value.entries.map { it.id })
    }

    @Test
    fun onlyTheTwentyNewestAreKept() {
        viewModel.opened()
        services.next.complete((1..30).map { entry("n$it", "2026-10-%02dT10:00:00Z".format(it)) })
        assertEquals(RECENT_LIMIT, viewModel.state.value.entries.size)
        assertEquals("n30", viewModel.state.value.entries.first().id)
    }

    @Test
    fun aFailedLoadLeavesAnEmptyLoadedState() {
        viewModel.opened()
        services.next.completeExceptionally(IllegalStateException("offline"))
        assertTrue(viewModel.state.value.loaded)
        assertTrue(viewModel.state.value.entries.isEmpty())
    }

    @Test
    fun aLateResultOfAnOlderLoadIsIgnored() {
        viewModel.opened()
        val first = services.next
        services.next = CompletableDeferred()
        viewModel.opened()
        services.next.complete(listOf(entry("fresh", "2026-10-18T10:00:00Z")))
        first.complete(listOf(entry("stale", "2026-10-19T10:00:00Z")))
        assertEquals(listOf("fresh"), viewModel.state.value.entries.map { it.id })
    }
}
