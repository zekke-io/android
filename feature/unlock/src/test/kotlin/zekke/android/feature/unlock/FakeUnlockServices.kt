package zekke.android.feature.unlock

class FakeUnlockServices : UnlockServices {
    var nextOutcome: UnlockAttempt = UnlockAttempt.Failed
    var unlockError: Exception? = null
    var forgetError: Exception? = null
    val receivedPins = mutableListOf<String>()
    var forgetCalls = 0

    override suspend fun unlock(pin: CharArray): UnlockAttempt {
        receivedPins += String(pin)
        unlockError?.let { throw it }
        return nextOutcome
    }

    override suspend fun forgetThisPhone() {
        forgetCalls++
        forgetError?.let { throw it }
    }
}
