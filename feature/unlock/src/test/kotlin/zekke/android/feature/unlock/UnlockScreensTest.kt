package zekke.android.feature.unlock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnlockScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

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

    private fun showUnlockScreen() {
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme {
                UnlockFlow(state, viewModel, onAddRecoveryPhrase = {})
            }
        }
    }

    @Test
    fun sixDigitsSubmitAutomaticallyAndShowAttemptsRemaining() {
        services.nextOutcome = UnlockAttempt.WrongPin(2)
        showUnlockScreen()

        composeRule.onNodeWithContentDescription("PIN").performTextInput("482913")
        composeRule.waitForIdle()

        assertEquals(listOf("482913"), services.receivedPins)
        composeRule.onNodeWithText(
            "Wrong PIN. 2 attempts left. When none are left, this phone forgets your account, and you will need your recovery phrase to add it again.",
        ).assertIsDisplayed()
    }

    @Test
    fun eachRemainingFailureHasItsOwnNotice() {
        val screenState = mutableStateOf(UnlockState())
        composeRule.setContent {
            ZekkeTheme {
                UnlockFlow(screenState.value, viewModel, onAddRecoveryPhrase = {})
            }
        }
        val cases = listOf(
            UnlockFeedback.Offline to "Unlocking needs the server, and it cannot be reached right now.",
            UnlockFeedback.RateLimited(30) to "Too many requests have come from this network",
            UnlockFeedback.PhoneLocked to "Unlock the phone first, then enter your Zekke PIN.",
            UnlockFeedback.Interrupted to "The phone locked before unlocking finished. Enter your PIN again.",
            UnlockFeedback.Failed to "Something went wrong. Please try again.",
        )
        cases.forEach { (feedback, message) ->
            composeRule.runOnIdle { screenState.value = UnlockState(feedback = feedback) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(message, substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun aRemovedPhoneOffersTheRecoveryPhrase() {
        services.nextOutcome = UnlockAttempt.Removed
        showUnlockScreen()

        composeRule.onNodeWithContentDescription("PIN").performTextInput("482913")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "This phone is no longer a device of your account: it was removed, or the account was deleted. Enter your recovery phrase to add it again.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("I have a recovery phrase").assertIsDisplayed()
    }

    @Test
    fun forgettingThePhoneNeedsConfirmationAndSaysItStaysListed() {
        showUnlockScreen()

        composeRule.onNodeWithText("I forgot this phone’s PIN").performClick()
        composeRule.onNodeWithText(
            "stays in your list of devices until you remove it",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Forget this phone").performClick()
        composeRule.waitForIdle()

        assertEquals(1, services.forgetCalls)
        composeRule.onNodeWithText("too many wrong PINs", substring = true).assertDoesNotExist()
        assertEquals(1, viewModel.state.value.forgotten)
    }

    @Test
    fun unlockedShellShowsChainAndRewrapWarnings() {
        composeRule.setContent {
            ZekkeTheme {
                UnlockSessionNotices(
                    UnlockState(chainProblem = true, rewrapIssue = RewrapIssue.Offline),
                    {},
                    {},
                )
            }
        }

        composeRule.onNodeWithText(
            "The history of your devices that the server returned does not verify from your recovery phrase’s key. Someone may have tampered with it. Do not trust your list of devices, and add no device until this is resolved.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Your account is open, but the phone could not reach the server to finish updating items that use older keys. They will be tried again after another unlock.",
        ).assertIsDisplayed()
    }

    @Test
    fun theVersionNoticeShowsOnTheUnlockScreen() {
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme {
                UnlockFlow(state, viewModel, {}, versionNotice = {
                    VersionNotice(ClientVersionStatus.UpdateAvailable("0.2.0")) {}
                })
            }
        }

        composeRule.onNodeWithText("Version 0.2.0 of Zekke is available. Update the app when you can.").assertIsDisplayed()
    }
}
