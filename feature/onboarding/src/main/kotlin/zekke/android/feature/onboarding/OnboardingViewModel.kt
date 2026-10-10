package zekke.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.core.pin.PinRejection
import zekke.core.pin.PinValidation
import zekke.core.pin.validatePin

enum class OnboardingStep { Start, Phrase, Mode, Pin, Creating, Recovery, Done }

sealed interface PinProblem {
    data class Rejected(val reason: PinRejection) : PinProblem

    data object Mismatch : PinProblem
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Start,
    val wordCount: Int = 12,
    val paranoid: Boolean = false,
    val pin: String = "",
    val confirmation: String = "",
    val pinProblem: PinProblem? = null,
    val failure: CreationFailure? = null,
    val username: String? = null,
    val paranoidFailed: Boolean = false,
    val revealedPhrase: List<String>? = null,
    val lostToLock: Boolean = false,
) {
    val inProgress: Boolean get() = step != OnboardingStep.Start && step != OnboardingStep.Done
}

class OnboardingViewModel(private val services: OnboardingServices) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    private var words: List<CharArray>? = null
    private val stopListening = services.onLocked(::lockedOut)

    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    fun startCreating() = mutableState.update { it.copy(step = OnboardingStep.Phrase, failure = null) }

    fun chooseWordCount(count: Int) = mutableState.update { it.copy(wordCount = count) }

    fun confirmPhrase() {
        forgetWords()
        words = services.newPhrase(mutableState.value.wordCount)
        mutableState.update { it.copy(step = OnboardingStep.Mode) }
    }

    fun chooseMode(paranoid: Boolean) = mutableState.update { it.copy(paranoid = paranoid) }

    fun confirmMode() = mutableState.update { it.copy(step = OnboardingStep.Pin, pin = "", confirmation = "", pinProblem = null) }

    fun pinChanged(value: String) = mutableState.update { it.copy(pin = value, pinProblem = null, failure = null) }

    fun confirmationChanged(value: String) = mutableState.update {
        it.copy(confirmation = value, pinProblem = if (value.length < it.pin.length) null else it.pinProblem)
    }

    fun submitPin() {
        val current = mutableState.value
        if (current.step != OnboardingStep.Pin) return
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
        val phrase = words ?: return
        mutableState.update { it.copy(step = OnboardingStep.Creating, pinProblem = null, failure = null) }
        viewModelScope.launch {
            try {
                val created = services.createAccount(phrase.map { it.copyOf() }, pin, current.paranoid)
                mutableState.update {
                    if (it.step != OnboardingStep.Creating) return@update it
                    it.copy(
                        step = OnboardingStep.Recovery,
                        pin = "",
                        confirmation = "",
                        username = created.username,
                        paranoid = created.paranoid,
                        paranoidFailed = created.paranoidFailed,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountCreationException) {
                mutableState.update { it.copy(step = OnboardingStep.Pin, failure = error.failure, confirmation = "") }
            } finally {
                pin.fill('\u0000')
            }
        }
    }

    fun back() = mutableState.update {
        when (it.step) {
            OnboardingStep.Phrase -> it.copy(step = OnboardingStep.Start)
            OnboardingStep.Mode -> it.copy(step = OnboardingStep.Phrase)
            OnboardingStep.Pin -> it.copy(step = OnboardingStep.Mode, pin = "", confirmation = "", pinProblem = null, failure = null)
            else -> it
        }
    }

    fun revealPhrase() {
        val phrase = words ?: return
        mutableState.update { it.copy(revealedPhrase = phrase.map { word -> String(word) }) }
    }

    fun finish() {
        forgetWords()
        mutableState.update { OnboardingState(step = OnboardingStep.Done) }
    }

    fun acknowledgeLoss() = mutableState.update { it.copy(lostToLock = false) }

    private fun lockedOut() {
        val accountExists = mutableState.value.step == OnboardingStep.Creating || mutableState.value.step == OnboardingStep.Recovery
        forgetWords()
        mutableState.update {
            if (accountExists) OnboardingState(step = OnboardingStep.Done, lostToLock = true) else OnboardingState()
        }
    }

    private fun forgetWords() {
        words?.forEach { it.fill(' ') }
        words = null
    }

    override fun onCleared() {
        stopListening()
        services.forgetDraft()
        forgetWords()
    }
}
