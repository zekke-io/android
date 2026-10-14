package zekke.android.feature.unlock

sealed interface RemovalOutcome {
    data object Removed : RemovalOutcome
    data object AlreadyRemoved : RemovalOutcome
    data object Offline : RemovalOutcome
    data class RateLimited(val retryAfterSeconds: Long?) : RemovalOutcome
    data object Refused : RemovalOutcome
    data object ChainProblem : RemovalOutcome
    data object PhoneLocked : RemovalOutcome
    data object Failed : RemovalOutcome
}

interface ThisPhoneServices {
    suspend fun removeThisPhone(): RemovalOutcome
}
