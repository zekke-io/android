package zekke.android.feature.unlock

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import zekke.android.ui.theme.ZekkeTheme

@RunWith(AndroidJUnit4::class)
class UnlockFlowDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sixthDigitSubmitsAutomaticallyAndShowsRemainingAttempts() {
        val services = DeviceUnlockServices()
        val viewModel = UnlockViewModel(services)
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            ZekkeTheme {
                UnlockFlow(state = state, viewModel = viewModel, onAddRecoveryPhrase = {})
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.unlock_pin_label))
            .performTextInput("123456")

        composeRule.waitUntil(timeoutMillis = 5_000) { services.receivedPin != null }
        assertEquals("123456", services.receivedPin)

        val expected = context.resources.getQuantityString(R.plurals.unlock_attempts_remaining, 4, 4)
        composeRule.onNodeWithText(expected).assertExists()
    }
}

private class DeviceUnlockServices : UnlockServices {
    @Volatile
    var receivedPin: String? = null
        private set

    override suspend fun unlock(pin: CharArray): UnlockAttempt {
        receivedPin = String(pin)
        return UnlockAttempt.WrongPin(attemptsRemaining = 4)
    }

    override suspend fun forgetThisPhone() = Unit
}
