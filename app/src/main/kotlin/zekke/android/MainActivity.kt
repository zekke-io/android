package zekke.android

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import zekke.android.feature.onboarding.OnboardingFlow
import zekke.android.feature.onboarding.OnboardingViewModel
import zekke.android.feature.unlock.UnlockFlow
import zekke.android.feature.unlock.UnlockSessionNotices
import zekke.android.feature.unlock.UnlockViewModel
import zekke.android.platform.KeystoreDeviceVault
import zekke.android.session.AppRoot
import zekke.android.session.HomeHoldingScreen
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
    private val vault: KeystoreDeviceVault by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent {
            val state by session.state.collectAsStateWithLifecycle()
            val epoch by viewModels.epoch.collectAsStateWithLifecycle()
            val onboarding = viewModel { OnboardingViewModel(this@MainActivity.get()) }
            val onboardingState by onboarding.state.collectAsStateWithLifecycle()
            val unlock = viewModel { UnlockViewModel(this@MainActivity.get()) }
            val unlockState by unlock.state.collectAsStateWithLifecycle()
            val hasRecord = remember(onboardingState.step, state, unlockState.registrationLost, unlockState.forgotten) { vault.hasRecord() }
            ZekkeTheme {
                if (onboardingState.inProgress || onboardingState.kitLost || (!hasRecord && unlockState.registrationLost == null)) {
                    OnboardingFlow(onboardingState, onboarding, identity.version)
                } else {
                    AppRoot(
                        state = state,
                        epoch = epoch,
                        viewModels = viewModels,
                        locked = {
                            UnlockFlow(unlockState, unlock) {
                                unlock.continueWithPhrase()
                                onboarding.startEnrolling()
                            }
                        },
                        unlocked = {
                            HomeHoldingScreen(guard::lockNow) {
                                UnlockSessionNotices(
                                    unlockState,
                                    unlock::dismissChainProblem,
                                    unlock::dismissRewrapIssue,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        guard.userActivity()
    }
}
