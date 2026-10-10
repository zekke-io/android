package zekke.android.session

import kotlinx.coroutines.flow.StateFlow
import zekke.core.session.SessionKeystore
import zekke.core.session.SessionState

interface SessionControl {
    val state: StateFlow<SessionState>

    fun lock()

    fun touch()
}

class KeystoreSessionControl(private val session: SessionKeystore) : SessionControl {
    override val state: StateFlow<SessionState> get() = session.state

    override fun lock() = session.lock()

    override fun touch() = session.touch()
}

fun interface ProcessTerminator {
    fun terminate()
}

object KillThisProcess : ProcessTerminator {
    override fun terminate() = android.os.Process.killProcess(android.os.Process.myPid())
}
