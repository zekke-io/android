package zekke.android.feature.settings

import zekke.android.ui.components.ItemFailure

sealed interface PairingOpen {
    data class Opened(val id: String, val code: String, val expiresAtMillis: Long) : PairingOpen

    data class Failed(val failure: ItemFailure) : PairingOpen
}

sealed interface PairingPoll {
    data object Waiting : PairingPoll

    data class Claimed(val fingerprint: String) : PairingPoll

    data object Linked : PairingPoll

    data object Expired : PairingPoll

    data object Cancelled : PairingPoll

    data class Failed(val failure: ItemFailure) : PairingPoll
}

sealed interface PairingWrite {
    data object Done : PairingWrite

    data class Failed(val failure: ItemFailure) : PairingWrite
}

interface ExtensionLinkServices {
    val canLink: Boolean

    val canNameExtension: Boolean

    suspend fun open(): PairingOpen

    suspend fun poll(id: String): PairingPoll

    suspend fun link(id: String, name: String): PairingWrite

    suspend fun cancel(id: String): PairingWrite
}
