package zekke.android.onboarding

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.android.feature.onboarding.AccountCreationException
import zekke.android.feature.onboarding.CreatedAccount
import zekke.android.feature.onboarding.CreationFailure
import zekke.android.feature.onboarding.OnboardingServices
import zekke.android.platform.KeystoreDeviceVault
import zekke.android.session.SessionGuard
import zekke.core.account.AccountServices
import zekke.core.account.SignUpDraft
import zekke.core.account.completeSignUp
import zekke.core.account.discardSignUpDraft
import zekke.core.account.draftSignUp
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.keys.generateMnemonic
import zekke.core.recoverykit.RecoveryKitContent
import zekke.core.recoverykit.RecoveryKitInput
import zekke.core.recoverykit.recoveryKitContent
import zekke.core.users.getMe

private const val ONBOARDING_PREFERENCES = "onboarding"
private const val KIT_PENDING = "kit_pending"
private const val USERNAME_ATTEMPTS = 3

class CoreOnboardingServices(
    context: Context,
    private val services: AccountServices,
    private val guard: SessionGuard,
    private val vault: KeystoreDeviceVault,
    private val now: () -> Long = System::currentTimeMillis,
) : OnboardingServices {
    private val preferences = context.getSharedPreferences(ONBOARDING_PREFERENCES, Context.MODE_PRIVATE)
    private var draft: SignUpDraft? = null

    override fun newPhrase(wordCount: Int): List<CharArray> {
        forgetDraft()
        return generateMnemonic(wordCount)
    }

    override fun forgetDraft() {
        discardSignUpDraft(draft)
        draft = null
    }

    override suspend fun createAccount(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount {
        try {
            withContext(Dispatchers.IO) { markKitPending() }
            val created = withContext(Dispatchers.Default) { signUp(words, pin, paranoid) }
            draft = null
            return created
        } finally {
            words.forEach { it.fill(' ') }
            if (!guard.isInForeground) guard.lockNow()
        }
    }

    override suspend fun username(): String? {
        repeat(USERNAME_ATTEMPTS) {
            try {
                return getMe(services.api).username
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
        return null
    }

    override fun kitContent(username: String, words: List<CharArray>, createdAtEpochMillis: Long): RecoveryKitContent =
        recoveryKitContent(RecoveryKitInput(username, words, createdAtEpochMillis))

    override fun onLocked(listener: () -> Unit): () -> Unit = guard.onLocked(listener)

    override fun holdProcess(): () -> Unit = guard.holdProcess()

    override fun systemScreenWillOpen() = guard.pickerWillOpen()

    override fun systemScreenReturned() = guard.pickerReturned()

    override fun kitWasLost(): Boolean {
        if (!preferences.getBoolean(KIT_PENDING, false)) return false
        if (vault.hasRecord()) return true
        kitSettled()
        return false
    }

    override fun kitSettled() {
        preferences.edit { remove(KIT_PENDING) }
    }

    private fun markKitPending() {
        preferences.edit(commit = true) { putBoolean(KIT_PENDING, true) }
    }

    private suspend fun signUp(words: List<CharArray>, pin: CharArray, paranoid: Boolean): CreatedAccount {
        val pending = draft ?: draftSignUp(words).also { draft = it }
        val result = try {
            completeSignUp(services, pending, pin, paranoid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: NetworkError) {
            throw AccountCreationException(CreationFailure.Offline)
        } catch (error: ApiError) {
            throw AccountCreationException(if (error.isRateLimited) CreationFailure.RateLimited(error.retryAfterSeconds) else CreationFailure.Failed)
        } catch (error: Exception) {
            throw AccountCreationException(CreationFailure.Failed)
        }
        return CreatedAccount(username(), result.paranoid, paranoid && !result.paranoid, now())
    }
}
