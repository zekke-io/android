package zekke.android.feature.unlock

import kotlinx.coroutines.CompletableDeferred

class FakeUnlockServices : UnlockServices {
    var nextOutcome: UnlockAttempt = UnlockAttempt.Failed
    var unlockError: Exception? = null
    var pendingUnlock: CompletableDeferred<UnlockAttempt>? = null
    var rewrapIssue: RewrapIssue? = null
    var rewrapError: Exception? = null
    var pendingRewrap: CompletableDeferred<RewrapIssue?>? = null
    var forgetError: Exception? = null
    val receivedPins = mutableListOf<String>()
    val receivedPinArrays = mutableListOf<CharArray>()
    var rewrapCalls = 0
    var forgetCalls = 0

    override suspend fun unlock(pin: CharArray): UnlockAttempt {
        receivedPins += String(pin)
        receivedPinArrays += pin
        unlockError?.let { throw it }
        return pendingUnlock?.await() ?: nextOutcome
    }

    override suspend fun rewrapStaleKeys(): RewrapIssue? {
        rewrapCalls++
        rewrapError?.let { throw it }
        return pendingRewrap?.await() ?: rewrapIssue
    }

    override suspend fun forgetThisPhone() {
        forgetCalls++
        forgetError?.let { throw it }
    }
}
