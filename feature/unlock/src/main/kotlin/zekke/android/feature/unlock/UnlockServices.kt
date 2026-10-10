package zekke.android.feature.unlock

import zekke.core.account.UnlockOutcome as AccountUnlockOutcome

sealed interface RewrapIssue {
    data object Offline : RewrapIssue
    data class RateLimited(val retryAfterSeconds: Long?) : RewrapIssue
    data object Failed : RewrapIssue
}

sealed interface UnlockAttempt {
    data class Opened(val chainProblem: Boolean, val rewrapIssue: RewrapIssue? = null) : UnlockAttempt
    data class WrongPin(val attemptsRemaining: Int) : UnlockAttempt
    data object Forgotten : UnlockAttempt
    data object Removed : UnlockAttempt
    data object Offline : UnlockAttempt
    data class RateLimited(val retryAfterSeconds: Long?) : UnlockAttempt
    data object PhoneLocked : UnlockAttempt
    data object Interrupted : UnlockAttempt
    data object Failed : UnlockAttempt
}

interface UnlockServices {
    suspend fun unlock(pin: CharArray): UnlockAttempt

    suspend fun forgetThisPhone()
}

fun mapUnlockOutcome(outcome: AccountUnlockOutcome): UnlockAttempt = when (outcome) {
    is AccountUnlockOutcome.Unlocked -> UnlockAttempt.Opened(outcome.chainProblem != null)
    AccountUnlockOutcome.NoDevice -> UnlockAttempt.Forgotten
    is AccountUnlockOutcome.WrongPin -> UnlockAttempt.WrongPin(outcome.attemptsRemaining)
    AccountUnlockOutcome.Forgotten -> UnlockAttempt.Forgotten
    AccountUnlockOutcome.Removed -> UnlockAttempt.Removed
    AccountUnlockOutcome.Offline -> UnlockAttempt.Offline
    is AccountUnlockOutcome.RateLimited -> UnlockAttempt.RateLimited(outcome.retryAfterSeconds)
}
