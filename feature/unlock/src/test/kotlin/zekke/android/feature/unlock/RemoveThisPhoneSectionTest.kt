package zekke.android.feature.unlock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class RemoveThisPhoneSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val services = FakeThisPhoneServices()
    private lateinit var viewModel: RemoveThisPhoneViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = RemoveThisPhoneViewModel(services)
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { RemoveThisPhoneSection(state, viewModel) }
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private val confirmation =
        "Remove this phone from your account? It forgets every key it holds, and the server stops accepting it at once. " +
            "To come back you type your recovery phrase, and nobody can issue you another. Your vault itself is untouched."

    @Test
    fun theButtonOpensTheWebConfirmation() {
        composeRule.onNodeWithText("Takes this phone off your account’s devices. Coming back needs your recovery phrase.").assertIsDisplayed()
        click("Remove this phone")
        composeRule.onNodeWithText(confirmation).assertIsDisplayed()
        composeRule.onNodeWithText("Stay signed in").assertIsDisplayed()
    }

    @Test
    fun stayClosesTheConfirmation() {
        click("Remove this phone")
        click("Stay signed in")
        composeRule.onNodeWithText(confirmation).assertDoesNotExist()
        composeRule.onNodeWithText("Remove this phone").assertIsDisplayed()
    }

    @Test
    fun confirmingCallsTheServiceAndDisablesBothButtonsWhileRemoving() {
        services.pending = CompletableDeferred()
        click("Remove this phone")
        click("Remove this phone")
        assertEquals(1, services.calls)
        composeRule.onNodeWithText("Removing…").assertIsNotEnabled()
        composeRule.onNodeWithText("Stay signed in").assertIsNotEnabled()
    }

    @Test
    fun eachFailureShowsItsNotice() {
        val failures = listOf(
            RemovalOutcome.Offline to "Removing this phone needs the server, and it cannot be reached right now. Check your connection and try again. This phone is still one of your devices.",
            RemovalOutcome.RateLimited(30) to "Too many requests have come from this network, so the server is pausing them. This is not a problem with your account or your PIN. Try again in 30 seconds.",
            RemovalOutcome.Refused to "The server refused this change to your devices, and nothing was changed. Try again.",
            RemovalOutcome.ChainProblem to "The history of your devices that the server returned does not verify from your recovery phrase’s key, so this phone was not removed. Someone may have tampered with it. Add no device until this is resolved.",
            RemovalOutcome.PhoneLocked to "Unlock the phone first, then try again.",
            RemovalOutcome.Failed to "Something went wrong. Please try again.",
        )
        for ((outcome, text) in failures) {
            services.nextOutcome = outcome
            click("Remove this phone")
            click("Remove this phone")
            composeRule.onNodeWithText(text).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Dismiss").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text).assertDoesNotExist()
        }
    }
}
