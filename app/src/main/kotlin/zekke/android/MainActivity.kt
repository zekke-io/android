package zekke.android

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.inject
import zekke.android.feature.onboarding.StartScreen
import zekke.android.session.AppRoot
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.android.session.SessionViewModels
import zekke.android.ui.theme.ZekkeTheme
import zekke.core.api.ClientIdentity

class MainActivity : ComponentActivity() {
    private val identity: ClientIdentity by inject()
    private val session: SessionControl by inject()
    private val guard: SessionGuard by inject()
    private val viewModels: SessionViewModels by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            val state by session.state.collectAsStateWithLifecycle()
            val epoch by viewModels.epoch.collectAsStateWithLifecycle()
            ZekkeTheme {
                AppRoot(
                    state = state,
                    epoch = epoch,
                    viewModels = viewModels,
                    locked = { StartScreen(version = identity.version) },
                    unlocked = { StartScreen(version = identity.version) },
                )
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        guard.userActivity()
    }
}
