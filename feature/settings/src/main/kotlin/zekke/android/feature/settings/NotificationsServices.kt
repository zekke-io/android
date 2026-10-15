package zekke.android.feature.settings

import zekke.core.notifications.NotificationPage

sealed interface NotificationsLoad {
    data class Loaded(val page: NotificationPage) : NotificationsLoad

    data object Offline : NotificationsLoad

    data class RateLimited(val retryAfterSeconds: Long?) : NotificationsLoad

    data object Failed : NotificationsLoad
}

interface NotificationsServices {
    suspend fun load(cursor: String?): NotificationsLoad

    suspend fun markRead(ids: List<String>)
}
