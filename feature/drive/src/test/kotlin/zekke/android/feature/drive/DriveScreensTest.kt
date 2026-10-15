package zekke.android.feature.drive

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DriveScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeDriveServices()
    private lateinit var model: DriveViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.folders = listOf(folder("f1", "Holidays"))
        services.files = listOf(
            entry("a", "report.pdf", size = 2_500_000),
            entry("b", "half.bin", r2State = "pending"),
            entry("c", null),
            entry("d", "inside.txt", folderId = "f1"),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(fake: FakeDriveServices = services) {
        model = DriveViewModel(fake)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                DriveScreen(state, model)
            }
        }
    }

    @Test
    fun theGridShowsFoldersFilesTheirCaptionsAndTheStorage() {
        show()
        compose.onNodeWithText("Holidays").assertExists()
        compose.onNodeWithText("1 file").assertExists()
        compose.onNodeWithText("report.pdf").assertExists()
        compose.onNodeWithText("2.4 MiB").assertExists()
        compose.onNodeWithText("This upload never finished, so the file is not in your vault.").assertExists()
        compose.onNodeWithText("Unreadable file").assertExists()
        compose.onNodeWithText("1.2 KiB", substring = true).assertExists()
    }

    @Test
    fun openingAFolderShowsItsFilesAndTheWayBack() {
        show()
        compose.onNodeWithText("Holidays").performClick()
        compose.onNodeWithText("inside.txt").assertExists()
        compose.onNodeWithText("report.pdf").assertDoesNotExist()
        compose.onNodeWithContentDescription("Up one folder").performClick()
        compose.onNodeWithText("report.pdf").assertExists()
    }

    @Test
    fun anEmptyFolderSaysSo() {
        services.files = emptyList()
        services.folders = emptyList()
        show()
        compose.onNodeWithText("This folder is empty.").assertExists()
    }

    @Test
    fun theListViewShowsTheSameItems() {
        show()
        compose.onNodeWithContentDescription("Show as a list").performClick()
        compose.onNodeWithText("report.pdf").assertExists()
        compose.onNodeWithText("Holidays").assertExists()
    }

    @Test
    fun aFilesActionsRenameMoveAndDeleteWithTheTrashQuestion() {
        show()
        compose.onNodeWithContentDescription("More · report.pdf").performClick()
        compose.onNodeWithText("Move to…").assertExists()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete this file?").assertExists()
        compose.onNodeWithText("report.pdf goes to the Trash, where you can restore it for 30 days.", substring = true).assertExists()
        compose.onNodeWithText("Keep it").performClick()
        assertEquals(emptyList<String>(), services.calls)
    }

    @Test
    fun aDeviceThatIsNotFullIsOfferedNoDelete() {
        val limited = FakeDriveServices(canDelete = false)
        limited.files = services.files
        show(limited)
        compose.onNodeWithContentDescription("More · report.pdf").performClick()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun theTrashShowsItsNoticeAndItsEntries() {
        services.trashEntries = listOf(trashed("t1", "old.txt"))
        show()
        compose.onNodeWithText("Trash").performClick()
        compose.onNodeWithText("old.txt").assertExists()
        compose.onNodeWithText("Deleted documents and Drive files stay here for 30 days", substring = true).assertExists()
        compose.onNodeWithText("Back to the Drive").performClick()
        compose.onNodeWithText("report.pdf").assertExists()
    }
}
