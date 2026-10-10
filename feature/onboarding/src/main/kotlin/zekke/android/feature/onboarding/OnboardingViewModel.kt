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
import zekke.core.recoverykit.RecoveryKitContent

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
    val lookingUpUsername: Boolean = false,
    val createdAtEpochMillis: Long = 0,
    val paranoidFailed: Boolean = false,
    val revealedPhrase: List<String>? = null,
    val kitSaved: Boolean = false,
    val kitFailed: Boolean = false,
    val kitLost: Boolean = false,
) {
    val inProgress: Boolean get() = step != OnboardingStep.Start && step != OnboardingStep.Done
}

class OnboardingViewModel(private val services: OnboardingServices) : ViewModel() {
    private val mutableState = MutableStateFlow(
        if (services.kitWasLost()) OnboardingState(step = OnboardingStep.Done, kitLost = true) else OnboardingState(),
    )
    private var words: List<CharArray>? = null
    private var releaseProcess: (() -> Unit)? = null
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
        if (releaseProcess == null) releaseProcess = services.holdProcess()
        viewModelScope.launch {
            try {
                val created = services.createAccount(phrase.map { it.copyOf() }, pin, current.paranoid)
                mutableState.update {
                    it.copy(
                        step = OnboardingStep.Recovery,
                        pin = "",
                        confirmation = "",
                        username = created.username,
                        createdAtEpochMillis = created.createdAtEpochMillis,
                        paranoid = created.paranoid,
                        paranoidFailed = created.paranoidFailed,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountCreationException) {
                releaseHeldProcess()
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

    fun lookUpUsername() {
        if (mutableState.value.lookingUpUsername) return
        mutableState.update { it.copy(lookingUpUsername = true) }
        viewModelScope.launch {
            val username = services.username()
            mutableState.update { it.copy(username = it.username ?: username, lookingUpUsername = false) }
        }
    }

    fun revealPhrase() {
        val phrase = words ?: return
        mutableState.update { it.copy(revealedPhrase = phrase.map { word -> String(word) }) }
    }

    fun kitContent(): RecoveryKitContent? {
        val current = mutableState.value
        val phrase = words ?: return null
        val username = current.username ?: return null
        if (current.step != OnboardingStep.Recovery) return null
        return services.kitContent(username, phrase, current.createdAtEpochMillis)
    }

    fun systemScreenWillOpen() = services.systemScreenWillOpen()

    fun systemScreenReturned() = services.systemScreenReturned()

    fun kitDelivered() {
        if (mutableState.value.step != OnboardingStep.Recovery) return
        services.kitSettled()
        mutableState.update { it.copy(kitSaved = true, kitFailed = false) }
    }

    fun kitFailed() = mutableState.update { it.copy(kitFailed = true) }

    fun finish() {
        if (!mutableState.value.kitSaved) return
        forgetWords()
        releaseHeldProcess()
        mutableState.update { OnboardingState(step = OnboardingStep.Done) }
    }

    fun acknowledgeLoss() {
        services.kitSettled()
        mutableState.update { it.copy(kitLost = false) }
    }

    private fun lockedOut() {
        val step = mutableState.value.step
        if (step == OnboardingStep.Creating || step == OnboardingStep.Recovery) return
        forgetWords()
        mutableState.update { OnboardingState() }
    }

    private fun forgetWords() {
        words?.forEach { it.fill(' ') }
        words = null
    }

    private fun releaseHeldProcess() {
        releaseProcess?.invoke()
        releaseProcess = null
    }

    override fun onCleared() {
        stopListening()
        services.forgetDraft()
        forgetWords()
        releaseHeldProcess()
    }
}
