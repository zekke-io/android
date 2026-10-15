package zekke.android.feature.settings

import zekke.android.ui.components.ItemFailure
import zekke.core.scopes.Scope

data class DeviceItem(
    val id: String,
    val name: String?,
    val thisDevice: Boolean,
    val scopes: List<Scope>,
    val addedAt: String,
) {
    val full: Boolean get() = Scope.ADMIN in scopes

    val everything: Boolean get() = Scope.ADMIN in scopes && Scope.entries.filter { it.isKeyring }.all { it in scopes }
}

fun orderedDevices(devices: List<DeviceItem>): List<DeviceItem> =
    devices.sortedWith(compareByDescending<DeviceItem> { it.thisDevice }.thenBy { it.addedAt })

sealed interface DevicesLoad {
    data class Loaded(val devices: List<DeviceItem>, val chainVerified: Boolean) : DevicesLoad

    data class Failed(val failure: ItemFailure) : DevicesLoad
}

sealed interface DevicesWrite {
    data object Done : DevicesWrite

    data class Failed(val failure: ItemFailure) : DevicesWrite
}

sealed interface DeviceRemoval {
    data object Removed : DeviceRemoval

    data object PhraseMismatch : DeviceRemoval

    data object Offline : DeviceRemoval

    data class RateLimited(val retryAfterSeconds: Long?) : DeviceRemoval

    data object PhoneLocked : DeviceRemoval

    data object ChainProblem : DeviceRemoval

    data object Failed : DeviceRemoval
}

interface DevicesServices {
    val fullDevice: Boolean

    val canName: Boolean

    fun phraseSuggestions(prefix: CharArray): List<String>

    fun isValidPhrase(words: List<CharArray>): Boolean

    fun systemScreenWillOpen()

    fun systemScreenReturned()

    suspend fun load(): DevicesLoad

    suspend fun rename(id: String, name: String): DevicesWrite

    suspend fun remove(id: String, words: List<CharArray>): DeviceRemoval
}
