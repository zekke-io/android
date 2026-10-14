package zekke.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CompletableDeferred
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
class AccountSecurityScreensTest {
    @get:Rule
    val compose = createComposeRule()

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

    private fun show() {
        compose.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme {
                Column {
                    AccountSecuritySection(state, viewModel)
                    AccountSecuritySheet(state, viewModel)
                }
            }
        }
    }

    private fun typeThePhrase() = compose.runOnIdle {
        VALID_PHRASE.forEachIndexed { index, word -> viewModel.phrase.wordChanged(index, word) }
    }

    @Test
    fun aStandardAccountOffersToTurnParanoidOn() {
        show()
        compose.onNodeWithText("Paranoid mode").assertExists()
        compose.onNodeWithText("Adds an account PIN", substring = true).assertExists()
        compose.onNodeWithText("Turn on Paranoid mode…").assertExists()
        compose.onNodeWithText("Change the account PIN…").assertDoesNotExist()
    }

    @Test
    fun aParanoidAccountSaysItIsOnAndOffersTheChange() {
        services.mode = AccountMode.Paranoid
        show()
        compose.onNodeWithText("Paranoid mode is on").assertExists()
        compose.onNodeWithText("There is no way back to Standard", substring = true).assertExists()
        compose.onNodeWithText("Change the account PIN…").assertExists()
        compose.onNodeWithText("Turn on Paranoid mode…").assertDoesNotExist()
    }

    @Test
    fun anUnreadableModeOffersToTryAgain() {
        services.mode = null
        show()
        compose.onNodeWithText("Your account settings could not be loaded", substring = true).assertExists()
        services.mode = AccountMode.Standard
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Turn on Paranoid mode…").assertExists()
    }

    @Test
    fun turningOnWalksThroughTheWarningThePhraseAndThePins() {
        show()
        compose.onNodeWithText("Turn on Paranoid mode…").performClick()
        compose.onNodeWithText("there is no way back to Standard", substring = true).assertExists()
        compose.onNodeWithText("signed out", substring = true).assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Word 1").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        typeThePhrase()
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("This is your account PIN", substring = true).assertExists()
        compose.onNode(hasText("Turn on Paranoid mode") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle {
            viewModel.pinChanged("395172")
            viewModel.confirmationChanged("395172")
        }
        compose.onNode(hasText("Turn on Paranoid mode") and hasClickAction()).assertIsEnabled()
    }

    @Test
    fun theFinishedNoticeSaysHowManyDevicesWereSignedOut() {
        show()
        compose.runOnIdle {
            viewModel.open(SecurityFlow.ChangeAccountPin)
            typeThePhrase()
            viewModel.confirmPhrase()
            viewModel.currentPinChanged("482913")
            viewModel.pinChanged("395172")
            viewModel.confirmationChanged("395172")
            viewModel.submit()
            services.outcome.complete(AccountSecurityOutcome.Done(signedOutDevices = 2))
        }
        compose.onNodeWithText("Your account PIN has changed", substring = true).assertExists()
        compose.onNodeWithText("2 other devices were signed out", substring = true).assertExists()
    }

    @Test
    fun aRefusedPinSuggestsWaitingAfterTwoTries() {
        show()
        compose.runOnIdle {
            viewModel.open(SecurityFlow.ChangeAccountPin)
            typeThePhrase()
            viewModel.confirmPhrase()
            repeat(2) {
                services.outcome = CompletableDeferred()
                viewModel.currentPinChanged("482913")
                viewModel.pinChanged("395172")
                viewModel.confirmationChanged("395172")
                viewModel.submit()
                services.outcome.complete(AccountSecurityOutcome.PinRefused)
            }
        }
        compose.onNodeWithText("wait a few minutes", substring = true).assertExists()
    }

    @Test
    fun aPhraseOfAnotherAccountIsSaidOnThePhraseStep() {
        show()
        compose.runOnIdle {
            viewModel.open(SecurityFlow.ChangeAccountPin)
            typeThePhrase()
            viewModel.confirmPhrase()
            viewModel.currentPinChanged("482913")
            viewModel.pinChanged("395172")
            viewModel.confirmationChanged("395172")
            viewModel.submit()
            services.outcome.complete(AccountSecurityOutcome.PhraseMismatch)
        }
        compose.onNodeWithText("belongs to a different account", substring = true).assertExists()
        compose.onNodeWithText("Word 1").assertExists()
        assertEquals(SecurityStep.Phrase, viewModel.state.value.step)
    }

    @Test
    fun aParanoidModeThatDidNotFinishStaysVisibleOnTheHomeScreen() {
        show()
        compose.runOnIdle {
            viewModel.open(SecurityFlow.TurnOnParanoid)
            viewModel.continueFromWarning()
            typeThePhrase()
            viewModel.confirmPhrase()
            viewModel.pinChanged("395172")
            viewModel.confirmationChanged("395172")
            viewModel.submit()
            services.outcome.complete(AccountSecurityOutcome.ParanoidOnUnfinished)
        }
        compose.onNodeWithText("Paranoid mode is on, but the change did not finish", substring = true).assertExists()
        compose.onNodeWithText("Change the account PIN…").assertExists()
    }
}
