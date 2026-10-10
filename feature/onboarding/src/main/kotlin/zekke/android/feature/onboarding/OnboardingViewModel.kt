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

enum class OnboardingStep { Start, Phrase, Mode, Pin, Creating, Recovery, EnterRecoveryPhrase, EnrolPin, Enrolling, Done }

sealed interface PinProblem {
    data class Rejected(val reason: PinRejection) : PinProblem

    data object Mismatch : PinProblem
}

sealed interface PhraseProblem {
    data object InvalidChecksum : PhraseProblem
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
    val phraseWordCount: Int = 12,
    val phraseRevision: Int = 0,
    val focusedPhraseWord: Int = 0,
    val phraseSuggestions: List<String> = emptyList(),
    val phraseProblem: PhraseProblem? = null,
    val enrolPin: String = "",
    val enrolPinConfirmation: String = "",
    val enrolPinProblem: PinProblem? = null,
    val enrolmentFailure: DeviceEnrolmentFailure? = null,
    val enrolmentRefusals: Int = 0,
    val devicesToRemove: List<EnrolmentDevice>? = null,
    val selectedDeviceIds: Set<String> = emptySet(),
) {
    val inProgress: Boolean get() = step != OnboardingStep.Start && step != OnboardingStep.Done
}

class OnboardingViewModel(private val services: OnboardingServices) : ViewModel() {
    private val mutableState = MutableStateFlow(
        if (services.kitWasLost()) OnboardingState(step = OnboardingStep.Done, kitLost = true) else OnboardingState(),
    )
    private var words: List<CharArray>? = null
    private var phraseEntry: MutableList<CharArray>? = null
    private var releaseProcess: (() -> Unit)? = null
    private var enrolmentAttempt = 0
    private val stopListening = services.onLocked(::lockedOut)

    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    fun startCreating() {
        forgetEnrolmentPhrase()
        mutableState.update { it.copy(step = OnboardingStep.Phrase, failure = null) }
    }

