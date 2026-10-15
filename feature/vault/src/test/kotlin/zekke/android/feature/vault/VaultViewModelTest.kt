package zekke.android.feature.vault

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.feature.tabs.TabsLoad
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.ManifestScope
import zekke.core.folders.emptyFolderManifest
import zekke.core.secrets.VaultView
import kotlinx.coroutines.flow.MutableSharedFlow

private const val AT = "2026-10-01T00:00:00.000Z"

private class FakeTabs(override var canDeleteItems: Boolean = true) : FolderTabsServices {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    var manifest: FolderManifest = emptyFolderManifest(ManifestScope.SECRETS.rules, AT)
    override suspend fun load(): TabsLoad = TabsLoad.Loaded(manifest)
    override suspend fun edit(edit: FolderEdit): TabsLoad {
        manifest = edit.apply(manifest, ManifestScope.SECRETS.rules)
        return TabsLoad.Loaded(manifest)
    }
    override suspend fun reset(): TabsLoad = TabsLoad.Loaded(manifest)
}

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {
    private val services = FakeVaultServices()
    private val tabs = FakeTabs()
    private val store = ViewModelStore()
    private var tabIds = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.view = VaultView(
            listOf(vaultRow("1", "Wifi"), vaultRow("2", "Bank PIN"), vaultRow("3", "", readable = false)),
            listOf(deletedSecret("9", "Old"), deletedSecret("8", "Older")),
        )
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): VaultViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { VaultViewModel(services, tabs) { "tab-${++tabIds}" } } })[VaultViewModel::class]

    @Test
    fun theVaultLoadsAtOnceAndKeepsValuesHidden() {
        val model = model()
        val state = model.state.value
        assertTrue(state.loaded)
        assertEquals(listOf("1", "2", "3"), state.rows.map { it.id })
        assertFalse(state.revealed)
        assertTrue(state.canDelete)
        model.toggleRevealed()
        assertTrue(model.state.value.revealed)
        model.toggleRevealed()
        assertFalse(model.state.value.revealed)
    }

    @Test
    fun aLoadFailureIsShownAndAChangeReloads() {
        services.loadFailure = ItemFailure.Offline
        val model = model()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        services.loadFailure = null
        services.changes.tryEmit(Unit)
        assertNull(model.state.value.failure)
    }

    @Test
    fun addingNeedsANameAndAValueAndTrimsOnlyTheName() {
        val model = model()
        model.startAdd()
        assertFalse(model.state.value.adding!!.complete)
        model.nameChanged("  Router  ")
        model.add()
        assertTrue(services.created.isEmpty())
        model.valueChanged(" s3 ")
        model.add()
        val payload = services.created.single()
        assertEquals("Router", payload.name)
        assertEquals(" s3 ", payload.value)
        assertNull(model.state.value.adding)
    }

    @Test
    fun aSecretAddedWhileATabIsOpenIsFiledThere() {
        val model = model()
        model.tabs.startNew()
        model.tabs.nameChanged("Work")
        model.tabs.commitName(emptyList())
        val work = model.tabs.state.value.wanted!!
        model.startAdd()
        model.nameChanged("n")
        model.valueChanged("v")
        model.add()
        assertEquals(work, tabs.manifest.items["new-id"]?.folderId)
    }

    @Test
    fun aSecretAddedInTheMainTabIsNotFiled() {
        val model = model()
        model.startAdd()
        model.nameChanged("n")
        model.valueChanged("v")
        model.add()
        assertNull(tabs.manifest.items["new-id"])
    }

    @Test
    fun aFailedAddKeepsTheFormAndNothingIsSavedTwiceWhileBusy() {
        val model = model()
        model.startAdd()
        model.nameChanged("n")
        model.valueChanged("v")
        services.result = VaultWrite.Failed(ItemFailure.TooLarge)
        model.add()
        assertEquals(ItemFailure.TooLarge, model.state.value.failure)
        assertTrue(model.state.value.adding != null)
        services.result = VaultWrite.Done("x")
        services.gate = CompletableDeferred()
        model.add()
        model.add()
        model.closeAdd()
        assertEquals(2, services.created.size)
        assertTrue(model.state.value.adding != null)
        services.gate!!.complete(Unit)
        assertNull(model.state.value.adding)
    }

    @Test
    fun openingARowShowsItsSheetButNotForAnUnreadableOne() {
        val model = model()
        model.open(model.state.value.rows[2])
        assertNull(model.state.value.detail)
        model.open(model.state.value.rows[0])
        assertEquals("1", model.state.value.detail)
        model.closeDetail()
        assertNull(model.state.value.detail)
    }

    @Test
    fun movingASecretMovesItAndClosesTheSheet() {
        val model = model()
        model.tabs.startNew()
        model.tabs.nameChanged("Work")
        model.tabs.commitName(emptyList())
        val work = model.tabs.state.value.wanted!!
        model.open(model.state.value.rows[0])
        model.moveTo(model.state.value.rows[0], work)
        assertEquals(work, tabs.manifest.items["1"]?.folderId)
        assertNull(model.state.value.detail)
    }

    @Test
    fun deletingNeedsAFullDeviceAndAConfirmation() {
        val model = model()
        model.askDelete(model.state.value.rows[0])
        assertEquals("1", model.state.value.confirmingDelete!!.id)
        model.cancelDelete()
        assertTrue(services.deleted.isEmpty())
        model.askDelete(model.state.value.rows[0])
        model.confirmDelete()
        assertEquals(listOf(listOf("1")), services.deleted)
        assertNull(model.state.value.confirmingDelete)
        val limited = FakeVaultServices(canDelete = false)
        limited.view = services.view
        val other = ViewModelProvider.create(ViewModelStore(), viewModelFactory { initializer { VaultViewModel(limited, tabs) { "other-${++tabIds}" } } })[VaultViewModel::class]
        other.askDelete(other.state.value.rows[0])
        assertNull(other.state.value.confirmingDelete)
    }

    @Test
    fun aFailedDeleteIsShownAndTheQuestionClosed() {
        val model = model()
        services.result = VaultWrite.Failed(ItemFailure.Offline)
        model.askDelete(model.state.value.rows[0])
        model.confirmDelete()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertNull(model.state.value.confirmingDelete)
    }

    @Test
    fun theTabsItemsAreDeletedThroughTheServicesAndTheirFailureIsReturned() = kotlinx.coroutines.test.runTest {
        val model = model()
        assertNull(model.deleteTabItems(listOf("1", "2")))
        assertEquals(listOf(listOf("1", "2")), services.deleted)
        services.result = VaultWrite.Failed(ItemFailure.Offline)
        assertEquals(ItemFailure.Offline, model.deleteTabItems(listOf("3")))
    }

    @Test
    fun restoringAndPurgingWorkOnTheDeletedOnesAndPurgeForgetsTheirTabs() {
        val model = model()
        model.toggleDeleted()
        assertTrue(model.state.value.showingDeleted)
        model.restore(model.state.value.deleted[0])
        assertEquals(listOf("9"), services.restored)
        model.askPurge(model.state.value.deleted)
        assertEquals(2, model.state.value.confirmingPurge!!.size)
        model.cancelPurge()
        assertTrue(services.purged.isEmpty())
        tabs.manifest = zekke.core.folders.placeItem("8", "home", AT).apply(tabs.manifest, ManifestScope.SECRETS.rules)
        model.askPurge(listOf(model.state.value.deleted[1]))
        model.confirmPurge()
        assertEquals(listOf(listOf("8")), services.purged)
        assertNull(model.state.value.confirmingPurge)
        assertNull(tabs.manifest.items["8"])
        model.hideDeleted()
        assertFalse(model.state.value.showingDeleted)
    }

    @Test
    fun aFailedPurgeKeepsThePlacements() {
        val model = model()
        services.result = VaultWrite.Failed(ItemFailure.StaleKeys)
        model.askPurge(listOf(model.state.value.deleted[0]))
        model.confirmPurge()
        assertEquals(ItemFailure.StaleKeys, model.state.value.failure)
        assertNull(model.state.value.confirmingPurge)
    }
}
