package zekke.android.session

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zekke.core.session.SessionState

const val PICKER_MAX_MILLIS = 3 * 60 * 1000L

class SessionGuard(
    private val session: SessionControl,
    private val scope: CoroutineScope,
    private val terminator: ProcessTerminator,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val pickerMaxMillis: Long = PICKER_MAX_MILLIS,
) : DefaultLifecycleObserver {
    private var foreground = false
    private var pickerDeadline: Long? = null
    private var pendingLock: Job? = null
    private var transfers = 0
    private var holds = 0
    @Volatile private var currentLockVersion = 0L
    private val lockListeners = mutableListOf<() -> Unit>()

    val isInForeground: Boolean get() = foreground

    val isPickerOpen: Boolean get() = pickerDeadline?.let { now() < it } ?: false

    val runningTransfers: Int get() = transfers

    val processHolds: Int get() = holds

    val lockVersion: Long get() = currentLockVersion

    init {
        scope.launch {
            var previous = session.state.value
            session.state.collect { state ->
                if (previous == SessionState.UNLOCKED && state != SessionState.UNLOCKED) {
                    currentLockVersion++
                    lockListeners.forEach { it() }
                    endIfIdle()
                }
                previous = state
            }
        }
    }

    fun onLocked(listener: () -> Unit): () -> Unit {
        lockListeners += listener
        return { lockListeners -= listener }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        pickerDeadline = null
        pendingLock?.cancel()
        pendingLock = null
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        val deadline = pickerDeadline
        if (deadline != null && now() < deadline) {
            pendingLock = scope.launch {
                delay(deadline - now())
                pickerDeadline = null
                if (!foreground) lockAndEnd()
            }
        } else {
            pickerDeadline = null
            lockAndEnd()
        }
    }

    fun screenTurnedOff() {
        pickerDeadline = null
        pendingLock?.cancel()
        pendingLock = null
        lockAndEnd()
    }

    fun userActivity() = session.touch()

    fun lockNow() = lockAndEnd()

    fun pickerWillOpen() {
        pickerDeadline = now() + pickerMaxMillis
    }

    fun pickerReturned() {
        pickerDeadline = null
    }

    fun transferStarted() {
        transfers++
    }

    fun transferFinished() {
        transfers = (transfers - 1).coerceAtLeast(0)
        if (session.state.value != SessionState.UNLOCKED) endIfIdle()
    }

    fun holdProcess(): () -> Unit {
        holds++
        var released = false
        return {
            if (!released) {
                released = true
                holds--
                if (session.state.value != SessionState.UNLOCKED) endIfIdle()
            }
        }
    }

    private fun lockAndEnd() {
        currentLockVersion++
        session.lock()
        endIfIdle()
    }

    private fun endIfIdle() {
        if (!foreground && transfers == 0 && holds == 0) terminator.terminate()
    }
}
