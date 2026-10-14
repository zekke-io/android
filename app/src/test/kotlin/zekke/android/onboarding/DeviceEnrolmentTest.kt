package zekke.android.onboarding

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.onboarding.DeviceEnrolmentException
import zekke.android.feature.onboarding.DeviceEnrolmentFailure
import zekke.android.feature.onboarding.DeviceRemoval
import zekke.android.feature.onboarding.EnrolmentDevice
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.core.account.DeviceSummary
import zekke.core.account.DevicesToRemove
import zekke.core.account.EnrolmentResult
import zekke.core.account.NoAccountForPhraseException
import zekke.core.account.ParanoidPinRequiredException
import zekke.core.account.TooManyDevicesException
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.auth.AuthEndpoint
import zekke.core.auth.AuthRejectedException
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeviceEnrolmentTest {
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
    private val receivedRemovals = mutableListOf<DevicesToRemove>()
    private var behaviour: suspend () -> EnrolmentResult = { EnrolmentResult(false, emptyList()) }

    private val enrolment = DeviceEnrolment(
        guard,
        rotations,
        { _, _, removal ->
            receivedRemovals += removal
            behaviour()
        },
        dispatcher,
    )

    private fun foreground() {
        guard.onStart(owner)
        scope.runCurrent()
    }

    private fun run(
        removal: DeviceRemoval = DeviceRemoval.None,
        words: List<CharArray> = listOf("abandon".toCharArray()),
        pin: CharArray = "395172".toCharArray(),
    ): Throwable? {
        var thrown: Throwable? = null
        scope.launch {
            try {
                enrolment.enrolDevice(words, pin, removal)
            } catch (error: Throwable) {
                thrown = error
            }
        }
        scope.runCurrent()
        return thrown
    }

    private fun failureOf(error: Exception): DeviceEnrolmentFailure {
        behaviour = { throw error }
        return (run() as DeviceEnrolmentException).failure
    }

    @Test
    fun anEnrolmentThatRemovedDevicesAsksForTheReWrap() {
        foreground()
        behaviour = { EnrolmentResult(false, listOf("old-device")) }
        run(DeviceRemoval.All)
        assertTrue(rotations.pending.value)
        assertEquals(listOf<DevicesToRemove>(DevicesToRemove.All), receivedRemovals)
    }

    @Test
    fun anEnrolmentThatRemovedNothingAsksForNothing() {
        foreground()
        run()
        assertFalse(rotations.pending.value)
        assertEquals(listOf<DevicesToRemove>(DevicesToRemove.None), receivedRemovals)
    }

    @Test
    fun theChosenDevicesReachTheCoreAsAList() {
        foreground()
        run(DeviceRemoval.Listed(listOf("a", "b")))
        assertEquals(listOf<DevicesToRemove>(DevicesToRemove.Listed(listOf("a", "b"))), receivedRemovals)
    }

    @Test
    fun thePhraseAndThePinAreZeroedOnSuccessAndOnFailure() {
        foreground()
        val words = listOf("abandon".toCharArray(), "about".toCharArray())
        val pin = "395172".toCharArray()
        run(words = words, pin = pin)
        assertTrue(words.all { word -> word.all { it == '\u0000' } })
        assertTrue(pin.all { it == '\u0000' })

        val failingWords = listOf("abandon".toCharArray())
        val failingPin = "395172".toCharArray()
        behaviour = { throw IllegalStateException() }
        run(words = failingWords, pin = failingPin)
        assertTrue(failingWords.all { word -> word.all { it == '\u0000' } })
        assertTrue(failingPin.all { it == '\u0000' })
    }

    @Test
    fun everyCoreFailureIsTyped() {
        foreground()
        val devices = listOf(DeviceSummary("11111111", "full", "2026-10-03T10:00:00Z"))
        assertEquals(
            DeviceEnrolmentFailure.TooManyDevices(listOf(EnrolmentDevice("11111111", "full", "2026-10-03T10:00:00Z"))),
            failureOf(TooManyDevicesException(devices)),
        )
        assertEquals(DeviceEnrolmentFailure.Refused, failureOf(AuthRejectedException(AuthEndpoint.ENROL, "private")))
        assertEquals(DeviceEnrolmentFailure.NoAccount, failureOf(NoAccountForPhraseException()))
        assertEquals(DeviceEnrolmentFailure.Refused, failureOf(ParanoidPinRequiredException()))
        assertEquals(DeviceEnrolmentFailure.Offline, failureOf(NetworkError("/devices/enrol")))
        assertEquals(DeviceEnrolmentFailure.RateLimited(30), failureOf(ApiError("TOO_MANY_REQUESTS", 429, "/devices/enrol", retryAfterSeconds = 30)))
        assertEquals(DeviceEnrolmentFailure.Failed, failureOf(ApiError("INTERNAL_ERROR", 500, "/devices/enrol")))
        assertEquals(DeviceEnrolmentFailure.Failed, failureOf(IllegalStateException("private")))
        assertFalse(rotations.pending.value)
    }

    @Test
    fun leavingTheAppDuringTheEnrolmentLocksAsSoonAsItEnds() {
        run()
        assertEquals(1, session.locks)
    }

    @Test
    fun anEnrolmentEndingInTheForegroundDoesNotLock() {
        foreground()
        run()
        assertEquals(0, session.locks)
    }

    @Test
    fun cancellationPropagatesAndStillZeroesTheSecrets() {
        foreground()
        val words = listOf("abandon".toCharArray())
        val pin = "395172".toCharArray()
        behaviour = { throw CancellationException("cancelled") }
        assertTrue(run(words = words, pin = pin) is CancellationException)
        assertTrue(words.all { word -> word.all { it == '\u0000' } })
        assertTrue(pin.all { it == '\u0000' })
        assertFalse(rotations.pending.value)
    }
}
