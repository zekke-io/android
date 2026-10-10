package zekke.android.feature.onboarding

import zekke.core.recoverykit.RecoveryKitContent

data class CreatedAccount(val username: String?, val paranoid: Boolean, val paranoidFailed: Boolean, val createdAtEpochMillis: Long)

sealed interface CreationFailure {
    data object Offline : CreationFailure

    data class RateLimited(val retryAfterSeconds: Long?) : CreationFailure

    data object Failed : CreationFailure
}

class AccountCreationException(val failure: CreationFailure) : Exception()

interface OnboardingServices {
    fun newPhrase(wordCount: Int): List<CharArray>

    suspend fun createAccount(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount

    suspend fun username(): String?

    fun kitContent(username: String, words: List<CharArray>, createdAtEpochMillis: Long): RecoveryKitContent

    fun onLocked(listener: () -> Unit): () -> Unit

    fun holdProcess(): () -> Unit

    fun systemScreenWillOpen()

    fun systemScreenReturned()

    fun kitWasLost(): Boolean

    fun kitSettled()

    fun forgetDraft()
}
