package zekke.android.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.PhraseEntry
import zekke.core.pin.PinRejection
import zekke.core.pin.PinValidation
import zekke.core.pin.validatePin
import zekke.core.recoverykit.RecoveryKitContent

enum class OnboardingStep { Start, Phrase, Mode, Pin, Creating, Recovery, EnterRecoveryPhrase, EnrolPin, Enrolling, Done }

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
    val enrolPin: String = "",
    val enrolPinConfirmation: String = "",
    val enrolPinProblem: PinProblem? = null,
    val enrolmentFailure: DeviceEnrolmentFailure? = null,
    val enrolmentRefusals: Int = 0,
    val devicesToRemove: List<EnrolmentDevice>? = null,
    val selectedDeviceIds: Set<String> = emptySet(),
    val removeAllDevices: Boolean = false,
) {
    val inProgress: Boolean get() = step != OnboardingStep.Start && step != OnboardingStep.Done
}

class OnboardingViewModel(private val services: OnboardingServices) : ViewModel() {
    private val mutableState = MutableStateFlow(
        if (services.kitWasLost()) OnboardingState(step = OnboardingStep.Done, kitLost = true) else OnboardingState(),
    )
    private var words: List<CharArray>? = null
    private var releaseProcess: (() -> Unit)? = null
    private var enrolmentAttempt = 0
    private val stopListening = services.onLocked(::lockedOut)

    val phrase = PhraseEntry(services::phraseSuggestions, services::isValidPhrase)

    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    fun startCreating() {
        phrase.forget()
        mutableState.update { it.copy(step = OnboardingStep.Phrase, failure = null) }
    }

    fun startEnrolling() {
        forgetWords()
        phrase.start()
        mutableState.update {
            it.copy(
                step = OnboardingStep.EnterRecoveryPhrase,
                enrolPin = "",
                enrolPinConfirmation = "",
                enrolPinProblem = null,
                enrolmentFailure = null,
                devicesToRemove = null,
                selectedDeviceIds = emptySet(),
            )
        }
    }

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

    fun confirmEnteredPhrase() {
        if (mutableState.value.step != OnboardingStep.EnterRecoveryPhrase || !phrase.complete) return
        if (!phrase.validate()) return
        mutableState.update {
            it.copy(
                step = OnboardingStep.EnrolPin,
                enrolPin = "",
                enrolPinConfirmation = "",
                enrolPinProblem = null,
                enrolmentFailure = null,
                selectedDeviceIds = emptySet(),
            )
        }
    }

    fun enrolPinChanged(value: String) = mutableState.update {
        it.copy(enrolPin = value, enrolPinProblem = null, enrolmentFailure = null)
    }

    fun enrolPinConfirmationChanged(value: String) = mutableState.update {
        it.copy(
            enrolPinConfirmation = value,
            enrolPinProblem = if (value.length < it.enrolPin.length) null else it.enrolPinProblem,
            enrolmentFailure = null,
        )
    }

    fun toggleDeviceRemoval(id: String) = mutableState.update { current ->
        val devices = current.devicesToRemove.orEmpty()
        if (devices.none { it.id == id }) current
        else current.copy(selectedDeviceIds = if (id in current.selectedDeviceIds) current.selectedDeviceIds - id else current.selectedDeviceIds + id)
    }

    fun toggleRemoveAllDevices(checked: Boolean) = mutableState.update {
        if (it.step == OnboardingStep.EnrolPin) it.copy(removeAllDevices = checked, enrolmentFailure = null) else it
    }

