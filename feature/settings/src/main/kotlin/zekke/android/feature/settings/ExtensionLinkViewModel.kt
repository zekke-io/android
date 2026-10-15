package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure

const val PAIRING_POLL_MILLIS = 2000L
const val MAX_EXTENSION_NAME_LENGTH = 64

enum class LinkEnd { Expired, Cancelled }

sealed interface LinkStep {
    data object Idle : LinkStep

    data object Opening : LinkStep

    data class Waiting(val id: String, val code: String, val secondsLeft: Long) : LinkStep

    data class Comparing(val id: String, val fingerprint: String, val secondsLeft: Long) : LinkStep

    data object Linking : LinkStep

    data object Linked : LinkStep

    data object Refused : LinkStep

    data class Ended(val reason: LinkEnd) : LinkStep
}

data class ExtensionLinkState(
    val step: LinkStep = LinkStep.Idle,
    val failure: ItemFailure? = null,
    val name: String = "",
    val canLink: Boolean = false,
    val canName: Boolean = false,
)

class ExtensionLinkViewModel(
    private val services: ExtensionLinkServices,
    private val pollMillis: Long = PAIRING_POLL_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExtensionLinkState(canLink = services.canLink, canName = services.canNameExtension))

    val state: StateFlow<ExtensionLinkState> = mutableState.asStateFlow()

    private var watching: Job? = null
    private var expiresAt = 0L

    fun dismiss() = mutableState.update { it.copy(step = LinkStep.Idle, failure = null) }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun nameChanged(value: String) = mutableState.update { it.copy(name = value.take(MAX_EXTENSION_NAME_LENGTH)) }

    fun start() {
        val current = mutableState.value
        if (!current.canLink) return
        if (current.step !is LinkStep.Idle && current.step !is LinkStep.Linked && current.step !is LinkStep.Refused && current.step !is LinkStep.Ended) return
        mutableState.update { it.copy(step = LinkStep.Opening, failure = null, name = "") }
        viewModelScope.launch {
            val opened = guarded({ PairingOpen.Failed(it) }) { services.open() }
            when (opened) {
                is PairingOpen.Failed -> mutableState.update { it.copy(step = LinkStep.Idle, failure = opened.failure) }
                is PairingOpen.Opened -> {
                    expiresAt = opened.expiresAtMillis
                    mutableState.update { it.copy(step = LinkStep.Waiting(opened.id, opened.code, secondsLeft())) }
                    watch(opened.id)
                }
            }
        }
    }

    private fun secondsLeft(): Long = ((expiresAt - nowMillis() + 999) / 1000).coerceAtLeast(0)

    private fun watch(id: String) {
        watching?.cancel()
        watching = viewModelScope.launch {
            while (true) {
                delay(pollMillis)
                val left = secondsLeft()
                val step = mutableState.value.step
                if (left <= 0) {
                    mutableState.update { it.copy(step = LinkStep.Ended(LinkEnd.Expired)) }
                    return@launch
                }
                when (step) {
                    is LinkStep.Waiting -> {
                        when (val polled = guarded({ PairingPoll.Failed(it) }) { services.poll(id) }) {
                            PairingPoll.Waiting -> mutableState.update { state -> (state.step as? LinkStep.Waiting)?.let { state.copy(step = it.copy(secondsLeft = left)) } ?: state }
                            is PairingPoll.Claimed -> mutableState.update { it.copy(step = LinkStep.Comparing(id, polled.fingerprint, left)) }
                            PairingPoll.Expired -> {
                                mutableState.update { it.copy(step = LinkStep.Ended(LinkEnd.Expired)) }
                                return@launch
                            }
                            PairingPoll.Cancelled -> {
                                mutableState.update { it.copy(step = LinkStep.Ended(LinkEnd.Cancelled)) }
                                return@launch
                            }
                            PairingPoll.Linked -> {
                                mutableState.update { it.copy(step = LinkStep.Linked) }
                                return@launch
                            }
                            is PairingPoll.Failed -> mutableState.update { it.copy(failure = polled.failure) }
                        }
                    }
                    is LinkStep.Comparing -> mutableState.update { state -> (state.step as? LinkStep.Comparing)?.let { state.copy(step = it.copy(secondsLeft = left)) } ?: state }
                    else -> return@launch
                }
            }
        }
    }

    fun cancel() {
        val step = mutableState.value.step as? LinkStep.Waiting ?: return
        watching?.cancel()
        mutableState.update { it.copy(step = LinkStep.Ended(LinkEnd.Cancelled)) }
        viewModelScope.launch { guarded({ PairingWrite.Failed(it) }) { services.cancel(step.id) } }
    }

    fun confirm() {
        val step = mutableState.value.step as? LinkStep.Comparing ?: return
        watching?.cancel()
        mutableState.update { it.copy(step = LinkStep.Linking) }
        viewModelScope.launch {
            val result = guarded({ PairingWrite.Failed(it) }) { services.link(step.id, mutableState.value.name.trim()) }
            when (result) {
                PairingWrite.Done -> mutableState.update { it.copy(step = LinkStep.Linked, name = "") }
                is PairingWrite.Failed -> mutableState.update { it.copy(step = LinkStep.Idle, failure = result.failure) }
            }
        }
    }

    fun refuse() {
        val step = mutableState.value.step as? LinkStep.Comparing ?: return
        watching?.cancel()
        mutableState.update { it.copy(step = LinkStep.Refused, name = "") }
        viewModelScope.launch { guarded({ PairingWrite.Failed(it) }) { services.cancel(step.id) } }
    }

    private suspend fun <T> guarded(failed: (ItemFailure) -> T, call: suspend () -> T): T = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(ItemFailure.Failed)
    }
}
