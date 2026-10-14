package zekke.android.session

import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import zekke.android.clients.ClientPolicyMonitor
import zekke.android.feature.unlock.ClientVersionStatus
import zekke.android.ui.theme.ZekkeTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClientGateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anUnsupportedVersionReplacesEveryScreen() {
        compose.setContent {
            ZekkeTheme { ClientGate(ClientVersionStatus.UpgradeRequired("0.2.0"), keepContent = false) { Text("vault") } }
        }
        compose.onNodeWithText("Update Zekke").assertExists()
        compose.onNodeWithText("vault").assertDoesNotExist()
    }

    @Test
    fun theKitStepIsKeptUntilSaved() {
        compose.setContent {
            ZekkeTheme { ClientGate(ClientVersionStatus.UpgradeRequired("0.2.0"), keepContent = true) { Text("vault") } }
        }
        compose.onNodeWithText("vault").assertExists()
        compose.onNodeWithText("Update Zekke").assertDoesNotExist()
    }

    @Test
    fun aSupportedVersionShowsTheApp() {
        var status by mutableStateOf<ClientVersionStatus>(ClientVersionStatus.Current)
        compose.setContent { ZekkeTheme { ClientGate(status, keepContent = false) { Text("vault") } } }
        val statuses = listOf(
            ClientVersionStatus.Current,
            ClientVersionStatus.UpdateAvailable("0.2.0"),
            ClientVersionStatus.Deprecated("0.3.0", 1_796_126_400_000L),
        )
        for (supported in statuses) {
            status = supported
            compose.waitForIdle()
            compose.onNodeWithText("vault").assertExists()
            compose.onNodeWithText("Update Zekke").assertDoesNotExist()
        }
    }

    @Test
    fun a426ReachesTheGateThroughTheMonitor() {
        val flag = MutableStateFlow(false)
        val scope = TestScope()
        val monitor = ClientPolicyMonitor("0.1.0", { null }, flag, scope, {})
        scope.runCurrent()
        compose.setContent {
            val status by monitor.status.collectAsState()
            ZekkeTheme { ClientGate(status, keepContent = false) { Text("vault") } }
        }
        compose.onNodeWithText("vault").assertExists()
        flag.value = true
        scope.runCurrent()
        compose.waitForIdle()
        compose.onNodeWithText("Update Zekke").assertExists()
        compose.onNodeWithText("vault").assertDoesNotExist()
    }
}