    fun submitEnrolment() {
        val current = mutableState.value
        if (current.step != OnboardingStep.EnrolPin) return
        val pin = current.enrolPin.toCharArray()
        val validation = validatePin(pin)
        if (validation is PinValidation.Invalid) {
            pin.fill('\u0000')
            mutableState.update { it.copy(enrolPinProblem = PinProblem.Rejected(validation.reason), enrolPinConfirmation = "") }
            return
        }
        if (current.enrolPinConfirmation != current.enrolPin) {
            pin.fill('\u0000')
            mutableState.update { it.copy(enrolPinProblem = PinProblem.Mismatch, enrolPinConfirmation = "") }
            return
        }
        if (!phrase.active) {
            pin.fill('\u0000')
            return
        }
        val requiresChoice = current.devicesToRemove != null && !current.removeAllDevices
        if (requiresChoice && (current.devicesToRemove.isNullOrEmpty() || current.selectedDeviceIds.isEmpty())) {
            pin.fill('\u0000')
            return
        }
        val attempt = ++enrolmentAttempt
        val phraseCopy = phrase.copyWords()
        if (releaseProcess == null) releaseProcess = services.holdProcess()
        val removal = when {
            current.removeAllDevices -> DeviceRemoval.All
            current.devicesToRemove != null -> DeviceRemoval.Listed(current.selectedDeviceIds.toList())
            else -> DeviceRemoval.None
        }
        mutableState.update { it.copy(step = OnboardingStep.Enrolling, enrolPin = "", enrolPinConfirmation = "", enrolPinProblem = null, enrolmentFailure = null) }
        viewModelScope.launch {
            try {
                services.enrolDevice(phraseCopy, pin, removal)
                if (attempt != enrolmentAttempt) return@launch
                phrase.forget()
                releaseHeldProcess()
                mutableState.update { OnboardingState(step = OnboardingStep.Done) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: DeviceEnrolmentException) {
                if (attempt != enrolmentAttempt) return@launch
                releaseHeldProcess()
                val tooMany = error.failure as? DeviceEnrolmentFailure.TooManyDevices
                mutableState.update {
                    it.copy(
                        step = OnboardingStep.EnrolPin,
                        enrolPin = "",
                        enrolPinConfirmation = "",
                        enrolPinProblem = null,
                        enrolmentFailure = error.failure,
                        enrolmentRefusals = if (error.failure == DeviceEnrolmentFailure.Refused) it.enrolmentRefusals + 1 else it.enrolmentRefusals,
                        devicesToRemove = tooMany?.devices ?: it.devicesToRemove,
                        selectedDeviceIds = if (tooMany == null) it.selectedDeviceIds else it.selectedDeviceIds.intersect(tooMany.devices.map { device -> device.id }.toSet()),
                    )
                }
            } finally {
                pin.fill('\u0000')
                phraseCopy.forEach { it.fill('\u0000') }
            }
        }
    }

    fun back() {
        if (mutableState.value.step == OnboardingStep.EnterRecoveryPhrase) phrase.forget()
        mutableState.update {
            when (it.step) {
                OnboardingStep.Phrase -> it.copy(step = OnboardingStep.Start)
                OnboardingStep.Mode -> it.copy(step = OnboardingStep.Phrase)
                OnboardingStep.Pin -> it.copy(step = OnboardingStep.Mode, pin = "", confirmation = "", pinProblem = null, failure = null)
                OnboardingStep.EnterRecoveryPhrase -> it.copy(step = OnboardingStep.Start, enrolmentFailure = null, devicesToRemove = null, selectedDeviceIds = emptySet(), removeAllDevices = false)
                OnboardingStep.EnrolPin -> it.copy(
                    step = OnboardingStep.EnterRecoveryPhrase,
                    enrolPin = "",
                    enrolPinConfirmation = "",
                    enrolPinProblem = null,
                    enrolmentFailure = null,
                    devicesToRemove = null,
                    selectedDeviceIds = emptySet(),
                    removeAllDevices = false,
                )
                else -> it
            }
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
        enrolmentAttempt++
        forgetWords()
        phrase.forget()
        releaseHeldProcess()
        mutableState.update { OnboardingState() }
    }

    private fun forgetWords() {
        words?.forEach { it.fill('\u0000') }
        words = null
    }

    private fun releaseHeldProcess() {
        releaseProcess?.invoke()
        releaseProcess = null
    }

    override fun onCleared() {
        stopListening()
        services.forgetDraft()
        enrolmentAttempt++
        forgetWords()
        phrase.forget()
        releaseHeldProcess()
    }
}
