package zekke.android.feature.settings

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ActivityScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var shown = 0
    private val opened = mutableListOf<RecentKind>()
    private val clock = NotificationClock(now = { Instant.parse("2026-10-18T12:00:00Z") })

    private fun show(unread: Int = 0, recent: RecentState = RecentState()) = compose.setContent {
        ZekkeTheme {
            ActivityScreen(
                unreadAlerts = unread,
                alerts = { Text("alerts body") },
                recent = recent,
                onRecentShown = { shown++ },
                onOpenRecent = { opened += it },
                clock = clock,
            )
        }
    }

    @Test
    fun alertsShowFirstWithTheirUnreadCount() {
        show(unread = 2)
        compose.onNodeWithText("alerts body").assertExists()
        compose.onNodeWithText("2").assertExists()
        assertEquals(0, shown)
    }

    @Test
    fun theRecentTabAsksForItsEntriesAndListsThem() {
        show(
            recent = RecentState(
                loaded = true,
                entries = listOf(
                    RecentEntry(RecentKind.Note, "n", "Trip ideas", "2026-10-18T11:48:00Z"),
                    RecentEntry(RecentKind.Password, "p", "Nubank", "2026-10-18T10:00:00Z"),
                    RecentEntry(RecentKind.Secret, "s", "Savings wallet", "2026-10-17T11:00:00Z"),
                    RecentEntry(RecentKind.File, "f", "Passport.pdf", "2026-10-10T11:00:00Z"),
                ),
            ),
        )
        compose.onNodeWithText("Recent").performClick()
        compose.waitForIdle()
        assertEquals(1, shown)
        compose.onNodeWithText("alerts body").assertDoesNotExist()
        compose.onNodeWithText("Trip ideas").assertExists()
        compose.onNodeWithText("Note").assertExists()
        compose.onNodeWithText("12 min ago").assertExists()
        compose.onNodeWithText("Password").assertExists()
        compose.onNodeWithText("Secret").assertExists()
        compose.onNodeWithText("File").assertExists()
    }

    @Test
    fun anEntryOpensItsSection() {
        show(recent = RecentState(loaded = true, entries = listOf(RecentEntry(RecentKind.Password, "p", "Nubank", "2026-10-18T10:00:00Z"))))
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("Nubank").performClick()
        assertEquals(listOf(RecentKind.Password), opened)
    }

    @Test
    fun anEmptyRecentSaysSo() {
        show(recent = RecentState(loaded = true))
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("Nothing yet. What you add or change shows up here.").assertExists()
    }
}
