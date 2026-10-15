package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.components.PhraseEntry

const val MAX_DEVICE_NAME_LENGTH = 64

sealed interface DevicesNotice {
    data object Removed : DevicesNotice
}

data class DevicesState(
    val loaded: Boolean = false,
    val devices: List<DeviceItem> = emptyList(),
    val chainVerified: Boolean? = null,
    val chainDismissed: Boolean = false,
    val failure: ItemFailure? = null,
    val notice: DevicesNotice? = null,
    val acting: String? = null,
    val draft: String = "",
    val busy: Boolean = false,
    val removing: DeviceItem? = null,
    val removalFailure: DeviceRemoval? = null,
    val working: Boolean = false,
    val fullDevice: Boolean = false,
    val canName: Boolean = false,
) {
    fun canRemove(device: DeviceItem): Boolean = fullDevice && !device.thisDevice && chainVerified == true

    val actingDevice: DeviceItem? get() = acting?.let { id -> devices.firstOrNull { it.id == id } }
}

class DevicesViewModel(private val services: DevicesServices) : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesState(fullDevice = services.fullDevice, canName = services.canName))
    private var attempt = 0

    val phrase = PhraseEntry(services::phraseSuggestions, services::isValidPhrase)

    val state: StateFlow<DevicesState> = mutableState.asStateFlow()

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    fun opened() {
        val current = ++attempt
        viewModelScope.launch {
            val result = guarded({ DevicesLoad.Failed(it) }) { services.load() }
            if (current != attempt) return@launch
            when (result) {
                is DevicesLoad.Failed -> mutableState.update { it.copy(loaded = true, failure = result.failure) }
                is DevicesLoad.Loaded -> mutableState.update {
                    it.copy(
                        loaded = true,
                        devices = orderedDevices(result.devices),
                        chainVerified = result.chainVerified,
                        chainDismissed = false,
                        failure = null,
                    )
                }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    fun dismissChain() = mutableState.update { it.copy(chainDismissed = true) }

    fun open(device: DeviceItem) {
        val state = mutableState.value
        if (!state.canName && !state.canRemove(device)) return
        mutableState.update { it.copy(acting = device.id, draft = device.name.orEmpty()) }
    }

    fun draftChanged(value: String) = mutableState.update { if (it.acting == null) it else it.copy(draft = value.take(MAX_DEVICE_NAME_LENGTH)) }

    fun closeActions() = mutableState.update { if (it.busy) it else it.copy(acting = null, draft = "") }

    fun saveName() {
        val state = mutableState.value
        val id = state.acting ?: return
        if (state.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = guarded({ DevicesWrite.Failed(it) }) { services.rename(id, state.draft.trim()) }
            when (result) {
                DevicesWrite.Done -> {
                    mutableState.update { it.copy(busy = false, acting = null, draft = "") }
                    opened()
                }
                is DevicesWrite.Failed -> mutableState.update { it.copy(busy = false, failure = result.failure) }
            }
        }
    }

    fun askRemove(device: DeviceItem) {
        if (!mutableState.value.canRemove(device)) return
        phrase.start()
        mutableState.update { it.copy(removing = device, acting = null, removalFailure = null, notice = null) }
    }

    fun cancelRemove() {
        if (mutableState.value.working) return
        phrase.forget()
        mutableState.update { it.copy(removing = null, removalFailure = null) }
    }

    fun confirmRemove() {
        val state = mutableState.value
        val device = state.removing ?: return
        if (state.working || !phrase.active || !phrase.complete) return
        if (!phrase.validate()) return
        val words = phrase.copyWords()
        mutableState.update { it.copy(working = true, removalFailure = null) }
        viewModelScope.launch {
            try {
                val outcome = try {
                    services.remove(device.id, words)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    DeviceRemoval.Failed
                }
                if (outcome == DeviceRemoval.Removed) {
                    phrase.forget()
                    mutableState.update { it.copy(working = false, removing = null, notice = DevicesNotice.Removed) }
                    opened()
                } else {
                    if (outcome == DeviceRemoval.PhraseMismatch) phrase.start()
                    mutableState.update { it.copy(working = false, removalFailure = outcome) }
                }
            } finally {
                words.forEach { it.fill('\u0000') }
            }
        }
    }

    fun dismissRemovalFailure() = mutableState.update { it.copy(removalFailure = null) }

    override fun onCleared() {
        attempt++
        phrase.forget()
    }

    private suspend fun <T> guarded(failed: (ItemFailure) -> T, call: suspend () -> T): T = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(ItemFailure.Failed)
    }
}
