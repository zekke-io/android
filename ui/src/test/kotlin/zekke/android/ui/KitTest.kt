package zekke.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.AddOption
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.FloatingAddMenu
import zekke.android.ui.components.Notice
import zekke.android.ui.components.StorageReading
import zekke.android.ui.components.ZekkeIconButton
import zekke.android.ui.components.formatBytes
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bytesAreFormattedAsTheWebAppDoes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.5 MiB", formatBytes(1024L * 1024 * 3 / 2))
        assertEquals("10.0 GiB", formatBytes(10L * 1024 * 1024 * 1024))
        assertEquals("—", formatBytes(-1))
    }

    @Test
    fun freeSpaceCountsUnfinishedUploadsWhileTheBarShowsThemApart() {
        val reading = StorageReading(storedBytes = 300, usedBytes = 400, quotaBytes = 1000)
        assertEquals(600L, reading.freeBytes)
        assertEquals(100L, reading.unfinishedBytes)
        assertEquals(0.3f, reading.storedShare, 0.0001f)
        assertEquals(0.1f, reading.reservedShare, 0.0001f)
        assertEquals(30.0, reading.percent, 0.0)
        val over = StorageReading(storedBytes = 1500, usedBytes = 1600, quotaBytes = 1000)
        assertEquals(0L, over.freeBytes)
        assertEquals(1f, over.storedShare, 0f)
        assertEquals(0f, over.reservedShare, 0f)
        assertEquals(0f, StorageReading(10, 10, 0).storedShare, 0f)
    }

    @Test
    fun aMessageNoticeClosesAndAPageNoticeHasNoClose() {
        var dismissed = 0
        compose.setContent {
            ZekkeTheme {
                Notice("Password saved.", onDismiss = { dismissed += 1 })
                Notice("A username is permanent.")
            }
        }
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, dismissed)
        compose.onAllNodesWithContentDescriptionCount("Dismiss", 1)
    }

    @Test
    fun anIconButtonIsNamedByItsFullLabel() {
        var pressed = false
        compose.setContent {
            ZekkeTheme { ZekkeIconButton(R.drawable.ic_trash, hint = "Delete", label = "Delete Bank PIN", onClick = { pressed = true }) }
        }
        compose.onNodeWithContentDescription("Delete Bank PIN").performClick()
        assertTrue(pressed)
    }

    @Test
    fun theAddMenuOpensAndRunsTheChosenOption() {
        val chosen = mutableListOf<String>()
        compose.setContent {
            ZekkeTheme {
                FloatingAddMenu(
                    label = "Add",
                    options = listOf(
                        AddOption("New folder", R.drawable.ic_folder_plus) { chosen += "folder" },
                        AddOption("Upload files", R.drawable.ic_upload) { chosen += "upload" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Upload files").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Upload files").performClick()
        assertEquals(listOf("upload"), chosen)
        compose.onNodeWithText("New folder").assertDoesNotExist()
    }

    @Test
    fun aRefusalShowsOnlyKeepIt() {
        var kept = false
        compose.setContent {
            ZekkeTheme { ConfirmDeleteDialog(title = "Delete this tab?", message = "This tab cannot be deleted from this phone.", onDismiss = { kept = true }, onConfirm = null) }
        }
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Keep it").performClick()
        assertTrue(kept)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionCount(label: String, count: Int) {
        assertEquals(count, onAllNodes(androidx.compose.ui.test.hasContentDescription(label)).fetchSemanticsNodes().size)
    }
}
