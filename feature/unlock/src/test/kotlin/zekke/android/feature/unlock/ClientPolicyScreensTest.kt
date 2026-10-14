package zekke.android.feature.unlock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.theme.ZekkeTheme
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClientPolicyScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val locale = Locale.getDefault()
    private val zone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(locale)
        TimeZone.setDefault(zone)
    }

    @Test
    fun theUpdateScreenSaysWhatToDoAndTheLatestVersion() {
        composeRule.setContent { ZekkeTheme { UpgradeRequiredScreen("0.2.0") } }
        composeRule.onNodeWithText("Update Zekke").assertIsDisplayed()
        composeRule.onNodeWithText("This version is no longer supported, so it cannot reach your vault. Your vault is safe in your account. Update the app and open it again.").assertIsDisplayed()
        composeRule.onNodeWithText("The current version is 0.2.0.").assertIsDisplayed()
        composeRule.onNodeWithText("Update Zekke where you installed it from, then open it again.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("PIN").assertDoesNotExist()
    }

    @Test
    fun withoutAKnownLatestTheLineIsLeftOut() {
        composeRule.setContent { ZekkeTheme { UpgradeRequiredScreen(null) } }
        composeRule.onNodeWithText("Update Zekke").assertIsDisplayed()
        composeRule.onNodeWithText("The current version is", substring = true).assertDoesNotExist()
    }

    @Test
    fun aDeprecationShowsItsDateInThePhonesLocale() {
        composeRule.setContent { ZekkeTheme { VersionNotice(ClientVersionStatus.Deprecated("0.3.0", 1_796_126_400_000L)) {} } }
        composeRule.onNodeWithText("This version stops working on Dec 1, 2026. Update the app before then to keep using it.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dismiss").assertDoesNotExist()
    }

    @Test
    fun anAvailableUpdateCanBeDismissed() {
        var dismissed = 0
        composeRule.setContent { ZekkeTheme { VersionNotice(ClientVersionStatus.UpdateAvailable("0.2.0")) { dismissed++ } } }
        composeRule.onNodeWithText("Version 0.2.0 of Zekke is available. Update the app when you can.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun aCurrentVersionShowsNothing() {
        composeRule.setContent { ZekkeTheme { VersionNotice(ClientVersionStatus.Current) {} } }
        composeRule.onNodeWithText("available", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("stops working", substring = true).assertDoesNotExist()
    }
}
