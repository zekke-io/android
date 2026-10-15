package zekke.android.feature.settings

val IDLE_CHOICES_MINUTES: List<Int> = listOf(1, 5, 15, 30, 60)

sealed interface DevicePinChange {
    data object Done : DevicePinChange

    data object Offline : DevicePinChange

    data class RateLimited(val retryAfterSeconds: Long?) : DevicePinChange

    data object PhoneLocked : DevicePinChange

    data object Failed : DevicePinChange
}

interface SecurityServices {
    val idleMinutes: Int

    fun setIdleMinutes(minutes: Int)

    suspend fun changeDevicePin(pin: CharArray): DevicePinChange
}
