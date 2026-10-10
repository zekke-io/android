package zekke.android.feature.onboarding

data class CreatedAccount(val username: String?, val paranoid: Boolean, val paranoidFailed: Boolean)

sealed interface CreationFailure {
    data object Offline : CreationFailure

    data class RateLimited(val retryAfterSeconds: Long?) : CreationFailure

    data object Failed : CreationFailure
}

class AccountCreationException(val failure: CreationFailure) : Exception()

interface OnboardingServices {
    fun newPhrase(wordCount: Int): List<CharArray>

    suspend fun createAccount(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount

    fun onLocked(listener: () -> Unit): () -> Unit

    fun forgetDraft()
}
