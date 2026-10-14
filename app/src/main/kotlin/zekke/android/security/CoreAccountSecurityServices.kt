package zekke.android.security

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.feature.settings.AccountMode
import zekke.android.feature.settings.AccountSecurityOutcome
import zekke.android.feature.settings.AccountSecurityServices
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.SessionGuard
import zekke.core.account.AccountPinChange
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.PhraseMismatchException
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.keys.isValidMnemonic
import zekke.core.keys.mnemonicWordSuggestions

class CoreAccountSecurityServices(
    private val guard: SessionGuard,
    private val rotations: KeyRotationEvents,
    private val turnOn: suspend (List<CharArray>, CharArray) -> AccountPinChange,
    private val change: suspend (List<CharArray>, CharArray, CharArray) -> AccountPinChange,
    private val isParanoid: suspend () -> Boolean,
    private val suggestionsFor: (CharArray) -> List<String> = ::mnemonicWordSuggestions,
    private val phraseIsValid: (List<CharArray>) -> Boolean = ::isValidMnemonic,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : AccountSecurityServices {
    override fun phraseSuggestions(prefix: CharArray): List<String> = suggestionsFor(prefix)

    override fun isValidPhrase(words: List<CharArray>): Boolean = phraseIsValid(words)

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()

    override suspend fun accountMode(): AccountMode? = try {
        withContext(work) { if (isParanoid()) AccountMode.Paranoid else AccountMode.Standard }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    override suspend fun turnOnParanoid(words: List<CharArray>, pin: CharArray): AccountSecurityOutcome =
        perform(listOf(pin), turningOn = true) { turnOn(words, pin) }

    override suspend fun changeAccountPin(words: List<CharArray>, currentPin: CharArray, newPin: CharArray): AccountSecurityOutcome =
        perform(listOf(currentPin, newPin), turningOn = false) { change(words, currentPin, newPin) }

    private suspend fun perform(
        pins: List<CharArray>,
        turningOn: Boolean,
        call: suspend () -> AccountPinChange,
    ): AccountSecurityOutcome = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            withContext(work) {
                try {
                    val signedOut = call().removedFullDevices
                    if (signedOut.isNotEmpty()) rotations.rotated()
                    AccountSecurityOutcome.Done(signedOut.size)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = failureOf(error)
                    if (turningOn && failure != AccountSecurityOutcome.PhraseMismatch && alreadyParanoid()) {
                        AccountSecurityOutcome.ParanoidOnUnfinished
                    } else {
                        failure
                    }
                }
            }
        } finally {
            pins.forEach { it.fill('\u0000') }
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }

    private suspend fun alreadyParanoid(): Boolean = try {
        isParanoid()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

internal fun failureOf(error: Exception): AccountSecurityOutcome = when (error) {
    is PhraseMismatchException -> AccountSecurityOutcome.PhraseMismatch
    is NetworkError -> AccountSecurityOutcome.Offline
    is ApiError -> when {
        error.isRateLimited -> AccountSecurityOutcome.RateLimited(error.retryAfterSeconds)
        error.isCredentialFailure -> AccountSecurityOutcome.PinRefused
        else -> AccountSecurityOutcome.Failed
    }
    is ChainNotVerifiedException -> AccountSecurityOutcome.ChainProblem
    is DeviceVaultUnavailableException -> AccountSecurityOutcome.PhoneLocked
    else -> AccountSecurityOutcome.Failed
}
