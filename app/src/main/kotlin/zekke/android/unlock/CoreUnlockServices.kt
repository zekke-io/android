package zekke.android.unlock

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.android.feature.unlock.RewrapIssue
import zekke.android.feature.unlock.UnlockAttempt
import zekke.android.feature.unlock.UnlockServices
import zekke.android.feature.unlock.mapUnlockOutcome
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.session.SessionGuard
import zekke.core.account.AccountServices
import zekke.core.account.UnlockOutcome
import zekke.core.account.forgetThisDevice
import zekke.core.account.unlockWithPin
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.items.ItemContext
import zekke.core.rekey.rewrapStale
import zekke.core.session.SessionState

class CoreUnlockServices(
    private val accountServices: AccountServices,
    private val guard: SessionGuard,
    private val onLeaving: (includeOutbox: Boolean) -> Unit = {},
) : UnlockServices {
    override suspend fun unlock(pin: CharArray): UnlockAttempt {
        val lockVersion = guard.lockVersion
        val release = guard.holdProcess()
        try {
            val outcome = try {
                withContext(Dispatchers.Default) { unlockWithPin(accountServices, pin) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: DeviceVaultUnavailableException) {
                return UnlockAttempt.PhoneLocked
            } catch (_: NetworkError) {
                return UnlockAttempt.Offline
            } catch (error: ApiError) {
                return if (error.isRateLimited) UnlockAttempt.RateLimited(error.retryAfterSeconds) else UnlockAttempt.Failed
            } catch (_: Exception) {
                return UnlockAttempt.Failed
            }

            if (outcome is UnlockOutcome.Unlocked && lockVersion != guard.lockVersion) {
                accountServices.session.lock()
                return UnlockAttempt.Interrupted
            }
            if (outcome is UnlockOutcome.Forgotten || outcome is UnlockOutcome.Removed || outcome is UnlockOutcome.NoDevice) onLeaving(false)
            return mapUnlockOutcome(outcome)
        } finally {
            pin.fill('\u0000')
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }

    override suspend fun rewrapStaleKeys(): RewrapIssue? {
        val issue = try {
            withContext(Dispatchers.Default) {
                rewrapStale(ItemContext(accountServices.api, accountServices.session, accountServices.primitives))
            }
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: NetworkError) {
            RewrapIssue.Offline
        } catch (error: ApiError) {
            if (error.isRateLimited) RewrapIssue.RateLimited(error.retryAfterSeconds) else RewrapIssue.Failed
        } catch (_: Exception) {
            RewrapIssue.Failed
        }
        return if (accountServices.session.state.value == SessionState.UNLOCKED) issue else null
    }

    override suspend fun forgetThisPhone() {
        withContext(Dispatchers.Default) { forgetThisDevice(accountServices) }
        onLeaving(true)
    }
}
