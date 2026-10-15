package zekke.android.feature.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure

sealed interface SendNotice {
    data class Sent(val username: String) : SendNotice

    data class SentNotFiled(val username: String) : SendNotice
}

sealed interface SendProblem {
    data class Failure(val failure: ItemFailure) : SendProblem

    data class Refused(val alarm: TrustAlarm?) : SendProblem

    data object NotShared : SendProblem
}

data class ShareState(
    val loaded: Boolean = false,
    val connections: List<ConnectionItem> = emptyList(),
    val recipients: List<Recipient> = emptyList(),
    val chosen: String? = null,
    val choices: List<FolderChoice> = emptyList(),
    val folder: String? = null,
    val busy: Boolean = false,
    val notice: SendNotice? = null,
    val problem: SendProblem? = null,
    val canRemove: Boolean = false,
) {
    val canSend: Boolean get() = chosen != null && !busy
}

class ShareViewModel(
    private val services: SharingServices,
    val type: SharedType,
    val itemId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ShareState(canRemove = services.canDisconnect))

    val state: StateFlow<ShareState> = mutableState.asStateFlow()

    private var attempts = 0

    fun opened() {
        mutableState.value = ShareState(canRemove = services.canDisconnect)
        load()
    }

    private fun load() {
        val attempt = ++attempts
        viewModelScope.launch {
            val result = guarded({ SendTargets.Failed(it) }) { services.sendTargets(type, itemId) }
            if (attempt != attempts) return@launch
            when (result) {
                is SendTargets.Failed -> mutableState.update { it.copy(loaded = true, problem = SendProblem.Failure(result.failure)) }
                is SendTargets.Loaded -> mutableState.update {
                    it.copy(loaded = true, connections = result.connections, recipients = result.recipients)
                }
            }
        }
    }

    fun choose(connectionId: String?) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(chosen = connectionId, choices = emptyList(), folder = null, problem = null) }
        if (connectionId == null || !services.holdsSharing) return
        viewModelScope.launch {
            val choices = try {
                services.folderChoicesOf(connectionId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            mutableState.update { if (it.chosen == connectionId) it.copy(choices = choices) else it }
        }
    }

    fun chooseFolder(folderId: String?) = mutableState.update { if (it.busy) it else it.copy(folder = folderId) }

    fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    fun dismissProblem() = mutableState.update { it.copy(problem = null) }

    fun send() {
        val state = mutableState.value
        val connectionId = state.chosen ?: return
        if (state.busy) return
        val username = state.connections.firstOrNull { it.id == connectionId }?.label.orEmpty()
        mutableState.update { it.copy(busy = true, notice = null, problem = null) }
        viewModelScope.launch {
            val outcome = guarded({ SendOutcome.Failed(it) }) { services.send(connectionId, type, itemId, state.folder) }
            when (outcome) {
                SendOutcome.Sent, SendOutcome.SentNotFiled -> {
                    val notice = if (outcome == SendOutcome.Sent) SendNotice.Sent(username) else SendNotice.SentNotFiled(username)
                    mutableState.update { it.copy(busy = false, chosen = null, choices = emptyList(), folder = null, notice = notice) }
                    load()
                }
                is SendOutcome.NotTrusted -> mutableState.update { it.copy(busy = false, problem = SendProblem.Refused(outcome.alarm)) }
                SendOutcome.NotShared -> mutableState.update { it.copy(busy = false, problem = SendProblem.NotShared) }
                is SendOutcome.Failed -> mutableState.update { it.copy(busy = false, problem = SendProblem.Failure(outcome.failure)) }
            }
        }
    }

    fun remove(recipient: Recipient) {
        if (mutableState.value.busy || !mutableState.value.canRemove) return
        mutableState.update { it.copy(busy = true, notice = null, problem = null) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.unshare(recipient.shareId) }
            when (result) {
                SharingWrite.Done -> {
                    mutableState.update { it.copy(busy = false) }
                    load()
                }
                is SharingWrite.Failed -> mutableState.update { it.copy(busy = false, problem = SendProblem.Failure(result.failure)) }
            }
        }
    }

    private suspend fun <T> guarded(failed: (ItemFailure) -> T, call: suspend () -> T): T = try {
        call()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(ItemFailure.Failed)
    }
}
