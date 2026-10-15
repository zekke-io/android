package zekke.android.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.material3.Text
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
import zekke.core.scopes.Scope

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DevicesScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeDevicesServices()
    private lateinit var model: DevicesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.devices = listOf(
            device("me", thisDevice = true),
            device("web", "Laptop"),
            device("ext", scopes = listOf(Scope.PASSWORDS)),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(fake: FakeDevicesServices = services) {
        model = DevicesViewModel(fake)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                DevicesScreen(state, model, extension = { Text("extension slot") }, thisPhone = { Text("this phone slot") })
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    private fun count(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().size

    @Test
    fun theListShowsNamesBadgesScopesAndTheSlots() {
        show()
        text("Every change to your devices verifies from your recovery phrase").assertExists()
        text("Laptop").assertExists()
        text("Unnamed device").assertExists()
        text("THIS PHONE").assertExists()
        text("Passwords · added").assertExists()
        assertEquals(2, compose.onAllNodesWithText("Everything · added", substring = true).fetchSemanticsNodes().size)
        text("LIMITED DEVICE").assertExists()
        text("extension slot").assertExists()
        text("this phone slot").assertExists()
    }

    @Test
    fun aBrokenChainWarnsAndOffersNoRemoval() {
        services.chainVerified = false
        show()
        text("does not verify from your recovery phrase").assertExists()
        compose.onNodeWithText("Laptop").performClick()
        assertEquals(0, count("Remove"))
    }

    @Test
    fun aDeviceIsRenamedFromItsSheetAndTheKeyboardSavesIt() {
        show()
        compose.onNodeWithText("Laptop").performClick()
        text("Only you see it").assertExists()
        compose.onNodeWithText("Name").performTextReplacement("Work laptop")
        text("Save name").performClick()
        assertEquals(listOf("web" to "Work laptop"), services.renamed)
    }

    @Test
    fun removingShowsTheWebsWarningAndNeedsAPhrase() {
        show()
        compose.onNodeWithText("Laptop").performClick()
        compose.onNodeWithText("Remove").performClick()
        text("Removing a device changes every key it held").assertExists()
        text("Removing another device re-keys everything it could open").assertExists()
        text("Remove it").assertExists()
    }

    @Test
    fun aLimitedDeviceThatCannotNameOrRemoveHasNoActions() {
        val limited = FakeDevicesServices(fullDevice = false, canName = false)
        limited.devices = services.devices
        show(limited)
        compose.onNodeWithText("Laptop").performClick()
        assertEquals(0, count("Save name"))
        assertEquals(0, count("Remove"))
    }
}
