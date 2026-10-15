package zekke.android.feature.drive

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import zekke.android.ui.components.ItemFailure
import zekke.core.trash.TrashKind

@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModelTest {
    private val services = FakeDriveServices()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.folders = listOf(folder("f1", "Zebra"), folder("f2", "Alpha"), folder("f3", "Inner", "f2"))
        services.files = listOf(entry("a", folderId = null), entry("b", folderId = "f2"), entry("c", folderId = "f2", thumbnailId = "t1"))
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(fake: FakeDriveServices = services): DriveViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { DriveViewModel(fake) } })[DriveViewModel::class]

    private val DriveViewModel.now get() = state.value

    @Test
    fun theDriveLoadsWithItsUsageAndTheTopFolderFirst() {
        val model = model()
        assertTrue(model.now.loaded)
        assertEquals(listOf("Alpha", "Zebra"), model.now.subfolders.map { it.name })
        assertEquals(listOf("a"), model.now.filesHere.map { it.id })
        assertEquals(2_000, model.now.usage!!.storedBytes)
        assertEquals(30, model.now.retentionDays)
    }

    @Test
    fun aLoadFailureIsShownAndAChangeReloads() {
        services.loadFailure = ItemFailure.Offline
        val model = model()
        assertEquals(ItemFailure.Offline, model.now.failure)
        services.loadFailure = null
        services.changes.tryEmit(Unit)
        assertNull(model.now.failure)
        assertTrue(model.now.loaded)
    }

    @Test
    fun foldersOpenAndTheWayBackFollowsThePath() {
        val model = model()
        model.openFolder("f3")
        assertEquals(listOf("Alpha", "Inner"), model.now.path.map { it.name })
        assertEquals(emptyList<String>(), model.now.filesHere.map { it.id })
        model.up()
        assertEquals("f2", model.now.folderId)
        assertEquals(listOf("b", "c"), model.now.filesHere.map { it.id })
        model.up()
        assertNull(model.now.folderId)
        model.up()
        assertNull(model.now.folderId)
    }

    @Test
    fun aFolderThatWasDeletedElsewhereSendsYouToTheTop() {
        val model = model()
        model.openFolder("f2")
        services.folders = listOf(folder("f1", "Zebra"))
        model.refresh()
        assertNull(model.now.folderId)
    }

    @Test
    fun theViewTogglesBetweenGridAndList() {
        val model = model()
        assertTrue(model.now.grid)
        model.toggleGrid()
        assertFalse(model.now.grid)
    }

    @Test
    fun aThumbnailIsAskedForOnceAndKept() {
        val model = model()
        val withThumbnail = services.files[2]
        model.requestThumbnail(withThumbnail)
        model.requestThumbnail(withThumbnail)
        model.requestThumbnail(services.files[0])
        assertEquals(listOf("t1"), services.thumbnailsAsked)
        assertEquals(3, model.now.thumbnails.getValue("t1").size)
    }

    @Test
    fun aMissingThumbnailLeavesTheGlyph() {
        services.thumbnailBytes = null
        val model = model()
        model.requestThumbnail(services.files[2])
        assertTrue(model.now.thumbnails.isEmpty())
    }

    @Test
    fun aNewFolderIsNamedCheckedAndCreatedInTheOpenFolder() {
        val model = model()
        model.openFolder("f2")
        model.startNewFolder()
        assertEquals(NamingProblem.FolderEmpty, model.namingProblem())
        model.nameChanged(" alpha ")
        assertTrue(model.namingProblem() == null)
        model.nameChanged("inner")
        assertEquals(NamingProblem.FolderClash("Inner"), model.namingProblem())
        model.nameChanged("x".repeat(121))
        assertEquals(NamingProblem.FolderTooLong(120), model.namingProblem())
        model.nameChanged("  Photos ")
        model.commitNaming()
        assertEquals(listOf("create:Photos:f2"), services.calls)
        assertNull(model.now.naming)
    }

    @Test
    fun aNameThatClashesIsNeverSent() {
        val model = model()
        model.startNewFolder()
        model.nameChanged("zebra")
        model.commitNaming()
        assertTrue(services.calls.isEmpty())
        assertTrue(model.now.naming != null)
    }

    @Test
    fun nothingCanBeCreatedPastTheDepthLimit() {
        val deep = (1..8).map { folder("d$it", "L$it", if (it == 1) null else "d${it - 1}") }
        services.folders = deep
        val model = model()
        model.openFolder("d8")
        assertFalse(model.now.canCreateFolder)
        model.startNewFolder()
        assertNull(model.now.naming)
        model.openFolder("d7")
        assertTrue(model.now.canCreateFolder)
    }

    @Test
    fun renamingAFolderAllowsItsOwnNameAndRefusesASiblings() {
        val model = model()
        model.act(DriveItem.OfFolder(services.folders[1]))
        model.startRename(DriveItem.OfFolder(services.folders[1]))
        assertNull(model.now.acting)
        model.nameChanged("alpha")
        assertNull(model.namingProblem())
        model.nameChanged("zebra")
        assertEquals(NamingProblem.FolderClash("Zebra"), model.namingProblem())
        model.nameChanged("Beta")
        model.commitNaming()
        assertEquals(listOf("renameFolder:f2:Beta"), services.calls)
    }

    @Test
    fun renamingAFileFollowsTheFileNameRules() {
        val model = model()
        model.startRename(DriveItem.OfFile(services.files[0]))
        assertEquals(DriveNaming.RenameFile("a", "file-a.txt", "file-a.txt"), model.now.naming)
        model.nameChanged("a/b")
        assertEquals(NamingProblem.File(FileNameProblem.Separator), model.namingProblem())
        model.nameChanged("  ")
        assertEquals(NamingProblem.File(FileNameProblem.Empty), model.namingProblem())
        model.nameChanged(" better.txt ")
        model.commitNaming()
        assertEquals(listOf("renameFile:a:better.txt"), services.calls)
    }

    @Test
    fun aFailedRenameKeepsTheDialogAndShowsWhy() {
        val model = model()
        model.startRename(DriveItem.OfFile(services.files[0]))
        model.nameChanged("ok.txt")
        services.result = DriveWrite.Failed(ItemFailure.Offline)
        model.commitNaming()
        assertEquals(ItemFailure.Offline, model.now.failure)
        assertTrue(model.now.naming != null)
        assertFalse(model.now.busy)
    }

    @Test
    fun aFolderCannotMoveIntoItselfOrItsOwnPlace() {
        val model = model()
        model.askMove(DriveItem.OfFolder(services.folders[1]))
        val offered = model.destinations().map { it.first?.id }
        assertEquals(listOf(null, "f1"), offered)
        assertFalse(model.canMoveTo(null))
        assertTrue(model.canMoveTo("f1"))
        assertFalse(model.canMoveTo("f3"))
        model.moveTo("f3")
        assertTrue(services.calls.isEmpty())
        model.moveTo("f1")
        assertEquals(listOf("moveFolder:f2:f1"), services.calls)
        assertNull(model.now.moving)
    }

    @Test
    fun aFileMovesAnywhereButWhereItAlreadyIs() {
        val model = model()
        model.askMove(DriveItem.OfFile(services.files[1]))
        assertEquals(listOf(null, "f2", "f3", "f1").sortedBy { it ?: "" }, model.destinations().map { it.first?.id }.sortedBy { it ?: "" })
        assertFalse(model.canMoveTo("f2"))
        assertTrue(model.canMoveTo(null))
        model.moveTo(null)
        assertEquals(listOf("moveFiles:b:null"), services.calls)
    }

    @Test
    fun deletingNeedsAFullDeviceAndAConfirmation() {
        val model = model()
        model.askDelete(DriveItem.OfFile(services.files[0]))
        model.cancelDelete()
        assertTrue(services.calls.isEmpty())
        model.askDelete(DriveItem.OfFile(services.files[0]))
        model.confirmDelete()
        assertEquals(listOf("deleteFiles:a"), services.calls)
        model.askDelete(DriveItem.OfFolder(services.folders[0]))
        model.confirmDelete()
        assertEquals("deleteFolder:f1", services.calls.last())
        val limited = FakeDriveServices(canDelete = false)
        limited.files = services.files
        val other = ViewModelProvider.create(ViewModelStore(), viewModelFactory { initializer { DriveViewModel(limited) } })[DriveViewModel::class]
        other.askDelete(DriveItem.OfFile(limited.files[0]))
        assertNull(other.now.deleting)
    }

    @Test
    fun aFailedDeleteIsShownAndTheQuestionClosed() {
        val model = model()
        services.result = DriveWrite.Failed(ItemFailure.RateLimited(20))
        model.askDelete(DriveItem.OfFile(services.files[0]))
        model.confirmDelete()
        assertEquals(ItemFailure.RateLimited(20), model.now.failure)
        assertNull(model.now.deleting)
    }

    @Test
    fun theTrashListsRestoresAndPurgesWithConfirmation() {
        services.trashEntries = listOf(trashed("t1"), trashed("t2", kind = TrashKind.FOLDER))
        val model = model()
        model.showTrash()
        assertTrue(model.now.showingTrash)
        assertEquals(listOf("t1", "t2"), model.now.trash!!.map { it.id })
        model.restore(listOf(model.now.trash!![0]))
        assertEquals(listOf("restore:t1"), services.calls)
        model.askPurge(model.now.trash!!)
        assertEquals(2, model.now.confirmingPurge!!.size)
        model.cancelPurge()
        assertEquals(1, services.calls.size)
        model.askPurge(listOf(model.now.trash!![1]))
        model.confirmPurge()
        assertEquals("purge:t2", services.calls.last())
        assertNull(model.now.confirmingPurge)
        model.hideTrash()
        assertFalse(model.now.showingTrash)
    }

    @Test
    fun aDeviceThatIsNotFullCannotPurge() {
        val limited = FakeDriveServices(canDelete = false)
        limited.trashEntries = listOf(trashed("t1"))
        val model = model(limited)
        model.showTrash()
        model.askPurge(model.now.trash!!)
        assertNull(model.now.confirmingPurge)
    }
}
