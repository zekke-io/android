package zekke.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.android.inject
import zekke.android.feature.onboarding.StartScreen
import zekke.android.ui.ZekkeTheme
import zekke.core.api.ClientIdentity

class MainActivity : ComponentActivity() {
    private val identity: ClientIdentity by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZekkeTheme { StartScreen(version = identity.version) } }
    }
}
