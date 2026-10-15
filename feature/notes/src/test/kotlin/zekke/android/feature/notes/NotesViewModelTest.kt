package zekke.android.feature.notes

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.android.ui.components.ItemFailure
import zekke.core.notes.NoteLineCommand

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val services = FakeNotesServices()
    private val tabs = FakeNoteTabs()
    private val store = ViewModelStore()
    private var tabIds = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        services.tiles = listOf(tile("a", "Shopping"), tile("b", null))
        services.texts["a"] = "# Shopping\n- milk"
        services.texts["b"] = null
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): NotesViewModel {
        val model = ViewModelProvider.create(store, viewModelFactory { initializer { NotesViewModel(services, tabs, 2000) { "tab-${++tabIds}" } } })[NotesViewModel::class]
        scheduler.runCurrent()
        return model
    }

    private fun type(model: NotesViewModel, text: String, selection: TextRange = TextRange(text.length)) =
        model.valueChanged(TextFieldValue(text, selection))

    @Test
    fun theTilesLoadAtOnce() {
        val model = model()
        assertTrue(model.state.value.loaded)
        assertEquals(listOf("a", "b"), model.state.value.tiles.map { it.id })
        assertTrue(model.state.value.canDelete)
    }

    @Test
    fun aLoadFailureIsShownAndAChangeReloads() {
        services.loadFailure = ItemFailure.Offline
        val model = model()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        services.loadFailure = null
        services.changes.tryEmit(Unit)
        scheduler.runCurrent()
        assertNull(model.state.value.failure)
    }

    @Test
    fun aNewNoteGetsItsIdOnceAndStartsBlank() {
        val model = model()
        model.startNew()
        val editor = model.state.value.editor!!
        assertEquals("note-1", editor.id)
        assertEquals(NoteSaveState.Blank, editor.saveState)
        assertNull(editor.saved)
    }

    @Test
    fun typingIsSavedTwoSecondsAfterTheLastKeyWithTheSerialisedNote() {
        val model = model()
        model.startNew()
        type(model, "Hel")
        scheduler.advanceTimeBy(1500)
        type(model, "Hello")
        scheduler.advanceTimeBy(1500)
        assertTrue(services.saves.isEmpty())
        scheduler.advanceTimeBy(600)
        scheduler.runCurrent()
        assertEquals(listOf("note-1" to "Hello"), services.saves)
        assertEquals(NoteSaveState.Saved, model.state.value.editor!!.saveState)
    }

    @Test
    fun theKeyboardsCompositionSurvivesAnOrdinaryEditAndIsDroppedWhenTheModelRewritesTheText() {
        val model = model()
        model.startNew()
        model.valueChanged(TextFieldValue("hel", TextRange(3), TextRange(0, 3)))
        assertEquals(TextRange(0, 3), model.state.value.editor!!.value.composition)
        model.lineType(NoteLineCommand.TOPIC)
        model.valueChanged(TextFieldValue("hel\n", TextRange(4)))
        model.valueChanged(TextFieldValue("hel\n\n", TextRange(5), TextRange(5, 5)))
        assertNull(model.state.value.editor!!.value.composition)
    }

    @Test
    fun anEmptyNoteIsNeverSaved() {
        val model = model()
        model.startNew()
        type(model, "   ")
        scheduler.advanceTimeBy(5000)
        assertTrue(services.saves.isEmpty())
        assertEquals(NoteSaveState.Blank, model.state.value.editor!!.saveState)
    }

    @Test
    fun aNoteOverTheLimitIsNotSavedAndSaysSo() {
        val model = model()
        model.startNew()
        type(model, "x".repeat(5001))
        scheduler.advanceTimeBy(5000)
        assertTrue(services.saves.isEmpty())
        assertEquals(NoteSaveState.OverLimit, model.state.value.editor!!.saveState)
        assertEquals(-1, model.state.value.editor!!.charactersLeft)
    }

    @Test
    fun formattingTheSelectionIsWhatIsSaved() {
        val model = model()
        model.startNew()
        type(model, "hello world", TextRange(0, 5))
        model.toggleBold()
        model.largerText()
        scheduler.advanceTimeBy(2100)
        assertEquals("{{16}}**hello**{{/}} world", services.saves.single().second)
    }

    @Test
    fun theLineCommandsFollowTheWebAppAndATaskCanBeTicked() {
        val model = model()
        model.startNew()
        type(model, "buy milk", TextRange(2))
        model.lineType(NoteLineCommand.TASK)
        assertEquals("- [ ] buy milk", model.state.value.editor!!.draft)
        model.toggleTask(0)
        assertEquals("- [x] buy milk", model.state.value.editor!!.draft)
        model.lineType(NoteLineCommand.TITLE)
        assertEquals("# buy milk", model.state.value.editor!!.draft)
    }

    @Test
    fun enterOnAnEmptyTopicEndsTheList() {
        val model = model()
        model.startNew()
        type(model, "milk")
        model.lineType(NoteLineCommand.TOPIC)
        type(model, "milk\n")
        assertEquals("- milk\n- ", model.state.value.editor!!.draft)
        type(model, "milk\n\n")
        assertEquals("- milk\n", model.state.value.editor!!.draft)
        assertEquals("milk\n", model.state.value.editor!!.document.plain)
    }

    @Test
    fun openingANoteShowsItsTextAndSavedState() {
        val model = model()
        model.open(model.state.value.tiles[0])
        scheduler.runCurrent()
        val editor = model.state.value.editor!!
        assertEquals("a", editor.id)
        assertEquals("Shopping\nmilk", editor.document.plain)
        assertEquals(NoteSaveState.Saved, editor.saveState)
    }

    @Test
    fun aNoteThatCannotBeReadIsNotEditableOrSaved() {
        services.tiles = listOf(tile("b", null, readable = true))
        val model = model()
        model.open(model.state.value.tiles[0])
        scheduler.runCurrent()
        val editor = model.state.value.editor!!
        assertTrue(editor.unreadable)
        type(model, "overwrite")
        model.toggleBold()
        scheduler.advanceTimeBy(5000)
        assertTrue(services.saves.isEmpty())
        assertEquals("", model.state.value.editor!!.document.plain)
    }

    @Test
    fun aTileThatIsNotReadableDoesNotOpen() {
        services.tiles = listOf(tile("z", readable = false))
        val model = model()
        model.open(model.state.value.tiles[0])
        scheduler.runCurrent()
        assertNull(model.state.value.editor)
    }

    @Test
    fun oneSaveRunsAtATimeAndWhatWasTypedMeanwhileIsSavedAfter() {
        val model = model()
        model.startNew()
        type(model, "one")
        services.gate = CompletableDeferred()
        scheduler.advanceTimeBy(2100)
        assertEquals(1, services.saves.size)
        assertEquals(NoteSaveState.Saving, model.state.value.editor!!.saveState)
        type(model, "one two")
        scheduler.advanceTimeBy(2100)
        assertEquals(1, services.saves.size)
        services.gate!!.complete(Unit)
        scheduler.advanceTimeBy(2100)
        scheduler.runCurrent()
        assertEquals(listOf("one", "one two"), services.saves.map { it.second })
        assertEquals(NoteSaveState.Saved, model.state.value.editor!!.saveState)
    }

    @Test
    fun aFailedSaveKeepsTheDraftShowsWhyAndDoesNotLoop() {
        val model = model()
        model.startNew()
        services.saveResult = NoteSave.Failed(ItemFailure.Offline)
        type(model, "draft")
        scheduler.advanceTimeBy(2100)
        scheduler.advanceTimeBy(20000)
        assertEquals(1, services.saves.size)
        val editor = model.state.value.editor!!
        assertEquals(ItemFailure.Offline, editor.failure)
        assertEquals("draft", editor.document.plain)
        assertEquals(NoteSaveState.Editing, editor.saveState)
        services.saveResult = NoteSave.Saved
        type(model, "draft!")
        scheduler.advanceTimeBy(2100)
        assertEquals(2, services.saves.size)
        assertNull(model.state.value.editor!!.failure)
    }

    @Test
    fun aFeedChangeNeverReplacesTheDraft() {
        val model = model()
        model.startNew()
        type(model, "mine")
        services.tiles = listOf(tile("a"))
        services.changes.tryEmit(Unit)
        scheduler.runCurrent()
        assertEquals("mine", model.state.value.editor!!.document.plain)
        assertEquals(listOf("a"), model.state.value.tiles.map { it.id })
    }

    @Test
    fun leavingTheEditorSavesAtOnceAndReturnsToTheGrid() {
        val model = model()
        model.startNew()
        type(model, "quick")
        model.close()
        scheduler.runCurrent()
        assertEquals(listOf("note-1" to "quick"), services.saves)
        assertNull(model.state.value.editor)
    }

    @Test
    fun leavingWithNothingNewSavesNothing() {
        val model = model()
        model.open(model.state.value.tiles[0])
        scheduler.runCurrent()
        model.close()
        scheduler.runCurrent()
        assertTrue(services.saves.isEmpty())
        assertNull(model.state.value.editor)
    }

    @Test
    fun theUnsavedTextIsOfferedToTheLockAndNothingElse() {
        val model = model()
        assertNull(services.provider!!())
        model.startNew()
        assertNull(services.provider!!())
        type(model, "typed just now")
        val pending = services.provider!!()!!
        assertEquals("note-1", pending.id)
        assertEquals("typed just now", pending.text)
        scheduler.advanceTimeBy(2100)
        assertNull(services.provider!!())
        store.clear()
        assertNull(services.provider)
    }

    @Test
    fun aNoteMadeInAnOpenTabIsFiledThereAfterItsFirstSave() {
        val model = model()
        model.tabs.startNew()
        model.tabs.nameChanged("Work")
        model.tabs.commitName(emptyList())
        scheduler.runCurrent()
        val work = model.tabs.state.value.wanted!!
        model.startNew()
        type(model, "in work")
        scheduler.advanceTimeBy(2100)
        scheduler.runCurrent()
        assertEquals(work, tabs.manifest.items["note-1"]?.folderId)
    }

    @Test
    fun aNoteMadeInTheMainTabIsNotFiled() {
        val model = model()
        model.startNew()
        type(model, "plain")
        scheduler.advanceTimeBy(2100)
        scheduler.runCurrent()
        assertNull(tabs.manifest.items["note-1"])
    }

    @Test
    fun deletingNeedsASavedNoteAFullDeviceAndAConfirmation() {
        val model = model()
        model.startNew()
        assertNotNull(model.state.value.editor)
        model.confirmDelete()
        scheduler.runCurrent()
        assertNull(model.state.value.editor)
        assertTrue(services.deleted.isEmpty())
        model.open(model.state.value.tiles[0])
        scheduler.runCurrent()
        model.askDelete()
        assertTrue(model.state.value.editor!!.confirmingDelete)
        model.cancelDelete()
        assertFalse(model.state.value.editor!!.confirmingDelete)
        model.askDelete()
        model.confirmDelete()
        scheduler.runCurrent()
        assertEquals(listOf(listOf("a")), services.deleted)
        assertNull(model.state.value.editor)
    }

    @Test
    fun aSpacesItemsAreDeletedThroughTheServices() = kotlinx.coroutines.test.runTest {
        val model = model()
        assertNull(model.deleteSpaceItems(listOf("a", "b")))
        assertEquals(listOf(listOf("a", "b")), services.deleted)
    }
}
