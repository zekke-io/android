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

const val MAX_USERNAME_LENGTH = 64
const val MAX_NICKNAME_LENGTH = 64

sealed interface InviteProblem {
    data object Unknown : InviteProblem

    data object Exists : InviteProblem

    data class Refused(val alarm: TrustAlarm?) : InviteProblem
}

data class Review(
    val connectionId: String,
    val username: String,
    val loading: Boolean = true,
    val theirs: String? = null,
    val mine: String? = null,
    val alarm: TrustAlarm? = null,
    val accepting: Boolean = false,
) {
    val canAccept: Boolean get() = !loading && !accepting && theirs != null && alarm == null
}

data class Renaming(val connectionId: String, val draft: String)

data class ConnectionsState(
    val loaded: Boolean = false,
    val items: List<ConnectionItem> = emptyList(),
    val alarms: Map<String, TrustAlarm?> = emptyMap(),
    val failure: ItemFailure? = null,
    val username: String = "",
    val inviting: Boolean = false,
    val invited: String? = null,
    val inviteProblem: InviteProblem? = null,
    val review: Review? = null,
    val detail: String? = null,
    val renaming: Renaming? = null,
    val confirmingDisconnect: ConnectionItem? = null,
    val busy: Boolean = false,
    val canDisconnect: Boolean = false,
) {
    val groups: ConnectionGroups get() = groupConnections(items)

    val canInvite: Boolean get() = username.trim().isNotEmpty() && !inviting
}

class ConnectionsViewModel(private val services: SharingServices) : ViewModel() {
    private val mutableState = MutableStateFlow(ConnectionsState(canDisconnect = services.canDisconnect))

    val state: StateFlow<ConnectionsState> = mutableState.asStateFlow()

    private var refreshes = 0

    fun opened() {
        mutableState.update { it.copy(invited = null, inviteProblem = null) }
        refresh()
    }

    fun refresh() {
        val attempt = ++refreshes
        viewModelScope.launch {
            val result = guarded({ ConnectionsLoad.Failed(it) }) { services.loadConnections() }
            if (attempt != refreshes) return@launch
            when (result) {
                is ConnectionsLoad.Failed -> mutableState.update { it.copy(failure = result.failure, loaded = true) }
                is ConnectionsLoad.Loaded -> {
                    mutableState.update { it.copy(items = result.items, loaded = true, failure = null) }
                    checkTrust(attempt, result.items)
                    catchUp(result.items)
                }
            }
        }
    }

