package zekke.android.feature.settings

import kotlinx.coroutines.CompletableDeferred
import zekke.android.ui.components.ItemFailure
import zekke.core.scopes.Scope

fun device(
    id: String,
    name: String? = null,
    thisDevice: Boolean = false,
    scopes: List<Scope> = Scope.entries.toList(),
    addedAt: String = "2026-10-01T10:00:00Z",
) = DeviceItem(id, name, thisDevice, scopes, addedAt)

const val TWELVE_WORDS = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

class FakeDevicesServices(
    override var fullDevice: Boolean = true,
    override var canName: Boolean = true,
) : DevicesServices {
    var devices: List<DeviceItem> = emptyList()
    var chainVerified = true
    var loadFailure: ItemFailure? = null
    var loads = 0
    var renamed = mutableListOf<Pair<String, String>>()
    var renameResult: DevicesWrite = DevicesWrite.Done
    var removed = mutableListOf<Pair<String, List<String>>>()
    var removeResult: DeviceRemoval = DeviceRemoval.Removed
    var phraseValid = true
    var gate: CompletableDeferred<Unit>? = null
    var pickerOpened = 0
    var pickerReturned = 0

    override fun phraseSuggestions(prefix: CharArray): List<String> = emptyList()

    override fun isValidPhrase(words: List<CharArray>): Boolean = phraseValid

    override fun systemScreenWillOpen() {
        pickerOpened++
    }

    override fun systemScreenReturned() {
        pickerReturned++
    }

    override suspend fun load(): DevicesLoad {
        loads++
        return loadFailure?.let { DevicesLoad.Failed(it) } ?: DevicesLoad.Loaded(devices, chainVerified)
    }

    override suspend fun rename(id: String, name: String): DevicesWrite {
        renamed += id to name
        gate?.await()
        return renameResult
    }

    override suspend fun remove(id: String, words: List<CharArray>): DeviceRemoval {
        removed += id to words.map { String(it) }
        gate?.await()
        return removeResult
    }
}
