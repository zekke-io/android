package zekke.android.feature.sharing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme
import zekke.core.folders.FolderEdit
import zekke.core.folders.createFolder
import zekke.core.folders.emptyFolderManifest
import zekke.core.folders.placeItem
import zekke.core.sharing.SHARED_FOLDER_RULES

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SharedScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeSharingServices()
    private lateinit var model: SharedViewModel
    private var ids = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.items = listOf(connection("1", "ann"), connection("2", "bob", nickname = "Bobby"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun manifestWith(vararg edits: FolderEdit) =
        edits.fold(emptyFolderManifest(SHARED_FOLDER_RULES, AT)) { manifest, edit -> edit.apply(manifest, SHARED_FOLDER_RULES) }

    private fun show() {
        model = SharedViewModel(services, { "folder-${ids++}" })
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                SharedScreen(state, model)
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    private fun count(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().size

    private fun enter(vararg items: SharedItem, manifest: zekke.core.folders.FolderManifest? = manifestWith(), organised: Boolean = true, invalid: Boolean = false) {
        services.space = SpaceLoad.Loaded(items.toList(), manifest, organised, invalid)
        show()
        compose.onNodeWithText("ann").performClick()
    }

    @Test
    fun theFriendshipsAreFoldersNamedAfterThePeople() {
        show()
        text("Bobby").assertExists()
        text("ann").assertExists()
    }

    @Test
    fun withoutFriendshipsTheEmptyStateExplains() {
        services.items = emptyList()
        show()
        text("Nothing is shared yet.").assertExists()
    }

    @Test
    fun aFriendshipShowsBothDirectionsAndMarksWhatWasSentByYou() {
        enter(sharedItem("1", "From them"), sharedItem("2", "To them", inbound = false))
        text("From them").assertExists()
        text("To them").assertExists()
        text("Note from ann").assertExists()
        text("Note you sent to ann").assertExists()
        text("SENT BY YOU").assertExists()
    }

    @Test
    fun anEmptyFriendshipSaysWhatWillAppearThere() {
        enter()
        text("Nothing here yet.").assertExists()
    }

    @Test
    fun aSecretIsMaskedUntilShownAndCanBeCopied() {
        enter(sharedItem("1", "Wifi", type = SharedType.Secret, text = "hunter2"))
        text("Wifi").performClick()
        text("••••••••").assertExists()
        assertEquals(0, count("hunter2"))
        text("Show value").performClick()
        text("hunter2").assertExists()
        text("Copy to my own account").assertIsEnabled()
        text("Copy to my own account").performClick()
        assertEquals(1, services.copied.size)
        text("Copied to your account. Your copy is independent of theirs.").assertExists()
    }

    @Test
    fun aNoteIsShownInFullAndWhatYouSentCannotBeCopied() {
        enter(sharedItem("1", "Title", text = "Title\nthe whole note"), sharedItem("2", "Mine", inbound = false))
        compose.onNodeWithText("Title").performClick()
        text("the whole note").assertExists()
        model.closeDetail()
        compose.onNodeWithText("Mine").performClick()
        text("Copy to my own account").assertIsNotEnabled()
        assertEquals(0, count("A copy is re-encrypted under a key of your own, so it survives the original being deleted or unshared. Anything you can read can be copied like this, which is why sharing never claims otherwise."))
    }

    @Test
    fun aDocumentSaysItCannotBeReadHereAndOffersNoCopy() {
        enter(sharedItem("1", "Plan", type = SharedType.Document, text = null))
        text("Plan").performClick()
        text("Shared documents cannot be opened here yet.").assertExists()
        text("Copy to my own account").assertIsNotEnabled()
    }

    @Test
    fun aFileCanBeDownloadedAndTheWarningSaysItIsNotEncrypted() {
        enter(sharedItem("1", "photo.jpg", type = SharedType.File, text = null))
        text("photo.jpg").performClick()
        text("Download").assertExists()
        text("The saved file is not encrypted").assertExists()
    }

    @Test
    fun anUnreadableItemIsNamedAsSuchAndDoesNotOpen() {
        enter(sharedItem("1", null, readable = false))
        text("Unreadable item").assertExists()
        text("This item could not be opened.").assertExists()
        text("Unreadable item").performClick()
        assertEquals(0, count("Copy to my own account"))
    }

    @Test
    fun theKeyboardCreatesAFolderThatShowsInTheList() {
        enter()
        text("New folder").performClick()
        text("Folder name").performTextInput("Trips")
        text("Folder name").performImeAction()
        assertEquals(1, services.edits.size)
        text("Trips").assertExists()
    }

    @Test
    fun aFolderOpensAndTheBreadcrumbGoesBack() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT), placeItem("1", "f1", AT))
        enter(sharedItem("1", "Inside"), manifest = manifest)
        assertEquals(0, count("Inside"))
        text("Trips").performClick()
        text("Inside").assertExists()
        compose.onNodeWithText("ann").performClick()
        assertEquals(0, count("Inside"))
    }

    @Test
    fun deletingAFolderNeedsTheConfirmationWhichStatesWhatHappensToItsContents() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT))
        enter(manifest = manifest)
        compose.onNodeWithContentDescription("Folder actions").performClick()
        text("Delete folder").performScrollTo().performClick()
        text("Everything filed in them moves to the top of this shared space; nothing is unshared.").assertExists()
        compose.onNodeWithText("Delete folder").performClick()
        assertEquals(1, services.edits.size)
    }

    @Test
    fun movingAnItemOffersTheFoldersAndTheTop() {
        val manifest = manifestWith(createFolder("Trips", null, "f1", AT))
        enter(sharedItem("1", "Thing"), manifest = manifest)
        text("Thing").performClick()
        text("Move to a folder").performScrollTo().performClick()
        text("The top of this space").assertExists()
        compose.onAllNodesWithText("Trips").onLast().performClick()
        text("Move here").performClick()
        assertEquals(1, services.edits.size)
        assertEquals(0, count("Thing"))
    }

    @Test
    fun aDeviceWithoutTheSharingKeySeesTheFlatSpaceAndNoFolderControls() {
        enter(sharedItem("1", "Flat"), manifest = null, organised = false)
        text("This phone does not hold the sharing key").assertExists()
        text("Flat").assertExists()
        assertEquals(0, count("New folder"))
    }

    @Test
    fun anInvalidManifestOffersTheReset() {
        enter(sharedItem("1", "Flat"), invalid = true)
        text("The folders in this space could not be shown safely").assertExists()
        text("Reset the folders").performClick()
        assertEquals(1, services.resets.size)
    }

    @Test
    fun theHostShowsBothSectionsOnlyWhenThePhoneHoldsSharing() {
        val connections = ConnectionsViewModel(services)
        val sharedModel = SharedViewModel(services)
        compose.setContent {
            ZekkeTheme {
                val shared by sharedModel.state.collectAsState()
                val state by connections.state.collectAsState()
                SharingScreen(shared, sharedModel, state, connections)
            }
        }
        text("Connections").performClick()
        text("Invite someone").assertExists()
        text("Shared").performClick()
        text("Bobby").assertExists()
    }

    @Test
    fun theHostOffersNoConnectionsWithoutTheSharingKey() {
        val limited = FakeSharingServices(holdsSharing = false)
        limited.items = services.items
        val connections = ConnectionsViewModel(limited)
        val sharedModel = SharedViewModel(limited)
        compose.setContent {
            ZekkeTheme {
                val shared by sharedModel.state.collectAsState()
                val state by connections.state.collectAsState()
                SharingScreen(shared, sharedModel, state, connections)
            }
        }
        assertEquals(0, count("Connections"))
        assertTrue(count("Invite someone") == 0)
    }
}
