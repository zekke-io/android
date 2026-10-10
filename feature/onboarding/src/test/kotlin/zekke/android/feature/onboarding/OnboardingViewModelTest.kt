package zekke.android.feature.onboarding

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
class OnboardingViewModelTest {
    private val services = FakeOnboardingServices()
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = OnboardingViewModel(services)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.state.value

    private fun reachPin(wordCount: Int = 12, paranoid: Boolean = false) {
        viewModel.startCreating()
        viewModel.chooseWordCount(wordCount)
        viewModel.confirmPhrase()
        viewModel.chooseMode(paranoid)
        viewModel.confirmMode()
    }

    private fun typePins(pin: String, confirmation: String = pin) {
        viewModel.pinChanged(pin)
        viewModel.confirmationChanged(confirmation)
        viewModel.submitPin()
    }

    @Test
    fun walksTheStepsInOrderAndBack() {
        assertEquals(OnboardingStep.Start, state.step)
        assertFalse(state.inProgress)
        viewModel.startCreating()
        assertEquals(OnboardingStep.Phrase, state.step)
        viewModel.confirmPhrase()
        assertEquals(OnboardingStep.Mode, state.step)
        viewModel.confirmMode()
        assertEquals(OnboardingStep.Pin, state.step)
        viewModel.pinChanged("482913")
        viewModel.back()
        assertEquals(OnboardingStep.Mode, state.step)
        assertEquals("", state.pin)
        viewModel.back()
        viewModel.back()
        assertEquals(OnboardingStep.Start, state.step)
    }

    @Test
    fun generatesThePhraseOfTheChosenLength() {
        reachPin(wordCount = 24)
        assertEquals(24, services.issuedPhrases.single().size)
    }

    @Test
    fun aNewPhraseZeroesThePreviousOne() {
        viewModel.startCreating()
        viewModel.confirmPhrase()
        viewModel.back()
        viewModel.confirmPhrase()
        assertTrue(services.issuedPhrases.first().all { word -> word.all { it == ' ' } })
        assertEquals(2, services.issuedPhrases.size)
    }

    @Test
    fun refusesAWeakPinWithItsReason() {
        reachPin()
        typePins("123456")
        assertEquals(PinProblem.Rejected(PinRejection.ASCENDING_SEQUENCE), state.pinProblem)
        assertEquals(OnboardingStep.Pin, state.step)
        assertEquals("", state.confirmation)
        typePins("777777")
        assertEquals(PinProblem.Rejected(PinRejection.REPEATING_DIGIT), state.pinProblem)
        assertTrue(services.receivedPins.isEmpty())
    }

    @Test
    fun refusesAConfirmationThatDiffers() {
        reachPin()
        typePins("482913", "482914")
        assertEquals(PinProblem.Mismatch, state.pinProblem)
        assertTrue(services.receivedPins.isEmpty())
    }

    @Test
    fun createsTheAccountAndShowsThePhraseOnlyWhenAsked() {
        reachPin(paranoid = true)
        typePins("482913")
        assertEquals(OnboardingStep.Creating, state.step)
        assertEquals("482913", services.receivedPins.single())
        assertEquals(true, services.receivedModes.single())
        assertEquals(services.issuedPhrases.single().map { String(it) }, services.receivedPhrases.single())

        services.outcome.complete(CreatedAccount("quiet-otter-42", paranoid = true, paranoidFailed = false))
        assertEquals(OnboardingStep.Recovery, state.step)
        assertEquals("quiet-otter-42", state.username)
        assertEquals("", state.pin)
        assertNull(state.revealedPhrase)

        viewModel.revealPhrase()
        assertEquals(List(12) { "word$it" }, state.revealedPhrase)

        viewModel.finish()
        assertEquals(OnboardingStep.Done, state.step)
        assertNull(state.revealedPhrase)
        assertFalse(state.inProgress)
        assertTrue(services.issuedPhrases.single().all { word -> word.all { it == ' ' } })
    }

    @Test
    fun reportsAParanoidModeThatDidNotFinish() {
        reachPin(paranoid = true)
        typePins("482913")
        services.outcome.complete(CreatedAccount("quiet-otter-42", paranoid = false, paranoidFailed = true))
        assertTrue(state.paranoidFailed)
        assertFalse(state.paranoid)
    }

    @Test
    fun aFailureReturnsToThePinWithTheSamePhrase() {
        reachPin()
        typePins("482913")
        services.outcome.completeExceptionally(AccountCreationException(CreationFailure.RateLimited(30)))
        assertEquals(OnboardingStep.Pin, state.step)
        assertEquals(CreationFailure.RateLimited(30), state.failure)

        services.outcome = kotlinx.coroutines.CompletableDeferred()
        viewModel.confirmationChanged("482913")
        viewModel.submitPin()
        assertEquals(1, services.issuedPhrases.size)
        assertEquals(services.receivedPhrases[0], services.receivedPhrases[1])
    }

    @Test
    fun aLockBeforeTheAccountExistsStartsOver() {
        reachPin()
        services.lock()
        assertEquals(OnboardingState(), state)
        assertTrue(services.issuedPhrases.single().all { word -> word.all { it == ' ' } })
    }

    @Test
    fun aLockDuringRecoveryLosesThePhraseAndSaysSo() {
        reachPin()
        typePins("482913")
        services.outcome.complete(CreatedAccount("quiet-otter-42", paranoid = false, paranoidFailed = false))
        viewModel.revealPhrase()
        services.lock()
        assertEquals(OnboardingStep.Done, state.step)
        assertTrue(state.lostToLock)
        assertNull(state.revealedPhrase)
        viewModel.acknowledgeLoss()
        assertFalse(state.lostToLock)
    }

    @Test
    fun aLockWhileCreatingIsNotOverwrittenByTheResult() {
        reachPin()
        typePins("482913")
        services.lock()
        services.outcome.complete(CreatedAccount("quiet-otter-42", paranoid = false, paranoidFailed = false))
        assertEquals(OnboardingStep.Done, state.step)
        assertTrue(state.lostToLock)
    }
}
