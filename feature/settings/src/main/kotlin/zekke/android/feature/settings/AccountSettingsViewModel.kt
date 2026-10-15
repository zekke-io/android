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
import zekke.android.ui.components.PIN_LENGTH
import zekke.android.ui.components.PhraseEntry

const val MAX_USERNAME_CLAIM_LENGTH = 64
const val REFUSALS_BEFORE_WAIT_HINT = 2

sealed interface UsernameProblem {
    data object Malformed : UsernameProblem

    data object Unchanged : UsernameProblem

    data class Refused(val outcome: UsernameOutcome) : UsernameProblem
}

data class AccountSettingsState(
    val loaded: Boolean = false,
    val profile: AccountProfile? = null,
    val failure: ItemFailure? = null,
    val fullDevice: Boolean = false,
    val claim: String = "",
    val renaming: Boolean = false,
    val renamed: String? = null,
    val usernameProblem: UsernameProblem? = null,
    val deleting: Boolean = false,
    val confirmed: Boolean = false,
    val pin: String = "",
    val working: Boolean = false,
    val deleteFailure: DeleteOutcome? = null,
    val refusals: Int = 0,
    val deleted: Boolean = false,
) {
    val needsPin: Boolean get() = profile?.paranoid == true
}

class AccountSettingsViewModel(private val services: AccountSettingsServices) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountSettingsState(fullDevice = services.fullDevice))
    private var loads = 0
    private var deletions = 0

    val phrase = PhraseEntry(services::phraseSuggestions, services::isValidPhrase)

    val state: StateFlow<AccountSettingsState> = mutableState.asStateFlow()

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    fun opened() {
        val mine = ++loads
        viewModelScope.launch {
            val result = try {
                services.profile()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ProfileLoad.Failed(ItemFailure.Failed)
            }
            if (mine != loads) return@launch
            when (result) {
                is ProfileLoad.Loaded -> mutableState.update { it.copy(loaded = true, profile = result.profile, failure = null) }
                is ProfileLoad.Failed -> mutableState.update { it.copy(loaded = true, failure = result.failure) }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun claimChanged(value: String) = mutableState.update {
        if (it.renaming) it else it.copy(claim = value.take(MAX_USERNAME_CLAIM_LENGTH), usernameProblem = null, renamed = null)
    }

    fun dismissRenamed() = mutableState.update { it.copy(renamed = null) }

    fun dismissUsernameProblem() = mutableState.update { it.copy(usernameProblem = null) }

    fun rename() {
        val current = mutableState.value
        if (current.renaming || !current.fullDevice) return
        when (val check = checkUsername(current.claim, current.profile?.username)) {
            UsernameCheck.Malformed -> mutableState.update { it.copy(usernameProblem = UsernameProblem.Malformed, renamed = null) }
            UsernameCheck.Unchanged -> mutableState.update { it.copy(usernameProblem = UsernameProblem.Unchanged, renamed = null) }
            is UsernameCheck.Ok -> {
                mutableState.update { it.copy(renaming = true, usernameProblem = null, renamed = null) }
                viewModelScope.launch {
                    val outcome = try {
                        services.changeUsername(check.username)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        UsernameOutcome.Failed
                    }
                    when (outcome) {
                        is UsernameOutcome.Changed -> mutableState.update {
                            it.copy(
                                renaming = false,
                                claim = "",
                                renamed = outcome.username,
                                profile = it.profile?.copy(username = outcome.username),
                            )
                        }
                        UsernameOutcome.Malformed -> mutableState.update { it.copy(renaming = false, usernameProblem = UsernameProblem.Malformed) }
                        else -> mutableState.update { it.copy(renaming = false, usernameProblem = UsernameProblem.Refused(outcome)) }
                    }
                }
            }
        }
    }

    fun openDelete() {
        if (!mutableState.value.fullDevice || mutableState.value.deleting) return
        phrase.start()
        mutableState.update { it.copy(deleting = true, confirmed = false, pin = "", deleteFailure = null) }
        opened()
    }

    fun closeDelete() {
        if (mutableState.value.working) return
        phrase.forget()
        mutableState.update { it.copy(deleting = false, confirmed = false, pin = "", deleteFailure = null) }
    }

    fun confirmedChanged(value: Boolean) = mutableState.update { if (it.working) it else it.copy(confirmed = value) }

    fun pinChanged(value: String) = mutableState.update { if (it.working) it else it.copy(pin = value, deleteFailure = null) }

    fun dismissDeleteFailure() = mutableState.update { it.copy(deleteFailure = null) }

    fun canDelete(): Boolean {
        val state = mutableState.value
        return state.deleting && !state.working && state.confirmed && phrase.complete && (!state.needsPin || state.pin.length == PIN_LENGTH)
    }

    fun delete() {
        if (!canDelete() || !phrase.active || !phrase.validate()) return
        val state = mutableState.value
        val words = phrase.copyWords()
        val pin = if (state.needsPin) state.pin.toCharArray() else null
        val mine = ++deletions
        mutableState.update { it.copy(working = true, deleteFailure = null) }
        viewModelScope.launch {
            try {
                val outcome = try {
                    services.deleteAccount(words, pin)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    DeleteOutcome.Failed
                }
                if (mine != deletions && outcome != DeleteOutcome.Deleted) {
                    mutableState.update { it.copy(working = false) }
                    return@launch
                }
                if (outcome == DeleteOutcome.Deleted) {
                    phrase.forget()
                    mutableState.update { AccountSettingsState(fullDevice = it.fullDevice, deleted = true) }
                } else {
                    if (outcome == DeleteOutcome.PhraseMismatch) phrase.start()
                    mutableState.update {
                        it.copy(
                            working = false,
                            deleteFailure = outcome,
                            refusals = if (outcome == DeleteOutcome.PinRefused) it.refusals + 1 else it.refusals,
                        )
                    }
                }
            } finally {
                words.forEach { it.fill('\u0000') }
                pin?.fill('\u0000')
            }
        }
    }

    override fun onCleared() {
        loads++
        deletions++
        phrase.forget()
    }
}
