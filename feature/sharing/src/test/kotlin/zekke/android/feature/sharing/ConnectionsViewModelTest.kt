package zekke.android.feature.sharing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zekke.android.ui.components.ItemFailure

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {
    private val services = FakeSharingServices()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(): ConnectionsViewModel = ViewModelProvider.create(
        store,
        viewModelFactory { initializer { ConnectionsViewModel(services) } },
    )[ConnectionsViewModel::class].also { it.refresh() }

    private val ann = connection("1", "ann")
    private val bob = connection("2", "bob", ConnectionStanding.WaitingForMe)
    private val cat = connection("3", "cat", ConnectionStanding.WaitingForThem)

    @Test
    fun loadingShowsTheConnectionsAndChecksAndCatchesUpTheOnesThatCanBe() {
        services.items = listOf(ann, bob, cat)
        services.alarms = mapOf("1" to TrustAlarm.RootChanged, "3" to null)
        val model = model()
        assertTrue(model.state.value.loaded)
        assertEquals(listOf(ann, bob, cat), model.state.value.items)
        assertEquals(listOf(listOf("1", "3")), services.checked)
        assertEquals(listOf(listOf("1", "3")), services.caughtUp)
        assertEquals(TrustAlarm.RootChanged, model.state.value.alarms["1"])
    }

    @Test
    fun nothingIsLoadedUntilTheScreenAsks() {
        val idle = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { ConnectionsViewModel(services) } },
        )[ConnectionsViewModel::class]
        assertEquals(0, services.loads)
        assertFalse(idle.state.value.loaded)
    }

    @Test
    fun noConnectionAwaitingMeIsChecked() {
        services.items = listOf(bob)
        model()
        assertTrue(services.checked.isEmpty())
        assertTrue(services.caughtUp.isEmpty())
    }

    @Test
    fun aFailedLoadIsShownAndDismissed() {
        services.loadFailure = ItemFailure.Offline
        val model = model()
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        model.dismissFailure()
        assertNull(model.state.value.failure)
    }

    @Test
    fun aFailedTrustCheckShowsNothingAndACatchUpFailureIsSilent() {
        services.items = listOf(ann)
        services.checkFailure = IllegalStateException()
        services.catchUpFailure = IllegalStateException()
        val model = model()
        assertTrue(model.state.value.alarms.isEmpty())
        assertNull(model.state.value.failure)
    }

    @Test
    fun anOlderCheckNeverOverwritesANewerLoad() {
        services.items = listOf(ann)
        services.alarms = mapOf("1" to TrustAlarm.ProofInvalid)
        val model = model()
        services.alarms = mapOf("1" to null)
        model.refresh()
        assertNull(model.state.value.alarms["1"])
    }

    @Test
    fun invitingNeedsAUsernameAndOneCallAtATime() {
        val model = model()
        model.invite()
        assertTrue(services.invited.isEmpty())
        model.usernameChanged("  bob ")
        services.gate = CompletableDeferred()
        model.invite()
        assertEquals(listOf("bob"), services.invited)
        assertTrue(model.state.value.inviting)
        model.invite()
        assertEquals(1, services.invited.size)
        model.usernameChanged("other")
        assertEquals("  bob ", model.state.value.username)
        services.gate!!.complete(Unit)
        assertFalse(model.state.value.inviting)
        assertEquals("bob", model.state.value.invited)
        assertEquals("", model.state.value.username)
    }

    @Test
    fun aSentInvitationRefreshesTheList() {
        val model = model()
        val before = services.loads
        model.usernameChanged("bob")
        model.invite()
        assertEquals(before + 1, services.loads)
    }

    @Test
    fun everyInviteRefusalHasItsOwnProblem() {
        val model = model()
        val cases = listOf(
            InviteOutcome.UnknownUsername to InviteProblem.Unknown,
            InviteOutcome.AlreadyConnected to InviteProblem.Exists,
            InviteOutcome.NotTrusted(TrustAlarm.RootChanged) to InviteProblem.Refused(TrustAlarm.RootChanged),
            InviteOutcome.NotTrusted(null) to InviteProblem.Refused(null),
        )
        cases.forEach { (outcome, problem) ->
            services.inviteOutcome = outcome
            model.usernameChanged("bob")
            model.invite()
            assertEquals(problem, model.state.value.inviteProblem)
            assertFalse(model.state.value.inviting)
            assertEquals("bob", model.state.value.username)
            model.dismissInviteProblem()
            assertNull(model.state.value.inviteProblem)
        }
    }

    @Test
    fun typingClearsAnInviteProblemAndAFailureIsShown() {
        val model = model()
        services.inviteOutcome = InviteOutcome.UnknownUsername
        model.usernameChanged("bob")
        model.invite()
        model.usernameChanged("bo")
        assertNull(model.state.value.inviteProblem)
        services.inviteOutcome = InviteOutcome.Failed(ItemFailure.RateLimited(30))
        model.invite()
        assertEquals(ItemFailure.RateLimited(30), model.state.value.failure)
    }

    @Test
    fun theUsernameIsCapped() {
        val model = model()
        model.usernameChanged("a".repeat(100))
        assertEquals(MAX_USERNAME_LENGTH, model.state.value.username.length)
    }

    @Test
    fun reviewingOnlyOpensForAnInvitationAwaitingMe() {
        services.items = listOf(ann, bob, cat)
        val model = model()
        model.startReview(ann)
        model.startReview(cat)
        assertNull(model.state.value.review)
        model.startReview(bob)
        val review = model.state.value.review!!
        assertEquals("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF", review.theirs)
        assertEquals("1111-2222-3333-4444-5555-6666", review.mine)
        assertTrue(review.canAccept)
    }

    @Test
    fun theReviewWaitsForTheFingerprintBeforeAccepting() {
        services.items = listOf(bob)
        services.reviewGate = CompletableDeferred()
        val model = model()
        model.startReview(bob)
        assertTrue(model.state.value.review!!.loading)
        model.accept()
        assertTrue(services.accepted.isEmpty())
        services.reviewGate!!.complete(Unit)
        assertTrue(model.state.value.review!!.canAccept)
    }

    @Test
    fun anAlarmOnTheReviewBlocksAccepting() {
        services.items = listOf(bob)
        for (alarm in TrustAlarm.entries) {
            services.reviewResult = ReviewLoad.Ready("AAAA", "BBBB", alarm)
            val model = model()
            model.startReview(bob)
            assertFalse(model.state.value.review!!.canAccept)
            model.accept()
            assertTrue(services.accepted.isEmpty())
        }
    }

    @Test
    fun aReviewWithoutTheirFingerprintCannotBeAccepted() {
        services.items = listOf(bob)
        services.reviewResult = ReviewLoad.Ready(null, "BBBB", TrustAlarm.Unresolvable)
        val model = model()
        model.startReview(bob)
        assertFalse(model.state.value.review!!.canAccept)
    }

    @Test
    fun aReviewThatFailsToLoadClosesWithTheFailure() {
        services.items = listOf(bob)
        services.reviewResult = ReviewLoad.Failed(ItemFailure.Offline)
        val model = model()
        model.startReview(bob)
        assertNull(model.state.value.review)
        assertEquals(ItemFailure.Offline, model.state.value.failure)
    }

    @Test
    fun acceptingClosesTheReviewAndRefreshes() {
        services.items = listOf(bob)
        val model = model()
        model.startReview(bob)
        val before = services.loads
        services.gate = CompletableDeferred()
        model.accept()
        assertTrue(model.state.value.review!!.accepting)
        model.accept()
        model.closeReview()
        assertEquals(listOf("2"), services.accepted)
        assertTrue(model.state.value.review != null)
        services.gate!!.complete(Unit)
        assertNull(model.state.value.review)
        assertEquals(before + 1, services.loads)
    }

    @Test
    fun aFailedAcceptKeepsTheReviewOpenWithTheFailure() {
        services.items = listOf(bob)
        services.writeResult = SharingWrite.Failed(ItemFailure.Failed)
        val model = model()
        model.startReview(bob)
        model.accept()
        assertFalse(model.state.value.review!!.accepting)
        assertEquals(ItemFailure.Failed, model.state.value.failure)
    }

    @Test
    fun decliningDisconnectsAndClosesTheReview() {
        services.items = listOf(bob)
        val model = model()
        model.startReview(bob)
        model.decline()
        assertEquals(listOf("2"), services.disconnected)
        assertNull(model.state.value.review)
    }

    @Test
    fun aFailedDeclineKeepsTheReview() {
        services.items = listOf(bob)
        services.writeResult = SharingWrite.Failed(ItemFailure.Offline)
        val model = model()
        model.startReview(bob)
        model.decline()
        assertTrue(model.state.value.review != null)
        assertEquals(ItemFailure.Offline, model.state.value.failure)
    }

    @Test
    fun theNicknameIsEditedAndSaved() {
        services.items = listOf(ann)
        val model = model()
        model.open(ann)
        assertEquals(Renaming("1", ""), model.state.value.renaming)
        model.nicknameChanged("Annie")
        model.saveNickname()
        assertEquals(listOf("1" to "Annie"), services.renamed)
        assertNull(model.state.value.renaming)
        assertNull(model.state.value.detail)
    }

    @Test
    fun theNicknameStartsFromTheOneAlreadySetAndIsTrimmed() {
        val named = connection("1", "ann", nickname = "Annie")
        services.items = listOf(named)
        val model = model()
        model.open(named)
        assertEquals("Annie", model.state.value.renaming!!.draft)
        model.nicknameChanged("  Anne  ")
        model.saveNickname()
        assertEquals(listOf("1" to "Anne"), services.renamed)
    }

    @Test
    fun anInvitationAwaitingMeHasNoDetail() {
        services.items = listOf(bob)
        val model = model()
        model.open(bob)
        assertNull(model.state.value.detail)
    }

    @Test
    fun aFailedNicknameKeepsTheSheetWithTheFailure() {
        services.items = listOf(ann)
        services.writeResult = SharingWrite.Failed(ItemFailure.Offline)
        val model = model()
        model.open(ann)
        model.saveNickname()
        assertEquals("1", model.state.value.detail)
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun theSheetCannotCloseWhileSaving() {
        services.items = listOf(ann)
        services.gate = CompletableDeferred()
        val model = model()
        model.open(ann)
        model.saveNickname()
        model.saveNickname()
        model.closeDetail()
        assertEquals("1", model.state.value.detail)
        assertEquals(1, services.renamed.size)
        services.gate!!.complete(Unit)
        assertNull(model.state.value.detail)
    }

    @Test
    fun disconnectingAsksFirstAndThenRefreshes() {
        services.items = listOf(ann)
        val model = model()
        model.open(ann)
        model.askDisconnect(ann)
        assertEquals(ann, model.state.value.confirmingDisconnect)
        assertNull(model.state.value.detail)
        model.cancelDisconnect()
        assertNull(model.state.value.confirmingDisconnect)
        assertTrue(services.disconnected.isEmpty())
        model.askDisconnect(ann)
        val before = services.loads
        services.gate = CompletableDeferred()
        model.confirmDisconnect()
        model.confirmDisconnect()
        model.cancelDisconnect()
        assertEquals(listOf("1"), services.disconnected)
        assertEquals(ann, model.state.value.confirmingDisconnect)
        services.gate!!.complete(Unit)
        assertNull(model.state.value.confirmingDisconnect)
        assertEquals(before + 1, services.loads)
    }

    @Test
    fun aDeviceThatCannotDeleteCannotDisconnect() {
        val limited = FakeSharingServices(canDisconnect = false)
        limited.items = listOf(ann)
        val model = ViewModelProvider.create(
            ViewModelStore().also { it.clear() },
            viewModelFactory { initializer { ConnectionsViewModel(limited) } },
        )[ConnectionsViewModel::class].also { it.refresh() }
        model.askDisconnect(ann)
        assertNull(model.state.value.confirmingDisconnect)
    }

    @Test
    fun aFailedDisconnectIsShown() {
        services.items = listOf(ann)
        services.writeResult = SharingWrite.Failed(ItemFailure.Failed)
        val model = model()
        model.askDisconnect(ann)
        model.confirmDisconnect()
        assertEquals(ItemFailure.Failed, model.state.value.failure)
        assertNull(model.state.value.confirmingDisconnect)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun anUnexpectedExceptionBecomesAFailure() {
        val broken = object : SharingServices by FakeSharingServices() {
            override suspend fun loadConnections(): ConnectionsLoad = throw IllegalStateException()
            override suspend fun invite(username: String): InviteOutcome = throw IllegalStateException()
        }
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { ConnectionsViewModel(broken) } },
        )[ConnectionsViewModel::class].also { it.refresh() }
        assertEquals(ItemFailure.Failed, model.state.value.failure)
        model.usernameChanged("bob")
        model.invite()
        assertEquals(ItemFailure.Failed, model.state.value.failure)
        assertFalse(model.state.value.inviting)
    }
}
