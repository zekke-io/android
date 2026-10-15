package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.core.notifications.NotificationRecord

enum class NotificationsProblem { Offline, RateLimited, Failed }

data class NotificationsState(
    val records: List<NotificationRecord> = emptyList(),
    val unreadIds: Set<String> = emptySet(),
    val unreadCount: Int = 0,
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val problem: NotificationsProblem? = null,
    val retryAfterSeconds: Long? = null,
)

class NotificationsViewModel(
    private val services: NotificationsServices,
    private val onUnreadChanged: (Int) -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationsState())

    val state: StateFlow<NotificationsState> = mutableState.asStateFlow()

    fun open() {
        if (mutableState.value.loading) return
        load(cursor = null)
    }

    fun retry() {
        val current = mutableState.value
        if (current.loading) return
        load(if (current.loadedOnce) current.nextCursor else null)
    }

    fun showOlder() {
        val current = mutableState.value
        val cursor = current.nextCursor ?: return
        if (current.loading) return
        load(cursor)
    }

    private fun load(cursor: String?) {
        mutableState.update { it.copy(loading = true, problem = null, retryAfterSeconds = null) }
        viewModelScope.launch {
            val result = try {
                services.load(cursor)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                NotificationsLoad.Failed
            }
            when (result) {
                is NotificationsLoad.Loaded -> received(result, appending = cursor != null)
                NotificationsLoad.Offline -> failed(NotificationsProblem.Offline, null)
                is NotificationsLoad.RateLimited -> failed(NotificationsProblem.RateLimited, result.retryAfterSeconds)
                NotificationsLoad.Failed -> failed(NotificationsProblem.Failed, null)
            }
        }
    }

    private fun failed(problem: NotificationsProblem, retryAfterSeconds: Long?) {
        mutableState.update { it.copy(loading = false, problem = problem, retryAfterSeconds = retryAfterSeconds) }
    }

    private suspend fun received(result: NotificationsLoad.Loaded, appending: Boolean) {
        val page = result.page
        val newlyUnread = page.notifications.filter { !it.isRead }.map { it.id }
        mutableState.update {
            it.copy(
                records = if (appending) it.records + page.notifications else page.notifications,
                unreadIds = if (appending) it.unreadIds + newlyUnread else newlyUnread.toSet(),
                unreadCount = page.unreadCount,
                nextCursor = page.nextCursor,
                loading = false,
                loadedOnce = true,
            )
        }
        if (newlyUnread.isEmpty()) return
        try {
            services.markRead(newlyUnread)
            val remaining = (page.unreadCount - newlyUnread.size).coerceAtLeast(0)
            mutableState.update { it.copy(unreadCount = remaining) }
            onUnreadChanged(remaining)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }
}
