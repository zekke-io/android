package zekke.android.feature.onboarding

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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
class EnrolmentScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeOnboardingServices()
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = OnboardingViewModel(services)
        compose.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme { OnboardingFlow(state, viewModel, "0.1.0") }
        }
        compose.onNodeWithText("I have a recovery phrase").performClick()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun field(index: Int) = compose.onAllNodes(hasSetTextAction())[index]

    @Test
    fun theKeyboardsNextKeyMovesToTheNextWordEvenWithSuggestionsShown() {
        field(0).performClick()
        field(0).performTextInput("aba")
        compose.onNodeWithText("abandon").assertExists()
        field(0).performImeAction()
        field(1).assertIsFocused()
        field(1).performTextInput("abandon")
        field(1).performImeAction()
        field(2).assertIsFocused()
    }

    @Test
    fun aSuggestionFillsTheWordAndMovesOn() {
        field(0).performClick()
        field(0).performTextInput("aba")
        compose.onNodeWithText("abandon").performClick()
        compose.runOnIdle { check(viewModel.phraseWord(0) == "abandon") }
        field(1).assertIsFocused()
    }

    @Test
    fun aFullAccountShowsItsDevicesAsRowsToTick() {
        compose.runOnIdle {
            VALID_PHRASE.forEachIndexed { index, word -> viewModel.phraseWordChanged(index, word) }
            viewModel.confirmEnteredPhrase()
            viewModel.enrolPinChanged("395172")
            viewModel.enrolPinConfirmationChanged("395172")
            viewModel.submitEnrolment()
            services.enrolment.completeExceptionally(
                DeviceEnrolmentException(
                    DeviceEnrolmentFailure.TooManyDevices(listOf(EnrolmentDevice("11111111-1111-4111-8111-1111d911b424", "full", "2026-10-03T10:00:00Z"))),
                ),
            )
        }
        compose.onNodeWithText("Remove selected devices and add this phone").assertIsNotEnabled()
        val row = compose.onNodeWithText("Device ending …d911b424", useUnmergedTree = true)
        row.assertExists()
        compose.onAllNodesWithText("Added", substring = true)[0].assertIsOff()
        compose.onAllNodesWithText("Added", substring = true)[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Added", substring = true)[0].assertIsOn()
    }

    @Test
    fun saysWhenNoAccountUsesThePhraseAndSuggestsWaitingAfterTwoRefusals() {
        compose.runOnIdle {
            VALID_PHRASE.forEachIndexed { index, word -> viewModel.phraseWordChanged(index, word) }
            viewModel.confirmEnteredPhrase()
            viewModel.enrolPinChanged("395172")
            viewModel.enrolPinConfirmationChanged("395172")
            viewModel.submitEnrolment()
            services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.NoAccount))
        }
        compose.onNodeWithText("No account uses this recovery phrase yet. Check it, or create a new account with it.").assertExists()
        repeat(2) {
            compose.runOnIdle {
                services.enrolment = kotlinx.coroutines.CompletableDeferred()
                viewModel.enrolPinChanged("395172")
                viewModel.enrolPinConfirmationChanged("395172")
                viewModel.submitEnrolment()
                services.enrolment.completeExceptionally(DeviceEnrolmentException(DeviceEnrolmentFailure.Refused))
            }
        }
        compose.onNodeWithText("Your account PIN was not accepted. Check it and try again.").assertExists()
        compose.onNodeWithText("wait before trying again", substring = true).assertExists()
    }
}
