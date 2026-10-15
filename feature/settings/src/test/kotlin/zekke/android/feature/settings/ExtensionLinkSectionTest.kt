package zekke.android.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ExtensionLinkSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val services = FakeExtensionLinkServices()
    private lateinit var model: ExtensionLinkViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(fake: FakeExtensionLinkServices = services) {
        model = ExtensionLinkViewModel(fake, 2000) { scheduler.currentTime }
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                ExtensionLinkSection(state, model)
            }
        }
    }

    private fun settle() {
        scheduler.runCurrent()
        compose.waitForIdle()
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    private fun claim() {
        services.pollResults.add(PairingPoll.Claimed("123 456"))
        scheduler.advanceTimeBy(2000)
        settle()
    }

    @Test
    fun aDeviceThatCannotLinkShowsNothing() {
        show(FakeExtensionLinkServices(canLink = false))
        compose.onNodeWithText("Browser extension").assertDoesNotExist()
    }

    @Test
    fun theSectionExplainsWhatTheExtensionCanReadAndTheParanoidNote() {
        show()
        text("It can read and save your passwords, and nothing else").assertExists()
        text("Paranoid Mode does not protect the extension").assertExists()
    }

    @Test
    fun startingShowsTheCodeToTypeAndCancelWorks() {
        show()
        text("Connect a browser extension").performClick()
        settle()
        text("Type this code into the extension").assertExists()
        text("ABCD-2345").assertExists()
        text("5:00 left").assertExists()
        text("Cancel").performClick()
        settle()
        text("The connection was cancelled.").assertExists()
        assertEquals(listOf("pairing-1"), services.cancelled)
    }

    @Test
    fun aClaimShowsTheNumberToCompareWithTheNameField() {
        show()
        text("Connect a browser extension").performClick()
        settle()
        claim()
        text("Compare the numbers").assertExists()
        text("123 456").assertExists()
        text("Name for this extension").assertExists()
        text("The numbers match").assertExists()
    }

    @Test
    fun aDeviceWithoutTheSharingKeyIsNotAskedForAName() {
        val limited = FakeExtensionLinkServices(canNameExtension = false)
        show(limited)
        text("Connect a browser extension").performClick()
        settle()
        limited.pollResults.add(PairingPoll.Claimed("123 456"))
        scheduler.advanceTimeBy(2000)
        settle()
        text("123 456").assertExists()
        compose.onNodeWithText("Name for this extension").assertDoesNotExist()
    }

    @Test
    fun matchingLinksWithTheTypedNameAndSaysTheExtensionIsConnected() {
        show()
        text("Connect a browser extension").performClick()
        settle()
        claim()
        text("Name for this extension").performTextInput("Work laptop")
        text("The numbers match").performClick()
        settle()
        assertEquals(listOf("pairing-1" to "Work laptop"), services.linked)
        text("The extension is connected. Choose its PIN in the extension to finish.").assertExists()
    }

    @Test
    fun differentNumbersRefuseCancelTheCodeAndExplainWithoutRetry() {
        show()
        text("Connect a browser extension").performClick()
        settle()
        claim()
        text("They don’t match").performClick()
        settle()
        assertEquals(listOf("pairing-1"), services.cancelled)
        text("The connection was refused and the code is cancelled.").assertExists()
        assertTrue(services.linked.isEmpty())
        compose.onNodeWithText("The numbers match").assertDoesNotExist()
    }

    @Test
    fun anExpiredCodeSaysSoAndOffersANewOne() {
        show()
        text("Connect a browser extension").performClick()
        settle()
        scheduler.advanceTimeBy(301_000)
        settle()
        text("The code expired before it was used.").assertExists()
        text("Connect a browser extension").assertExists()
    }
}
