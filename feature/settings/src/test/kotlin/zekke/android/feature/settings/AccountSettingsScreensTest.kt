package zekke.android.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
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
class AccountSettingsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeAccountSettingsServices()
    private lateinit var model: AccountSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show(fake: FakeAccountSettingsServices = services) {
        model = AccountSettingsViewModel(fake)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    AccountSettingsSection(state, model)
                    DeleteAccountSection(state, model)
                    DeleteAccountSheet(state, model)
                }
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    private fun count(value: String) = compose.onAllNodesWithText(value).fetchSemanticsNodes().size

    @Test
    fun theCurrentUsernameShowsWithTheWebsPermanenceAndOldNameNotices() {
        show()
        text("Current username").assertExists()
        assert(count("ann") >= 1)
        text("Changing your username adds a name, it does not remove one").assertExists()
        text("Your old name stops working the moment you change it").assertExists()
    }

    @Test
    fun aBadUsernameIsRefusedBeforeSendingAndANewOneIsAnnounced() {
        show()
        compose.onNodeWithText("Username").performTextInput("a")
        text("Change username").performClick()
        text("A username is 3 to 64 characters").assertExists()
        compose.onNodeWithText("Username").performTextReplacement("bobby")
        text("Change username").performClick()
        text("You are now bobby.").assertExists()
        assertEquals(listOf("bobby"), services.renamed)
    }

    @Test
    fun aTakenNameIsSaidWithoutGuessingWhoHoldsIt() {
        services.renameResult = UsernameOutcome.Unavailable
        show()
        compose.onNodeWithText("Username").performTextInput("bobby")
        text("Change username").performClick()
        text("That username is unavailable. Choose another one.").assertExists()
    }

    @Test
    fun aLimitedPhoneCannotRenameOrDelete() {
        show(FakeAccountSettingsServices(fullDevice = false))
        text("This phone cannot change the username").assertExists()
        text("This phone cannot delete the account").assertExists()
        assertEquals(0, count("Delete my account…"))
    }

    @Test
    fun theDeletionExplainsWhatItMeansAndThatTheTrashRetentionDoesNotApply() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", false, 30, true))
        show()
        text("This cannot be undone, by you or by anyone at Zekke").assertExists()
        text("the 30 days that deleted files are normally kept do not apply").assertExists()
        text("Your card subscription still renews").assertExists()
        text("Cancel it from the web app").assertExists()
    }

    @Test
    fun theSheetKeepsTheDeleteButtonDisabledUntilPhraseAndConfirmation() {
        show()
        text("Delete my account…").performScrollTo().performClick()
        text("Your recovery phrase signs the deletion").assertExists()
        compose.onNodeWithText("Delete my account").assertIsNotEnabled()
        model.phrase.acceptScanned(TWELVE_WORDS)
        text("I understand that my account and everything in it will be gone for good.").performScrollTo().performClick()
        compose.onNodeWithText("Delete my account").assertIsEnabled()
    }

    @Test
    fun aParanoidAccountIsAskedForItsAccountPin() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 0, false))
        show()
        text("Delete my account…").performScrollTo().performClick()
        text("Account PIN").assertExists()
    }

    @Test
    fun aRefusedPinSaysOneThingAndSuggestsWaitingAfterTwo() {
        services.profile = ProfileLoad.Loaded(AccountProfile("ann", true, 0, false))
        services.deleteResult = DeleteOutcome.PinRefused
        show()
        text("Delete my account…").performScrollTo().performClick()
        model.phrase.acceptScanned(TWELVE_WORDS)
        model.confirmedChanged(true)
        model.pinChanged("395172")
        compose.onNodeWithText("Delete my account").performClick()
        text("That did not work. Nothing was changed. Check your recovery phrase and account PIN.").assertExists()
        model.pinChanged("395172")
        compose.onNodeWithText("Delete my account").performClick()
        text("wait a few minutes before trying again").assertExists()
    }
}
