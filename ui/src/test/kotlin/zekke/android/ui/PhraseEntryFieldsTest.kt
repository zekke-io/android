package zekke.android.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.PhraseEntry
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhraseEntryFieldsTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = listOf("abandon", "ability", "able", "about")
    private var opened = 0
    private var returned = 0
    private var valid = true
    private val entry = PhraseEntry(
        suggestionsFor = { prefix -> words.filter { it.startsWith(String(prefix)) && prefix.isNotEmpty() } },
        isValid = { valid },
    ).also { it.start(12) }

    private fun show(scannable: Boolean = false) {
        compose.setContent {
            ZekkeTheme { PhraseEntryFields(entry, scan = if (scannable) PhraseScan({ opened++ }, { returned++ }) else null) }
        }
    }

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    @Test
    fun theKeyboardsNextKeyMovesToTheNextWordEvenWithSuggestionsShown() {
        show()
        field(0).performClick()
        field(0).performTextInput("aba")
        compose.onNodeWithText("abandon").assertExists()
        field(0).performImeAction()
        field(1).assertIsFocused()
        field(1).performTextInput("abi")
        field(1).performImeAction()
        field(2).assertIsFocused()
    }

    @Test
    fun aSuggestionFillsTheWordAndMovesOn() {
        show()
        field(0).performClick()
        field(0).performTextInput("aba")
        compose.onNodeWithText("abandon").performClick()
        compose.runOnIdle { assertEquals("abandon", entry.word(0)) }
        field(1).assertIsFocused()
    }

    @Test
    fun choosingTwentyFourWordsShowsTwentyFourFieldsAndStartsOver() {
        show()
        field(0).performTextInput("abandon")
        compose.onNodeWithText("24 words").performClick()
        compose.runOnIdle {
            assertEquals(24, entry.state.wordCount)
            assertEquals("", entry.word(0))
        }
        compose.onNodeWithText("Word 24").assertExists()
    }

    @Test
    fun theScanButtonShowsOnlyWhenOfferedAndRunsTheCallback() {
        show(scannable = false)
        compose.onNodeWithText("Scan the QR code of your recovery kit").assertDoesNotExist()
    }

    @Test
    fun theScanButtonOpensTheScannerThatExplainsTheCameraBeforeAskingForIt() {
        show(scannable = true)
        compose.onNodeWithText("Scan the QR code of your recovery kit").performClick()
        compose.onNodeWithText("Scan your recovery kit").assertExists()
        compose.onNodeWithText("Zekke uses the camera only to read the QR code", substring = true).assertExists()
        assertEquals(0, opened)
        compose.onNodeWithText("Allow the camera").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun cancellingTheScannerReturnsToTheFormWithItsWords() {
        show(scannable = true)
        compose.runOnIdle { entry.wordChanged(0, "abandon") }
        compose.onNodeWithText("Scan the QR code of your recovery kit").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Scan your recovery kit").assertDoesNotExist()
        compose.runOnIdle { assertEquals("abandon", entry.word(0)) }
    }

    @Test
    fun theChecksumNoticeShowsWhenAskedForAndGoesAtTheNextEdit() {
        valid = false
        show()
        repeat(12) { entry.wordChanged(it, "abandon") }
        compose.runOnIdle { assertFalse(entry.validate()) }
        compose.onNodeWithText("This recovery phrase could not be verified", substring = true).assertExists()
        compose.runOnIdle { entry.wordChanged(11, "about") }
        compose.onNodeWithText("This recovery phrase could not be verified", substring = true).assertDoesNotExist()
    }

    @Test
    fun aRefusedScanIsSaidAndANotherPhraseFillsEveryField() {
        show()
        compose.runOnIdle { assertFalse(entry.acceptScanned("not a phrase")) }
        compose.onNodeWithText("That code is not a Zekke recovery phrase", substring = true).assertExists()
        compose.runOnIdle { assertTrue(entry.acceptScanned(List(12) { "abandon" }.joinToString(" "))) }
        compose.onNodeWithText("That code is not a Zekke recovery phrase", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText("abandon").assertCountEquals(12)
    }
}
