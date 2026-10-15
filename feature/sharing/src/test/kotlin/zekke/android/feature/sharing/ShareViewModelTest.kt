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
class ShareViewModelTest {
    private val services = FakeSharingServices()
    private val store = ViewModelStore()
    private val ann = connection("1", "ann")
    private val bob = connection("2", "bob", nickname = "Bobby")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.targets = SendTargets.Loaded(listOf(ann, bob), listOf(Recipient("s9", "cat")))
        services.choicesFor = listOf(FolderChoice("f1", "Trips", 0))
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(fake: FakeSharingServices = services): ShareViewModel = ViewModelProvider.create(
        ViewModelStore(),
        viewModelFactory { initializer { ShareViewModel(fake, SharedType.Secret, "item-1") } },
    )[ShareViewModel::class].also { it.opened() }

    @Test
    fun openingListsWhoCanBeSentToAndWhoAlreadyHasIt() {
        val model = model()
        assertTrue(model.state.value.loaded)
        assertEquals(listOf(ann, bob), model.state.value.connections)
        assertEquals(listOf(Recipient("s9", "cat")), model.state.value.recipients)
        assertFalse(model.state.value.canSend)
    }

    @Test
    fun aFailedLoadIsShown() {
        services.targets = SendTargets.Failed(ItemFailure.Offline)
        val model = model()
        assertEquals(SendProblem.Failure(ItemFailure.Offline), model.state.value.problem)
        assertTrue(model.state.value.loaded)
    }

    @Test
    fun choosingAConnectionOffersItsFoldersAndChoosingAgainClears() {
        val model = model()
        model.choose("1")
        assertEquals("1", model.state.value.chosen)
        assertEquals(listOf(FolderChoice("f1", "Trips", 0)), model.state.value.choices)
        assertTrue(model.state.value.canSend)
        model.chooseFolder("f1")
        assertEquals("f1", model.state.value.folder)
        model.choose("2")
        assertNull(model.state.value.folder)
        model.choose(null)
        assertNull(model.state.value.chosen)
        assertFalse(model.state.value.canSend)
    }

    @Test
    fun aPhoneWithoutSharingOffersNoFolders() {
        val limited = FakeSharingServices(holdsSharing = false)
        limited.targets = services.targets
        limited.choicesFor = services.choicesFor
        val model = model(limited)
        model.choose("1")
        assertTrue(model.state.value.choices.isEmpty())
    }

    @Test
    fun sendingFilesItInTheChosenFolderAndReloadsTheRecipients() {
        val model = model()
        val before = services.targetsLoads
        model.choose("2")
        model.chooseFolder("f1")
        model.send()
        assertEquals(listOf(listOf("2", SharedType.Secret, "item-1", "f1")), services.sent)
        assertEquals(SendNotice.Sent("Bobby"), model.state.value.notice)
        assertNull(model.state.value.chosen)
        assertFalse(model.state.value.busy)
        assertEquals(before + 1, services.targetsLoads)
    }

    @Test
    fun anItemSentButNotFiledSaysSoWithoutAskingToSendAgain() {
        services.sendOutcome = SendOutcome.SentNotFiled
        val model = model()
        model.choose("1")
        model.send()
        assertEquals(SendNotice.SentNotFiled("ann"), model.state.value.notice)
        assertNull(model.state.value.problem)
    }

    @Test
    fun everyRefusalIsItsOwnProblemAndKeepsTheChoice() {
        val cases = listOf(
            SendOutcome.NotTrusted(TrustAlarm.RootChanged) to SendProblem.Refused(TrustAlarm.RootChanged),
            SendOutcome.NotTrusted(null) to SendProblem.Refused(null),
            SendOutcome.NotShared to SendProblem.NotShared,
            SendOutcome.Failed(ItemFailure.RateLimited(5)) to SendProblem.Failure(ItemFailure.RateLimited(5)),
        )
        val model = model()
        model.choose("1")
        cases.forEach { (outcome, problem) ->
            services.sendOutcome = outcome
            model.send()
            assertEquals(problem, model.state.value.problem)
            assertEquals("1", model.state.value.chosen)
            assertFalse(model.state.value.busy)
            model.dismissProblem()
        }
    }

    @Test
    fun oneSendAtATimeAndNothingWithoutAChoice() {
        val model = model()
        model.send()
        assertTrue(services.sent.isEmpty())
        model.choose("1")
        services.gate = CompletableDeferred()
        model.send()
        model.send()
        model.choose("2")
        assertEquals(1, services.sent.size)
        assertEquals("1", model.state.value.chosen)
        assertTrue(model.state.value.busy)
        services.gate!!.complete(Unit)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun aShareIsRemovedOnAFullDeviceOnly() {
        val model = model()
        model.remove(Recipient("s9", "cat"))
        assertEquals(listOf("s9"), services.unshared)
        val limited = FakeSharingServices(canDisconnect = false)
        limited.targets = services.targets
        val other = model(limited)
        other.remove(Recipient("s9", "cat"))
        assertTrue(limited.unshared.isEmpty())
    }

    @Test
    fun aFailedRemovalIsShown() {
        services.unshareResult = SharingWrite.Failed(ItemFailure.Offline)
        val model = model()
        model.remove(Recipient("s9", "cat"))
        assertEquals(SendProblem.Failure(ItemFailure.Offline), model.state.value.problem)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun openingAgainStartsClean() {
        val model = model()
        model.choose("1")
        model.send()
        assertTrue(model.state.value.notice != null)
        model.opened()
        assertNull(model.state.value.notice)
        assertNull(model.state.value.chosen)
    }
}
