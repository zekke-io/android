package zekke.android.onboarding

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zekke.android.feature.onboarding.AccountCreationException
import zekke.android.feature.onboarding.CreatedAccount
import zekke.android.feature.onboarding.CreationFailure
import zekke.android.feature.onboarding.OnboardingServices
import zekke.android.session.SessionGuard
import zekke.core.account.AccountServices
import zekke.core.account.SignUpDraft
import zekke.core.account.completeSignUp
import zekke.core.account.discardSignUpDraft
import zekke.core.account.draftSignUp
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.keys.generateMnemonic
import zekke.core.users.getMe

class CoreOnboardingServices(
    private val services: AccountServices,
    private val guard: SessionGuard,
) : OnboardingServices {
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
        guard.transferStarted()
        try {
            val created = withContext(Dispatchers.Default) { signUp(words, pin, paranoid) }
            draft = null
            return created
        } finally {
            words.forEach { it.fill(' ') }
            guard.transferFinished()
            if (!guard.isInForeground) guard.lockNow()
        }
    }

    override fun onLocked(listener: () -> Unit): () -> Unit = guard.onLocked(listener)

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
        val username = try {
            getMe(services.api).username
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            null
        }
        return CreatedAccount(username, result.paranoid, paranoid && !result.paranoid)
    }
}