    private fun checkTrust(attempt: Int, items: List<ConnectionItem>) {
        val ids = items.filter { it.shouldBeChecked }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val alarms = try {
                services.checkTrust(ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            if (attempt == refreshes) mutableState.update { it.copy(alarms = alarms) }
        }
    }

    private fun catchUp(items: List<ConnectionItem>) {
        val ids = items.filter { it.standing == ConnectionStanding.Accepted || it.standing == ConnectionStanding.WaitingForThem }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                services.catchUp(ids)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun dismissInvited() = mutableState.update { it.copy(invited = null) }

    fun dismissInviteProblem() = mutableState.update { it.copy(inviteProblem = null) }

    fun usernameChanged(value: String) = mutableState.update {
        if (it.inviting) it else it.copy(username = value.take(MAX_USERNAME_LENGTH), inviteProblem = null)
    }

    fun invite() {
        val current = mutableState.value
        if (!current.canInvite) return
        mutableState.update { it.copy(inviting = true, invited = null, inviteProblem = null, failure = null) }
        viewModelScope.launch {
            val outcome = guarded({ InviteOutcome.Failed(it) }) { services.invite(current.username.trim()) }
            when (outcome) {
                is InviteOutcome.Sent -> {
                    mutableState.update { it.copy(inviting = false, username = "", invited = outcome.username) }
                    refresh()
                }
                InviteOutcome.UnknownUsername -> mutableState.update { it.copy(inviting = false, inviteProblem = InviteProblem.Unknown) }
                InviteOutcome.AlreadyConnected -> mutableState.update { it.copy(inviting = false, inviteProblem = InviteProblem.Exists) }
                is InviteOutcome.NotTrusted -> mutableState.update { it.copy(inviting = false, inviteProblem = InviteProblem.Refused(outcome.alarm)) }
                is InviteOutcome.Failed -> mutableState.update { it.copy(inviting = false, failure = outcome.failure) }
            }
        }
    }

    fun startReview(item: ConnectionItem) {
        if (item.standing != ConnectionStanding.WaitingForMe) return
        mutableState.update { it.copy(review = Review(item.id, item.username)) }
        viewModelScope.launch {
            val load = guarded({ ReviewLoad.Failed(it) }) { services.review(item.id) }
            mutableState.update { state ->
                val review = state.review?.takeIf { it.connectionId == item.id } ?: return@update state
                when (load) {
                    is ReviewLoad.Ready -> state.copy(review = review.copy(loading = false, theirs = load.theirs, mine = load.mine, alarm = load.alarm))
                    is ReviewLoad.Failed -> state.copy(review = null, failure = load.failure)
                }
            }
        }
    }

    fun closeReview() = mutableState.update { if (it.review?.accepting == true) it else it.copy(review = null) }

    fun accept() {
        val review = mutableState.value.review ?: return
        if (!review.canAccept) return
        mutableState.update { it.copy(review = review.copy(accepting = true)) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.accept(review.connectionId) }
            when (result) {
                SharingWrite.Done -> {
                    mutableState.update { it.copy(review = null) }
                    refresh()
                }
                is SharingWrite.Failed -> mutableState.update { it.copy(review = it.review?.copy(accepting = false), failure = result.failure) }
            }
        }
    }

    fun decline() {
        val review = mutableState.value.review ?: return
        if (review.accepting) return
        mutableState.update { it.copy(review = review.copy(accepting = true)) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.disconnect(review.connectionId) }
            when (result) {
                SharingWrite.Done -> {
                    mutableState.update { it.copy(review = null) }
                    refresh()
                }
                is SharingWrite.Failed -> mutableState.update { it.copy(review = it.review?.copy(accepting = false), failure = result.failure) }
            }
        }
    }

    fun open(item: ConnectionItem) {
        if (item.standing == ConnectionStanding.WaitingForMe) return
        mutableState.update { it.copy(detail = item.id, renaming = Renaming(item.id, item.nickname.orEmpty())) }
    }

    fun closeDetail() = mutableState.update { if (it.busy) it else it.copy(detail = null, renaming = null) }

    fun nicknameChanged(value: String) = mutableState.update { state ->
        state.renaming?.let { state.copy(renaming = it.copy(draft = value.take(MAX_NICKNAME_LENGTH))) } ?: state
    }

    fun saveNickname() {
        val renaming = mutableState.value.renaming ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.rename(renaming.connectionId, renaming.draft.trim()) }
            when (result) {
                SharingWrite.Done -> {
                    mutableState.update { it.copy(busy = false, detail = null, renaming = null) }
                    refresh()
                }
                is SharingWrite.Failed -> mutableState.update { it.copy(busy = false, failure = result.failure) }
            }
        }
    }

    fun askDisconnect(item: ConnectionItem) {
        if (!mutableState.value.canDisconnect) return
        mutableState.update { it.copy(confirmingDisconnect = item, detail = null, renaming = null) }
    }

    fun cancelDisconnect() = mutableState.update { if (it.busy) it else it.copy(confirmingDisconnect = null) }

    fun confirmDisconnect() {
        val item = mutableState.value.confirmingDisconnect ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = guarded({ SharingWrite.Failed(it) }) { services.disconnect(item.id) }
            mutableState.update { state ->
                when (result) {
                    SharingWrite.Done -> state.copy(busy = false, confirmingDisconnect = null)
                    is SharingWrite.Failed -> state.copy(busy = false, confirmingDisconnect = null, failure = result.failure)
                }
            }
            if (result == SharingWrite.Done) refresh()
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
