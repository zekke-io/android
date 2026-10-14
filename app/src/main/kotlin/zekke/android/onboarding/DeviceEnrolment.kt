package zekke.android.onboarding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.android.feature.onboarding.DeviceEnrolmentException
import zekke.android.feature.onboarding.DeviceEnrolmentFailure
import zekke.android.feature.onboarding.DeviceRemoval
import zekke.android.feature.onboarding.EnrolmentDevice
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.SessionGuard
import zekke.core.account.DevicesToRemove
import zekke.core.account.EnrolmentResult
import zekke.core.account.NoAccountForPhraseException
import zekke.core.account.ParanoidPinRequiredException
import zekke.core.account.TooManyDevicesException
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.auth.AuthRejectedException

class DeviceEnrolment(
    private val guard: SessionGuard,
    private val rotations: KeyRotationEvents,
    private val enrol: suspend (List<CharArray>, CharArray, DevicesToRemove) -> EnrolmentResult,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun enrolDevice(words: List<CharArray>, pin: CharArray, removal: DeviceRemoval) {
        try {
            val result = withContext(work) { enrol(words, pin, removal.toCore()) }
            if (result.removed.isNotEmpty()) rotations.rotated()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TooManyDevicesException) {
            throw DeviceEnrolmentException(
                DeviceEnrolmentFailure.TooManyDevices(error.devices.map { EnrolmentDevice(it.id, it.scopes, it.createdAt) }),
            )
        } catch (error: AuthRejectedException) {
            throw DeviceEnrolmentException(DeviceEnrolmentFailure.Refused)
        } catch (error: NoAccountForPhraseException) {
            throw DeviceEnrolmentException(DeviceEnrolmentFailure.NoAccount)
        } catch (error: ParanoidPinRequiredException) {
            throw DeviceEnrolmentException(DeviceEnrolmentFailure.Refused)
        } catch (error: NetworkError) {
            throw DeviceEnrolmentException(DeviceEnrolmentFailure.Offline)
        } catch (error: ApiError) {
            throw DeviceEnrolmentException(
                if (error.isRateLimited) DeviceEnrolmentFailure.RateLimited(error.retryAfterSeconds) else DeviceEnrolmentFailure.Failed,
            )
        } catch (error: Exception) {
            throw DeviceEnrolmentException(DeviceEnrolmentFailure.Failed)
        } finally {
            words.forEach { it.fill('\u0000') }
            pin.fill('\u0000')
            if (!guard.isInForeground) guard.lockNow()
        }
    }
}

private fun DeviceRemoval.toCore(): DevicesToRemove = when (this) {
    DeviceRemoval.None -> DevicesToRemove.None
    DeviceRemoval.All -> DevicesToRemove.All
    is DeviceRemoval.Listed -> DevicesToRemove.Listed(ids)
}
