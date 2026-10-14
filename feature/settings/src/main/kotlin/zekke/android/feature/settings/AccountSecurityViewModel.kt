package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.PIN_LENGTH
import zekke.android.ui.components.PhraseEntry
import zekke.core.pin.PinRejection
import zekke.core.pin.PinValidation
import zekke.core.pin.validatePin

enum class SecurityFlow { TurnOnParanoid, ChangeAccountPin }

enum class SecurityStep { Closed, Warning, Phrase, Pins, Working }

sealed interface PinProblem {
    data class Rejected(val reason: PinRejection) : PinProblem

    data object Mismatch : PinProblem

    data object CurrentIncomplete : PinProblem
}

data class SecurityFinished(val flow: SecurityFlow, val signedOutDevices: Int)

data class AccountSecurityState(
    val mode: AccountMode? = null,
    val modeUnavailable: Boolean = false,
    val flow: SecurityFlow? = null,
    val step: SecurityStep = SecurityStep.Closed,
    val currentPin: String = "",
    val pin: String = "",
    val confirmation: String = "",
    val pinProblem: PinProblem? = null,
    val failure: AccountSecurityOutcome? = null,
    val refusals: Int = 0,
    val finished: SecurityFinished? = null,
) {
    val open: Boolean get() = step != SecurityStep.Closed
}

class AccountSecurityViewModel(private val services: AccountSecurityServices) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountSecurityState())
    private var attemptId = 0

    val phrase = PhraseEntry(services::phraseSuggestions, services::isValidPhrase)

    val state: StateFlow<AccountSecurityState> = mutableState.asStateFlow()

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    fun loadMode() {
        if (mutableState.value.mode != null) return
        mutableState.update { it.copy(modeUnavailable = false) }
        viewModelScope.launch {
            val mode = try {
                services.accountMode()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            mutableState.update { it.copy(mode = mode, modeUnavailable = mode == null) }
        }
    }

    fun open(flow: SecurityFlow) {
        if (mutableState.value.open) return
        phrase.start()
        mutableState.update {
            AccountSecurityState(
                mode = it.mode,
                refusals = it.refusals,
                flow = flow,
                step = if (flow == SecurityFlow.TurnOnParanoid) SecurityStep.Warning else SecurityStep.Phrase,
            )
        }
    }

    fun continueFromWarning() = mutableState.update {
        if (it.step == SecurityStep.Warning) it.copy(step = SecurityStep.Phrase) else it
    }

    fun confirmPhrase() {
        if (mutableState.value.step != SecurityStep.Phrase || !phrase.complete) return
        if (!phrase.validate()) return
        mutableState.update { it.copy(step = SecurityStep.Pins, failure = null) }
    }

    fun currentPinChanged(value: String) = mutableState.update { it.copy(currentPin = value, pinProblem = null, failure = null) }

    fun pinChanged(value: String) = mutableState.update { it.copy(pin = value, pinProblem = null, failure = null) }

    fun confirmationChanged(value: String) = mutableState.update {
        it.copy(confirmation = value, pinProblem = if (value.length < it.pin.length) null else it.pinProblem, failure = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.step != SecurityStep.Pins || !phrase.active) return
        val flow = current.flow ?: return
        if (flow == SecurityFlow.ChangeAccountPin && current.currentPin.length != PIN_LENGTH) {
            mutableState.update { it.copy(pinProblem = PinProblem.CurrentIncomplete) }
            return
        }
        val pin = current.pin.toCharArray()
        val validation = validatePin(pin)
        if (validation is PinValidation.Invalid) {
            pin.fill('\u0000')
            mutableState.update { it.copy(pinProblem = PinProblem.Rejected(validation.reason), confirmation = "") }
            return
        }
        if (current.confirmation != current.pin) {
            pin.fill('\u0000')
            mutableState.update { it.copy(pinProblem = PinProblem.Mismatch, confirmation = "") }
            return
        }
        val currentPin = current.currentPin.toCharArray()
        val words = phrase.copyWords()
        val attempt = ++attemptId
        mutableState.update { it.copy(step = SecurityStep.Working, pinProblem = null, failure = null) }
        viewModelScope.launch {
            try {
                val outcome = try {
                    when (flow) {
                        SecurityFlow.TurnOnParanoid -> services.turnOnParanoid(words, pin)
                        SecurityFlow.ChangeAccountPin -> services.changeAccountPin(words, currentPin, pin)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    AccountSecurityOutcome.Failed
                }
                if (attempt != attemptId) return@launch
                if (outcome is AccountSecurityOutcome.Done || outcome == AccountSecurityOutcome.ParanoidOnUnfinished) phrase.forget()
                mutableState.update { state -> apply(state, flow, outcome) }
            } finally {
                pin.fill('\u0000')
                currentPin.fill('\u0000')
                words.forEach { it.fill('\u0000') }
            }
        }
    }

    private fun apply(state: AccountSecurityState, flow: SecurityFlow, outcome: AccountSecurityOutcome): AccountSecurityState = when (outcome) {
        is AccountSecurityOutcome.Done -> AccountSecurityState(
            mode = AccountMode.Paranoid,
            finished = SecurityFinished(flow, outcome.signedOutDevices),
        )
        AccountSecurityOutcome.ParanoidOnUnfinished -> AccountSecurityState(mode = AccountMode.Paranoid, failure = outcome)
        else -> AccountSecurityState(
            mode = state.mode,
            flow = flow,
            step = if (outcome == AccountSecurityOutcome.PhraseMismatch) SecurityStep.Phrase else SecurityStep.Pins,
            failure = outcome,
            refusals = if (outcome == AccountSecurityOutcome.PinRefused) state.refusals + 1 else state.refusals,
        )
    }

    fun close() {
        if (mutableState.value.step == SecurityStep.Working) return
        mutableState.update { closed(it) }
    }

    fun dismissFinished() = mutableState.update { it.copy(finished = null) }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    private fun closed(state: AccountSecurityState): AccountSecurityState {
        phrase.forget()
        return AccountSecurityState(mode = state.mode, refusals = state.refusals)
    }

    override fun onCleared() {
        attemptId++
        phrase.forget()
    }
}
