package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.feature.settings.DevicePinChange
import zekke.android.feature.settings.SecurityServices
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.session.IdleTimeoutStore
import zekke.android.session.SessionGuard
import zekke.android.session.idleDurationOf
import zekke.core.account.AccountServices
import zekke.core.account.changeDevicePin
import zekke.core.api.ApiError
import zekke.core.api.NetworkError

fun devicePinOutcomeOf(error: Exception): DevicePinChange = when (error) {
    is NetworkError -> DevicePinChange.Offline
    is ApiError -> if (error.isRateLimited) DevicePinChange.RateLimited(error.retryAfterSeconds) else DevicePinChange.Failed
    is DeviceVaultUnavailableException -> DevicePinChange.PhoneLocked
    else -> DevicePinChange.Failed
}

class CoreSecurityServices(
    private val accounts: AccountServices,
    private val idle: IdleTimeoutStore,
    private val guard: SessionGuard,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : SecurityServices {
    override val idleMinutes: Int get() = idle.minutes()

    override fun setIdleMinutes(minutes: Int) {
        idle.save(minutes)
        accounts.session.changeIdleTimeout(idleDurationOf(minutes))
    }

    override suspend fun changeDevicePin(pin: CharArray): DevicePinChange = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            withContext(work) {
                try {
                    changeDevicePin(accounts, pin)
                    DevicePinChange.Done
                } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    DevicePinChange.Failed
                } catch (error: Exception) {
                    devicePinOutcomeOf(error)
                } finally {
                    pin.fill('\u0000')
                }
            }
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }
}
