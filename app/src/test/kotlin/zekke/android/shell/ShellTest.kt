package zekke.android.shell

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.sync.SectionCounts
import zekke.android.sync.SyncFailure
import zekke.android.sync.SyncStatus
import zekke.core.feed.FeedScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ShellTest {
    @get:Rule
    val compose = createComposeRule()

    private var locks = 0
    private var opened = 0

    private fun show(status: SyncStatus) = compose.setContent {
        Shell(
            status = status,
            onLock = { locks++ },
            notices = { Text("a notice") },
            settings = { openNotifications ->
                Text("settings body", Modifier.clickable { openNotifications() })
            },
            notifications = { Text("notifications body") },
            listPages = mapOf<Destination, @Composable () -> Unit>(Destination.Passwords to { Text("passwords body") }),
        )
    }

    private val held = listOf(FeedScope.PASSWORDS, FeedScope.SECRETS, FeedScope.NOTES, FeedScope.FILES)

    @Test
    fun homeKeepsTheMarkerTheNoticesAndLock() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithText("Your account is open on this phone.").assertExists()
        compose.onNodeWithText("a notice").assertExists()
        compose.onNodeWithText("Lock").performScrollTo().performClick()
        assertEquals(1, locks)
    }

    @Test
    fun onlyTheHeldSectionsHaveATile() {
        show(SyncStatus(active = true, heldScopes = listOf(FeedScope.PASSWORDS, FeedScope.NOTES)))
        compose.onNodeWithText("Passwords").assertExists()
        compose.onNodeWithText("Notes").assertExists()
        compose.onNodeWithText("Vault").assertDoesNotExist()
        compose.onNodeWithText("Drive").assertDoesNotExist()
    }

    @Test
    fun aSectionShowsItsCountAndGoesBackToHome() {
        show(SyncStatus(active = true, heldScopes = held, counts = SectionCounts(vault = 3), lastSyncEpochSeconds = 1))
        compose.onNodeWithText("Vault").performClick()
        compose.onNodeWithText("3 secrets on this phone").assertExists()
        compose.onNodeWithText("Up to date").assertExists()
        compose.onNodeWithText("Your account is open on this phone.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your account is open on this phone.").assertExists()
    }

    @Test
    fun thePasswordsTileOpensThePasswordsScreenUnderItsBar() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithText("Passwords").performClick()
        compose.onNodeWithText("passwords body").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your account is open on this phone.").assertExists()
    }

    @Test
    fun anEmptySectionAndASingleItemAreWordedApart() {
        show(SyncStatus(active = true, heldScopes = held, counts = SectionCounts(vault = 1)))
        compose.onNodeWithText("Vault").performClick()
        compose.onNodeWithText("1 secret on this phone").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("Nothing here yet.").assertExists()
    }

    @Test
    fun theSyncFailureAndTheOutboxAreShownInASection() {
        show(SyncStatus(active = true, heldScopes = held, failure = SyncFailure.Offline, outboxPending = 2, outboxWaiting = 1))
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithText("This phone is offline. What it already holds is shown.").assertExists()
        compose.onNodeWithText("2 changes are waiting to be sent.").assertExists()
        compose.onNodeWithText("1 change is waiting for the keys on this phone to catch up.").assertExists()
        compose.onNodeWithText("Not updated yet").assertExists()
    }

    @Test
    fun theSessionOverFailureHasItsOwnWords() {
        show(SyncStatus(active = true, heldScopes = held, failure = SyncFailure.SessionOver))
        compose.onNodeWithText("Drive").performClick()
        compose.onNodeWithText("Your session ended. Lock this phone and unlock it again to keep it up to date.").assertExists()
    }

    @Test
    fun theSectionPageHasLockToo() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithText("Notes").performClick()
        compose.onNodeWithContentDescription("Lock").performClick()
        assertEquals(1, locks)
    }

    @Test
    fun theBellOpensNotificationsAndBackReturnsHome() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithText("notifications body").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your account is open on this phone.").assertExists()
    }

    @Test
    fun notificationsOpenedFromSettingsGoBackToSettings() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("settings body").performClick()
        compose.onNodeWithText("notifications body").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("settings body").assertExists()
    }

    @Test
    fun noBadgeWhenNothingIsUnread() {
        show(SyncStatus(active = true, heldScopes = held))
        compose.onNodeWithContentDescription("Notifications").assertExists()
        compose.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun theBadgeIsCappedAtNinetyNinePlus() {
        show(SyncStatus(active = true, heldScopes = held, unreadNotifications = 150))
        compose.onNodeWithText("99+").assertExists()
    }

    @Test
    fun theBellNamesTheUnreadCount() {
        show(SyncStatus(active = true, heldScopes = held, unreadNotifications = 3))
        compose.onNodeWithContentDescription("Notifications, 3 unread").assertExists()
        compose.onNodeWithText("3").assertExists()
    }
}
