package zekke.android.feature.settings

import zekke.android.ui.components.ItemFailure
import zekke.core.users.isUsername
import zekke.core.users.normalizeUsername

data class AccountProfile(
    val username: String,
    val paranoid: Boolean,
    val retentionDays: Int,
    val renews: Boolean,
)

sealed interface ProfileLoad {
    data class Loaded(val profile: AccountProfile) : ProfileLoad

    data class Failed(val failure: ItemFailure) : ProfileLoad
}

sealed interface UsernameOutcome {
    data class Changed(val username: String) : UsernameOutcome

    data object Malformed : UsernameOutcome

    data object Unavailable : UsernameOutcome

    data class RateLimited(val retryAfterSeconds: Long?) : UsernameOutcome

    data object Offline : UsernameOutcome

    data object Failed : UsernameOutcome
}

sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    data object PhraseMismatch : DeleteOutcome

    data object PinRefused : DeleteOutcome

    data object Offline : DeleteOutcome

    data class RateLimited(val retryAfterSeconds: Long?) : DeleteOutcome

    data object PhoneLocked : DeleteOutcome

    data object ChainProblem : DeleteOutcome

    data object Failed : DeleteOutcome
}

sealed interface UsernameCheck {
    data class Ok(val username: String) : UsernameCheck

    data object Malformed : UsernameCheck

    data object Unchanged : UsernameCheck
}

fun checkUsername(input: String, current: String?): UsernameCheck {
    val claimed = normalizeUsername(input)
    return when {
        !isUsername(claimed) -> UsernameCheck.Malformed
        claimed == current -> UsernameCheck.Unchanged
        else -> UsernameCheck.Ok(claimed)
    }
}

interface AccountSettingsServices {
    val fullDevice: Boolean

    fun phraseSuggestions(prefix: CharArray): List<String>

    fun isValidPhrase(words: List<CharArray>): Boolean

    fun systemScreenWillOpen()

    fun systemScreenReturned()

    suspend fun profile(): ProfileLoad

    suspend fun changeUsername(username: String): UsernameOutcome

    suspend fun deleteAccount(words: List<CharArray>, accountPin: CharArray?): DeleteOutcome
}
