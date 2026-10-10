package zekke.android.feature.onboarding

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

val VALID_PHRASE = List(11) { "abandon" } + "about"

@OptIn(ExperimentalCoroutinesApi::class)
class EnrolmentViewModelTest {
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

    private fun typePhrase(words: List<String> = VALID_PHRASE) {
        viewModel.startEnrolling()
        words.forEachIndexed { index, word -> viewModel.phraseWordChanged(index, word) }
    }

    private fun reachPin() {
        typePhrase()
        viewModel.confirmEnteredPhrase()
    }

    private fun submit(pin: String = "395172", confirmation: String = pin) {
        viewModel.enrolPinChanged(pin)
        viewModel.enrolPinConfirmationChanged(confirmation)
        viewModel.submitEnrolment()
    }

    private val devices = listOf(
        EnrolmentDevice("11111111-1111-4111-8111-111111111111", "full", "2026-10-03T10:00:00Z"),
        EnrolmentDevice("22222222-2222-4222-8222-222222222222", "full", "2026-10-09T10:00:00Z"),
    )

    @Test
    fun opensAnEmptyFormOfTheChosenLength() {
        viewModel.startEnrolling()
        assertEquals(OnboardingStep.EnterRecoveryPhrase, state.step)
        assertFalse(viewModel.phraseComplete)
        viewModel.changePhraseWordCount(24)
        assertEquals(24, state.phraseWordCount)
        assertEquals("", viewModel.phraseWord(23))
        viewModel.changePhraseWordCount(13)
        assertEquals(24, state.phraseWordCount)
    }

    @Test
    fun keepsOnlyLowercaseLettersOfAWord() {
        viewModel.startEnrolling()
        viewModel.phraseWordChanged(0, "  AbAn1don ")
        assertEquals("abandon", viewModel.phraseWord(0))
        viewModel.phraseWordChanged(1, "zoologically")
        assertEquals("zoologic", viewModel.phraseWord(1))
    }

    @Test
    fun offersTheWordsThatStartWithWhatWasTyped() {
        viewModel.startEnrolling()
        viewModel.phraseWordChanged(0, "aba")
        assertEquals(listOf("abandon"), state.phraseSuggestions)
        assertEquals(0, state.focusedPhraseWord)
        viewModel.choosePhraseSuggestion(0, "abandon")
        assertEquals("abandon", viewModel.phraseWord(0))
        assertEquals(1, state.focusedPhraseWord)
        assertTrue(state.phraseSuggestions.isEmpty())
    }

    @Test
    fun refusesAPhraseThatFailsItsChecksumBeforeThePin() {
        typePhrase(List(12) { "abandon" })
        assertTrue(viewModel.phraseComplete)
        viewModel.confirmEnteredPhrase()
        assertEquals(OnboardingStep.EnterRecoveryPhrase, state.step)
        assertEquals(PhraseProblem.InvalidChecksum, state.phraseProblem)
        viewModel.phraseWordChanged(11, "about")
        assertNull(state.phraseProblem)
        viewModel.confirmEnteredPhrase()
        assertEquals(OnboardingStep.EnrolPin, state.step)
    }

    @Test
    fun refusesAWeakPinOrAMismatchBeforeAnythingIsSent() {
        reachPin()
        submit("123456")
        assertEquals(PinProblem.Rejected(PinRejection.ASCENDING_SEQUENCE), state.enrolPinProblem)
        submit("395172", "395173")
        assertEquals(PinProblem.Mismatch, state.enrolPinProblem)
        assertTrue(services.enrolmentPins.isEmpty())
    }

    @Test
    fun addsThePhoneHoldingTheProcessAndZeroesThePhrase() {
        reachPin()
        submit()
        assertEquals(OnboardingStep.Enrolling, state.step)
        assertEquals("", state.enrolPin)
        assertEquals(1, services.holds)
        assertEquals(VALID_PHRASE, services.enrolmentPhrases.single())
        assertEquals("395172", services.enrolmentPins.single())
        assertEquals(emptyList<String>(), services.removedDeviceIds.single())

        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Done, state.step)
        assertEquals(0, services.holds)
        assertTrue(services.enrolmentWordArrays.single().all { word -> word.all { it == '\u0000' } })
        assertEquals("", viewModel.phraseWord(0))
    }

    @Test
    fun aFailureReleasesTheProcessAndKeepsThePhrase() {
        reachPin()
        submit()
        services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.Offline))
        assertEquals(OnboardingStep.EnrolPin, state.step)
        assertEquals(DeviceEnrolmentFailure.Offline, state.enrolmentFailure)
        assertEquals(0, services.holds)
        viewModel.back()
        assertEquals(OnboardingStep.EnterRecoveryPhrase, state.step)
        assertEquals("about", viewModel.phraseWord(11))
    }

    @Test
    fun saysWhenNoAccountUsesThePhrase() {
        reachPin()
        submit()
        services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.NoAccount))
        assertEquals(DeviceEnrolmentFailure.NoAccount, state.enrolmentFailure)
        assertEquals(0, state.enrolmentRefusals)
    }

    @Test
    fun countsRefusedAccountPins() {
        reachPin()
        repeat(2) {
            services.enrolment = CompletableDeferred()
            submit()
            services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.Refused))
        }
        assertEquals(2, state.enrolmentRefusals)
        assertEquals(DeviceEnrolmentFailure.Refused, state.enrolmentFailure)
    }

    @Test
    fun aFullAccountListsItsDevicesAndRetriesWithTheChosenOnes() {
        reachPin()
        submit()
        services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.TooManyDevices(devices)))
        assertEquals(devices, state.devicesToRemove)
        assertTrue(state.selectedDeviceIds.isEmpty())

        submit()
        assertEquals(1, services.enrolmentPins.size)

        viewModel.toggleDeviceRemoval("not-listed")
        assertTrue(state.selectedDeviceIds.isEmpty())
        viewModel.toggleDeviceRemoval(devices[1].id)
        viewModel.toggleDeviceRemoval(devices[0].id)
        viewModel.toggleDeviceRemoval(devices[0].id)
        assertEquals(setOf(devices[1].id), state.selectedDeviceIds)

        services.enrolment = CompletableDeferred()
        submit()
        assertEquals(listOf(devices[1].id), services.removedDeviceIds.last())
        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Done, state.step)
    }

    @Test
    fun backFromThePhraseZeroesIt() {
        typePhrase()
        viewModel.back()
        assertEquals(OnboardingStep.Start, state.step)
        assertEquals("", viewModel.phraseWord(0))
    }

    @Test
    fun aLockWhileAddingStartsOverAndIgnoresTheLateResult() {
        reachPin()
        submit()
        services.lock()
        assertEquals(OnboardingState(), state)
        assertEquals(0, services.holds)
        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Start, state.step)
    }

    @Test
    fun namesTheDayADeviceWasAdded() {
        val day = deviceAddedDate("2026-10-03T10:00:00Z", java.time.ZoneId.of("UTC"))
        assertTrue(day, day.contains("2026") && day.contains("3"))
        assertEquals("not a date", deviceAddedDate("not a date"))
    }
}
