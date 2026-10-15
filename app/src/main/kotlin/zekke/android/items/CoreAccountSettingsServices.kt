package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.feature.settings.AccountProfile
import zekke.android.feature.settings.AccountSettingsServices
import zekke.android.feature.settings.DeleteOutcome
import zekke.android.feature.settings.ProfileLoad
import zekke.android.feature.settings.UsernameOutcome
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.session.SessionGuard
import zekke.android.unlock.ThisPhoneEvents
import zekke.core.account.AccountServices
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.PhraseMismatchException
import zekke.core.account.deleteAccountWithPhrase
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.items.ItemContext
import zekke.core.keys.isValidMnemonic
import zekke.core.keys.mnemonicWordSuggestions
import zekke.core.users.MalformedUsernameException
import zekke.core.users.getMe
import zekke.core.users.updateUsername

private const val UNPROCESSABLE = 422

fun usernameOutcomeOf(error: Exception): UsernameOutcome = when (error) {
    is MalformedUsernameException -> UsernameOutcome.Malformed
    is NetworkError -> UsernameOutcome.Offline
    is ApiError -> when {
        error.isRateLimited -> UsernameOutcome.RateLimited(error.retryAfterSeconds)
        error.status == UNPROCESSABLE -> UsernameOutcome.Unavailable
        else -> UsernameOutcome.Failed
    }
    else -> UsernameOutcome.Failed
}

fun deleteOutcomeOf(error: Exception): DeleteOutcome = when (error) {
    is PhraseMismatchException -> DeleteOutcome.PhraseMismatch
    is NetworkError -> DeleteOutcome.Offline
    is ApiError -> when {
        error.isRateLimited -> DeleteOutcome.RateLimited(error.retryAfterSeconds)
        error.isCredentialFailure -> DeleteOutcome.PinRefused
        else -> DeleteOutcome.Failed
    }
    is ChainNotVerifiedException -> DeleteOutcome.ChainProblem
    is DeviceVaultUnavailableException -> DeleteOutcome.PhoneLocked
    else -> DeleteOutcome.Failed
}

class CoreAccountSettingsServices(
    private val accounts: AccountServices,
    private val calls: ItemCalls,
    private val context: ItemContext,
    private val guard: SessionGuard,
    private val events: ThisPhoneEvents,
    private val suggestionsFor: (CharArray) -> List<String> = ::mnemonicWordSuggestions,
    private val phraseIsValid: (List<CharArray>) -> Boolean = ::isValidMnemonic,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : AccountSettingsServices {
    override val fullDevice: Boolean get() = context.session.isFullDevice

    override fun phraseSuggestions(prefix: CharArray): List<String> = suggestionsFor(prefix)

    override fun isValidPhrase(words: List<CharArray>): Boolean = phraseIsValid(words)

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()

    override suspend fun profile(): ProfileLoad = calls.read {
        val account = getMe(context.api)
        ProfileLoad.Loaded(
            AccountProfile(
                username = account.username,
                paranoid = account.paranoid,
                retentionDays = account.plan?.retentionDays ?: account.retentionDays,
                renews = account.plan?.renews ?: false,
            ),
        )
    }.fold({ it }, { ProfileLoad.Failed(it) })

    override suspend fun changeUsername(username: String): UsernameOutcome = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            withContext(work) {
                try {
                    UsernameOutcome.Changed(updateUsername(context.api, context.session.signer(), username))
                } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    UsernameOutcome.Failed
                } catch (error: Exception) {
                    usernameOutcomeOf(error)
                }
            }
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }

    override suspend fun deleteAccount(words: List<CharArray>, accountPin: CharArray?): DeleteOutcome = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            val outcome = withContext(work) {
                try {
                    deleteAccountWithPhrase(accounts, words, accountPin)
                    DeleteOutcome.Deleted
                } catch (error: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    DeleteOutcome.Failed
                } catch (error: Exception) {
                    deleteOutcomeOf(error)
                }
            }
            if (outcome == DeleteOutcome.Deleted) events.removed()
            outcome
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }
}
