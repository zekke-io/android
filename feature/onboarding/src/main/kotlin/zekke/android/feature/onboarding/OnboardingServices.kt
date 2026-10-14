package zekke.android.feature.onboarding

import zekke.core.recoverykit.RecoveryKitContent

data class CreatedAccount(val username: String?, val paranoid: Boolean, val paranoidFailed: Boolean, val createdAtEpochMillis: Long)

sealed interface CreationFailure {
    data object Offline : CreationFailure

    data class RateLimited(val retryAfterSeconds: Long?) : CreationFailure

    data object Failed : CreationFailure
}

class AccountCreationException(val failure: CreationFailure) : Exception()

data class EnrolmentDevice(val id: String, val scopes: String, val createdAt: String)

sealed interface DeviceEnrolmentFailure {
    data object NoAccount : DeviceEnrolmentFailure

    data object Refused : DeviceEnrolmentFailure

    data object Offline : DeviceEnrolmentFailure

    data class RateLimited(val retryAfterSeconds: Long?) : DeviceEnrolmentFailure

    data class TooManyDevices(val devices: List<EnrolmentDevice>) : DeviceEnrolmentFailure

    data object Failed : DeviceEnrolmentFailure
}

class DeviceEnrolmentException(val failure: DeviceEnrolmentFailure) : Exception()

sealed interface DeviceRemoval {
    data object None : DeviceRemoval

    data object All : DeviceRemoval

    data class Listed(val ids: List<String>) : DeviceRemoval
}

interface OnboardingServices {
    fun phraseSuggestions(prefix: CharArray): List<String>

    fun isValidPhrase(words: List<CharArray>): Boolean

    suspend fun enrolDevice(words: List<CharArray>, pin: CharArray, removal: DeviceRemoval)

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
