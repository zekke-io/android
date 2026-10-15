package zekke.android.sync

import kotlinx.coroutines.flow.StateFlow
import zekke.core.feed.FeedScope
import zekke.core.session.SessionKeystore
import zekke.core.session.SessionState

interface SyncSession {
    val state: StateFlow<SessionState>

    fun userAddress(): String

    fun heldFeedScopes(): List<FeedScope>
}

class KeystoreSyncSession(private val keystore: SessionKeystore) : SyncSession {
    override val state: StateFlow<SessionState> get() = keystore.state

    override fun userAddress(): String = keystore.userAddress

    override fun heldFeedScopes(): List<FeedScope> = FeedScope.entries.filter { keystore.holds(it.scope) }
}
