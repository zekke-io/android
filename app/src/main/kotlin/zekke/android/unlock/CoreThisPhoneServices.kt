package zekke.android.unlock

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.feature.unlock.RemovalOutcome
import zekke.android.feature.unlock.ThisPhoneServices
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.session.SessionGuard
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.DeviceRemovedException
import zekke.core.api.ApiError
import zekke.core.api.NetworkError

class CoreThisPhoneServices(
    private val guard: SessionGuard,
    private val events: ThisPhoneEvents,
    private val removeThisDevice: suspend () -> Unit,
    private val forgetThisDevice: suspend () -> Unit,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ThisPhoneServices {
    override suspend fun removeThisPhone(): RemovalOutcome = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            val outcome = withContext(work) {
                try {
                    removeThisDevice()
                    RemovalOutcome.Removed
                } catch (error: CancellationException) {
                    throw error
                } catch (_: DeviceRemovedException) {
                    forgetAfterRemoval()
                } catch (error: Exception) {
                    mapRemovalFailure(error)
                }
            }
            if (outcome == RemovalOutcome.Removed || outcome == RemovalOutcome.AlreadyRemoved) events.removed()
            outcome
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }

    private suspend fun forgetAfterRemoval(): RemovalOutcome = try {
        forgetThisDevice()
        RemovalOutcome.AlreadyRemoved
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        mapRemovalFailure(error).takeUnless { it == RemovalOutcome.ChainProblem } ?: RemovalOutcome.Failed
    }
}

internal fun mapRemovalFailure(error: Exception): RemovalOutcome = when (error) {
    is NetworkError -> RemovalOutcome.Offline
    is ApiError -> when {
        error.isRateLimited -> RemovalOutcome.RateLimited(error.retryAfterSeconds)
        error.isInvalidBatch -> RemovalOutcome.Refused
        else -> RemovalOutcome.Failed
    }
    is ChainNotVerifiedException -> RemovalOutcome.ChainProblem
    is DeviceVaultUnavailableException -> RemovalOutcome.PhoneLocked
    else -> RemovalOutcome.Failed
}
