package zekke.android.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.collectAsState
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
class OnboardingScreensTest {
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun theOneWayWarningAppearsOnlyForParanoid() {
        compose.onNodeWithText("Create an account").performClick()
        compose.onNodeWithText("Create my recovery phrase").performClick()
        compose.onNodeWithText("There is no way back", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Paranoid").performClick()
        compose.onNodeWithText("There is no way back", substring = true).assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("There is no way back", substring = true).assertExists()
    }

    @Test
    fun thePinStepNamesTheProblem() {
        compose.onNodeWithText("Create an account").performClick()
        compose.onNodeWithText("Create my recovery phrase").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.runOnIdle {
            viewModel.pinChanged("111111")
            viewModel.confirmationChanged("111111")
        }
        compose.onNodeWithText("Create my account").performClick()
        compose.onNodeWithText("That PIN repeats one digit. Choose a less predictable one.").assertExists()
    }

    @Test
    fun theKitStepHoldsContinueUntilTheKitIsSaved() {
        compose.runOnIdle {
            viewModel.startCreating()
            viewModel.confirmPhrase()
            viewModel.confirmMode()
            viewModel.pinChanged("482913")
            viewModel.confirmationChanged("482913")
            viewModel.submitPin()
            services.outcome.complete(CreatedAccount("quiet-otter-42", paranoid = false, paranoidFailed = false, createdAtEpochMillis = 7))
        }
        compose.onNodeWithText("Your username is quiet-otter-42. It is permanent.").assertExists()
        compose.onNodeWithText("Continue to my vault").assertIsNotEnabled()
        compose.onNodeWithText("Save as PDF").assertIsEnabled()
        compose.onNodeWithText("Print").assertIsEnabled()
        compose.onNodeWithText("1. word0").assertDoesNotExist()
        compose.onNodeWithText("Show my phrase").performClick()
        compose.onNodeWithText("1. word0").assertExists()
        compose.onNodeWithText("12. word11").assertExists()
        compose.onNodeWithText("Continue to my vault").assertIsNotEnabled()
        compose.runOnIdle { viewModel.kitDelivered() }
        compose.onNodeWithText("Your recovery kit is saved. You can save or print it again.").assertExists()
        compose.onNodeWithText("Continue to my vault").assertIsEnabled()
    }

    @Test
    fun aFailureIsShownOnThePinStep() {
        compose.runOnIdle {
            viewModel.startCreating()
            viewModel.confirmPhrase()
            viewModel.confirmMode()
            viewModel.pinChanged("482913")
            viewModel.confirmationChanged("482913")
            viewModel.submitPin()
            services.outcome.completeExceptionally(AccountCreationException(CreationFailure.Offline))
        }
        compose.onNodeWithText("Zekke could not be reached", substring = true).assertExists()
    }
}
