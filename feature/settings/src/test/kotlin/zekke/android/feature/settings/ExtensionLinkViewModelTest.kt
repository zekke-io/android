package zekke.android.feature.settings

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.android.ui.components.ItemFailure

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionLinkViewModelTest {
    private val scheduler = TestCoroutineScheduler()
    private val services = FakeExtensionLinkServices()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(fake: FakeExtensionLinkServices = services): ExtensionLinkViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { ExtensionLinkViewModel(fake, 2000) { scheduler.currentTime } } },
    )[ExtensionLinkViewModel::class]

    private fun ExtensionLinkViewModel.step() = state.value.step

    private fun started(): ExtensionLinkViewModel = model().also {
        it.start()
        scheduler.runCurrent()
    }

    private fun claimed(): ExtensionLinkViewModel = started().also {
        services.pollResults.add(PairingPoll.Claimed("123 456"))
        scheduler.advanceTimeBy(2000)
        scheduler.runCurrent()
    }

    @Test
    fun aDeviceThatCannotLinkOffersNothing() {
        val limited = FakeExtensionLinkServices(canLink = false)
        val model = model(limited)
        model.start()
        scheduler.runCurrent()
        assertEquals(LinkStep.Idle, model.step())
        assertEquals(0, limited.opens)
    }

    @Test
    fun startingShowsTheCodeAndTheTimeLeft() {
        val model = started()
        assertEquals(LinkStep.Waiting("pairing-1", "ABCD-2345", 300), model.step())
    }

    @Test
    fun aFailedOpenGoesBackWithTheFailure() {
        services.opened = PairingOpen.Failed(ItemFailure.Offline)
        val model = started()
        assertEquals(LinkStep.Idle, model.step())
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        model.dismissFailure()
        assertNull(model.state.value.failure)
    }

    @Test
    fun theCountdownFollowsTheClockAsTheCodeIsPolled() {
        val model = started()
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        assertEquals(290L, (model.step() as LinkStep.Waiting).secondsLeft)
        assertTrue(services.polls.size >= 4)
    }

    @Test
    fun aClaimMovesToTheComparisonWithTheNumber() {
        val model = claimed()
        assertEquals("123 456", (model.step() as LinkStep.Comparing).fingerprint)
    }

    @Test
    fun theCodeExpiringEndsTheFlow() {
        val model = started()
        scheduler.advanceTimeBy(301_000)
        scheduler.runCurrent()
        assertEquals(LinkStep.Ended(LinkEnd.Expired), model.step())
    }

    @Test
    fun theServerSayingExpiredCancelledOrLinkedEndsIt() {
        listOf(
            PairingPoll.Expired to LinkStep.Ended(LinkEnd.Expired),
            PairingPoll.Cancelled to LinkStep.Ended(LinkEnd.Cancelled),
            PairingPoll.Linked to LinkStep.Linked,
        ).forEach { (poll, step) ->
            val model = ViewModelProvider.create(
                ViewModelStore(),
                viewModelFactory { initializer { ExtensionLinkViewModel(services, 2000) { scheduler.currentTime } } },
            )[ExtensionLinkViewModel::class]
            model.start()
            scheduler.runCurrent()
            services.pollResults.add(poll)
            scheduler.advanceTimeBy(2000)
            scheduler.runCurrent()
            assertEquals(step, model.step())
        }
    }

    @Test
    fun aFailedPollShowsTheFailureAndKeepsWaiting() {
        val model = started()
        services.pollResults.add(PairingPoll.Failed(ItemFailure.Offline))
        scheduler.advanceTimeBy(2000)
        scheduler.runCurrent()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertTrue(model.step() is LinkStep.Waiting)
    }

    @Test
    fun cancellingTheWaitCancelsTheCodeAndStopsPolling() {
        val model = started()
        model.cancel()
        scheduler.runCurrent()
        assertEquals(listOf("pairing-1"), services.cancelled)
        assertEquals(LinkStep.Ended(LinkEnd.Cancelled), model.step())
        val polls = services.polls.size
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        assertEquals(polls, services.polls.size)
    }

    @Test
    fun matchingLinksWithTheNameAndSaysSo() {
        val model = claimed()
        model.nameChanged("  Work laptop ")
        model.confirm()
        scheduler.runCurrent()
        assertEquals(listOf("pairing-1" to "Work laptop"), services.linked)
        assertEquals(LinkStep.Linked, model.step())
        assertEquals("", model.state.value.name)
    }

    @Test
    fun linkingWaitsForTheResultAndTakesOneConfirmationOnly() {
        val model = claimed()
        services.gate = CompletableDeferred()
        model.confirm()
        model.confirm()
        scheduler.runCurrent()
        assertEquals(LinkStep.Linking, model.step())
        assertEquals(1, services.linked.size)
        services.gate!!.complete(Unit)
        scheduler.runCurrent()
        assertEquals(LinkStep.Linked, model.step())
    }

    @Test
    fun aFailedLinkGoesBackWithTheFailureAndOffersANewCode() {
        services.linkResult = PairingWrite.Failed(ItemFailure.Failed)
        val model = claimed()
        model.confirm()
        scheduler.runCurrent()
        assertEquals(LinkStep.Idle, model.step())
        assertEquals(ItemFailure.Failed, model.state.value.failure)
    }

    @Test
    fun aMismatchCancelsTheCodeAndOffersNoRetryOnIt() {
        val model = claimed()
        model.refuse()
        scheduler.runCurrent()
        assertEquals(listOf("pairing-1"), services.cancelled)
        assertEquals(LinkStep.Refused, model.step())
        model.confirm()
        scheduler.runCurrent()
        assertTrue(services.linked.isEmpty())
        val polls = services.polls.size
        scheduler.advanceTimeBy(10_000)
        scheduler.runCurrent()
        assertEquals(polls, services.polls.size)
    }

    @Test
    fun startingAgainGetsAFreshCode() {
        val model = claimed()
        model.refuse()
        scheduler.runCurrent()
        services.opened = PairingOpen.Opened("pairing-2", "WXYZ-6789", scheduler.currentTime + 300_000)
        model.start()
        scheduler.runCurrent()
        assertEquals("pairing-2", (model.step() as LinkStep.Waiting).id)
    }

    @Test
    fun startingIsIgnoredWhileACodeIsOpen() {
        val model = started()
        model.start()
        scheduler.runCurrent()
        assertEquals(1, services.opens)
    }

    @Test
    fun theNameIsCapped() {
        val model = model()
        model.nameChanged("x".repeat(100))
        assertEquals(MAX_EXTENSION_NAME_LENGTH, model.state.value.name.length)
    }

    @Test
    fun anUnexpectedExceptionBecomesAFailure() {
        val broken = object : ExtensionLinkServices {
            override val canLink = true
            override val canNameExtension = false
            override suspend fun open(): PairingOpen = throw IllegalStateException()
            override suspend fun poll(id: String): PairingPoll = PairingPoll.Waiting
            override suspend fun link(id: String, name: String): PairingWrite = PairingWrite.Done
            override suspend fun cancel(id: String): PairingWrite = PairingWrite.Done
        }
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { ExtensionLinkViewModel(broken, 2000) { scheduler.currentTime } } },
        )[ExtensionLinkViewModel::class]
        model.start()
        scheduler.runCurrent()
        assertEquals(ItemFailure.Failed, model.state.value.failure)
        assertEquals(LinkStep.Idle, model.step())
    }
}
