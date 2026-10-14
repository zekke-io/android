package zekke.android.security

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.settings.AccountMode
import zekke.android.feature.settings.AccountSecurityOutcome
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.core.account.AccountPinChange
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.PhraseMismatchException
import zekke.core.api.ApiError
import zekke.core.api.ErrorCodes
import zekke.core.api.NetworkError
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoreAccountSecurityServicesTest {
    private class FakeSession : SessionControl {
        override val state = MutableStateFlow(SessionState.UNLOCKED)
        var locks = 0

        override fun lock() {
            locks++
            state.value = SessionState.LOCKED
        }

        override fun touch() {}
    }

    private val scope = TestScope()
    private val dispatcher = StandardTestDispatcher(scope.testScheduler)
    private val session = FakeSession()
    private val guard = SessionGuard(session, scope, {})
    private val rotations = KeyRotationEvents()
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private var turnOnCalls = 0
    private var changeCalls = 0
    private var paranoidReads = 0
    private var turnOnBehaviour: suspend () -> AccountPinChange = { AccountPinChange(emptyList()) }
    private var changeBehaviour: suspend () -> AccountPinChange = { AccountPinChange(emptyList()) }
    private var paranoidBehaviour: suspend () -> Boolean = { false }

    private val services = CoreAccountSecurityServices(
        guard,
        rotations,
        { _, _ -> turnOnCalls++; turnOnBehaviour() },
        { _, _, _ -> changeCalls++; changeBehaviour() },
        { paranoidReads++; paranoidBehaviour() },
        suggestionsFor = { emptyList() },
        phraseIsValid = { true },
        main = dispatcher,
        work = dispatcher,
    )

    private val words = listOf("abandon".toCharArray())

    private fun foreground() {
        guard.onStart(owner)
        scope.runCurrent()
    }

    private fun turnOn(pin: CharArray = "395172".toCharArray()): () -> AccountSecurityOutcome? {
        var outcome: AccountSecurityOutcome? = null
        scope.launch { outcome = services.turnOnParanoid(words, pin) }
        scope.runCurrent()
        return { outcome }
    }

    private fun change(current: CharArray = "482913".toCharArray(), new: CharArray = "395172".toCharArray()): () -> AccountSecurityOutcome? {
        var outcome: AccountSecurityOutcome? = null
        scope.launch { outcome = services.changeAccountPin(words, current, new) }
        scope.runCurrent()
        return { outcome }
    }

    @Test
    fun turningParanoidOnReportsHowManyDevicesWereSignedOutAndAsksForTheReWrap() {
        foreground()
        turnOnBehaviour = { AccountPinChange(listOf("a", "b")) }
        assertEquals(AccountSecurityOutcome.Done(2), turnOn()())
        assertTrue(rotations.pending.value)
    }

    @Test
    fun aChangeThatSignedNobodyOutAsksForNoReWrap() {
        foreground()
        assertEquals(AccountSecurityOutcome.Done(0), change()())
        assertFalse(rotations.pending.value)
        assertEquals(1, changeCalls)
    }

    @Test
    fun theProcessIsHeldWhileTheChangeRunsAndReleasedAfter() {
        foreground()
        val gate = CompletableDeferred<AccountPinChange>()
        changeBehaviour = { gate.await() }
        val outcome = change()
        assertEquals(1, guard.processHolds)
        assertNull(outcome())
        gate.complete(AccountPinChange(emptyList()))
        scope.runCurrent()
        assertEquals(0, guard.processHolds)
        assertEquals(AccountSecurityOutcome.Done(0), outcome())
    }

    @Test
    fun theChangeFinishesWhenTheCallerIsCancelledAndTheHoldIsReleased() {
        foreground()
        val gate = CompletableDeferred<AccountPinChange>()
        changeBehaviour = { gate.await() }
        var job: Job? = null
        var outcome: AccountSecurityOutcome? = null
        job = scope.launch { outcome = services.changeAccountPin(words, "482913".toCharArray(), "395172".toCharArray()) }
        scope.runCurrent()
        job.cancel()
        gate.complete(AccountPinChange(listOf("a")))
        scope.runCurrent()
        assertEquals(0, guard.processHolds)
        assertTrue(rotations.pending.value)
        assertNull(outcome)
    }

    @Test
    fun aChangeCutByLeavingTheAppLocksWhenItEnds() {
        foreground()
        val gate = CompletableDeferred<AccountPinChange>()
        changeBehaviour = { gate.await() }
        change()
        guard.onStop(owner)
        scope.runCurrent()
        val locksBefore = session.locks
        gate.complete(AccountPinChange(emptyList()))
        scope.runCurrent()
        assertTrue(session.locks >= locksBefore)
        assertEquals(SessionState.LOCKED, session.state.value)
        assertEquals(0, guard.processHolds)
    }

    @Test
    fun thePinsAreZeroedOnSuccessAndOnFailure() {
        foreground()
        val pin = "395172".toCharArray()
        turnOn(pin)
        assertTrue(pin.all { it == '\u0000' })
        val current = "482913".toCharArray()
        val new = "395172".toCharArray()
        changeBehaviour = { throw IllegalStateException() }
        change(current, new)
        assertTrue(current.all { it == '\u0000' } && new.all { it == '\u0000' })
    }

    @Test
    fun everyCoreFailureIsTyped() {
        foreground()
        fun failure(error: Exception): AccountSecurityOutcome? {
            changeBehaviour = { throw error }
            return change()()
        }
        assertEquals(AccountSecurityOutcome.PhraseMismatch, failure(PhraseMismatchException()))
        assertEquals(AccountSecurityOutcome.Offline, failure(NetworkError("/x")))
        assertEquals(AccountSecurityOutcome.RateLimited(45), failure(ApiError(ErrorCodes.TOO_MANY_REQUESTS, 429, "/x", retryAfterSeconds = 45)))
        assertEquals(AccountSecurityOutcome.PinRefused, failure(ApiError(ErrorCodes.INVALID_CREDENTIALS, 401, "/x")))
        assertEquals(AccountSecurityOutcome.Failed, failure(ApiError(ErrorCodes.INTERNAL_ERROR, 500, "/x")))
        assertEquals(AccountSecurityOutcome.ChainProblem, failure(ChainNotVerifiedException(IllegalStateException())))
        assertEquals(AccountSecurityOutcome.PhoneLocked, failure(DeviceVaultUnavailableException(IllegalStateException())))
        assertEquals(AccountSecurityOutcome.Failed, failure(IllegalStateException("private")))
        assertFalse(rotations.pending.value)
        assertEquals(0, paranoidReads)
    }

    @Test
    fun aTurnOnThatFailedAfterTheAccountBecameParanoidIsUnfinished() {
        foreground()
        turnOnBehaviour = { throw NetworkError("/devices") }
        paranoidBehaviour = { true }
        assertEquals(AccountSecurityOutcome.ParanoidOnUnfinished, turnOn()())
        assertEquals(1, paranoidReads)
    }

    @Test
    fun aTurnOnThatFailedBeforeAnythingChangedKeepsItsOwnFailure() {
        foreground()
        turnOnBehaviour = { throw ApiError(ErrorCodes.INVALID_CREDENTIALS, 401, "/x") }
        paranoidBehaviour = { false }
        assertEquals(AccountSecurityOutcome.PinRefused, turnOn()())
        paranoidBehaviour = { throw NetworkError("/users/me") }
        turnOnBehaviour = { throw NetworkError("/devices") }
        assertEquals(AccountSecurityOutcome.Offline, turnOn()())
    }

    @Test
    fun aMismatchedPhraseIsNeverReadAsUnfinished() {
        foreground()
        turnOnBehaviour = { throw PhraseMismatchException() }
        paranoidBehaviour = { true }
        assertEquals(AccountSecurityOutcome.PhraseMismatch, turnOn()())
        assertEquals(0, paranoidReads)
    }

    @Test
    fun aPinChangeIsNeverReadAsUnfinished() {
        foreground()
        changeBehaviour = { throw NetworkError("/devices") }
        paranoidBehaviour = { true }
        assertEquals(AccountSecurityOutcome.Offline, change()())
        assertEquals(0, paranoidReads)
    }

    @Test
    fun theAccountModeIsReadAsStandardParanoidOrUnknown() {
        var mode: AccountMode? = AccountMode.Standard
        suspend fun read() {
            mode = services.accountMode()
        }
        paranoidBehaviour = { true }
        scope.launch { read() }
        scope.runCurrent()
        assertEquals(AccountMode.Paranoid, mode)
        paranoidBehaviour = { false }
        scope.launch { read() }
        scope.runCurrent()
        assertEquals(AccountMode.Standard, mode)
        paranoidBehaviour = { throw NetworkError("/users/me") }
        scope.launch { read() }
        scope.runCurrent()
        assertNull(mode)
    }
}
