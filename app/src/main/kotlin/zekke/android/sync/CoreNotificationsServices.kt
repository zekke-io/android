package zekke.android.sync

import kotlinx.coroutines.CancellationException
import zekke.android.feature.settings.NotificationsLoad
import zekke.android.feature.settings.NotificationsServices
import zekke.core.api.ApiError
import zekke.core.api.NetworkError
import zekke.core.api.ZekkeApi
import zekke.core.notifications.listNotifications
import zekke.core.notifications.markNotificationsRead

class CoreNotificationsServices(private val api: ZekkeApi) : NotificationsServices {
    override suspend fun load(cursor: String?): NotificationsLoad = try {
        NotificationsLoad.Loaded(listNotifications(api, cursor = cursor))
    } catch (error: CancellationException) {
        throw error
    } catch (_: NetworkError) {
        NotificationsLoad.Offline
    } catch (error: ApiError) {
        if (error.isRateLimited) NotificationsLoad.RateLimited(error.retryAfterSeconds) else NotificationsLoad.Failed
    } catch (_: Exception) {
        NotificationsLoad.Failed
    }

    override suspend fun markRead(ids: List<String>) = markNotificationsRead(api, ids)
}
