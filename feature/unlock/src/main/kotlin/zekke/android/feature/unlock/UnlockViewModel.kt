package zekke.android.feature.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.PIN_LENGTH

enum class RegistrationLost { Forgotten, Removed }

sealed interface UnlockFeedback {
    data class WrongPin(val attemptsRemaining: Int) : UnlockFeedback
    data object Offline : UnlockFeedback
    data class RateLimited(val retryAfterSeconds: Long?) : UnlockFeedback
    data object PhoneLocked : UnlockFeedback
    data object Interrupted : UnlockFeedback
    data object Failed : UnlockFeedback
}

data class UnlockState(
    val pinRevision: Int = 0,
    val busy: Boolean = false,
    val feedback: UnlockFeedback? = null,
    val registrationLost: RegistrationLost? = null,
    val confirmingForget: Boolean = false,
    val chainProblem: Boolean = false,
    val rewrapIssue: RewrapIssue? = null,
)

class UnlockViewModel(private val services: UnlockServices) : ViewModel() {
    private val mutableState = MutableStateFlow(UnlockState())
    private var pin = CharArray(0)
    private var attemptId = 0

    val state = mutableState.asStateFlow()

    fun pinValue(): String = String(pin)

    fun pinChanged(value: String) {
        if (mutableState.value.busy || mutableState.value.registrationLost != null) return
        val next = value.filter { it in '0'..'9' }.take(PIN_LENGTH).toCharArray()
        pin.fill('\u0000')
        pin = next
        mutableState.update { it.copy(pinRevision = it.pinRevision + 1, feedback = null) }
    }

    fun submitPin() {
        if (mutableState.value.busy || mutableState.value.registrationLost != null || pin.size != PIN_LENGTH) return
        val currentAttempt = ++attemptId
        val pinCopy = pin.copyOf()
        clearPin()
        mutableState.update { it.copy(busy = true, feedback = null, chainProblem = false, rewrapIssue = null) }
        viewModelScope.launch {
            val outcome = try {
                services.unlock(pinCopy)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UnlockAttempt.Failed
            } finally {
                pinCopy.fill('\u0000')
            }
            if (currentAttempt != attemptId) return@launch
            mutableState.update { state ->
                when (outcome) {
                    is UnlockAttempt.Opened -> state.copy(
                        busy = false,
                        feedback = null,
                        chainProblem = outcome.chainProblem,
                        rewrapIssue = outcome.rewrapIssue,
                    )
                    is UnlockAttempt.WrongPin -> state.copy(busy = false, feedback = UnlockFeedback.WrongPin(outcome.attemptsRemaining))
                    UnlockAttempt.Forgotten -> state.copy(
                        busy = false,
                        feedback = null,
                        registrationLost = RegistrationLost.Forgotten,
                    )
                    UnlockAttempt.Removed -> state.copy(
                        busy = false,
                        feedback = null,
                        registrationLost = RegistrationLost.Removed,
                    )
                    UnlockAttempt.Offline -> state.copy(busy = false, feedback = UnlockFeedback.Offline)
                    is UnlockAttempt.RateLimited -> state.copy(
                        busy = false,
                        feedback = UnlockFeedback.RateLimited(outcome.retryAfterSeconds),
                    )
                    UnlockAttempt.PhoneLocked -> state.copy(busy = false, feedback = UnlockFeedback.PhoneLocked)
                    UnlockAttempt.Interrupted -> state.copy(busy = false, feedback = UnlockFeedback.Interrupted)
                    UnlockAttempt.Failed -> state.copy(busy = false, feedback = UnlockFeedback.Failed)
                }
            }
        }
    }

    fun dismissFeedback() = mutableState.update { it.copy(feedback = null) }

    fun requestForget() {
        if (!mutableState.value.busy && mutableState.value.registrationLost == null) {
            mutableState.update { it.copy(confirmingForget = true, feedback = null) }
        }
    }

    fun keepTrying() = mutableState.update { it.copy(confirmingForget = false) }

    fun forgetThisPhone() {
        if (mutableState.value.busy || !mutableState.value.confirmingForget) return
        val currentAttempt = ++attemptId
        clearPin()
        mutableState.update { it.copy(busy = true, feedback = null) }
        viewModelScope.launch {
            try {
                services.forgetThisPhone()
                if (currentAttempt == attemptId) {
                    mutableState.update {
                        it.copy(
                            busy = false,
                            confirmingForget = false,
                            registrationLost = RegistrationLost.Forgotten,
                            feedback = null,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (currentAttempt == attemptId) {
                    mutableState.update { it.copy(busy = false, feedback = UnlockFeedback.Failed) }
                }
            }
        }
    }

    fun continueWithPhrase() {
        if (mutableState.value.registrationLost == null) return
        attemptId++
        clearPin()
        mutableState.value = UnlockState(pinRevision = mutableState.value.pinRevision + 1)
    }

    fun dismissChainProblem() = mutableState.update { it.copy(chainProblem = false) }

    fun dismissRewrapIssue() = mutableState.update { it.copy(rewrapIssue = null) }

    private fun clearPin() {
        pin.fill('\u0000')
        pin = CharArray(0)
        mutableState.update { it.copy(pinRevision = it.pinRevision + 1) }
    }

    override fun onCleared() {
        attemptId++
        clearPin()
    }
}
