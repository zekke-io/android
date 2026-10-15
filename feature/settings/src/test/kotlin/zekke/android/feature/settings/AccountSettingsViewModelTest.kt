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

class FakeAccountSettingsServices(override var fullDevice: Boolean = true) : AccountSettingsServices {
    var profile: ProfileLoad = ProfileLoad.Loaded(AccountProfile("ann", false, 30, false))
    var renamed = mutableListOf<String>()
    var renameResult: UsernameOutcome? = null
    var deleted = mutableListOf<Pair<List<String>, String?>>()
    var deleteResult: DeleteOutcome = DeleteOutcome.Deleted
    var phraseValid = true
    var gate: CompletableDeferred<Unit>? = null
    var opened = 0
    var returned = 0

    override fun phraseSuggestions(prefix: CharArray): List<String> = emptyList()

    override fun isValidPhrase(words: List<CharArray>): Boolean = phraseValid

    override fun systemScreenWillOpen() {
        opened++
    }

    override fun systemScreenReturned() {
        returned++
    }

    override suspend fun profile(): ProfileLoad = profile

    override suspend fun changeUsername(username: String): UsernameOutcome {
        renamed += username
        gate?.await()
        return renameResult ?: UsernameOutcome.Changed(username)
    }

    override suspend fun deleteAccount(words: List<CharArray>, accountPin: CharArray?): DeleteOutcome {
        deleted += words.map { String(it) } to accountPin?.let { String(it) }
        gate?.await()
        return deleteResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {
    private val services = FakeAccountSettingsServices()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(fake: FakeAccountSettingsServices = services): AccountSettingsViewModel = ViewModelProvider.create(
        ViewModelStore(),
        viewModelFactory { initializer { AccountSettingsViewModel(fake) } },
    )[AccountSettingsViewModel::class].also { it.opened() }

    private fun AccountSettingsViewModel.ready() {
        openDelete()
        phrase.acceptScanned(TWELVE_WORDS)
        confirmedChanged(true)
    }

    @Test
    fun theProfileLoadsAndAFailedLoadIsShown() {
        assertEquals("ann", model().state.value.profile?.username)
        services.profile = ProfileLoad.Failed(ItemFailure.Offline)
        val failing = model()
        assertEquals(ItemFailure.Offline, failing.state.value.failure)
        failing.dismissFailure()
        assertNull(failing.state.value.failure)
    }

    @Test
    fun theUsernameIsCheckedBeforeAnythingIsSent() {
        val model = model()
        model.claimChanged("Bad Name!")
        model.rename()
        assertEquals(UsernameProblem.Malformed, model.state.value.usernameProblem)
        model.claimChanged("  ANN ")
        model.rename()
        assertEquals(UsernameProblem.Unchanged, model.state.value.usernameProblem)
        assertTrue(services.renamed.isEmpty())
    }

    @Test
    fun aNewUsernameIsSentNormalisedAndShown() {
        val model = model()
        model.claimChanged("  Bob.Smith ")
        model.rename()
        assertEquals(listOf("bob.smith"), services.renamed)
        assertEquals("bob.smith", model.state.value.renamed)
        assertEquals("bob.smith", model.state.value.profile?.username)
        assertEquals("", model.state.value.claim)
        model.dismissRenamed()
        assertNull(model.state.value.renamed)
    }

    @Test
    fun everyUsernameRefusalIsKept() {
        val model = model()
        listOf(UsernameOutcome.Unavailable, UsernameOutcome.Offline, UsernameOutcome.RateLimited(60), UsernameOutcome.Failed).forEach { outcome ->
            services.renameResult = outcome
            model.claimChanged("bobby")
            model.rename()
            assertEquals(UsernameProblem.Refused(outcome), model.state.value.usernameProblem)
            assertFalse(model.state.value.renaming)
            assertEquals("ann", model.state.value.profile?.username)
            model.dismissUsernameProblem()
        }
        services.renameResult = UsernameOutcome.Malformed
        model.claimChanged("bobby")
        model.rename()
        assertEquals(UsernameProblem.Malformed, model.state.value.usernameProblem)
    }

    @Test
    fun oneRenameAtATimeAndALimitedDeviceCannotRename() {
        val model = model()
        services.gate = CompletableDeferred()
        model.claimChanged("bobby")
        model.rename()
        model.rename()
        model.claimChanged("other")
        assertEquals(1, services.renamed.size)
        assertEquals("bobby", model.state.value.claim)
        services.gate!!.complete(Unit)
        val limited = FakeAccountSettingsServices(fullDevice = false)
        val other = model(limited)
        other.claimChanged("bobby")
        other.rename()
        assertTrue(limited.renamed.isEmpty())
    }

    @Test
    fun deletingNeedsAFullDevicePhraseAndConfirmation() {
        val limited = FakeAccountSettingsServices(fullDevice = false)
        val other = model(limited)
        other.openDelete()
        assertFalse(other.state.value.deleting)
        val model = model()
        model.openDelete()
        assertTrue(model.state.value.deleting)
        assertFalse(model.canDelete())
        model.phrase.acceptScanned(TWELVE_WORDS)
        assertFalse(model.canDelete())
        model.confirmedChanged(true)
        assertTrue(model.canDelete())
    }

    @Test
    fun aStandardAccountSendsThePhraseWithNoPin() {
        val model = model()
        model.ready()
        model.delete()
        assertEquals(listOf(TWELVE_WORDS.split(" ") to null), services.deleted)
        assertTrue(model.state.value.deleted)
        assertFalse(model.phrase.active)
    }

    @Test
    fun aParanoidAccountNeedsItsAccountPin() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 30, false))
        val model = model()
        model.ready()
        assertFalse(model.canDelete())
        model.pinChanged("39517")
        assertFalse(model.canDelete())
        model.pinChanged("395172")
        assertTrue(model.canDelete())
        model.delete()
        assertEquals("395172", services.deleted.single().second)
    }

    @Test
    fun theModeIsReadAgainWhenTheDeletionOpensSoAParanoidAccountTurnedOnMeanwhileAsksForItsPin() {
        val model = model()
        assertFalse(model.state.value.needsPin)
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 30, false))
        model.openDelete()
        assertTrue(model.state.value.needsPin)
        model.phrase.acceptScanned(TWELVE_WORDS)
        model.confirmedChanged(true)
        assertFalse(model.canDelete())
    }

    @Test
    fun aPhraseThatFailsItsChecksumIsNeverSent() {
        services.phraseValid = false
        val model = model()
        model.ready()
        model.delete()
        assertTrue(services.deleted.isEmpty())
        assertTrue(model.phrase.state.checksumFailed)
    }

    @Test
    fun everyDeleteFailureIsKeptAndTheWrongPinCountsRefusals() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 30, false))
        val model = model()
        listOf(
            DeleteOutcome.PhraseMismatch,
            DeleteOutcome.PinRefused,
            DeleteOutcome.PinRefused,
            DeleteOutcome.Offline,
            DeleteOutcome.RateLimited(30),
            DeleteOutcome.PhoneLocked,
            DeleteOutcome.ChainProblem,
            DeleteOutcome.Failed,
        ).forEach { outcome ->
            services.deleteResult = outcome
            model.ready()
            model.pinChanged("395172")
            model.delete()
            assertEquals(outcome, model.state.value.deleteFailure)
            assertFalse(model.state.value.working)
            assertFalse(model.state.value.deleted)
            model.closeDelete()
        }
        assertEquals(2, model.state.value.refusals)
    }

    @Test
    fun oneDeletionAtATimeAndTheSheetCannotCloseWhileItRuns() {
        val model = model()
        services.gate = CompletableDeferred()
        model.ready()
        model.delete()
        model.delete()
        model.closeDelete()
        assertEquals(1, services.deleted.size)
        assertTrue(model.state.value.deleting)
        assertTrue(model.state.value.working)
        services.gate!!.complete(Unit)
        assertTrue(model.state.value.deleted)
    }

    @Test
    fun aProfileReloadWhileTheDeletionRunsNeverDropsItsOutcome() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 30, false))
        services.deleteResult = DeleteOutcome.PinRefused
        val model = model()
        services.gate = CompletableDeferred()
        model.ready()
        model.pinChanged("395172")
        model.delete()
        assertTrue(model.state.value.working)
        model.opened()
        services.gate!!.complete(Unit)
        assertFalse(model.state.value.working)
        assertEquals(DeleteOutcome.PinRefused, model.state.value.deleteFailure)
    }

    @Test
    fun closingForgetsThePhraseAndThePin() {
        val model = model()
        model.ready()
        model.pinChanged("395172")
        model.closeDelete()
        assertFalse(model.phrase.active)
        assertEquals("", model.state.value.pin)
        assertFalse(model.state.value.deleting)
    }

    @Test
    fun anUnexpectedExceptionIsAFailure() {
        val broken = object : AccountSettingsServices by FakeAccountSettingsServices() {
            override suspend fun profile(): ProfileLoad = throw IllegalStateException()
            override suspend fun changeUsername(username: String): UsernameOutcome = throw IllegalStateException()
            override suspend fun deleteAccount(words: List<CharArray>, accountPin: CharArray?): DeleteOutcome = throw IllegalStateException()
        }
        val model = ViewModelProvider.create(
            ViewModelStore(),
            viewModelFactory { initializer { AccountSettingsViewModel(broken) } },
        )[AccountSettingsViewModel::class].also { it.opened() }
        assertEquals(ItemFailure.Failed, model.state.value.failure)
        model.claimChanged("bobby")
        model.rename()
        assertEquals(UsernameProblem.Refused(UsernameOutcome.Failed), model.state.value.usernameProblem)
        model.ready()
        model.delete()
        assertEquals(DeleteOutcome.Failed, model.state.value.deleteFailure)
    }

    @Test
    fun thePickerIsAnnounced() {
        val model = model()
        model.systemScreenWillOpen()
        model.systemScreenReturned()
        assertEquals(1, services.opened)
        assertEquals(1, services.returned)
    }

    @Test
    fun theUsernameRulesAreTheWebs() {
        assertEquals(UsernameCheck.Ok("bob.smith"), checkUsername(" Bob.Smith ", "ann"))
        assertEquals(UsernameCheck.Malformed, checkUsername("ab", "ann"))
        assertEquals(UsernameCheck.Malformed, checkUsername("-bad", "ann"))
        assertEquals(UsernameCheck.Unchanged, checkUsername("ANN", "ann"))
    }
}
