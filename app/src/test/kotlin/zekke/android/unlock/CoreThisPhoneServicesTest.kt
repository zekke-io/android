package zekke.android.unlock

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.unlock.RemovalOutcome
import zekke.android.platform.DeviceVaultUnavailableException
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.DeviceRemovedException
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CoreThisPhoneServicesTest {
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
    private var terminations = 0
    private val guard = SessionGuard(session, scope, { terminations++ })
    private val events = ThisPhoneEvents()
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private var removeCalls = 0
    private var forgetCalls = 0
    private var removeBehaviour: suspend () -> Unit = {}
    private var forgetBehaviour: suspend () -> Unit = {}

    private val services = CoreThisPhoneServices(
        guard,
        events,
        { removeCalls++; removeBehaviour() },
        { forgetCalls++; forgetBehaviour() },
        main = dispatcher,
        work = dispatcher,
    )

    private fun foreground() {
        guard.onStart(owner)
        scope.runCurrent()
    }

    private fun background() {
        guard.onStop(owner)
        scope.runCurrent()
    }

    private fun run(): () -> RemovalOutcome? {
        var outcome: RemovalOutcome? = null
        scope.launch { outcome = services.removeThisPhone() }
        scope.runCurrent()
        return { outcome }
    }

    @Test
    fun theRemovalHoldsTheProcessUntilItEnds() {
        foreground()
        val gate = CompletableDeferred<Unit>()
        removeBehaviour = { gate.await() }
        val outcome = run()
        assertEquals(1, guard.processHolds)
        assertEquals(null, outcome())
        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(0, guard.processHolds)
        assertEquals(RemovalOutcome.Removed, outcome())
        assertEquals(1, events.removals.value)
    }

    @Test
    fun aRemovalCutByLeavingTheAppEndsTheProcessOnlyWhenItFinishes() {
        foreground()
        val gate = CompletableDeferred<Unit>()
        removeBehaviour = { gate.await() }
        run()
        background()
        assertEquals(SessionState.LOCKED, session.state.value)
        assertEquals(0, terminations)
        gate.complete(Unit)
        scope.runCurrent()
        assertTrue(terminations >= 1)
        assertEquals(0, guard.processHolds)
    }

    @Test
    fun cancellingTheCallerNeitherCutsTheRemovalNorReleasesEarly() {
        foreground()
        val gate = CompletableDeferred<Unit>()
        removeBehaviour = { gate.await() }
        val job = scope.launch { services.removeThisPhone() }
        scope.runCurrent()
        job.cancel()
        scope.runCurrent()
        assertEquals(1, guard.processHolds)
        assertEquals(0, events.removals.value)
        gate.complete(Unit)
        scope.runCurrent()
        assertEquals(0, guard.processHolds)
        assertEquals(1, events.removals.value)
    }

    @Test
    fun endingInTheBackgroundLocksAtOnce() {
        foreground()
        val gate = CompletableDeferred<Unit>()
        removeBehaviour = { gate.await() }
        run()
        background()
        val locksBefore = session.locks
        gate.completeExceptionally(NetworkError("x"))
        scope.runCurrent()
        assertTrue(session.locks > locksBefore)
        assertEquals(0, guard.processHolds)
    }

    @Test
    fun eachFailureIsTypedAndReleasesTheHold() {
        val failures: List<Pair<Exception, RemovalOutcome>> = listOf(
            NetworkError("x") to RemovalOutcome.Offline,
            ApiError("TOO_MANY_REQUESTS", 429, "x", retryAfterSeconds = 30) to RemovalOutcome.RateLimited(30),
            ApiError("INVALID_BATCH", 400, "x") to RemovalOutcome.Refused,
            ChainNotVerifiedException(RuntimeException()) to RemovalOutcome.ChainProblem,
            DeviceVaultUnavailableException(RuntimeException()) to RemovalOutcome.PhoneLocked,
            ApiError("UPGRADE_REQUIRED", 426, "x") to RemovalOutcome.Failed,
            ApiError("SERVER", 500, "x") to RemovalOutcome.Failed,
            IllegalStateException() to RemovalOutcome.Failed,
        )
        foreground()
        for ((error, expected) in failures) {
            removeBehaviour = { throw error }
            val outcome = run()
            assertEquals(expected, outcome())
            assertEquals(0, guard.processHolds)
            assertEquals(0, events.removals.value)
        }
    }

    @Test
    fun aPhoneAlreadyRemovedIsForgottenHere() {
        foreground()
        removeBehaviour = { throw DeviceRemovedException() }
        val outcome = run()
        assertEquals(RemovalOutcome.AlreadyRemoved, outcome())
        assertEquals(1, forgetCalls)
        assertEquals(1, events.removals.value)
        assertEquals(0, guard.processHolds)
    }

    @Test
    fun aPhoneAlreadyRemovedWhoseForgettingFailsIsTypedAndNotCounted() {
        foreground()
        removeBehaviour = { throw DeviceRemovedException() }
        forgetBehaviour = { throw NetworkError("x") }
        val outcome = run()
        assertEquals(RemovalOutcome.Offline, outcome())
        assertEquals(0, events.removals.value)
        assertEquals(0, guard.processHolds)
    }

    @Test
    fun aPhoneAlreadyRemovedWhoseForgettingMeetsABrokenChainIsFailedAndReleasesTheHold() {
        foreground()
        removeBehaviour = { throw DeviceRemovedException() }
        forgetBehaviour = { throw ChainNotVerifiedException(RuntimeException()) }
        val outcome = run()
        assertEquals(RemovalOutcome.Failed, outcome())
        assertEquals(1, forgetCalls)
        assertEquals(0, events.removals.value)
        assertEquals(0, guard.processHolds)
    }
}
