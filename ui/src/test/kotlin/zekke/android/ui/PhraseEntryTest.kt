package zekke.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zekke.android.ui.components.PHRASE_NO_FOCUS
import zekke.android.ui.components.PhraseEntry
import zekke.android.ui.components.normalizePhraseWord

class PhraseEntryTest {
    private val prefixes = mutableListOf<CharArray>()
    private val validated = mutableListOf<List<CharArray>>()
    private var valid = true

    private val entry = PhraseEntry(
        suggestionsFor = { prefix ->
            prefixes += prefix
            if (prefix.isEmpty()) emptyList() else listOf(String(prefix) + "a", String(prefix) + "b")
        },
        isValid = { words ->
            validated += words
            valid
        },
    )

    private fun fill(count: Int) {
        entry.start(count)
        repeat(count) { entry.wordChanged(it, "wordx") }
    }

    @Test
    fun anInactiveEntryHoldsNothingAndIgnoresEdits() {
        assertFalse(entry.active)
        entry.wordChanged(0, "abandon")
        assertEquals("", entry.word(0))
        assertFalse(entry.complete)
        assertFalse(entry.validate())
        assertTrue(entry.copyWords().isEmpty())
    }

    @Test
    fun startsEmptyAtTheChosenLengthAndIgnoresOtherLengths() {
        entry.start(24)
        assertTrue(entry.active)
        assertEquals(24, entry.state.wordCount)
        assertEquals("", entry.word(23))
        assertFalse(entry.complete)
        entry.start(13)
        assertEquals(12, entry.state.wordCount)
    }

    @Test
    fun changingToAnotherLengthStartsOverAndToTheSameLengthKeepsTheWords() {
        entry.start(12)
        entry.wordChanged(0, "abandon")
        entry.changeWordCount(12)
        assertEquals("abandon", entry.word(0))
        entry.changeWordCount(24)
        assertEquals(24, entry.state.wordCount)
        assertEquals("", entry.word(0))
        entry.changeWordCount(7)
        assertEquals(24, entry.state.wordCount)
    }

    @Test
    fun aWordKeepsOnlyLowercaseLettersUpToEight() {
        assertEquals("abandon", normalizePhraseWord("  AbAn1don "))
        assertEquals("zoologic", normalizePhraseWord("zoologically"))
        entry.start(12)
        entry.wordChanged(3, "Wo-rd9")
        assertEquals("word", entry.word(3))
    }

    @Test
    fun suggestionsFollowTheEditedAndTheFocusedWordAndThePrefixCopyIsZeroed() {
        entry.start(12)
        entry.wordChanged(0, "ab")
        assertEquals(listOf("aba", "abb"), entry.state.suggestions)
        assertEquals(0, entry.state.focusedWord)
        entry.focus(5)
        assertEquals(5, entry.state.focusedWord)
        assertTrue(entry.state.suggestions.isEmpty())
        entry.focus(0)
        assertEquals(listOf("aba", "abb"), entry.state.suggestions)
        assertTrue(prefixes.all { prefix -> prefix.all { it == '\u0000' } })
    }

    @Test
    fun choosingASuggestionFillsTheWordAndMovesToTheNextOneButNotPastTheLast() {
        entry.start(12)
        entry.chooseSuggestion(0, "abandon")
        assertEquals("abandon", entry.word(0))
        assertEquals(1, entry.state.focusedWord)
        entry.chooseSuggestion(11, "about")
        assertEquals("about", entry.word(11))
        assertEquals(11, entry.state.focusedWord)
    }

    @Test
    fun validationNeedsEveryWordThenAsksTheValidatorWithCopiesItZeroesAfter() {
        entry.start(12)
        assertFalse(entry.validate())
        assertTrue(validated.isEmpty())
        fill(12)
        assertTrue(entry.complete)
        assertTrue(entry.validate())
        assertFalse(entry.state.checksumFailed)
        assertTrue(validated.single().all { word -> word.all { it == '\u0000' } })
        assertEquals("wordx", entry.word(0))
    }

    @Test
    fun aFailedChecksumIsShownUntilAWordChanges() {
        fill(12)
        valid = false
        assertFalse(entry.validate())
        assertTrue(entry.state.checksumFailed)
        entry.wordChanged(11, "about")
        assertFalse(entry.state.checksumFailed)
    }

    @Test
    fun aScannedPhraseFillsTheWordsWhateverItsCaseAndSpacing() {
        entry.start(12)
        val phrase = "  Abandon\tabandon  abandon abandon\nabandon abandon abandon abandon abandon abandon abandon ABOUT  "
        assertTrue(entry.acceptScanned(phrase))
        assertTrue(entry.complete)
        assertEquals("abandon", entry.word(0))
        assertEquals("about", entry.word(11))
        assertEquals(12, entry.state.wordCount)
        assertFalse(entry.state.scanRefused)
        assertEquals(PHRASE_NO_FOCUS, entry.state.focusedWord)
        assertTrue(entry.state.suggestions.isEmpty())
    }

    @Test
    fun aScannedTwentyFourWordPhraseSwitchesTheLength() {
        entry.start(12)
        assertTrue(entry.acceptScanned(List(24) { "zoo" }.joinToString(" ")))
        assertEquals(24, entry.state.wordCount)
        assertEquals("zoo", entry.word(23))
    }

    @Test
    fun aScanThatIsNotAPhraseIsRefusedAndKeepsWhatWasTyped() {
        entry.start(12)
        entry.wordChanged(0, "abandon")
        listOf(
            "",
            "https://zekke.io",
            List(11) { "abandon" }.joinToString(" "),
            List(12) { "abandon" }.joinToString(" ").replaceFirst("abandon", "aband0n"),
            List(12) { "abandon" }.joinToString(" ").replaceFirst("abandon", "zekke:abandon"),
            List(12) { "abandonxxx" }.joinToString(" "),
        ).forEach { text ->
            assertFalse(text, entry.acceptScanned(text))
            assertTrue(entry.state.scanRefused)
            assertEquals("abandon", entry.word(0))
        }
        entry.wordChanged(1, "ability")
        assertFalse(entry.state.scanRefused)
    }

    @Test
    fun forgettingEmptiesTheEntry() {
        fill(12)
        entry.forget()
        assertFalse(entry.active)
        assertEquals("", entry.word(0))
        assertTrue(entry.copyWords().isEmpty())
        entry.wordChanged(0, "abandon")
        assertEquals("", entry.word(0))
    }

    @Test
    fun copiesAreIndependentOfTheEntry() {
        entry.start(12)
        entry.wordChanged(0, "abandon")
        val copy = entry.copyWords()
        copy.forEach { it.fill('\u0000') }
        assertEquals("abandon", entry.word(0))
    }
}
