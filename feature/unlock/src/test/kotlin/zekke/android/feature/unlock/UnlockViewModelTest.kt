package zekke.android.feature.unlock

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
import zekke.core.account.ChainNotVerifiedException
import zekke.core.account.UnlockOutcome as AccountUnlockOutcome

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockViewModelTest {
    private val services = FakeUnlockServices()
    private lateinit var viewModel: UnlockViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = UnlockViewModel(services)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val state get() = viewModel.state.value

    private fun submit(pin: String = "482913") {
        viewModel.pinChanged(pin)
        viewModel.submitPin()
    }

    @Test
    fun submitsOnceWhenTheSixthDigitArrivesAndClearsThePin() {
        services.nextOutcome = UnlockAttempt.WrongPin(3)
        viewModel.pinChanged("48291")
        viewModel.submitPin()
        assertTrue(services.receivedPins.isEmpty())
        assertEquals("48291", viewModel.pinValue())

        viewModel.pinChanged("482913")
        viewModel.submitPin()
        assertEquals(listOf("482913"), services.receivedPins)
        assertEquals("", viewModel.pinValue())
        assertEquals(UnlockFeedback.WrongPin(3), state.feedback)
    }

    @Test
    fun wrongPinKeepsTheServerAttemptCount() {
        services.nextOutcome = UnlockAttempt.WrongPin(1)
        submit()
        assertEquals(UnlockFeedback.WrongPin(1), state.feedback)
        assertFalse(state.busy)
        assertEquals("", viewModel.pinValue())
    }

    @Test
    fun forgottenRegistrationRequestsThePhrase() {
        services.nextOutcome = UnlockAttempt.Forgotten
        submit()
        assertEquals(RegistrationLost.Forgotten, state.registrationLost)
        assertNull(state.feedback)
        viewModel.continueWithPhrase()
        assertNull(state.registrationLost)
    }

    @Test
    fun removedDeviceRequestsThePhraseWithItsOwnOutcome() {
        services.nextOutcome = UnlockAttempt.Removed
        submit()
        assertEquals(RegistrationLost.Removed, state.registrationLost)
    }

    @Test
    fun offlineRateLimitPhoneLockAndInterruptedAttemptsStayTyped() {
        val cases = listOf(
            UnlockAttempt.Offline to UnlockFeedback.Offline,
            UnlockAttempt.RateLimited(37) to UnlockFeedback.RateLimited(37),
            UnlockAttempt.PhoneLocked to UnlockFeedback.PhoneLocked,
            UnlockAttempt.Interrupted to UnlockFeedback.Interrupted,
            UnlockAttempt.Failed to UnlockFeedback.Failed,
        )
        cases.forEach { (outcome, feedback) ->
            services.nextOutcome = outcome
            submit()
            assertEquals(feedback, state.feedback)
            assertFalse(state.busy)
            assertNull(state.registrationLost)
            viewModel.dismissFeedback()
            assertNull(state.feedback)
        }
    }

    @Test
    fun aServiceExceptionBecomesAVisibleFailure() {
        services.unlockError = IllegalStateException("private detail")
        submit()
        assertEquals(UnlockFeedback.Failed, state.feedback)
        assertFalse(state.busy)
    }

    @Test
    fun openedSessionRetainsChainAndRewrapProblemsForTheUnlockedShell() {
        services.nextOutcome = UnlockAttempt.Opened(chainProblem = true, rewrapIssue = RewrapIssue.Offline)
        submit()
        assertTrue(state.chainProblem)
        assertEquals(RewrapIssue.Offline, state.rewrapIssue)
        viewModel.dismissChainProblem()
        viewModel.dismissRewrapIssue()
        assertFalse(state.chainProblem)
        assertNull(state.rewrapIssue)
    }

    @Test
    fun forgetRequiresConfirmationAndThenAsksForThePhrase() {
        viewModel.forgetThisPhone()
        assertEquals(0, services.forgetCalls)
        viewModel.requestForget()
        assertTrue(state.confirmingForget)
        viewModel.forgetThisPhone()
        assertEquals(1, services.forgetCalls)
        assertEquals(RegistrationLost.Forgotten, state.registrationLost)
        assertFalse(state.confirmingForget)
    }

    @Test
    fun keepTryingCancelsTheForgetConfirmation() {
        viewModel.requestForget()
        viewModel.keepTrying()
        assertFalse(state.confirmingForget)
        assertEquals(0, services.forgetCalls)
    }

    @Test
    fun aFailedForgetKeepsTheConfirmationAndShowsAnError() {
        services.forgetError = IllegalStateException("private detail")
        viewModel.requestForget()
        viewModel.forgetThisPhone()
        assertEquals(UnlockFeedback.Failed, state.feedback)
        assertTrue(state.confirmingForget)
        assertNull(state.registrationLost)
    }

    @Test
    fun mapsEveryCoreUnlockOutcome() {
        assertEquals(UnlockAttempt.Opened(false), mapUnlockOutcome(AccountUnlockOutcome.Unlocked(null)))
        assertEquals(
            UnlockAttempt.Opened(true),
            mapUnlockOutcome(AccountUnlockOutcome.Unlocked(ChainNotVerifiedException(IllegalStateException()))),
        )
        assertEquals(UnlockAttempt.Forgotten, mapUnlockOutcome(AccountUnlockOutcome.NoDevice))
        assertEquals(UnlockAttempt.WrongPin(4), mapUnlockOutcome(AccountUnlockOutcome.WrongPin(4)))
        assertEquals(UnlockAttempt.Forgotten, mapUnlockOutcome(AccountUnlockOutcome.Forgotten))
        assertEquals(UnlockAttempt.Removed, mapUnlockOutcome(AccountUnlockOutcome.Removed))
        assertEquals(UnlockAttempt.Offline, mapUnlockOutcome(AccountUnlockOutcome.Offline))
        assertEquals(UnlockAttempt.RateLimited(60), mapUnlockOutcome(AccountUnlockOutcome.RateLimited(60)))
    }
}
