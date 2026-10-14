package zekke.android.feature.settings

enum class AccountMode { Standard, Paranoid }

sealed interface AccountSecurityOutcome {
    data class Done(val signedOutDevices: Int) : AccountSecurityOutcome

    data object PhraseMismatch : AccountSecurityOutcome

    data object PinRefused : AccountSecurityOutcome

    data object ParanoidOnUnfinished : AccountSecurityOutcome

    data object Offline : AccountSecurityOutcome

    data class RateLimited(val retryAfterSeconds: Long?) : AccountSecurityOutcome

    data object PhoneLocked : AccountSecurityOutcome

    data object ChainProblem : AccountSecurityOutcome

    data object Failed : AccountSecurityOutcome
}

interface AccountSecurityServices {
    fun phraseSuggestions(prefix: CharArray): List<String>

    fun isValidPhrase(words: List<CharArray>): Boolean

    fun systemScreenWillOpen()

    fun systemScreenReturned()

    suspend fun accountMode(): AccountMode?

    suspend fun turnOnParanoid(words: List<CharArray>, pin: CharArray): AccountSecurityOutcome

    suspend fun changeAccountPin(words: List<CharArray>, currentPin: CharArray, newPin: CharArray): AccountSecurityOutcome
}
