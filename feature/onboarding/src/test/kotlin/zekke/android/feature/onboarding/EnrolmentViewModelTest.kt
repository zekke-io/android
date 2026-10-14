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
        words.forEachIndexed { index, word -> viewModel.phrase.wordChanged(index, word) }
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
        assertFalse(viewModel.phrase.complete)
        viewModel.phrase.changeWordCount(24)
        assertEquals(24, viewModel.phrase.state.wordCount)
        assertEquals("", viewModel.phrase.word(23))
        viewModel.phrase.changeWordCount(13)
        assertEquals(24, viewModel.phrase.state.wordCount)
    }

    @Test
    fun theFormUsesTheCoresSuggestions() {
        viewModel.startEnrolling()
        viewModel.phrase.wordChanged(0, "aba")
        assertEquals(listOf("abandon"), viewModel.phrase.state.suggestions)
    }

    @Test
    fun refusesAPhraseThatFailsItsChecksumBeforeThePin() {
        typePhrase(List(12) { "abandon" })
        assertTrue(viewModel.phrase.complete)
        viewModel.confirmEnteredPhrase()
        assertEquals(OnboardingStep.EnterRecoveryPhrase, state.step)
        assertTrue(viewModel.phrase.state.checksumFailed)
        viewModel.phrase.wordChanged(11, "about")
        assertFalse(viewModel.phrase.state.checksumFailed)
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
        assertEquals(DeviceRemoval.None, services.removals.single())

        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Done, state.step)
        assertEquals(0, services.holds)
        assertTrue(services.enrolmentWordArrays.single().all { word -> word.all { it == '\u0000' } })
        assertEquals("", viewModel.phrase.word(0))
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
        assertEquals("about", viewModel.phrase.word(11))
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
        assertEquals(DeviceRemoval.Listed(listOf(devices[1].id)), services.removals.last())
        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Done, state.step)
    }

    @Test
    fun backFromThePhraseZeroesIt() {
        typePhrase()
        viewModel.back()
        assertEquals(OnboardingStep.Start, state.step)
        assertEquals("", viewModel.phrase.word(0))
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

    @Test
    fun lostDevicesSendsTheRemovalOfEveryOtherDevice() {
        reachPin()
        viewModel.toggleRemoveAllDevices(true)
        assertTrue(state.removeAllDevices)
        submit()
        assertEquals(DeviceRemoval.All, services.removals.single())
        assertEquals(1, services.holds)
        services.enrolment.complete(Unit)
        assertEquals(OnboardingStep.Done, state.step)
        assertFalse(state.removeAllDevices)
        assertEquals(0, services.holds)
    }

    @Test
    fun lostDevicesNeedsNoChoiceOnAFullAccount() {
        reachPin()
        submit()
        services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.TooManyDevices(devices)))
        services.enrolment = CompletableDeferred()
        submit()
        assertEquals(1, services.removals.size)
        viewModel.toggleRemoveAllDevices(true)
        submit()
        assertEquals(DeviceRemoval.All, services.removals.last())
    }

    @Test
    fun theLostDevicesBoxIsClearedByGoingBackAndIgnoredOutsideThePinStep() {
        viewModel.toggleRemoveAllDevices(true)
        assertFalse(state.removeAllDevices)
        reachPin()
        viewModel.toggleRemoveAllDevices(true)
        viewModel.back()
        assertFalse(state.removeAllDevices)
        assertEquals(OnboardingStep.EnterRecoveryPhrase, state.step)
    }

    @Test
    fun aFailureKeepsTheLostDevicesChoice() {
        reachPin()
        viewModel.toggleRemoveAllDevices(true)
        submit()
        services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.Offline))
        assertTrue(state.removeAllDevices)
        assertEquals(OnboardingStep.EnrolPin, state.step)
    }
}
