package zekke.android.feature.sharing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.ByteArrayOutputStream
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
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.FolderEditProblem
import zekke.core.folders.createFolder
import zekke.core.folders.emptyFolderManifest
import zekke.core.folders.placeItem
import zekke.core.sharing.SHARED_FOLDER_RULES

@OptIn(ExperimentalCoroutinesApi::class)
class SharedViewModelTest {
    private val services = FakeSharingServices()
    private val store = ViewModelStore()
    private var ids = 0
    private val ann = connection("1", "ann")
    private val bob = connection("2", "bob", nickname = "Bobby")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.items = listOf(bob, ann, connection("3", "cat", ConnectionStanding.WaitingForMe))
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): SharedViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { SharedViewModel(services, { "folder-${ids++}" }) } },
    )[SharedViewModel::class]

    private fun opened(): SharedViewModel = model().also { it.opened() }

    private fun inSpace(vararg items: SharedItem): SharedViewModel {
        services.space = SpaceLoad.Loaded(items.toList(), manifestWith(), true, false)
        return opened().also { it.openFriendship(ann) }
    }

    private fun manifestWith(vararg edits: zekke.core.folders.FolderEdit) =
        edits.fold(emptyFolderManifest(SHARED_FOLDER_RULES, AT)) { manifest, edit -> edit.apply(manifest, SHARED_FOLDER_RULES) }

    private fun SharedViewModel.space() = state.value.space!!

    @Test
    fun openingListsTheAcceptedConnectionsAndCatchesUpTheOnesThatCan() {
        val model = opened()
        assertEquals(listOf("1", "2"), model.state.value.friendships.map { it.id })
        assertTrue(model.state.value.loaded)
        assertEquals(listOf(listOf("2", "1")), services.caughtUp)
    }

    @Test
    fun aFailedLoadIsShownAndDismissed() {
        services.loadFailure = ItemFailure.Offline
        val model = opened()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        model.dismissFailure()
        assertNull(model.state.value.failure)
    }

    @Test
    fun theSpaceOpensWithWhatWentBothWays() {
        val sent = sharedItem("2", "Mine", inbound = false)
        val model = inSpace(sharedItem("1", "Theirs"), sent)
        assertEquals(listOf("1"), services.spaceLoads)
        assertFalse(model.space().loading)
        assertEquals(listOf("1", "2"), model.space().visibleItems.map { it.shareId })
        assertEquals("1", model.state.value.openId)
    }

    @Test
    fun aSlowLoadOfAnotherFriendshipNeverLandsInTheOneOpenNow() {
        val model = opened()
        val slow = CompletableDeferred<Unit>()
        services.spaceGate = slow
        services.space = SpaceLoad.Loaded(listOf(sharedItem("old", "Old")), manifestWith(), true, false)
        model.openFriendship(bob)
        model.closeFriendship()
        services.spaceGate = null
        services.space = SpaceLoad.Loaded(listOf(sharedItem("new", "New")), manifestWith(), true, false)
        model.openFriendship(ann)
        slow.complete(Unit)
        assertEquals(listOf("new"), model.space().items.map { it.shareId })
    }

    @Test
    fun aSpaceThatFailsToLoadSaysSo() {
        services.space = SpaceLoad.Failed(ItemFailure.Offline)
        val model = opened().also { it.openFriendship(ann) }
        assertEquals(SpaceProblem.Failure(ItemFailure.Offline), model.space().problem)
        assertFalse(model.space().loading)
    }

    @Test
    fun backStepsFromAnItemToTheFolderToItsParentToTheFriendships() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT), createFolder("Inner", "f1", "f2", AT))
        services.space = SpaceLoad.Loaded(listOf(sharedItem("1", "a")), manifest, true, false)
        val model = opened().also { it.openFriendship(ann) }
        model.openFolder("f2")
        model.openItem(sharedItem("1", "a"))
        assertEquals(listOf("f1", "f2"), model.space().path.map { it.id })
        assertTrue(model.back())
        assertNull(model.space().detail)
        assertTrue(model.back())
        assertEquals("f1", model.space().folderId)
        assertTrue(model.back())
        assertNull(model.space().folderId)
        assertTrue(model.back())
        assertNull(model.state.value.space)
        assertNull(model.state.value.openId)
        assertFalse(model.back())
    }

    @Test
    fun backClosesTheOpenFormAndTheMoveBeforeLeavingTheFolder() {
        val model = inSpace(sharedItem("1", "a"))
        model.startCreateFolder()
        assertTrue(model.back())
        assertNull(model.space().form)
        model.startMove(sharedItem("1", "a"))
        assertTrue(model.back())
        assertNull(model.space().moving)
    }

    @Test
    fun anUnreadableItemDoesNotOpen() {
        val model = inSpace(sharedItem("1", null, readable = false))
        model.openItem(model.space().items.single())
        assertNull(model.space().detail)
    }

    @Test
    fun aFolderIsCreatedInTheOpenFolderAndTheFormCloses() {
        val model = inSpace()
        model.startCreateFolder()
        model.submitForm()
        assertTrue(services.edits.isEmpty())
        model.draftChanged("Trips")
        model.submitForm()
        assertEquals(1, services.edits.size)
        assertEquals(listOf("Trips"), model.space().visibleFolders.map { it.name })
        assertNull(model.space().form)
        assertFalse(model.space().busy)
    }

    @Test
    fun aFolderRefusalKeepsTheFormAndSaysWhy() {
        val model = inSpace()
        services.folderResult = FolderChange.Refused(FolderEditProblem.TOO_DEEP)
        model.startCreateFolder()
        model.draftChanged("x")
        model.submitForm()
        assertEquals(SpaceProblem.Folders(FolderEditProblem.TOO_DEEP), model.space().problem)
        assertTrue(model.space().form != null)
        model.dismissProblem()
        assertNull(model.space().problem)
    }

    @Test
    fun aFailedFolderEditShowsTheFailure() {
        val model = inSpace()
        services.folderResult = FolderChange.Failed(ItemFailure.Conflict)
        model.startCreateFolder()
        model.draftChanged("x")
        model.submitForm()
        assertEquals(SpaceProblem.Failure(ItemFailure.Conflict), model.space().problem)
    }

    @Test
    fun oneFolderEditAtATime() {
        val model = inSpace()
        services.gate = CompletableDeferred()
        model.startCreateFolder()
        model.draftChanged("x")
        model.submitForm()
        model.submitForm()
        model.cancelForm()
        assertEquals(1, services.edits.size)
        assertTrue(model.space().form != null)
        services.gate!!.complete(Unit)
        assertNull(model.space().form)
    }

    @Test
    fun aFolderIsRenamed() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT))
        services.space = SpaceLoad.Loaded(emptyList(), manifest, true, false)
        val model = opened().also { it.openFriendship(ann) }
        val folder = model.space().visibleFolders.single()
        model.openFolderMenu(folder)
        assertEquals("f1", model.space().menuFolder!!.id)
        model.startRenameFolder(folder)
        assertNull(model.space().folderMenu)
        assertEquals(FolderForm.Rename("f1", "Trips"), model.space().form)
        model.draftChanged("Journeys")
        model.submitForm()
        assertEquals(listOf("Journeys"), model.space().visibleFolders.map { it.name })
    }

    @Test
    fun deletingAFolderAsksFirstAndTheItemsFallToTheTop() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT), placeItem("1", "f1", AT))
        services.space = SpaceLoad.Loaded(listOf(sharedItem("1", "a")), manifest, true, false)
        val model = opened().also { it.openFriendship(ann) }
        assertTrue(model.space().visibleItems.isEmpty())
        val folder = model.space().visibleFolders.single()
        model.askDeleteFolder(folder)
        assertEquals("f1", model.space().confirmingFolderDelete!!.id)
        model.cancelDeleteFolder()
        assertTrue(services.edits.isEmpty())
        model.askDeleteFolder(folder)
        model.confirmDeleteFolder()
        assertNull(model.space().confirmingFolderDelete)
        assertTrue(model.space().visibleFolders.isEmpty())
        assertEquals(listOf("1"), model.space().visibleItems.map { it.shareId })
    }

    @Test
    fun deletingTheOpenFolderBringsYouToTheTop() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT))
        services.space = SpaceLoad.Loaded(emptyList(), manifest, true, false)
        val model = opened().also { it.openFriendship(ann) }
        model.openFolder("f1")
        model.askDeleteFolder(model.space().path.single())
        model.confirmDeleteFolder()
        assertNull(model.space().folderId)
    }

    @Test
    fun anItemIsMovedIntoAFolderAndBackToTheTop() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT))
        val item = sharedItem("1", "a")
        services.space = SpaceLoad.Loaded(listOf(item), manifest, true, false)
        val model = opened().also { it.openFriendship(ann) }
        model.openItem(item)
        model.startMove(item)
        assertNull(model.space().detail)
        model.moveTo("f1")
        assertNull(model.space().moving)
        assertTrue(model.space().visibleItems.isEmpty())
        model.openFolder("f1")
        assertEquals(listOf("1"), model.space().visibleItems.map { it.shareId })
        model.startMove(item)
        model.moveTo(null)
        model.openTop()
        assertEquals(listOf("1"), model.space().visibleItems.map { it.shareId })
    }

    @Test
    fun aSpaceWithoutTheSharingKeyIsFlatAndCannotBeOrganised() {
        services.space = SpaceLoad.Loaded(listOf(sharedItem("1", "a")), null, false, false)
        val model = opened().also { it.openFriendship(ann) }
        model.startCreateFolder()
        assertNull(model.space().form)
        model.startMove(sharedItem("1", "a"))
        assertNull(model.space().moving)
        assertEquals(listOf("1"), model.space().visibleItems.map { it.shareId })
    }

    @Test
    fun anInvalidManifestListsEverythingAtTheTopAndCanBeReset() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT), placeItem("1", "f1", AT))
        services.space = SpaceLoad.Loaded(listOf(sharedItem("1", "a")), manifest, true, true)
        val model = opened().also { it.openFriendship(ann) }
        assertTrue(model.space().visibleFolders.isEmpty())
        assertEquals(listOf("1"), model.space().visibleItems.map { it.shareId })
        model.startCreateFolder()
        assertNull(model.space().form)
        model.resetFolders()
        assertEquals(listOf("1"), services.resets)
        assertFalse(model.space().invalid)
    }

    @Test
    fun aCopyShowsItsNoticeAndClosesTheItem() {
        val item = sharedItem("1", "Note")
        val model = inSpace(item)
        model.openItem(item)
        model.copy(item)
        assertEquals(listOf("1" to item), services.copied)
        assertEquals(SpaceNotice.Copied("Note"), model.space().notice)
        assertNull(model.space().detail)
        model.dismissNotice()
        assertNull(model.space().notice)
    }

    @Test
    fun eachCopyRefusalHasItsOwnProblem() {
        val item = sharedItem("1", "File", type = SharedType.File)
        val model = inSpace(item)
        listOf(
            CopyOutcome.TooLarge to SpaceProblem.TooLarge,
            CopyOutcome.NoRoom to SpaceProblem.NoRoom,
            CopyOutcome.Failed(ItemFailure.Offline) to SpaceProblem.Failure(ItemFailure.Offline),
        ).forEach { (outcome, problem) ->
            services.copyResult = outcome
            model.openItem(item)
            model.copy(item)
            assertEquals(problem, model.space().problem)
            assertFalse(model.space().busy)
            model.dismissProblem()
        }
    }

    @Test
    fun whatCannotBeCopiedIsNeverSent() {
        val sent = sharedItem("1", "Mine", inbound = false)
        val document = sharedItem("2", "Doc", type = SharedType.Document)
        val model = inSpace(sent, document)
        model.copy(sent)
        model.copy(document)
        assertTrue(services.copied.isEmpty())
    }

    @Test
    fun oneCopyAtATime() {
        val item = sharedItem("1", "Note")
        val model = inSpace(item)
        services.gate = CompletableDeferred()
        model.copy(item)
        model.copy(item)
        assertEquals(1, services.copied.size)
        assertTrue(model.space().busy)
        services.gate!!.complete(Unit)
        assertFalse(model.space().busy)
    }

    @Test
    fun aFileIsSavedToTheChosenDocument() {
        val item = sharedItem("1", "photo.jpg", type = SharedType.File)
        val model = inSpace(item)
        val out = ByteArrayOutputStream()
        var discarded = false
        model.save(item, SaveTarget({ out }, { discarded = true }))
        assertEquals(listOf("1" to item), services.saved)
        assertEquals(SpaceNotice.Saved("photo.jpg"), model.space().notice)
        assertFalse(discarded)
    }

    @Test
    fun aFailedSaveShowsWhyAndAnImpossibleOneDiscardsTheTarget() {
        val item = sharedItem("1", "photo.jpg", type = SharedType.File)
        val note = sharedItem("2", "n")
        val model = inSpace(item, note)
        services.saveResult = SharingWrite.Failed(ItemFailure.Offline)
        model.save(item, SaveTarget({ ByteArrayOutputStream() }, {}))
        assertEquals(SpaceProblem.Failure(ItemFailure.Offline), model.space().problem)
        var discarded = false
        model.save(note, SaveTarget({ ByteArrayOutputStream() }, { discarded = true }))
        assertTrue(discarded)
        assertEquals(1, services.saved.size)
    }

    @Test
    fun theSystemPickerIsAnnounced() {
        val model = inSpace()
        model.systemScreenWillOpen()
        model.systemScreenReturned()
        assertEquals(1, services.pickerOpened)
        assertEquals(1, services.pickerReturned)
    }

    @Test
    fun aFriendshipThatWentAwayClosesItsSpaceWhenThePageOpensAgain() {
        val model = inSpace()
        services.items = listOf(bob)
        model.opened()
        assertNull(model.state.value.openId)
        assertNull(model.state.value.space)
    }

    @Test
    fun anOpenSpaceIsReadAgainWhenThePageOpensAgain() {
        val model = inSpace()
        val before = services.spaceLoads.size
        model.opened()
        assertEquals(before + 1, services.spaceLoads.size)
    }

    @Test
    fun aDeviceWithoutSharingDoesNotOrganise() {
        val limited = FakeSharingServices(holdsSharing = false, copyable = setOf(SharedType.Note))
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { SharedViewModel(limited) } },
        )[SharedViewModel::class]
        assertFalse(model.state.value.organises)
        assertEquals(setOf(SharedType.Note), model.state.value.copyable)
    }
}
