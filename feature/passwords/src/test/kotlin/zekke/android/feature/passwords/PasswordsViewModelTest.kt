package zekke.android.feature.passwords

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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.CredentialPayload
import zekke.core.credentials.PasswordsView
import zekke.core.credentials.SiteMatch

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordsViewModelTest {
    private val services = FakePasswordsServices()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.view = PasswordsView(
            listOf(passwordRow("1", "https://www.github.com", "ana", note = "work"), passwordRow("2", "bank.example", "bob")),
            listOf(deletedRow("9", "old.example"), deletedRow("8", "older.example")),
        )
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): PasswordsViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { PasswordsViewModel(services) } })[PasswordsViewModel::class]

    @Test
    fun theListLoadsAtOnceWithItsDeletedAndWhetherItIsSynced() {
        val model = model()
        val state = model.state.value
        assertTrue(state.loaded)
        assertEquals(listOf("1", "2"), state.rows.map { it.id })
        assertEquals(listOf("9", "8"), state.deleted.map { it.id })
        assertTrue(state.synced)
        assertTrue(state.canPurge)
    }

    @Test
    fun aLoadFailureIsShownAndAnotherLoadClearsIt() {
        services.loadFailure = ItemFailure.Offline
        val model = model()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertFalse(model.state.value.loaded)
        services.loadFailure = null
        model.refresh()
        assertNull(model.state.value.failure)
        assertTrue(model.state.value.loaded)
    }

    @Test
    fun aThrowingServiceIsAFailureNotACrash() {
        services.throwOnLoad = true
        assertEquals(ItemFailure.Failed, model().state.value.failure)
    }

    @Test
    fun aChangeInThePasswordsScopeReloads() {
        model()
        services.changes.tryEmit(Unit)
        assertEquals(2, services.loads.size)
    }

    @Test
    fun searchMatchesSiteUsernameNoteAndOtherAddressesIgnoringCase() {
        val model = model()
        model.queryChanged("GITHUB")
        assertEquals(listOf("1"), model.state.value.visible.map { it.id })
        model.queryChanged("bob")
        assertEquals(listOf("2"), model.state.value.visible.map { it.id })
        model.queryChanged("work")
        assertEquals(listOf("1"), model.state.value.visible.map { it.id })
        model.queryChanged("  ")
        assertEquals(2, model.state.value.visible.size)
        model.queryChanged("nothing like it")
        assertTrue(model.state.value.visible.isEmpty())
    }

    @Test
    fun addingNeedsASiteAUsernameAndAPasswordAndTrimsWhatItSends() {
        val model = model()
        model.startAdd()
        assertFalse(model.state.value.form!!.complete)
        model.siteChanged("  example.org ")
        model.usernameChanged(" carol ")
        model.save()
        assertTrue(services.saved.isEmpty())
        model.passwordChanged(" s3 cret ")
        model.noteChanged("  a note ")
        model.urlsChanged("a.example, b.example\nc.example,, ")
        model.matchChanged(SiteMatch.HOST)
        model.save()
        val (payload, id) = services.saved.single()
        assertNull(id)
        assertEquals("example.org", payload.site)
        assertEquals("carol", payload.username)
        assertEquals(" s3 cret ", payload.password)
        assertEquals("a note", payload.note)
        assertEquals(listOf("a.example", "b.example", "c.example"), payload.urls.map { it.trim() }.filter { it.isNotEmpty() })
        assertEquals(SiteMatch.HOST, payload.match)
        assertNull(model.state.value.form)
    }

    @Test
    fun editingKeepsWhatThisAppDoesNotShowAndWritesToTheSameCredential() {
        val extra = JsonObject(mapOf("totp" to JsonPrimitive("abc")))
        services.view = PasswordsView(
            listOf(passwordRow("1", "example.org", "ana", payload = CredentialPayload("example.org", "ana", "old", urls = listOf("x.example"), match = SiteMatch.HOST, extra = extra))),
            emptyList(),
        )
        val model = model()
        model.startEdit(model.state.value.rows.single())
        val form = model.state.value.form!!
        assertEquals("x.example", form.urls)
        assertEquals(SiteMatch.HOST, form.match)
        model.passwordChanged("new")
        model.save()
        val (payload, id) = services.saved.single()
        assertEquals("1", id)
        assertEquals(extra, payload.extra)
        assertEquals(SiteMatch.HOST, payload.match)
        assertEquals("new", payload.password)
    }

    @Test
    fun anUnreadableCredentialCannotBeEdited() {
        services.view = PasswordsView(listOf(passwordRow("1", "", readable = false, payload = null)), emptyList())
        val model = model()
        model.startEdit(model.state.value.rows.single())
        assertNull(model.state.value.form)
    }

    @Test
    fun aFailedSaveKeepsTheFormAndShowsWhy() {
        val model = model()
        model.startAdd()
        model.siteChanged("a")
        model.usernameChanged("b")
        model.passwordChanged("c")
        services.writeResult = PasswordsWrite.Failed(ItemFailure.Offline)
        model.save()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertEquals("a", model.state.value.form!!.site)
        assertFalse(model.state.value.busy)
        services.writeResult = PasswordsWrite.Done
        model.save()
        assertNull(model.state.value.form)
        assertNull(model.state.value.failure)
    }

    @Test
    fun theFormCannotBeClosedOrSavedTwiceWhileSaving() {
        val model = model()
        model.startAdd()
        model.siteChanged("a")
        model.usernameChanged("b")
        model.passwordChanged("c")
        services.gate = CompletableDeferred()
        model.save()
        assertTrue(model.state.value.busy)
        model.save()
        model.closeForm()
        assertEquals(1, services.saved.size)
        assertTrue(model.state.value.form != null)
        services.gate!!.complete(Unit)
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.form)
    }

    @Test
    fun deletingAsksFirstThenWritesAndClosesTheFormOfThatPassword() {
        val model = model()
        val row = model.state.value.rows.first()
        model.startEdit(row)
        model.askDelete(row)
        assertEquals(row, model.state.value.confirmingDelete)
        model.cancelDelete()
        assertNull(model.state.value.confirmingDelete)
        assertTrue(services.deletedIds.isEmpty())
        model.askDelete(row)
        model.confirmDelete()
        assertEquals(listOf("1"), services.deletedIds)
        assertNull(model.state.value.confirmingDelete)
        assertNull(model.state.value.form)
    }

    @Test
    fun aFailedDeleteClosesTheQuestionAndShowsWhy() {
        val model = model()
        services.writeResult = PasswordsWrite.Failed(ItemFailure.RateLimited(30))
        model.askDelete(model.state.value.rows.first())
        model.confirmDelete()
        assertEquals(ItemFailure.RateLimited(30), model.state.value.failure)
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun restoringWritesTheDeletedOne() {
        val model = model()
        model.toggleDeleted()
        assertTrue(model.state.value.showingDeleted)
        model.restore(model.state.value.deleted.first())
        assertEquals(listOf("9"), services.restored)
        model.hideDeleted()
        assertFalse(model.state.value.showingDeleted)
    }

    @Test
    fun purgingNeedsAFullDeviceAndAConfirmation() {
        val model = model()
        model.askPurge(model.state.value.deleted)
        assertEquals(2, model.state.value.confirmingPurge!!.size)
        model.cancelPurge()
        assertTrue(services.purged.isEmpty())
        model.askPurge(listOf(model.state.value.deleted[1]))
        model.confirmPurge()
        assertEquals(listOf(listOf("8")), services.purged)
        assertNull(model.state.value.confirmingPurge)
    }

    @Test
    fun aDeviceThatIsNotFullCannotPurge() {
        val limited = FakePasswordsServices(canPurge = false)
        limited.view = services.view
        val model = ViewModelProvider.create(store, viewModelFactory { initializer { PasswordsViewModel(limited) } })[PasswordsViewModel::class]
        assertFalse(model.state.value.canPurge)
        model.askPurge(model.state.value.deleted)
        assertNull(model.state.value.confirmingPurge)
    }

    @Test
    fun previousPasswordsLoadOnDemandAndTheirFailureStaysInTheForm() {
        val model = model()
        model.startEdit(model.state.value.rows.first())
        services.previous = PreviousLoad.Failed(ItemFailure.Offline)
        model.showPrevious()
        assertEquals(ItemFailure.Offline, model.state.value.form!!.previousFailure)
        assertNull(model.state.value.form!!.previous)
        services.previous = PreviousLoad.Loaded(listOf(PreviousPassword("r", "old", "2026-09-01T00:00:00Z")))
        model.showPrevious()
        assertEquals("old", model.state.value.form!!.previous!!.single().password)
        assertNull(model.state.value.form!!.previousFailure)
    }
}
