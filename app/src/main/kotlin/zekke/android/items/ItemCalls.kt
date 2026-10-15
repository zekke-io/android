package zekke.android.items

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import zekke.android.session.SessionGuard
import zekke.android.ui.components.ItemFailure
import zekke.core.account.AccountServices
import zekke.core.account.DeviceRemovedException
import zekke.core.account.renewSignIn
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.items.ItemTooLargeException
import zekke.core.session.ScopeNotHeldException
import zekke.core.session.SessionLockedException

sealed interface ItemResult<out T> {
    data class Done<T>(val value: T) : ItemResult<T>

    data class Failed(val failure: ItemFailure) : ItemResult<Nothing>
}

class ItemCalls(
    private val accounts: AccountServices,
    private val guard: SessionGuard,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun <T> read(call: suspend () -> T): ItemResult<T> = withContext(work) { attempt(call) }

    suspend fun <T> write(call: suspend () -> T): ItemResult<T> = withContext(NonCancellable + main) {
        val release = guard.holdProcess()
        try {
            withContext(work) { attempt(call) }
        } finally {
            if (!guard.isInForeground) guard.lockNow()
            release()
        }
    }

    private suspend fun <T> attempt(call: suspend () -> T): ItemResult<T> = try {
        ItemResult.Done(call())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ItemResult.Failed(failureOf(error))
    }

    private suspend fun failureOf(error: Exception): ItemFailure = when {
        error is NetworkError -> ItemFailure.Offline
        error is ItemTooLargeException -> ItemFailure.TooLarge
        error is ScopeNotHeldException -> ItemFailure.ScopeMissing
        error is SessionLockedException -> ItemFailure.SessionOver
        error is ApiError -> apiFailureOf(error)
        else -> ItemFailure.Failed
    }

    private suspend fun apiFailureOf(error: ApiError): ItemFailure = when {
        error.isRateLimited -> ItemFailure.RateLimited(error.retryAfterSeconds)
        error.isSessionOver -> renewed()
        error.isStaleKeyGeneration -> ItemFailure.StaleKeys
        error.status == 404 -> ItemFailure.NotFound
        else -> ItemFailure.Failed
    }

    private suspend fun renewed(): ItemFailure = try {
        renewSignIn(accounts)
        ItemFailure.SessionRenewed
    } catch (error: CancellationException) {
        throw error
    } catch (_: DeviceRemovedException) {
        ItemFailure.SessionOver
    } catch (_: NetworkError) {
        ItemFailure.Offline
    } catch (_: Exception) {
        ItemFailure.SessionOver
    }
}

inline fun <T, R> ItemResult<T>.fold(done: (T) -> R, failed: (ItemFailure) -> R): R = when (this) {
    is ItemResult.Done -> done(value)
    is ItemResult.Failed -> failed(failure)
}
