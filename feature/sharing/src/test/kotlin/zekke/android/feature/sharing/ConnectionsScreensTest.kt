package zekke.android.feature.sharing

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.theme.ZekkeTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ConnectionsScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val services = FakeSharingServices()
    private lateinit var model: ConnectionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun show() {
        model = ConnectionsViewModel(services)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                ConnectionsScreen(state, model)
            }
        }
    }

    private fun text(value: String) = compose.onNodeWithText(value, substring = true)

    @Test
    fun theInviteFormExplainsAndTheKeyboardSendsTheInvitation() {
        show()
        text("Ask them for their username").assertExists()
        text("Anything you send can be copied").assertExists()
        text("Send invitation").assertIsNotEnabled()
        text("Their username").performTextInput("bob")
        text("Send invitation").assertIsEnabled()
        text("Their username").performImeAction()
        assertEquals(listOf("bob"), services.invited)
        text("Invitation sent to bob.").assertExists()
    }

    @Test
    fun eachInviteRefusalIsSaidInTheWebsWords() {
        show()
        val cases = listOf(
            InviteOutcome.UnknownUsername to "No account currently uses that username.",
            InviteOutcome.AlreadyConnected to "You are already connected to that account, or have an invitation pending with it.",
            InviteOutcome.NotTrusted(TrustAlarm.RootChanged) to "This connection’s account key is not the one you first checked.",
            InviteOutcome.NotTrusted(null) to "Nothing can be sent through a connection to this account right now.",
        )
        cases.forEach { (outcome, words) ->
            services.inviteOutcome = outcome
            text("Their username").performTextInput("x")
            text("Send invitation").performClick()
            text(words).assertExists()
            model.dismissInviteProblem()
            model.usernameChanged("")
        }
    }

    @Test
    fun aConnectionWithAChangedKeyShowsDoNotSendAndItsReason() {
        services.items = listOf(connection("1", "ann"), connection("2", "bob"))
        services.alarms = mapOf("1" to TrustAlarm.RootChanged, "2" to null)
        show()
        text("DO NOT SEND").assertExists()
        text("This connection’s account key is not the one you first checked.").assertExists()
        assertEquals(1, compose.onAllNodesWithTextCount("DO NOT SEND"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(value: String) =
        onAllNodesWithText(value).fetchSemanticsNodes().size

    @Test
    fun eachDangerStateAndTheWarningHaveTheirOwnWords() {
        services.items = listOf(connection("1", "ann"), connection("2", "bob"), connection("3", "cat"), connection("4", "dee"))
        services.alarms = mapOf(
            "1" to TrustAlarm.RootChanged,
            "2" to TrustAlarm.ProofInvalid,
            "3" to TrustAlarm.AccountChanged,
            "4" to TrustAlarm.Unresolvable,
        )
        show()
        text("An account key never changes").assertExists()
        text("could not be traced back to their account key").assertExists()
        text("now leads to a different account").assertExists()
        text("could not be checked, so nothing can be sent").assertExists()
        assertEquals(3, compose.onAllNodesWithTextCount("DO NOT SEND"))
    }

    @Test
    fun anInvitationAwaitingMeOpensTheFingerprintReview() {
        services.items = listOf(connection("2", "bob", ConnectionStanding.WaitingForMe))
        show()
        text("Waiting for you").assertExists()
        text("Review").performClick()
        text("Check their fingerprint before you accept").assertExists()
        text("bob’s fingerprint").assertExists()
        text("AAAA-BBBB-CCCC-DDDD-EEEE-FFFF").assertExists()
        text("1111-2222-3333-4444-5555-6666").assertExists()
        text("It matches — accept").assertIsEnabled()
    }

    @Test
    fun aReviewWithAnAlarmCannotBeAccepted() {
        services.items = listOf(connection("2", "bob", ConnectionStanding.WaitingForMe))
        services.reviewResult = ReviewLoad.Ready("AAAA-BBBB", "1111-2222", TrustAlarm.ProofInvalid)
        show()
        text("Review").performClick()
        text("could not be traced back to their account key").assertExists()
        text("It matches — accept").assertIsNotEnabled()
    }

    @Test
    fun acceptingAndDecliningCallTheServices() {
        services.items = listOf(connection("2", "bob", ConnectionStanding.WaitingForMe))
        show()
        text("Review").performClick()
        text("It matches — accept").performClick()
        assertEquals(listOf("2"), services.accepted)
        text("Review").performClick()
        text("Decline").performScrollTo().performClick()
        assertEquals(listOf("2"), services.disconnected)
    }

    @Test
    fun aConnectionAwaitingTheOtherPersonSaysSoAndHasNoDisconnect() {
        services.items = listOf(connection("3", "cat", ConnectionStanding.WaitingForThem))
        show()
        text("WAITING FOR THEM").assertExists()
        text("cat").performClick()
        text("Save nickname").assertExists()
        assertEquals(0, compose.onAllNodesWithTextCount("Disconnect"))
    }

    @Test
    fun theNicknameIsSavedFromTheSheetAndShownInsteadOfTheUsername() {
        services.items = listOf(connection("1", "ann"))
        show()
        text("ann").performClick()
        text("Only you see it").assertExists()
        text("Nickname").performTextInput("Annie")
        text("Save nickname").performClick()
        assertEquals(listOf("1" to "Annie"), services.renamed)
    }

    @Test
    fun disconnectingNeedsTheConfirmationWhichStatesTheConsequence() {
        services.items = listOf(connection("1", "ann"))
        show()
        text("ann").performClick()
        compose.onNodeWithText("Disconnect").performScrollTo().performClick()
        text("Disconnect from ann?").assertExists()
        text("Every item you shared with each other stops being shared").assertExists()
        compose.onNodeWithText("Keep it").performClick()
        assertTrue(services.disconnected.isEmpty())
        text("ann").performClick()
        compose.onNodeWithText("Disconnect").performScrollTo().performClick()
        compose.onNodeWithText("Disconnect").performClick()
        assertEquals(listOf("1"), services.disconnected)
    }

    @Test
    fun aLimitedDeviceIsOfferedNoDisconnect() {
        val limited = FakeSharingServices(canDisconnect = false)
        limited.items = listOf(connection("1", "ann"))
        model = ConnectionsViewModel(limited)
        compose.setContent {
            ZekkeTheme {
                val state by model.state.collectAsState()
                ConnectionsScreen(state, model)
            }
        }
        text("ann").performClick()
        assertEquals(0, compose.onAllNodesWithTextCount("Disconnect"))
    }

    @Test
    fun openingThePageAgainReadsTheConnectionsAgain() {
        show()
        val before = services.loads
        model.refresh()
        assertEquals(before + 1, services.loads)
    }

    @Test
    fun theEmptyListAndAFailureAreShown() {
        services.loadFailure = ItemFailure.Offline
        show()
        text("You have no connections yet.").assertExists()
        text("This phone is offline.").assertExists()
    }
}
