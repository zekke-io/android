package zekke.android.clients

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zekke.android.feature.unlock.ClientVersionStatus
import zekke.core.clients.ClientPolicy
import zekke.core.clients.VersionNotice
import zekke.core.clients.compareVersions
import zekke.core.clients.versionNotice
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

const val POLICY_MAX_AGE_MILLIS = 15 * 60 * 1000L

class ClientPolicyMonitor(
    private val version: String,
    private val fetchPolicy: suspend () -> ClientPolicy?,
    private val upgradeRequired: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    private val onUpgradeRequired: () -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val maxAgeMillis: Long = POLICY_MAX_AGE_MILLIS,
) : DefaultLifecycleObserver {
    private val mutableStatus = MutableStateFlow<ClientVersionStatus>(ClientVersionStatus.Current)
    private var policy: ClientPolicy? = null
    private var fetchedAt: Long? = null
    private var fetching: Job? = null
    private var dismissedLatest: String? = null
    private var blockRaised = false
    private var readAgain = false

    val status: StateFlow<ClientVersionStatus> = mutableStatus.asStateFlow()

    init {
        scope.launch {
            upgradeRequired.collect { raised ->
                if (raised) {
                    recompute()
                    if (fetching?.isActive == true) readAgain = true else refresh()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val last = fetchedAt
        if (last == null || now() - last >= maxAgeMillis) refresh()
    }

    fun refresh() {
        if (fetching?.isActive == true) return
        fetching = scope.launch {
            do {
                readAgain = false
                val fetched = try {
                    fetchPolicy()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                policy = fetched
                fetchedAt = now()
                recompute()
            } while (readAgain)
        }
    }

    fun dismissUpdateAvailable() {
        val current = mutableStatus.value
        if (current is ClientVersionStatus.UpdateAvailable) {
            dismissedLatest = current.latest
            recompute()
        }
    }

    private fun recompute() {
        val known = policy
        val notice = if (known == null) VersionNotice.Current else versionNotice(known, version)
        val next = when {
            upgradeRequired.value || blockRaised || notice is VersionNotice.Required ->
                ClientVersionStatus.UpgradeRequired(known?.latest?.takeIf(::isNewerThanThisVersion))
            notice is VersionNotice.Deprecated -> {
                val ends = endsAtEpochMillis(notice.deprecationEnds)
                if (ends == null) available(notice.latest) else ClientVersionStatus.Deprecated(notice.latest, ends)
            }
            notice is VersionNotice.Available -> available(notice.latest)
            else -> ClientVersionStatus.Current
        }
        mutableStatus.value = next
        if (next is ClientVersionStatus.UpgradeRequired && !blockRaised) {
            blockRaised = true
            onUpgradeRequired()
        }
    }

    private fun isNewerThanThisVersion(latest: String): Boolean = (compareVersions(latest, version) ?: 0) > 0

    private fun available(latest: String): ClientVersionStatus =
        if (dismissedLatest == latest) ClientVersionStatus.Current else ClientVersionStatus.UpdateAvailable(latest)

    private fun endsAtEpochMillis(value: String): Long? = try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
