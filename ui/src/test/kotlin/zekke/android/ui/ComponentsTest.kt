package zekke.android.ui

import android.app.AlarmManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import zekke.android.ui.clipboard.CLIPBOARD_CLEAR_AFTER_MILLIS
import zekke.android.ui.clipboard.ClipboardClearReceiver
import zekke.android.ui.clipboard.EXTRA_IS_SENSITIVE
import zekke.android.ui.clipboard.SensitiveClipboard
import zekke.android.ui.components.PinField
import zekke.android.ui.components.SecretField
import zekke.android.ui.components.acceptPinInput
import zekke.android.ui.components.pinCompleted
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private val isPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    private val isNotPassword = SemanticsMatcher.keyNotDefined(SemanticsProperties.Password)

    @Test
    fun aSecretIsMaskedUntilRevealedAndMaskedAgainOnHide() {
        compose.setContent {
            ZekkeTheme {
                var value by remember { mutableStateOf("correct horse") }
                SecretField(value = value, onValueChange = { value = it }, label = "Value")
            }
        }
        compose.onNodeWithText("Show").assertExists()
        compose.onNode(isPassword).assertExists()
        compose.onNodeWithText("Show").performClick()
        compose.onNodeWithText("Hide").assertExists()
        compose.onNode(isPassword).assertDoesNotExist()
        compose.onNodeWithText("Hide").performClick()
        compose.onNode(isPassword).assertExists()
    }

    @Test
    fun aPinTakesSixDigitsAndCompletesOnce() {
        val completed = mutableListOf<String>()
        compose.setContent {
            ZekkeTheme {
                var value by remember { mutableStateOf("") }
                PinField(value = value, onValueChange = { value = it }, label = "PIN", onComplete = { completed += it })
            }
        }
        compose.onNodeWithContentDescription("PIN").assert(isPassword).performTextInput("12a34")
        compose.onNodeWithContentDescription("PIN").performTextInput("5678")
        assertEquals(listOf("123456"), completed)
        assertEquals("123456", acceptPinInput("12-34 5678"))
        assertTrue(pinCompleted("12345", "123456"))
        assertTrue(!pinCompleted("123456", "123456"))
        compose.onNode(isNotPassword and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertDoesNotExist()
    }

    @Test
    fun aCopiedSecretIsMarkedSensitiveAndClearedLater() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        SensitiveClipboard(context).copy("Password", "s3nha")
        val clip = clipboard.primaryClip
        assertNotNull(clip)
        assertEquals("s3nha", clip!!.getItemAt(0).text.toString())
        assertTrue(clip.description.extras.getBoolean(EXTRA_IS_SENSITIVE))

        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        val scheduled = alarms.peekNextScheduledAlarm()
        assertNotNull(scheduled)
        assertTrue(scheduled!!.triggerAtMs - android.os.SystemClock.elapsedRealtime() in (CLIPBOARD_CLEAR_AFTER_MILLIS - 1_000)..CLIPBOARD_CLEAR_AFTER_MILLIS)

        ClipboardClearReceiver().onReceive(context, Intent())
        assertNull(clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.takeIf { it.isNotEmpty() })
    }
}
