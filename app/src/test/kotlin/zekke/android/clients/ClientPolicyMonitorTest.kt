package zekke.android.clients

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.unlock.ClientVersionStatus
import zekke.core.clients.ClientPolicy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClientPolicyMonitorTest {
    private val scope = TestScope()
    private val flag = MutableStateFlow(false)
    private var clock = 0L
    private var fetches = 0
    private var blocks = 0
    private var next: suspend () -> ClientPolicy? = { null }
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }

    private val monitor = ClientPolicyMonitor(
        version = "0.1.0",
        fetchPolicy = { fetches++; next() },
        upgradeRequired = flag,
        scope = scope,
        onUpgradeRequired = { blocks++ },
        now = { clock },
    )

    private fun policy(
        minSupported: String = "0.1.0",
        latest: String = "0.1.0",
        deprecatedBelow: String? = null,
        deprecationEnds: String? = null,
    ) = ClientPolicy("android", minSupported, latest, deprecatedBelow, deprecationEnds)

    private fun refreshed(): ClientVersionStatus {
        monitor.refresh()
        scope.runCurrent()
        return monitor.status.value
    }

    @Test
    fun noPolicyIsCurrent() {
        next = { null }
        assertEquals(ClientVersionStatus.Current, refreshed())
        next = { throw IllegalStateException("down") }
        assertEquals(ClientVersionStatus.Current, refreshed())
        assertEquals(0, blocks)
    }

    @Test
    fun eachNoticeBecomesItsStatus() {
        next = { policy(latest = "0.2.0") }
        assertEquals(ClientVersionStatus.UpdateAvailable("0.2.0"), refreshed())

        next = { policy(latest = "0.3.0", deprecatedBelow = "0.2.0", deprecationEnds = "2026-12-01T12:00:00Z") }
        assertEquals(ClientVersionStatus.Deprecated("0.3.0", 1_796_126_400_000L), refreshed())

        next = { policy(latest = "0.3.0", deprecatedBelow = "0.2.0", deprecationEnds = "soon") }
        assertEquals(ClientVersionStatus.UpdateAvailable("0.3.0"), refreshed())
        assertEquals(0, blocks)

        next = { policy(minSupported = "0.2.0", latest = "0.2.0") }
        assertEquals(ClientVersionStatus.UpgradeRequired("0.2.0"), refreshed())
        assertEquals(1, blocks)
    }

    @Test
    fun a426FromAnyRequestBlocksAndReadsThePolicyAgain() {
        scope.runCurrent()
        assertEquals(ClientVersionStatus.Current, monitor.status.value)
        val answer = CompletableDeferred<ClientPolicy?>()
        next = { answer.await() }
        flag.value = true
        scope.runCurrent()
        assertEquals(ClientVersionStatus.UpgradeRequired(null), monitor.status.value)
        assertEquals(1, fetches)
        assertEquals(1, blocks)
        answer.complete(policy(minSupported = "0.2.0", latest = "0.2.0"))
        scope.runCurrent()
        assertEquals(ClientVersionStatus.UpgradeRequired("0.2.0"), monitor.status.value)
        assertEquals(1, blocks)
    }

    @Test
    fun theFirstStartReadsThePolicyAndALaterOneOnlyWhenStale() {
        monitor.onStart(owner)
        scope.runCurrent()
        assertEquals(1, fetches)
        clock = 60_000
        monitor.onStart(owner)
        scope.runCurrent()
        assertEquals(1, fetches)
        clock = 16 * 60_000L
        monitor.onStart(owner)
        scope.runCurrent()
        assertEquals(2, fetches)
    }

    @Test
    fun aDismissedUpdateStaysDismissedUntilANewerOne() {
        next = { policy(latest = "0.2.0") }
        assertEquals(ClientVersionStatus.UpdateAvailable("0.2.0"), refreshed())
        monitor.dismissUpdateAvailable()
        assertEquals(ClientVersionStatus.Current, monitor.status.value)
        assertEquals(ClientVersionStatus.Current, refreshed())
        next = { policy(latest = "0.3.0") }
        assertEquals(ClientVersionStatus.UpdateAvailable("0.3.0"), refreshed())
    }

    @Test
    fun oneFetchAtATime() {
        val answer = CompletableDeferred<ClientPolicy?>()
        next = { answer.await() }
        monitor.refresh()
        monitor.refresh()
        scope.runCurrent()
        assertEquals(1, fetches)
        answer.complete(null)
        scope.runCurrent()
        monitor.refresh()
        scope.runCurrent()
        assertEquals(2, fetches)
    }

    @Test
    fun a426DuringAReadReadsAgainAndNeverNamesAVersionNoNewerThanThisOne() {
        scope.runCurrent()
        val first = CompletableDeferred<ClientPolicy?>()
        val second = CompletableDeferred<ClientPolicy?>()
        next = { first.await() }
        monitor.refresh()
        scope.runCurrent()
        flag.value = true
        scope.runCurrent()
        assertEquals(1, fetches)
        next = { second.await() }
        first.complete(policy(latest = "0.1.0"))
        scope.runCurrent()
        assertEquals(2, fetches)
        assertEquals(ClientVersionStatus.UpgradeRequired(null), monitor.status.value)
        second.complete(policy(minSupported = "0.2.0", latest = "0.2.0"))
        scope.runCurrent()
        assertEquals(ClientVersionStatus.UpgradeRequired("0.2.0"), monitor.status.value)
        assertEquals(2, fetches)
    }

    @Test
    fun aBlockStaysRaisedEvenWhenALaterReadFails() {
        next = { policy(minSupported = "0.2.0", latest = "0.2.0") }
        assertEquals(ClientVersionStatus.UpgradeRequired("0.2.0"), refreshed())
        next = { null }
        assertEquals(ClientVersionStatus.UpgradeRequired(null), refreshed())
        assertEquals(1, blocks)
    }
}
