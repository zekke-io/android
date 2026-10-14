package zekke.android.feature.settings

import kotlinx.coroutines.CompletableDeferred

val VALID_PHRASE: List<String> = List(11) { "abandon" } + "about"

data class SecurityCall(val flow: SecurityFlow, val words: List<String>, val currentPin: String?, val pin: String)

class FakeAccountSecurityServices : AccountSecurityServices {
    var mode: AccountMode? = AccountMode.Standard
    var modeError: Exception? = null
    var modeCalls = 0
    var systemScreens = 0
    var validPhrases: Set<List<String>> = setOf(VALID_PHRASE)
    var outcome: CompletableDeferred<AccountSecurityOutcome> = CompletableDeferred()
    var error: Exception? = null
    val calls = mutableListOf<SecurityCall>()
    val wordArrays = mutableListOf<List<CharArray>>()
    val pinArrays = mutableListOf<CharArray>()

    override fun phraseSuggestions(prefix: CharArray): List<String> = emptyList()

    override fun isValidPhrase(words: List<CharArray>): Boolean = words.map { String(it) } in validPhrases

    override fun systemScreenWillOpen() {
        systemScreens++
    }

    override fun systemScreenReturned() {
        systemScreens--
    }

    override suspend fun accountMode(): AccountMode? {
        modeCalls++
        modeError?.let { throw it }
        return mode
    }

    override suspend fun turnOnParanoid(words: List<CharArray>, pin: CharArray): AccountSecurityOutcome {
        record(SecurityCall(SecurityFlow.TurnOnParanoid, words.map { String(it) }, null, String(pin)), words, listOf(pin))
        return result()
    }

    override suspend fun changeAccountPin(words: List<CharArray>, currentPin: CharArray, newPin: CharArray): AccountSecurityOutcome {
        record(SecurityCall(SecurityFlow.ChangeAccountPin, words.map { String(it) }, String(currentPin), String(newPin)), words, listOf(currentPin, newPin))
        return result()
    }

    private fun record(call: SecurityCall, words: List<CharArray>, pins: List<CharArray>) {
        calls += call
        wordArrays += words
        pinArrays += pins
    }

    private suspend fun result(): AccountSecurityOutcome {
        error?.let { throw it }
        return outcome.await()
    }
}
