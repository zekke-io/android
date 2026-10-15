package zekke.android.feature.settings

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
import zekke.core.scopes.Scope

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {
    private val services = FakeDevicesServices()
    private val store = ViewModelStore()
    private val mine = device("me", thisDevice = true, addedAt = "2026-10-03T10:00:00Z")
    private val web = device("web", "Laptop", addedAt = "2026-10-01T10:00:00Z")
    private val extension = device("ext", scopes = listOf(Scope.PASSWORDS), addedAt = "2026-10-02T10:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.devices = listOf(extension, mine, web)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(fake: FakeDevicesServices = services): DevicesViewModel = ViewModelProvider.create(
        ViewModelStore(),
        viewModelFactory { initializer { DevicesViewModel(fake) } },
    )[DevicesViewModel::class].also { it.opened() }

    private fun DevicesViewModel.fillPhrase() {
        phrase.acceptScanned(TWELVE_WORDS)
    }

    @Test
    fun thisPhoneComesFirstThenTheOthersByAge() {
        val model = model()
        assertEquals(listOf("me", "web", "ext"), model.state.value.devices.map { it.id })
        assertTrue(model.state.value.loaded)
        assertEquals(true, model.state.value.chainVerified)
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
    fun aFullDeviceIsOneThatHoldsAdminAndEverythingIsSaidOnlyForAll() {
        assertTrue(mine.full)
        assertTrue(mine.everything)
        assertFalse(extension.full)
        assertFalse(extension.everything)
    }

    @Test
    fun removingNeedsAFullDeviceAnotherDeviceAndAVerifiedChain() {
        val model = model()
        val state = model.state.value
        assertTrue(state.canRemove(web))
        assertFalse(state.canRemove(mine))
        val limited = FakeDevicesServices(fullDevice = false).also { it.devices = services.devices }
        assertFalse(model(limited).state.value.canRemove(web))
        services.chainVerified = false
        assertFalse(model().state.value.canRemove(web))
    }

    @Test
    fun aBrokenChainIsKeptApartFromAVerifiedOne() {
        services.chainVerified = false
        val model = model()
        assertEquals(false, model.state.value.chainVerified)
        model.dismissChain()
        assertTrue(model.state.value.chainDismissed)
    }

    @Test
    fun renamingNeedsTheSharingKeyAndSavesTheTrimmedName() {
        val model = model()
        model.open(web)
        assertEquals("Laptop", model.state.value.draft)
        model.draftChanged("  Work laptop ")
        model.saveName()
        assertEquals(listOf("web" to "Work laptop"), services.renamed)
        assertNull(model.state.value.acting)
        assertEquals(2, services.loads)
        val limited = FakeDevicesServices(canName = false, fullDevice = false).also { it.devices = services.devices }
        val other = model(limited)
        other.open(web)
        assertNull(other.state.value.acting)
    }

    @Test
    fun aFailedRenameKeepsTheSheetWithTheFailure() {
        services.renameResult = DevicesWrite.Failed(ItemFailure.Offline)
        val model = model()
        model.open(web)
        model.saveName()
        assertEquals("web", model.state.value.acting)
        assertEquals(ItemFailure.Offline, model.state.value.failure)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun theNameIsCappedAndTheSheetClosesWithoutSaving() {
        val model = model()
        model.open(web)
        model.draftChanged("x".repeat(100))
        assertEquals(MAX_DEVICE_NAME_LENGTH, model.state.value.draft.length)
        model.closeActions()
        assertNull(model.state.value.acting)
        assertTrue(services.renamed.isEmpty())
    }

    @Test
    fun removingAsksForThePhraseAndSendsItThenZeroesNothingItKeeps() {
        val model = model()
        model.askRemove(web)
        assertEquals(web, model.state.value.removing)
        model.confirmRemove()
        assertTrue(services.removed.isEmpty())
        model.fillPhrase()
        model.confirmRemove()
        assertEquals(1, services.removed.size)
        assertEquals("web", services.removed.single().first)
        assertEquals(TWELVE_WORDS.split(" "), services.removed.single().second)
        assertNull(model.state.value.removing)
        assertEquals(DevicesNotice.Removed, model.state.value.notice)
        assertEquals(2, services.loads)
        assertFalse(model.phrase.active)
    }

    @Test
    fun aPhraseThatFailsItsChecksumIsNeverSent() {
        services.phraseValid = false
        val model = model()
        model.askRemove(web)
        model.fillPhrase()
        model.confirmRemove()
        assertTrue(services.removed.isEmpty())
        assertTrue(model.phrase.state.checksumFailed)
    }

    @Test
    fun thisPhoneAndAnUnverifiedChainCannotBeAskedToRemove() {
        val model = model()
        model.askRemove(mine)
        assertNull(model.state.value.removing)
        services.chainVerified = false
        val other = model()
        other.askRemove(web)
        assertNull(other.state.value.removing)
    }

    @Test
    fun everyRemovalFailureIsKeptAndAMismatchAsksForThePhraseAgain() {
        val model = model()
        listOf(
            DeviceRemoval.PhraseMismatch,
            DeviceRemoval.Offline,
            DeviceRemoval.RateLimited(30),
            DeviceRemoval.PhoneLocked,
            DeviceRemoval.ChainProblem,
            DeviceRemoval.Failed,
        ).forEach { outcome ->
            services.removeResult = outcome
            model.askRemove(web)
            model.fillPhrase()
            model.confirmRemove()
            assertEquals(outcome, model.state.value.removalFailure)
            assertEquals(web, model.state.value.removing)
            assertFalse(model.state.value.working)
            model.dismissRemovalFailure()
            assertNull(model.state.value.removalFailure)
            model.cancelRemove()
        }
    }

    @Test
    fun oneRemovalAtATimeAndTheSheetCannotCloseWhileItRuns() {
        val model = model()
        services.gate = CompletableDeferred()
        model.askRemove(web)
        model.fillPhrase()
        model.confirmRemove()
        model.confirmRemove()
        model.cancelRemove()
        assertEquals(1, services.removed.size)
        assertEquals(web, model.state.value.removing)
        assertTrue(model.state.value.working)
        services.gate!!.complete(Unit)
        assertNull(model.state.value.removing)
    }

    @Test
    fun cancellingForgetsThePhrase() {
        val model = model()
        model.askRemove(web)
        model.fillPhrase()
        model.cancelRemove()
        assertFalse(model.phrase.active)
        assertNull(model.state.value.removing)
    }

    @Test
    fun anUnexpectedExceptionIsAFailure() {
        val broken = object : DevicesServices by FakeDevicesServices() {
            override suspend fun load(): DevicesLoad = throw IllegalStateException()
            override suspend fun remove(id: String, words: List<CharArray>): DeviceRemoval = throw IllegalStateException()
        }
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { DevicesViewModel(broken) } },
        )[DevicesViewModel::class].also { it.opened() }
        assertEquals(ItemFailure.Failed, model.state.value.failure)
    }

    @Test
    fun thePickerIsAnnounced() {
        val model = model()
        model.systemScreenWillOpen()
        model.systemScreenReturned()
        assertEquals(1, services.pickerOpened)
        assertEquals(1, services.pickerReturned)
    }

    @Test
    fun theDateIsFormattedForThePhone() {
        assertEquals("Oct 1, 2026", deviceDate("2026-10-01T10:00:00Z", java.util.Locale.US, java.time.ZoneId.of("UTC")))
        assertEquals("", deviceDate("nonsense"))
    }
}
