package zekke.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

const val PHRASE_MAX_WORD_LENGTH = 8
val PHRASE_WORD_COUNTS: List<Int> = listOf(12, 24)
const val PHRASE_NO_FOCUS = -1

data class PhraseEntryState(
    val wordCount: Int = PHRASE_WORD_COUNTS.first(),
    val revision: Int = 0,
    val focusedWord: Int = 0,
    val suggestions: List<String> = emptyList(),
    val complete: Boolean = false,
    val checksumFailed: Boolean = false,
    val scanRefused: Boolean = false,
)

class PhraseEntry(
    private val suggestionsFor: (CharArray) -> List<String>,
    private val isValid: (List<CharArray>) -> Boolean,
) {
    private var words: MutableList<CharArray>? = null

    var state by mutableStateOf(PhraseEntryState())
        private set

    val active: Boolean get() = words != null

    val complete: Boolean get() = state.complete

    fun word(index: Int): String = words?.getOrNull(index)?.let { String(it) }.orEmpty()

    fun start(wordCount: Int = state.wordCount) {
        forget()
        val count = wordCount.takeIf { it in PHRASE_WORD_COUNTS } ?: PHRASE_WORD_COUNTS.first()
        words = MutableList(count) { CharArray(0) }
        publish(PhraseEntryState(wordCount = count))
    }

    fun changeWordCount(count: Int) {
        if (!active || count !in PHRASE_WORD_COUNTS || count == state.wordCount) return
        start(count)
    }

    fun wordChanged(index: Int, value: String) {
        val entry = words ?: return
        if (index !in entry.indices) return
        entry[index].fill('\u0000')
        entry[index] = normalizePhraseWord(value).toCharArray()
        publish(state.copy(focusedWord = index, suggestions = suggestionsOf(entry[index]), checksumFailed = false, scanRefused = false))
    }

    fun focus(index: Int) {
        val entry = words ?: return
        if (index !in entry.indices) return
        publish(state.copy(focusedWord = index, suggestions = suggestionsOf(entry[index])))
    }

    fun chooseSuggestion(index: Int, word: String) {
        wordChanged(index, word)
        val next = (index + 1).coerceAtMost((words?.size ?: 1) - 1)
        focus(next)
    }

    fun validate(): Boolean {
        val entry = words ?: return false
        if (!complete) return false
        val copy = entry.map { it.copyOf() }
        val valid = try {
            isValid(copy)
        } finally {
            copy.forEach { it.fill('\u0000') }
        }
        publish(state.copy(checksumFailed = !valid, suggestions = if (valid) emptyList() else state.suggestions))
        return valid
    }

    fun acceptScanned(text: CharSequence): Boolean {
        if (!active) return false
        val parts = text.trim().split(WHITESPACE).map { it.lowercase() }
        val wellFormed = parts.size in PHRASE_WORD_COUNTS && parts.all { part ->
            part.isNotEmpty() && part.length <= PHRASE_MAX_WORD_LENGTH && part.all { it in 'a'..'z' }
        }
        if (!wellFormed) {
            publish(state.copy(scanRefused = true))
            return false
        }
        words?.forEach { it.fill('\u0000') }
        words = parts.mapTo(ArrayList(parts.size)) { it.toCharArray() }
        publish(PhraseEntryState(wordCount = parts.size, focusedWord = PHRASE_NO_FOCUS))
        return true
    }

    fun copyWords(): List<CharArray> = words?.map { it.copyOf() }.orEmpty()

    fun forget() {
        words?.forEach { it.fill('\u0000') }
        words = null
        publish(state.copy(suggestions = emptyList(), checksumFailed = false, scanRefused = false))
    }

    private fun publish(next: PhraseEntryState) {
        state = next.copy(revision = state.revision + 1, complete = words?.all { it.isNotEmpty() } == true)
    }

    private fun suggestionsOf(word: CharArray): List<String> {
        val prefix = word.copyOf()
        return try {
            suggestionsFor(prefix)
        } finally {
            prefix.fill('\u0000')
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

fun normalizePhraseWord(value: String): String =
    value.trim().lowercase().filter { it in 'a'..'z' }.take(PHRASE_MAX_WORD_LENGTH)

class PhraseScan(val systemScreenWillOpen: () -> Unit, val systemScreenReturned: () -> Unit)

@Composable
fun PhraseEntryFields(entry: PhraseEntry, modifier: Modifier = Modifier, scan: PhraseScan? = null) {
    var scanning by remember { mutableStateOf(false) }
    val state = entry.state
    val requesters = remember(state.wordCount) { List(state.wordCount) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.focusedWord, state.wordCount) {
        requesters.getOrNull(state.focusedWord)?.requestFocus()
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SegmentedControl(
            segments = PHRASE_WORD_COUNTS.map { Segment(it, pluralStringResource(R.plurals.phrase_entry_words, it, it)) },
            selected = state.wordCount,
            onSelect = entry::changeWordCount,
            label = stringResource(R.string.phrase_entry_length),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (scan != null) {
            ZekkeButton(stringResource(R.string.phrase_entry_scan), { scanning = true }, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        }
        if (scanning && scan != null) PhraseScanSheet(entry, scan) { scanning = false }
        repeat(state.wordCount) { index ->
            val last = index == state.wordCount - 1
            PrivateField(
                value = entry.word(index),
                onValueChange = { entry.wordChanged(index, it) },
                label = stringResource(R.string.phrase_entry_word, index + 1),
                singleLine = true,
                keyboardType = KeyboardType.Ascii,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                onImeAction = { if (last) focusManager.clearFocus() else requesters[index + 1].requestFocus() },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(requesters[index])
                    .onFocusChanged { if (it.isFocused) entry.focus(index) },
            )
            if (state.focusedWord == index && state.suggestions.isNotEmpty()) {
                Text(stringResource(R.string.phrase_entry_suggestions), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.suggestions.forEach { suggestion ->
                        ZekkeButton(suggestion, { entry.chooseSuggestion(index, suggestion) }, variant = ButtonVariant.Ghost)
                    }
                }
            }
        }
        if (state.checksumFailed) Notice(stringResource(R.string.phrase_entry_checksum), tone = NoticeTone.Danger)
        if (state.scanRefused) Notice(stringResource(R.string.phrase_entry_scan_refused), tone = NoticeTone.Danger)
    }
}
