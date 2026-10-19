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
    fun whileWhatTheAccountKeepsIsUnknownNothingIsOfferedForDeletion() {
        services.usageFailure = ItemFailure.Offline
        val model = model()
        assertNull(model.now.retentionDays)
        assertFalse(model.canDeleteItem(DriveItem.OfFile(services.files[0])))
        model.askDelete(DriveItem.OfFile(services.files[0]))
        assertNull(model.now.deleting)
        services.usageFailure = null
        model.refresh()
        assertEquals(30, model.now.retentionDays)
        assertTrue(model.canDeleteItem(DriveItem.OfFile(services.files[0])))
    }

    @Test
    fun anUnfinishedUploadIsDiscardedNotDeletedAndOnAnyDevice() {
        val limited = FakeDriveServices(canDelete = false)
        limited.files = listOf(entry("p", "half.bin", r2State = "pending"), entry("q"))
        limited.usageFailure = ItemFailure.Offline
        val model = model(limited)
        assertTrue(model.canDeleteItem(DriveItem.OfFile(limited.files[0])))
        assertFalse(model.canDeleteItem(DriveItem.OfFile(limited.files[1])))
        model.askDelete(DriveItem.OfFile(limited.files[0]))
        model.confirmDelete()
        assertEquals(listOf("discard:p"), limited.calls)
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

    private fun photo(id: String = "p1", size: Long = 1000) =
        DriveEntry(id, "photo.jpg", "image/jpeg", size, null, null, "ok", "ok")

    private fun pdf(id: String = "d1") = DriveEntry(id, "paper.pdf", "application/pdf", 5000, null, null, "ok", "ok")

    @Test
    fun anImageOpensFromMemory() {
        val model = model()
        model.preview(photo())
        val viewing = model.now.viewing as Viewing.Image
        assertEquals("p1", viewing.entry.id)
        assertEquals(listOf("p1"), services.previewed)
        model.closePreview()
        assertNull(model.now.viewing)
    }

    @Test
    fun aPdfOpensFromATemporaryFileThatIsDeletedOnClose() {
        val file = java.io.File("preview.pdf")
        services.previewResult = PreviewLoad.Pdf(file)
        val model = model()
        model.preview(pdf())
        assertEquals(file, (model.now.viewing as Viewing.Pdf).file)
        assertTrue(services.discardedPreviews.isEmpty())
        model.closePreview()
        assertEquals(listOf(file), services.discardedPreviews)
        assertNull(model.now.viewing)
    }

    @Test
    fun thePdfFileIsDeletedWhenTheViewModelEnds() {
        val file = java.io.File("preview.pdf")
        services.previewResult = PreviewLoad.Pdf(file)
        val model = model()
        model.preview(pdf())
        store.clear()
        assertEquals(listOf(file), services.discardedPreviews)
    }

    @Test
    fun aTooLargeImageAndAFailureAreShownNotCrashed() {
        val model = model()
        services.previewResult = PreviewLoad.TooLarge
        model.preview(photo(size = 99_000_000))
        assertTrue(model.now.viewing is Viewing.TooLarge)
        model.closePreview()
        services.previewResult = PreviewLoad.Failed(ItemFailure.Offline)
        model.preview(photo())
        assertEquals(ItemFailure.Offline, (model.now.viewing as Viewing.Failed).failure)
    }

    @Test
    fun onlyReadableFinishedImagesAndPdfsPreview() {
        val model = model()
        model.preview(entry("t"))
        model.preview(photo().copy(name = null))
        model.preview(photo().copy(r2State = "pending"))
        assertNull(model.now.viewing)
        assertTrue(services.previewed.isEmpty())
    }

    @Test
    fun savingWritesThroughTheTargetAndSaysTheCopyIsUnprotected() {
        val model = model()
        val output = java.io.ByteArrayOutputStream()
        var discarded = false
        model.saveTo(photo(), ExportTarget({ output }, { discarded = true }))
        assertEquals(listOf("save:p1"), services.calls)
        assertEquals(listOf<Byte>(9, 9), output.toByteArray().toList())
        assertEquals("photo.jpg", model.now.saved)
        assertFalse(discarded)
        model.dismissSaved()
        assertNull(model.now.saved)
    }

    @Test
    fun aFailedSaveDeletesWhatWasWrittenAndSaysWhy() {
        val model = model()
        services.saveResult = DriveWrite.Failed(ItemFailure.Offline)
        var discarded = false
        model.saveTo(photo(), ExportTarget({ java.io.ByteArrayOutputStream() }, { discarded = true }))
        assertTrue(discarded)
        assertNull(model.now.saved)
        assertEquals(ItemFailure.Offline, model.now.failure)
    }

    @Test
    fun sharingHandsTheUriToTheScreenOnceAndTheSystemScreenIsMarked() {
        val model = model()
        services.shareResult = ShareLoad.Ready("content://zekke.shared/shared/x/photo.jpg", "image/jpeg")
        model.shareFile(photo())
        assertTrue(model.now.share != null)
        assertEquals(listOf("share:p1"), services.calls)
        model.shareHandled()
        assertNull(model.now.share)
        model.systemScreenWillOpen()
        model.systemScreenReturned()
        assertEquals(1, services.screens)
        assertEquals(1, services.screensReturned)
    }

    @Test
    fun aFailedShareIsShown() {
        val model = model()
        services.shareResult = ShareLoad.Failed(ItemFailure.RateLimited(5))
        model.shareFile(photo())
        assertNull(model.now.share)
        assertEquals(ItemFailure.RateLimited(5), model.now.failure)
    }

    private fun upload(id: String, status: UploadStatus, folder: String? = null, percent: Int = 0) =
        UploadItem(id, "$id.bin", 100, folder, status, percent)

    @Test
    fun theQueueShowsOnlyTheUploadsOfTheOpenFolder() {
        val model = model()
        services.uploadItems.value = listOf(upload("a", UploadStatus.Uploading, null, 40), upload("b", UploadStatus.Waiting, "f2"))
        assertEquals(listOf("a"), model.now.uploadsHere.map { it.id })
        model.openFolder("f2")
        assertEquals(listOf("b"), model.now.uploadsHere.map { it.id })
    }

    @Test
    fun pickedFilesGoToTheQueueInTheOpenFolder() {
        val model = model()
        model.filesPicked(emptyList())
        assertTrue(services.enqueued.isEmpty())
        model.openFolder("f2")
        model.filesPicked(listOf("content://a", "content://b"))
        assertEquals(listOf(listOf("content://a", "content://b") to "f2"), services.enqueued)
    }

    @Test
    fun anUploadIsRetriedAndDiscardedOnlyAfterAConfirmation() {
        val model = model()
        val item = upload("a", UploadStatus.Failed)
        model.retryUpload("a")
        assertEquals(listOf("a"), services.retried)
        model.askCancelUpload(item)
        model.keepUpload()
        assertTrue(services.cancelled.isEmpty())
        model.askCancelUpload(item)
        model.confirmCancelUpload()
        assertEquals(listOf("a"), services.cancelled)
        assertNull(model.now.cancelling)
    }

    @Test
    fun theFileChosenAgainGoesToTheUploadThatNeededIt() {
        val model = model()
        model.chosenAgain("content://ignored")
        assertTrue(services.reChosen.isEmpty())
        model.askChooseAgain(upload("a", UploadStatus.NeedsFile))
        model.chosenAgain("content://new")
        assertEquals(listOf("a" to "content://new"), services.reChosen)
        assertNull(model.now.choosing)
        model.askChooseAgain(upload("a", UploadStatus.NeedsFile))
        model.cancelChoosing()
        assertNull(model.now.choosing)
    }

    @Test
    fun theUploadsPageOpensAndCloses() {
        val model = DriveViewModel(FakeDriveServices())
        model.showUploads()
        assertTrue(model.state.value.showingUploads)
        model.hideUploads()
        assertFalse(model.state.value.showingUploads)
    }
}
