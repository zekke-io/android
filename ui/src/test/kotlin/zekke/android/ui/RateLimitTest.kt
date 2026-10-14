package zekke.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.components.waitDescription
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RateLimitTest {
    @get:Rule
    val compose = createComposeRule()

    private fun waits(vararg seconds: Long?): List<String> {
        val described = mutableListOf<String>()
        compose.setContent { ZekkeTheme { seconds.forEach { described += waitDescription(it) } } }
        compose.waitForIdle()
        return described
    }

    @Test
    fun describesTheWaitInSecondsThenInMinutesRoundedUp() {
        assertEquals(
            listOf("in a moment", "in a moment", "in 1 second", "in 45 seconds", "in about a minute", "in about 2 minutes", "in about 60 minutes"),
            waits(null, 0, 1, 45, 60, 61, 3600),
        )
    }

    @Test
    fun theMessageSaysItIsNotTheAccountOrThePin() {
        var message = ""
        compose.setContent { ZekkeTheme { message = rateLimitedMessage(30) } }
        compose.waitForIdle()
        assertEquals(
            "Too many requests have come from this network, so the server is pausing them. This is not a problem with your account or your PIN. Try again in 30 seconds.",
            message,
        )
    }
}
