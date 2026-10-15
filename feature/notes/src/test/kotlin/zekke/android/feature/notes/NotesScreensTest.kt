package zekke.android.feature.notes

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class NotesScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeNotesServices()
    private val tabs = FakeNoteTabs()
    private var ids = 0
    private lateinit var model: NotesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.tiles = listOf(tile("a", "Shopping", "• milk"), tile("b", null, "plain"), tile("c", null, "", readable = false))
        services.texts["a"] = "# Shopping\n- milk"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        model = NotesViewModel(services, tabs, 2000) { "tab-${++ids}" }
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                NotesScreen(state, model)
            }
        }
    }

    @Test
    fun theGridShowsTitlesUntitledAndUnreadableNotesAndTheCount() {
        show()
        compose.onNodeWithText("Shopping").assertExists()
        compose.onNodeWithText("Untitled note").assertExists()
        compose.onNodeWithText("Unreadable note").assertExists()
        compose.onNodeWithText("3 notes").assertExists()
    }

    @Test
    fun anEmptyGridSaysSo() {
        services.tiles = emptyList()
        show()
        compose.onNodeWithText("No notes yet", substring = true).assertExists()
    }

    @Test
    fun theAddButtonOpensABlankEditorWithTheCounter() {
        show()
        compose.onNodeWithContentDescription("Add a note").performClick()
        compose.onNodeWithText("5,000 characters left").assertExists()
        compose.onNodeWithText("Bold").assertExists()
        compose.onNodeWithContentDescription("Note body").performTextInput("hi")
        compose.onNodeWithText("4,998 characters left").assertExists()
    }

    @Test
    fun openingANoteShowsItsTitleAndSavedState() {
        show()
        compose.onNodeWithText("Shopping").performClick()
        compose.onNodeWithText("Saved").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        compose.onNodeWithContentDescription("Back to notes").performClick()
        compose.onNodeWithText("3 notes").assertExists()
        assertEquals(emptyList<Pair<String, String>>(), services.saves)
    }

    @Test
    fun aDeviceThatIsNotFullIsOfferedNoDelete() {
        val limited = FakeNotesServices(canDelete = false)
        limited.tiles = services.tiles
        limited.texts["a"] = "x"
        model = NotesViewModel(limited, tabs, 2000) { "tab-${++ids}" }
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                NotesScreen(state, model)
            }
        }
        compose.onNodeWithText("Shopping").performClick()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun theSurfaceModelDrawsTypesAndStylesWithoutMarkers() {
        val model = surfaceModelOf(zekke.core.notes.documentOfNote("# T\n- [x] done\n**b** plain"))
        assertEquals(listOf(zekke.android.ui.components.SurfaceLineKind.Title, zekke.android.ui.components.SurfaceLineKind.Task, zekke.android.ui.components.SurfaceLineKind.Text), model.lines.map { it.kind })
        assertEquals(listOf(0, 2, 7), model.lines.map { it.start })
        assertEquals(true, model.lines[1].checked)
        assertEquals(1, model.spans.size)
        assertEquals(7, model.spans[0].start)
        assertEquals(8, model.spans[0].end)
    }
}
