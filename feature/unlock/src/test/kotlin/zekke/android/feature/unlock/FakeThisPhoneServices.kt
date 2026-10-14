package zekke.android.feature.unlock

import kotlinx.coroutines.CompletableDeferred

class FakeThisPhoneServices : ThisPhoneServices {
    var nextOutcome: RemovalOutcome = RemovalOutcome.Removed
    var error: Exception? = null
    var pending: CompletableDeferred<RemovalOutcome>? = null
    var calls = 0

    override suspend fun removeThisPhone(): RemovalOutcome {
        calls++
        error?.let { throw it }
        return pending?.await() ?: nextOutcome
    }
}
