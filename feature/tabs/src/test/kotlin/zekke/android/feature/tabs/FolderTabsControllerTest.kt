package zekke.android.feature.tabs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEdit
import zekke.core.folders.FolderManifest
import zekke.core.folders.FolderManifestProblem
import zekke.core.folders.HOME_FOLDER_ID
import zekke.core.folders.ManifestScope
import zekke.core.folders.emptyFolderManifest
import zekke.core.folders.liveFolders

private const val AT = "2026-10-01T00:00:00.000Z"

class FakeTabsServices(override var canDeleteItems: Boolean = true) : FolderTabsServices {
    private val rules = ManifestScope.SECRETS.rules
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    var manifest: FolderManifest = emptyFolderManifest(rules, AT)
    var next: TabsLoad? = null
    var gate: CompletableDeferred<Unit>? = null
    val edits = mutableListOf<FolderEdit>()
    var resets = 0
    var loads = 0

    override suspend fun load(): TabsLoad {
        loads++
        return next ?: TabsLoad.Loaded(manifest)
    }

    override suspend fun edit(edit: FolderEdit): TabsLoad {
        gate?.await()
        next?.let { return it }
        edits += edit
        manifest = edit.apply(manifest, rules)
        return TabsLoad.Loaded(manifest)
    }

    override suspend fun reset(): TabsLoad {
        resets++
        manifest = emptyFolderManifest(rules, AT)
        next = null
        return TabsLoad.Loaded(manifest)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FolderTabsControllerTest {
    private val services = FakeTabsServices()
    private val rules = ManifestScope.SECRETS.rules
    private var ids = 0
    private val controller by lazy {
        FolderTabsController(services, rules, CoroutineScope(UnconfinedTestDispatcher())) { "tab-${++ids}" }
    }

    private fun addTab(name: String): String {
        controller.startNew()
        controller.nameChanged(name)
        controller.commitName(controller.tabs(emptyList()))
        return controller.state.value.wanted!!
    }

    @Test
    fun theTabsLoadWithHomeFirstAndTheirCounts() {
        val work = addTab("Work")
        services.manifest = zekke.core.folders.placeItem("a", work, AT).apply(services.manifest, rules)
        controller.reload()
        val tabs = controller.tabs(listOf("a", "b"))
        assertEquals(HOME_FOLDER_ID, tabs.first().id)
        assertEquals(mapOf(HOME_FOLDER_ID to 1, work to 1), tabs.associate { it.id to it.count })
    }

    @Test
    fun anInvalidManifestIsReportedAndResetClearsItAndGoesHome() {
        services.next = TabsLoad.Invalid(FolderManifestProblem.CYCLE)
        controller.reload()
        assertEquals(FolderManifestProblem.CYCLE, controller.state.value.invalid)
        assertNull(controller.state.value.manifest)
        controller.reset()
        assertEquals(1, services.resets)
        assertNull(controller.state.value.invalid)
        assertTrue(controller.state.value.manifest != null)
        assertFalse(controller.state.value.busy)
    }

    @Test
    fun aLoadFailureIsShown() {
        services.next = TabsLoad.Failed(ItemFailure.Offline)
        controller.reload()
        assertEquals(ItemFailure.Offline, controller.state.value.failure)
        controller.dismissFailure()
        assertNull(controller.state.value.failure)
    }

    @Test
    fun aChangeInTheScopeReloads() {
        controller
        val before = services.loads
        services.changes.tryEmit(Unit)
        assertEquals(before + 1, services.loads)
    }

    @Test
    fun theActiveTabFollowsTheChoiceAndFallsBackWhenItDisappears() {
        val work = addTab("Work")
        assertEquals(work, controller.active(emptyList()))
        controller.select(HOME_FOLDER_ID)
        assertEquals(HOME_FOLDER_ID, controller.active(emptyList()))
        controller.select("gone")
        assertEquals(HOME_FOLDER_ID, controller.active(emptyList()))
    }

    @Test
    fun theFilterKeepsTheItemsOfTheOpenTab() {
        val work = addTab("Work")
        services.manifest = zekke.core.folders.placeItem("a", work, AT).apply(services.manifest, rules)
        controller.reload()
        assertEquals(listOf("a"), controller.filter(listOf("a", "b")) { it })
        controller.select(HOME_FOLDER_ID)
        assertEquals(listOf("b"), controller.filter(listOf("a", "b")) { it })
    }

    @Test
    fun aTabNameIsCheckedBeforeItIsSent() {
        addTab("Work")
        val tabs = controller.tabs(emptyList())
        controller.startNew()
        assertEquals(TabNameProblem.Empty, controller.nameProblem(tabs))
        controller.nameChanged("  work ")
        assertEquals(TabNameProblem.Clash("Work"), controller.nameProblem(tabs))
        controller.nameChanged("x".repeat(121))
        assertEquals(TabNameProblem.TooLong(120), controller.nameProblem(tabs))
        controller.nameChanged("Bank")
        assertNull(controller.nameProblem(tabs))
    }

    @Test
    fun renamingAllowsTheTabsOwnNameAndRefusesAnotherTabs() {
        val work = addTab("Work")
        addTab("Bank")
        val tabs = controller.tabs(emptyList())
        controller.startRename(tabs.first { it.id == work })
        controller.nameChanged("work")
        assertNull(controller.nameProblem(tabs))
        controller.nameChanged("Bank")
        assertEquals(TabNameProblem.Clash("Bank"), controller.nameProblem(tabs))
        controller.nameChanged("Office")
        controller.commitName(tabs)
        assertEquals("Office", controller.tabs(emptyList()).first { it.id == work }.name)
        assertNull(controller.state.value.naming)
    }

    @Test
    fun creatingATabOpensIt() {
        val id = addTab("Work")
        assertEquals("tab-1", id)
        assertEquals(id, controller.active(emptyList()))
        assertNull(controller.state.value.naming)
    }

    @Test
    fun aProblemNameIsNeverSent() {
        controller.startNew()
        controller.nameChanged(" ")
        controller.commitName(controller.tabs(emptyList()))
        assertTrue(services.edits.isEmpty())
    }

    @Test
    fun aFailedEditKeepsTheDialogAndShowsWhy() {
        controller.startNew()
        controller.nameChanged("Work")
        services.next = TabsLoad.Failed(ItemFailure.Conflict)
        controller.commitName(controller.tabs(emptyList()))
        assertEquals(ItemFailure.Conflict, controller.state.value.failure)
        assertTrue(controller.state.value.naming != null)
        assertFalse(controller.state.value.busy)
    }

    @Test
    fun theMainTabCannotBeDeletedAndTheItemsOfATabNeedAFullDevice() {
        val work = addTab("Work")
        services.manifest = zekke.core.folders.placeItem("a", work, AT).apply(services.manifest, rules)
        controller.reload()
        val tabs = controller.tabs(listOf("a"))
        assertEquals(TabDeleteRefusal.Home, controller.deleteRefusal(tabs.first { it.home }))
        assertNull(controller.deleteRefusal(tabs.first { it.id == work }))
        services.canDeleteItems = false
        assertEquals(TabDeleteRefusal.NeedsFullDevice("Work"), controller.deleteRefusal(tabs.first { it.id == work }))
        val empty = addTab("Empty")
        assertNull(controller.deleteRefusal(controller.tabs(listOf("a")).first { it.id == empty }))
    }

    @Test
    fun deletingATabDeletesItsItemsFirstThenTheTab() {
        val work = addTab("Work")
        services.manifest = zekke.core.folders.placeItem("a", work, AT).apply(services.manifest, rules)
        controller.reload()
        val ids = listOf("a", "b")
        val tabs = controller.tabs(ids)
        val deleted = mutableListOf<List<String>>()
        controller.startDelete(tabs.first { it.id == work })
        controller.confirmDelete(tabs, ids) { deleted += it; null }
        assertEquals(listOf(listOf("a")), deleted)
        assertTrue(liveFolders(services.manifest).none { it.id == work })
        assertNull(services.manifest.items["a"])
        assertNull(controller.state.value.deleting)
        assertEquals(HOME_FOLDER_ID, controller.active(ids))
    }

    @Test
    fun aFailureDeletingTheItemsKeepsTheTab() {
        val work = addTab("Work")
        services.manifest = zekke.core.folders.placeItem("a", work, AT).apply(services.manifest, rules)
        controller.reload()
        val ids = listOf("a")
        val tabs = controller.tabs(ids)
        controller.startDelete(tabs.first { it.id == work })
        controller.confirmDelete(tabs, ids) { ItemFailure.RateLimited(30) }
        assertEquals(ItemFailure.RateLimited(30), controller.state.value.failure)
        assertTrue(liveFolders(services.manifest).any { it.id == work })
        assertNull(controller.state.value.deleting)
        assertFalse(controller.state.value.busy)
    }

    @Test
    fun anEmptyTabIsDeletedWithoutTouchingItems() {
        val work = addTab("Work")
        val tabs = controller.tabs(emptyList())
        var called = false
        controller.startDelete(tabs.first { it.id == work })
        controller.confirmDelete(tabs, emptyList()) { called = true; null }
        assertFalse(called)
        assertTrue(liveFolders(services.manifest).none { it.id == work })
    }

    @Test
    fun movingFilingAndForgettingEditThePlacements() = kotlinx.coroutines.test.runTest {
        val work = addTab("Work")
        controller.move(listOf("a", "b"), work)
        assertEquals(work, services.manifest.items["a"]?.folderId)
        assertTrue(controller.file("c", work))
        assertEquals(work, services.manifest.items["c"]?.folderId)
        assertTrue(controller.file("d", HOME_FOLDER_ID))
        assertNull(services.manifest.items["d"])
        controller.forget(listOf("a"))
        assertNull(services.manifest.items["a"])
    }

    @Test
    fun theManagementChoiceOpensAndCloses() {
        val work = addTab("Work")
        controller.manage(work)
        assertEquals(work, controller.state.value.managing)
        controller.stopManaging()
        assertNull(controller.state.value.managing)
        controller.manage(work)
        controller.startRename(controller.tabs(emptyList()).first { it.id == work })
        assertNull(controller.state.value.managing)
        assertEquals(TabNaming(work, "Work"), controller.state.value.naming)
        controller.cancelNaming()
        assertNull(controller.state.value.naming)
    }
}
