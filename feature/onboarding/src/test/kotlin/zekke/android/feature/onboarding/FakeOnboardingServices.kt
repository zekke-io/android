package zekke.android.feature.onboarding

import kotlinx.coroutines.CompletableDeferred
import zekke.core.keys.mnemonicWordSuggestions
import zekke.core.recoverykit.RecoveryKitContent
import zekke.core.recoverykit.RecoveryKitWord

class FakeOnboardingServices(var lostKit: Boolean = false) : OnboardingServices {
    val issuedPhrases = mutableListOf<List<CharArray>>()
    val receivedPhrases = mutableListOf<List<String>>()
    val receivedPins = mutableListOf<String>()
    val receivedModes = mutableListOf<Boolean>()
    var outcome: CompletableDeferred<CreatedAccount> = CompletableDeferred()
    var usernameAnswer: String? = null
    var forgotten = 0
    var holds = 0
    var systemScreens = 0
    val enrolmentPhrases = mutableListOf<List<String>>()
    val enrolmentPins = mutableListOf<String>()
    val removals = mutableListOf<DeviceRemoval>()
    var settled = 0
    private val listeners = mutableListOf<() -> Unit>()

    override fun phraseSuggestions(prefix: CharArray): List<String> = mnemonicWordSuggestions(prefix)

    var validPhrases: Set<List<String>> = setOf(VALID_PHRASE)

    override fun isValidPhrase(words: List<CharArray>): Boolean = words.map { String(it) } in validPhrases

    var enrolment: CompletableDeferred<Unit> = CompletableDeferred()
    val enrolmentWordArrays = mutableListOf<List<CharArray>>()

    override suspend fun enrolDevice(words: List<CharArray>, pin: CharArray, removal: DeviceRemoval) {
        enrolmentPhrases += words.map { String(it) }
        enrolmentWordArrays += words
        enrolmentPins += String(pin)
        removals += removal
        enrolment.await()
    }

    override fun newPhrase(wordCount: Int): List<CharArray> =
        List(wordCount) { "word$it".toCharArray() }.also { issuedPhrases += it }

    override suspend fun createAccount(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount {
        receivedPhrases += words.map { String(it) }
        receivedPins += String(pin)
        receivedModes += paranoid
        return outcome.await()
    }

    override suspend fun username(): String? = usernameAnswer

    override fun kitContent(username: String, words: List<CharArray>, createdAtEpochMillis: Long): RecoveryKitContent {
        val phrase = words.joinToString(" ") { String(it) }
        return RecoveryKitContent(
            "Zekke", "Recovery Kit", "", username, createdAtEpochMillis.toString(),
            phrase.split(' ').mapIndexed { index, word -> RecoveryKitWord(index + 1, word) }, phrase, "", "",
        )
    }

    override fun onLocked(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    override fun holdProcess(): () -> Unit {
        holds++
        var released = false
        return {
            if (!released) {
                released = true
                holds--
            }
        }
    }

    override fun systemScreenWillOpen() {
        systemScreens++
    }

    override fun systemScreenReturned() {
        systemScreens--
    }

    override fun kitWasLost(): Boolean = lostKit

    override fun kitSettled() {
        settled++
        lostKit = false
    }

    override fun forgetDraft() {
        forgotten++
    }

    fun lock() = listeners.toList().forEach { it() }
}
