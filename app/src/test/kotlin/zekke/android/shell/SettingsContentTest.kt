package zekke.android.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.feature.settings.PlanInfo
import zekke.android.sync.SyncStatus
import zekke.android.ui.components.StorageReading
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Destination>()
    private var locks = 0
    private var notifications = 0
    private var shown = 0

    private val free = PlanInfo("free", "free", null, false, null, 0)
    private val storage = StorageReading(storedBytes = 128 * 1024, usedBytes = 128 * 1024, quotaBytes = 500 * 1024)

    private fun show(
        profile: SettingsProfile = SettingsProfile("alex", false),
        plan: PlanInfo? = free,
        storage: StorageReading? = this.storage,
        status: SyncStatus = SyncStatus(),
        idleMinutes: Int = 15,
    ) = compose.setContent {
        ZekkeTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsContent(
                status = status,
                profile = profile,
                plan = plan,
                storage = storage,
                idleMinutes = idleMinutes,
                onOpened = { shown++ },
                onOpenNotifications = { notifications++ },
                onDiscard = {},
                onOpen = { opened += it },
                onLock = { locks++ },
                notices = {},
                )
            }
        }
    }

    @Test
    fun theStorageCardAndTheProfileCardOpenThePlanAndTheAccount() {
        show()
        compose.onNodeWithText("128.0 KiB").assertExists()
        compose.onNodeWithText("372.0 KiB free", substring = true).assertExists()
        compose.onNodeWithText("Plan details").performClick()
        compose.onAllNodesWithText("@alex").assertCountEquals(2)
        assertEquals(listOf(Destination.SettingsPlan), opened)
    }

    @Test
    fun theRowsShowTheirValuesAndOpenTheirPages() {
        show(idleMinutes = 1)
        compose.onNodeWithText("1 minute").assertExists()
        compose.onNodeWithText("Devices").performScrollTo().performClick()
        compose.onNodeWithText("Lock when idle").performScrollTo().performClick()
        compose.onNodeWithText("Plan and storage").performScrollTo().performClick()
        assertEquals(listOf(Destination.SettingsDevices, Destination.SettingsSecurity, Destination.SettingsPlan), opened)
    }

    @Test
    fun theAccountPinIsOffUntilTheAccountIsParanoid() {
        show(SettingsProfile("alex", false))
        compose.onNodeWithText("Paranoid only").assertExists()
        compose.onNodeWithText("Account PIN").performScrollTo().performClick()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun theAccountPinOpensTheAccountWhenParanoid() {
        show(SettingsProfile("alex", true))
        compose.onNodeWithText("Paranoid only").assertDoesNotExist()
        compose.onNodeWithText("Account PIN").performScrollTo().performClick()
        assertEquals(listOf(Destination.SettingsAccount), opened)
    }

    @Test
    fun lockNowNotificationsAndDeleteAccountAreRows() {
        show(status = SyncStatus(unreadNotifications = 2))
        compose.onNodeWithText("2 unread").assertExists()
        compose.onNodeWithText("Notifications").performScrollTo().performClick()
        compose.onNodeWithText("Lock Zekke now").performScrollTo().performClick()
        compose.onNodeWithText("Delete account").performScrollTo().performClick()
        assertEquals(1, notifications)
        assertEquals(1, locks)
        assertEquals(listOf(Destination.SettingsAccount), opened)
    }

    @Test
    fun withoutAPlanOrAProfileTheCardsAreLeftOut() {
        show(SettingsProfile(null, null), plan = null, storage = null)
        compose.onNodeWithText("Plan details").assertDoesNotExist()
        compose.onNodeWithText("Username").assertExists()
    }

    @Test
    fun theScreenAsksForTheProfileWhenItOpens() {
        show()
        compose.waitForIdle()
        assertEquals(1, shown)
    }
}
