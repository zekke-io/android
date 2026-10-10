package zekke.android.session

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import zekke.core.session.SessionState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionGuardTest {
    private class FakeSession : SessionControl {
        override val state = MutableStateFlow(SessionState.UNLOCKED)
        var locks = 0
        var touches = 0

        override fun lock() {
            locks++
            state.value = SessionState.LOCKED
        }

        override fun touch() {
            touches++
        }
    }

    private val scope = TestScope()
    private val session = FakeSession()
    private var clock = 0L
    private var terminations = 0
    private val guard = SessionGuard(session, scope, { terminations++ }, now = { clock }, pickerMaxMillis = 60_000)
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private fun foreground() {
        guard.onStart(owner)
        scope.runCurrent()
    }

    private fun background() {
        guard.onStop(owner)
        scope.runCurrent()
    }

    @Test
    fun leavingTheForegroundLocksAndEndsTheProcess() {
        foreground()
        background()
        assertEquals(1, session.locks)
        assertEquals(SessionState.LOCKED, session.state.value)
        assertTrue(terminations >= 1)
    }

    @Test
    fun theScreenTurningOffLocksEvenWithAPickerOpen() {
        foreground()
        guard.pickerWillOpen()
        guard.screenTurnedOff()
        scope.runCurrent()
        assertEquals(1, session.locks)
        assertFalse(guard.isPickerOpen)
    }

    @Test
    fun aPickerTheAppOpenedDoesNotLockAndItsReturnClearsTheException() {
        foreground()
        guard.pickerWillOpen()
        background()
        assertEquals(0, session.locks)
        assertEquals(0, terminations)
        clock += 20_000
        scope.advanceTimeBy(20_000)
        foreground()
        assertFalse(guard.isPickerOpen)
        scope.advanceTimeBy(120_000)
        scope.runCurrent()
        assertEquals(0, session.locks)
        background()
        assertEquals(1, session.locks)
    }

    @Test
    fun aPickerLeftOpenPastItsMaximumLocks() {
        foreground()
        guard.pickerWillOpen()
        background()
        clock += 60_000
        scope.advanceTimeBy(60_001)
        scope.runCurrent()
        assertEquals(1, session.locks)
        assertTrue(terminations >= 1)
    }

    @Test
    fun aRunningUploadKeepsTheProcessUntilItFinishes() {
        foreground()
        guard.transferStarted()
        background()
        assertEquals(1, session.locks)
        assertEquals(0, terminations)
        guard.transferFinished()
        assertEquals(1, terminations)
    }

    @Test
    fun anIdleLockInTheForegroundKeepsTheProcessAndClearsTheScreens() {
        var cleared = 0
        guard.onLocked { cleared++ }
        foreground()
        session.state.value = SessionState.LOCKED
        scope.runCurrent()
        assertEquals(1, cleared)
        assertEquals(0, terminations)
    }

    @Test
    fun anIdleLockInTheBackgroundEndsTheProcess() {
        foreground()
        guard.pickerWillOpen()
        background()
        session.state.value = SessionState.LOCKED
        scope.runCurrent()
        assertEquals(1, terminations)
    }

    @Test
    fun userActivityPostponesTheIdleLock() {
        guard.userActivity()
        guard.userActivity()
        assertEquals(2, session.touches)
    }

    @Test
    fun theScreenOffBroadcastReachesTheGuard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        foreground()
        registerScreenOffLock(context, guard)
        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        scope.runCurrent()
        assertEquals(1, session.locks)
    }
}
