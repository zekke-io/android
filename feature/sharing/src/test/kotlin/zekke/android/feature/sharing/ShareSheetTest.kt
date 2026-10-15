package zekke.android.feature.sharing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
@Config(sdk = [34], application = android.app.Application::class)
class ShareSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeSharingServices()
    private var closed = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.targets = SendTargets.Loaded(listOf(connection("1", "ann"), connection("2", "bob", nickname = "Bobby")), listOf(Recipient("s9", "cat")))
        services.choicesFor = listOf(FolderChoice("f1", "Trips", 0))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(type: SharedType = SharedType.Secret, fake: FakeSharingServices = services): ShareViewModel {
        val model = ShareViewModel(fake, type, "item-1")
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                ShareSheet(state, model) { closed++ }
            }
        }
        return model
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    private fun count(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().size

    @Test
    fun theSheetIsNamedForTheKindAndListsWhoCanReceiveAndWhoAlreadyHas() {
        show(SharedType.Note)
        text("Share this note").assertExists()
        text("Send to").assertExists()
        text("ann").assertExists()
        text("Bobby").assertExists()
        text("Already sent to").assertExists()
        text("cat").assertExists()
        assertEquals(0, count("Anything you send can be copied"))
    }

    @Test
    fun sendIsDisabledUntilAConnectionIsChosenAndThenFilesInAFolder() {
        show()
        compose.onNodeWithText("Send").assertIsNotEnabled()
        text("Bobby").performClick()
        compose.onNodeWithText("Send").assertIsEnabled()
        text("Put it in").assertExists()
        compose.onNodeWithText("Trips").performScrollTo().performClick()
        compose.onNodeWithText("Send").performClick()
        assertEquals(listOf(listOf("2", SharedType.Secret, "item-1", "f1")), services.sent)
        text("Sent to Bobby.").assertExists()
    }

    @Test
    fun aRefusedSendSaysWhyInTheWebsWords() {
        services.sendOutcome = SendOutcome.NotTrusted(TrustAlarm.ProofInvalid)
        show()
        text("ann").performClick()
        compose.onNodeWithText("Send").performClick()
        text("could not be traced back to their account key").assertExists()
    }

    @Test
    fun aConnectionThatIsNotAcceptedIsSaidSo() {
        services.sendOutcome = SendOutcome.NotTrusted(null)
        show()
        text("ann").performClick()
        compose.onNodeWithText("Send").performClick()
        text("has not been accepted yet").assertExists()
    }

    @Test
    fun removingAnExistingShareIsOfferedOnAFullDevice() {
        show()
        text("Remove").performScrollTo().performClick()
        assertEquals(listOf("s9"), services.unshared)
    }

    @Test
    fun aLimitedDeviceCannotRemoveAShare() {
        val limited = FakeSharingServices(canDisconnect = false)
        limited.targets = services.targets
        show(fake = limited)
        assertEquals(0, count("Remove"))
    }

    @Test
    fun withNobodyToSendToTheEmptyStateSays() {
        services.targets = SendTargets.Loaded(emptyList(), emptyList())
        show()
        text("You have no connections yet.").assertExists()
        text("Nobody yet.").assertExists()
    }
}
