package zekke.android.feature.passwords

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.theme.ZekkeTheme
import zekke.core.credentials.PasswordsView

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PasswordsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakePasswordsServices()
    private lateinit var model: PasswordsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        services.view = PasswordsView(
            listOf(passwordRow("1", "https://www.github.com", "ana"), passwordRow("2", "bank.example", "bob")),
            listOf(deletedRow("9", "old.example")),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        model = PasswordsViewModel(services)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                PasswordsScreen(state, model)
            }
        }
    }

    @Test
    fun theRowsShowTheSiteAndTheUsernameButNeverThePassword() {
        show()
        compose.onNodeWithText("github.com").assertExists()
        compose.onNodeWithText("ana").assertExists()
        compose.onNodeWithText("bank.example").assertExists()
        compose.onNodeWithText("pw-1").assertDoesNotExist()
    }

    @Test
    fun anEmptyVaultSaysSo() {
        services.view = PasswordsView(emptyList(), emptyList())
        show()
        compose.onNodeWithText("No passwords saved yet", substring = true).assertExists()
    }

    @Test
    fun searchNarrowsTheListAndSaysWhenNothingMatches() {
        show()
        compose.onNodeWithText("Search passwords").performTextInput("bank")
        compose.onNodeWithText("github.com").assertDoesNotExist()
        compose.onNodeWithText("bank.example").assertExists()
        compose.onNodeWithText("Search passwords").performTextInput("zzz")
        compose.onNodeWithText("No password matches that search.").assertExists()
    }

    @Test
    fun aFailureShowsItsWordsAndCanBeDismissed() {
        services.loadFailure = ItemFailure.Offline
        show()
        compose.onNodeWithText("This phone is offline. Nothing was changed. Try again when it is back online.").assertExists()
    }

    @Test
    fun theAddFormEnablesItsButtonOnlyWhenComplete() {
        show()
        compose.onNodeWithContentDescription("Add a password").performClick()
        compose.onNodeWithText("Add password").assertIsNotEnabled()
        compose.onNodeWithText("Site").performTextInput("example.org")
        compose.onNodeWithText("Username").performTextInput("carol")
        compose.onNodeWithText("Password").performTextInput("secret")
        compose.onNodeWithText("Add password").assertIsEnabled()
        compose.onNodeWithText("Add password").performClick()
        assertEquals("example.org", services.saved.single().first.site)
    }

    @Test
    fun theKeyboardsNextMovesThroughTheFieldsInOrder() {
        show()
        compose.onNodeWithContentDescription("Add a password").performClick()
        compose.onNodeWithText("Site").performClick()
        compose.onNodeWithText("Site").performImeAction()
        compose.onNodeWithText("Username").assertIsFocused()
        compose.onNodeWithText("Username").performImeAction()
        compose.onNodeWithText("Password").assertIsFocused()
        compose.onNodeWithText("Password").performImeAction()
        compose.onNodeWithText("Note").assertIsFocused()
        compose.onNodeWithText("Note").performImeAction()
        compose.onNodeWithText("Also used on").assertIsFocused()
    }

    @Test
    fun openingARowEditsItAndOffersDeleteWithAConfirmation() {
        show()
        compose.onNodeWithText("bank.example").performClick()
        compose.onNodeWithText("Edit password").assertExists()
        compose.onNodeWithText("Save revision").assertExists()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete this password?").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        compose.onNodeWithText("Delete this password?").assertDoesNotExist()
    }

    @Test
    fun theDeletedPageRestoresAndPurgesWithAConfirmation() {
        show()
        compose.onNodeWithText("Recently deleted").performClick()
        compose.onNodeWithText("old.example").assertExists()
        compose.onNodeWithText("Restore").performClick()
        assertEquals(listOf("9"), services.restored)
        compose.onNodeWithText("Delete permanently").performScrollTo().performClick()
        compose.onNodeWithText("Delete this password permanently?").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        assertEquals(emptyList<List<String>>(), services.purged)
    }

    @Test
    fun aDeviceThatIsNotFullIsNotOfferedPermanentDeletion() {
        val limited = FakePasswordsServices(canPurge = false)
        limited.view = services.view
        model = PasswordsViewModel(limited)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                PasswordsScreen(state, model)
            }
        }
        compose.onNodeWithText("Recently deleted").performClick()
        compose.onNodeWithText("Restore").assertExists()
        compose.onNodeWithText("Delete permanently").assertDoesNotExist()
    }
}
