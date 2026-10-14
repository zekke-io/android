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
import zekke.core.pin.PinRejection

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSecurityViewModelTest {
    private val services = FakeAccountSecurityServices()
    private lateinit var viewModel: AccountSecurityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AccountSecurityViewModel(services)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.state.value

    private fun typePhrase(words: List<String> = VALID_PHRASE) = words.forEachIndexed { index, word -> viewModel.phrase.wordChanged(index, word) }

    private fun reachPins(flow: SecurityFlow) {
        viewModel.open(flow)
        if (flow == SecurityFlow.TurnOnParanoid) viewModel.continueFromWarning()
        typePhrase()
        viewModel.confirmPhrase()
    }

    private fun submit(flow: SecurityFlow, pin: String = "395172", confirmation: String = pin, current: String = "482913") {
        if (flow == SecurityFlow.ChangeAccountPin) viewModel.currentPinChanged(current)
        viewModel.pinChanged(pin)
        viewModel.confirmationChanged(confirmation)
        viewModel.submit()
    }

    @Test
    fun loadsTheAccountModeOnceAndOffersARetryWhenItCannotBeRead() {
        services.mode = AccountMode.Paranoid
        viewModel.loadMode()
        assertEquals(AccountMode.Paranoid, state.mode)
        viewModel.loadMode()
        assertEquals(1, services.modeCalls)

        val failing = AccountSecurityViewModel(FakeAccountSecurityServices().also { it.mode = null })
        failing.loadMode()
        assertTrue(failing.state.value.modeUnavailable)
        assertNull(failing.state.value.mode)

        val throwing = AccountSecurityViewModel(FakeAccountSecurityServices().also { it.modeError = IllegalStateException("private") })
        throwing.loadMode()
        assertTrue(throwing.state.value.modeUnavailable)
    }

    @Test
    fun turningParanoidOnStartsWithItsOneWayWarningThenTheThePhrase() {
        viewModel.open(SecurityFlow.TurnOnParanoid)
        assertEquals(SecurityStep.Warning, state.step)
        assertTrue(viewModel.phrase.active)
        viewModel.confirmPhrase()
        assertEquals(SecurityStep.Warning, state.step)
        viewModel.continueFromWarning()
        assertEquals(SecurityStep.Phrase, state.step)
    }

    @Test
    fun changingThePinStartsAtThePhraseAndAFlowCannotBeOpenedTwice() {
        viewModel.open(SecurityFlow.ChangeAccountPin)
        assertEquals(SecurityStep.Phrase, state.step)
        viewModel.open(SecurityFlow.TurnOnParanoid)
        assertEquals(SecurityFlow.ChangeAccountPin, state.flow)
    }

    @Test
    fun theChecksumIsCheckedBeforeThePins() {
        viewModel.open(SecurityFlow.ChangeAccountPin)
        typePhrase(List(12) { "abandon" })
        viewModel.confirmPhrase()
        assertEquals(SecurityStep.Phrase, state.step)
        assertTrue(viewModel.phrase.state.checksumFailed)
        viewModel.phrase.wordChanged(11, "about")
        viewModel.confirmPhrase()
        assertEquals(SecurityStep.Pins, state.step)
    }

    @Test
    fun aWeakPinAMismatchOrAMissingCurrentPinIsRefusedBeforeAnythingIsSent() {
        reachPins(SecurityFlow.ChangeAccountPin)
        submit(SecurityFlow.ChangeAccountPin, pin = "123456")
        assertEquals(PinProblem.Rejected(PinRejection.ASCENDING_SEQUENCE), state.pinProblem)
        submit(SecurityFlow.ChangeAccountPin, pin = "395172", confirmation = "395173")
        assertEquals(PinProblem.Mismatch, state.pinProblem)
        submit(SecurityFlow.ChangeAccountPin, current = "4829")
        assertEquals(PinProblem.CurrentIncomplete, state.pinProblem)
        assertTrue(services.calls.isEmpty())
        assertEquals(SecurityStep.Pins, state.step)
    }

    @Test
    fun turningParanoidOnSendsThePhraseAndThePinAndEndsInTheFinishedNotice() {
        reachPins(SecurityFlow.TurnOnParanoid)
        submit(SecurityFlow.TurnOnParanoid)
        assertEquals(SecurityStep.Working, state.step)
        assertEquals(SecurityCall(SecurityFlow.TurnOnParanoid, VALID_PHRASE, null, "395172"), services.calls.single())

        services.outcome.complete(AccountSecurityOutcome.Done(signedOutDevices = 2))
        assertEquals(SecurityStep.Closed, state.step)
        assertEquals(AccountMode.Paranoid, state.mode)
        assertEquals(SecurityFinished(SecurityFlow.TurnOnParanoid, 2), state.finished)
        assertFalse(viewModel.phrase.active)
        assertTrue(services.wordArrays.single().all { word -> word.all { it == '\u0000' } })
        assertTrue(services.pinArrays.all { pin -> pin.all { it == '\u0000' } })
    }

    @Test
    fun changingThePinSendsTheCurrentAndTheNewOne() {
        reachPins(SecurityFlow.ChangeAccountPin)
        submit(SecurityFlow.ChangeAccountPin)
        assertEquals(SecurityCall(SecurityFlow.ChangeAccountPin, VALID_PHRASE, "482913", "395172"), services.calls.single())
        services.outcome.complete(AccountSecurityOutcome.Done(signedOutDevices = 0))
        assertEquals(SecurityFinished(SecurityFlow.ChangeAccountPin, 0), state.finished)
        assertTrue(services.pinArrays.all { pin -> pin.all { it == '\u0000' } })
    }

    @Test
    fun aPhraseOfAnotherAccountReturnsToThePhraseWithTheWordsKept() {
        reachPins(SecurityFlow.ChangeAccountPin)
        submit(SecurityFlow.ChangeAccountPin)
        services.outcome.complete(AccountSecurityOutcome.PhraseMismatch)
        assertEquals(SecurityStep.Phrase, state.step)
        assertEquals(AccountSecurityOutcome.PhraseMismatch, state.failure)
        assertEquals("about", viewModel.phrase.word(11))
        assertEquals("", state.pin)
    }

    @Test
    fun everyOtherFailureReturnsToThePinsWithThePhraseKept() {
        listOf(
            AccountSecurityOutcome.Offline,
            AccountSecurityOutcome.RateLimited(30),
            AccountSecurityOutcome.PhoneLocked,
            AccountSecurityOutcome.ChainProblem,
            AccountSecurityOutcome.Failed,
        ).forEach { failure ->
            val vm = AccountSecurityViewModel(FakeAccountSecurityServices())
            val fake = FakeAccountSecurityServices()
            val owned = AccountSecurityViewModel(fake)
            owned.open(SecurityFlow.ChangeAccountPin)
            VALID_PHRASE.forEachIndexed { index, word -> owned.phrase.wordChanged(index, word) }
            owned.confirmPhrase()
            owned.currentPinChanged("482913")
            owned.pinChanged("395172")
            owned.confirmationChanged("395172")
            owned.submit()
            fake.outcome.complete(failure)
            assertEquals(failure.toString(), SecurityStep.Pins, owned.state.value.step)
            assertEquals(failure, owned.state.value.failure)
            assertEquals("about", owned.phrase.word(11))
            assertEquals("", owned.state.value.pin)
            assertEquals("", owned.state.value.currentPin)
            assertEquals(vm.state.value.refusals, owned.state.value.refusals)
        }
    }

    @Test
    fun refusedAccountPinsAreCounted() {
        reachPins(SecurityFlow.ChangeAccountPin)
        repeat(2) {
            services.outcome = CompletableDeferred()
            submit(SecurityFlow.ChangeAccountPin)
            services.outcome.complete(AccountSecurityOutcome.PinRefused)
        }
        assertEquals(2, state.refusals)
        assertEquals(AccountSecurityOutcome.PinRefused, state.failure)
        assertEquals(SecurityStep.Pins, state.step)
    }

    @Test
    fun aParanoidModeThatDidNotFinishClosesTheFlowAndKeepsTheWarning() {
        reachPins(SecurityFlow.TurnOnParanoid)
        submit(SecurityFlow.TurnOnParanoid)
        services.outcome.complete(AccountSecurityOutcome.ParanoidOnUnfinished)
        assertEquals(SecurityStep.Closed, state.step)
        assertEquals(AccountMode.Paranoid, state.mode)
        assertEquals(AccountSecurityOutcome.ParanoidOnUnfinished, state.failure)
        assertFalse(viewModel.phrase.active)
        viewModel.dismissFailure()
        assertNull(state.failure)
    }

    @Test
    fun aServiceExceptionIsAFailure() {
        services.error = IllegalStateException("private detail")
        reachPins(SecurityFlow.TurnOnParanoid)
        submit(SecurityFlow.TurnOnParanoid)
        assertEquals(AccountSecurityOutcome.Failed, state.failure)
        assertEquals(SecurityStep.Pins, state.step)
        assertTrue(services.wordArrays.single().all { word -> word.all { it == '\u0000' } })
    }

    @Test
    fun closingForgetsThePhraseButNotWhileWorking() {
        reachPins(SecurityFlow.TurnOnParanoid)
        viewModel.close()
        assertEquals(SecurityStep.Closed, state.step)
        assertFalse(viewModel.phrase.active)

        reachPins(SecurityFlow.ChangeAccountPin)
        submit(SecurityFlow.ChangeAccountPin)
        viewModel.close()
        assertEquals(SecurityStep.Working, state.step)
        assertTrue(viewModel.phrase.active)
    }

    @Test
    fun theFinishedNoticeCanBeDismissed() {
        reachPins(SecurityFlow.ChangeAccountPin)
        submit(SecurityFlow.ChangeAccountPin)
        services.outcome.complete(AccountSecurityOutcome.Done(1))
        viewModel.dismissFinished()
        assertNull(state.finished)
    }

    @Test
    fun aLateResultAfterTheViewModelEndsChangesNothing() {
        val store = ViewModelStore()
        val owned = ViewModelProvider.create(store, viewModelFactory { initializer { AccountSecurityViewModel(services) } })[AccountSecurityViewModel::class]
        owned.open(SecurityFlow.ChangeAccountPin)
        VALID_PHRASE.forEachIndexed { index, word -> owned.phrase.wordChanged(index, word) }
        owned.confirmPhrase()
        owned.currentPinChanged("482913")
        owned.pinChanged("395172")
        owned.confirmationChanged("395172")
        owned.submit()
        store.clear()
        services.outcome.complete(AccountSecurityOutcome.Done(3))
        assertNull(owned.state.value.finished)
        assertFalse(owned.phrase.active)
    }

    @Test
    fun theScannerTellsTheServicesWhenASystemScreenOpensAndReturns() {
        viewModel.systemScreenWillOpen()
        assertEquals(1, services.systemScreens)
        viewModel.systemScreenReturned()
        assertEquals(0, services.systemScreens)
    }

    @Test
    fun refusedAccountPinsAreRememberedWhenTheFlowIsReopenedAndForgottenOnSuccess() {
        repeat(2) {
            reachPins(SecurityFlow.ChangeAccountPin)
            submit(SecurityFlow.ChangeAccountPin)
            services.outcome.complete(AccountSecurityOutcome.PinRefused)
            services.outcome = CompletableDeferred()
            viewModel.close()
        }
        viewModel.open(SecurityFlow.ChangeAccountPin)
        assertEquals(2, state.refusals)

        typePhrase()
        viewModel.confirmPhrase()
        submit(SecurityFlow.ChangeAccountPin)
        services.outcome.complete(AccountSecurityOutcome.Done(0))
        assertEquals(0, state.refusals)
    }
}