    fun startEnrolling() {
        forgetWords()
        forgetEnrolmentPhrase()
        phraseEntry = MutableList(mutableState.value.phraseWordCount) { CharArray(0) }
        mutableState.update {
            it.copy(
                step = OnboardingStep.EnterRecoveryPhrase,
                phraseRevision = it.phraseRevision + 1,
                focusedPhraseWord = 0,
                phraseSuggestions = emptyList(),
                phraseProblem = null,
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

    fun phraseWord(index: Int): String = phraseEntry?.getOrNull(index)?.let { String(it) }.orEmpty()

    val phraseComplete: Boolean get() = phraseEntry?.all { it.isNotEmpty() } == true

    fun changePhraseWordCount(count: Int) {
        if (count !in listOf(12, 24) || mutableState.value.step != OnboardingStep.EnterRecoveryPhrase) return
        forgetEnrolmentPhrase()
        phraseEntry = MutableList(count) { CharArray(0) }
        mutableState.update {
            it.copy(
                phraseWordCount = count,
                phraseRevision = it.phraseRevision + 1,
                focusedPhraseWord = 0,
                phraseSuggestions = emptyList(),
                phraseProblem = null,
                enrolmentFailure = null,
                devicesToRemove = null,
                selectedDeviceIds = emptySet(),
            )
        }
    }

    fun phraseWordChanged(index: Int, value: String) {
        val entry = phraseEntry ?: return
        if (mutableState.value.step != OnboardingStep.EnterRecoveryPhrase || index !in entry.indices) return
        val normalized = value.trim().lowercase().filter { it in 'a'..'z' }.take(8)
        entry[index].fill('\u0000')
        entry[index] = normalized.toCharArray()
        val prefix = entry[index].copyOf()
        val suggestions = try {
            services.phraseSuggestions(prefix)
        } finally {
            prefix.fill('\u0000')
        }
        mutableState.update {
            it.copy(
                phraseRevision = it.phraseRevision + 1,
                focusedPhraseWord = index,
                phraseSuggestions = suggestions,
                phraseProblem = null,
                enrolmentFailure = null,
            )
        }
    }

    fun focusPhraseWord(index: Int) {
        val entry = phraseEntry ?: return
        if (mutableState.value.step != OnboardingStep.EnterRecoveryPhrase || index !in entry.indices) return
        val prefix = entry[index].copyOf()
        val suggestions = try {
            services.phraseSuggestions(prefix)
        } finally {
            prefix.fill('\u0000')
        }
        mutableState.update { it.copy(focusedPhraseWord = index, phraseSuggestions = suggestions) }
    }

    fun choosePhraseSuggestion(index: Int, word: String) {
        phraseWordChanged(index, word)
        val next = (index + 1).coerceAtMost((phraseEntry?.size ?: 1) - 1)
        focusPhraseWord(next)
    }

    fun confirmEnteredPhrase() {
        if (mutableState.value.step != OnboardingStep.EnterRecoveryPhrase || !phraseComplete) return
        val copy = phraseEntry?.map { it.copyOf() } ?: return
        val valid = try {
            services.isValidPhrase(copy)
        } finally {
            copy.forEach { it.fill('\u0000') }
        }
        if (!valid) {
            mutableState.update { it.copy(phraseProblem = PhraseProblem.InvalidChecksum) }
            return
        }
        mutableState.update {
            it.copy(
                step = OnboardingStep.EnrolPin,
                phraseProblem = null,
                phraseSuggestions = emptyList(),
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
        val phrase = phraseEntry ?: run { pin.fill('\u0000'); return }
        val requiresRemoval = current.devicesToRemove != null
        if (requiresRemoval && (current.devicesToRemove.isNullOrEmpty() || current.selectedDeviceIds.isEmpty())) {
            pin.fill('\u0000')
            return
        }
        val attempt = ++enrolmentAttempt
        val phraseCopy = phrase.map { it.copyOf() }
        if (releaseProcess == null) releaseProcess = services.holdProcess()
        val removeIds = current.selectedDeviceIds.toList()
        mutableState.update { it.copy(step = OnboardingStep.Enrolling, enrolPin = "", enrolPinConfirmation = "", enrolPinProblem = null, enrolmentFailure = null) }
        viewModelScope.launch {
            try {
                services.enrolDevice(phraseCopy, pin, removeIds)
                if (attempt != enrolmentAttempt) return@launch
                forgetEnrolmentPhrase()
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
        val step = mutableState.value.step
        if (step == OnboardingStep.EnterRecoveryPhrase) forgetEnrolmentPhrase()
        mutableState.update {
            when (it.step) {
                OnboardingStep.Phrase -> it.copy(step = OnboardingStep.Start)
                OnboardingStep.Mode -> it.copy(step = OnboardingStep.Phrase)
                OnboardingStep.Pin -> it.copy(step = OnboardingStep.Mode, pin = "", confirmation = "", pinProblem = null, failure = null)
                OnboardingStep.EnterRecoveryPhrase -> it.copy(step = OnboardingStep.Start, phraseProblem = null, phraseSuggestions = emptyList(), enrolmentFailure = null, devicesToRemove = null, selectedDeviceIds = emptySet())
                OnboardingStep.EnrolPin -> it.copy(
                    step = OnboardingStep.EnterRecoveryPhrase,
                    enrolPin = "",
                    enrolPinConfirmation = "",
                    enrolPinProblem = null,
                    enrolmentFailure = null,
                    devicesToRemove = null,
                    selectedDeviceIds = emptySet(),
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
        forgetEnrolmentPhrase()
        releaseHeldProcess()
        mutableState.update { OnboardingState() }
    }

    private fun forgetWords() {
        words?.forEach { it.fill('\u0000') }
        words = null
    }

    private fun forgetEnrolmentPhrase() {
        phraseEntry?.forEach { it.fill('\u0000') }
        phraseEntry = null
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
        forgetEnrolmentPhrase()
        releaseHeldProcess()
    }
}
