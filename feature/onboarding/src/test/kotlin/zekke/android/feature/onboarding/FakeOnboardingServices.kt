package zekke.android.feature.onboarding

import kotlinx.coroutines.CompletableDeferred

class FakeOnboardingServices : OnboardingServices {
    val issuedPhrases = mutableListOf<List<CharArray>>()
    val receivedPhrases = mutableListOf<List<String>>()
    val receivedPins = mutableListOf<String>()
    val receivedModes = mutableListOf<Boolean>()
    var outcome: CompletableDeferred<CreatedAccount> = CompletableDeferred()
    var forgotten = 0
    private val listeners = mutableListOf<() -> Unit>()

    override fun newPhrase(wordCount: Int): List<CharArray> =
        List(wordCount) { "word$it".toCharArray() }.also { issuedPhrases += it }

    override suspend fun createAccount(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount {
        receivedPhrases += words.map { String(it) }
        receivedPins += String(pin)
        receivedModes += paranoid
        return outcome.await()
    }

    override fun onLocked(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    override fun forgetDraft() {
        forgotten++
    }

    fun lock() = listeners.toList().forEach { it() }

    val listenerCount: Int get() = listeners.size
}
