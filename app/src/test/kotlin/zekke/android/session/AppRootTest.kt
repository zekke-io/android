package zekke.android.session

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.core.session.SessionState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppRootTest {
    @get:Rule
    val compose = createComposeRule()

    class ScreenModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun aLockDropsEveryScreensStateAndShowsTheLockedContent() {
        val viewModels = SessionViewModels()
        var state by mutableStateOf(SessionState.UNLOCKED)
        val models = mutableListOf<ScreenModel>()
        compose.setContent {
            val epoch by viewModels.epoch.collectAsState()
            AppRoot(
                state = state,
                epoch = epoch,
                viewModels = viewModels,
                locked = { Text("unlock") },
                unlocked = {
                    val model: ScreenModel = viewModel()
                    if (models.lastOrNull() !== model) models += model
                    var typed by rememberSaveable { mutableStateOf("") }
                    Text("screen:$typed", Modifier.clickable { typed = "plaintext" })
                },
            )
        }
        compose.onNodeWithText("screen:").performClick()
        compose.onNodeWithText("screen:plaintext").assertExists()

        viewModels.clear()
        state = SessionState.LOCKED
        compose.waitForIdle()
        compose.onNodeWithText("unlock").assertExists()
        assertEquals(true, models.single().cleared)

        state = SessionState.UNLOCKED
        compose.waitForIdle()
        compose.onNodeWithText("screen:").assertExists()
        assertEquals(2, models.size)
        assertNotSame(models[0], models[1])
    }
}
