package zekke.android

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import zekke.android.feature.onboarding.OnboardingFlow
import zekke.android.clients.ClientPolicyMonitor
import zekke.android.feature.onboarding.OnboardingStep
import zekke.android.feature.onboarding.OnboardingViewModel
import zekke.android.feature.settings.AccountSecuritySection
import zekke.android.feature.settings.AccountSecuritySheet
import zekke.android.feature.settings.AccountSecurityViewModel
import zekke.android.feature.unlock.RemoveThisPhoneSection
import zekke.android.feature.unlock.RemoveThisPhoneViewModel
import zekke.android.feature.unlock.UnlockFlow
import zekke.android.feature.unlock.UnlockSessionNotices
import zekke.android.feature.unlock.UnlockViewModel
import zekke.android.feature.unlock.VersionNotice
import zekke.android.platform.KeystoreDeviceVault
import zekke.android.rekey.KeyRotationEvents
import zekke.android.session.AppRoot
import zekke.android.session.ClientGate
import zekke.android.shell.SettingsContent
import zekke.android.shell.Shell
import zekke.android.sync.SyncService
import org.koin.core.qualifier.named
import zekke.android.feature.passwords.PasswordsScreen
import zekke.android.feature.drive.DriveScreen
import zekke.android.feature.drive.DriveServices
import zekke.android.feature.drive.DriveViewModel
import zekke.android.feature.notes.NotesScreen
import zekke.android.feature.notes.NotesServices
import zekke.android.feature.notes.NotesViewModel
import zekke.android.feature.sharing.ConnectionsScreen
import zekke.android.feature.sharing.ConnectionsViewModel
import zekke.android.feature.sharing.SharingServices
import zekke.android.feature.vault.VaultScreen
import zekke.android.feature.vault.VaultServices
import zekke.android.feature.vault.VaultViewModel
import zekke.android.items.CoreFolderTabsServices
import zekke.android.shell.Destination
import zekke.android.feature.passwords.PasswordsServices
import zekke.android.feature.passwords.PasswordsViewModel
import zekke.android.feature.settings.NotificationsScreen
import zekke.android.feature.settings.NotificationsServices
import zekke.android.feature.settings.NotificationsViewModel
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.android.session.SessionViewModels
import zekke.android.ui.theme.ZekkeTheme
import zekke.android.unlock.ThisPhoneEvents
import zekke.core.api.ClientIdentity

class MainActivity : ComponentActivity() {
    private val identity: ClientIdentity by inject()
    private val session: SessionControl by inject()
    private val guard: SessionGuard by inject()
    private val viewModels: SessionViewModels by inject()
    private val vault: KeystoreDeviceVault by inject()
    private val policy: ClientPolicyMonitor by inject()
    private val events: ThisPhoneEvents by inject()
    private val rotations: KeyRotationEvents by inject()
    private val sync: SyncService by inject()

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
            val versionStatus by policy.status.collectAsStateWithLifecycle()
            val removals by events.removals.collectAsStateWithLifecycle()
            val hasRecord = remember(onboardingState.step, state, unlockState.registrationLost, unlockState.forgotten, removals) { vault.hasRecord() }
            ZekkeTheme {
                ClientGate(versionStatus, keepContent = onboardingState.step == OnboardingStep.Creating || onboardingState.step == OnboardingStep.Recovery) {
                    if (onboardingState.inProgress || onboardingState.kitLost || (!hasRecord && unlockState.registrationLost == null)) {
                        OnboardingFlow(onboardingState, onboarding, identity.version)
                    } else {
                        AppRoot(
                            state = state,
                            epoch = epoch,
                            viewModels = viewModels,
                            locked = {
                                UnlockFlow(
                                    unlockState,
                                    unlock,
                                    onAddRecoveryPhrase = {
                                        unlock.continueWithPhrase()
                                        onboarding.startEnrolling()
                                    },
                                    versionNotice = { VersionNotice(versionStatus, policy::dismissUpdateAvailable) },
                                )
                            },
                            unlocked = {
                                val rotationPending by rotations.pending.collectAsStateWithLifecycle()
                                LaunchedEffect(rotationPending) {
                                    if (rotationPending) {
                                        unlock.rewrapNow()
                                        rotations.handled()
                                    }
                                }
                                val removal = viewModel { RemoveThisPhoneViewModel(this@MainActivity.get()) }
                                val removalState by removal.state.collectAsStateWithLifecycle()
                                val security = viewModel { AccountSecurityViewModel(this@MainActivity.get()) }
                                val securityState by security.state.collectAsStateWithLifecycle()
                                val syncStatus by sync.status.collectAsStateWithLifecycle()
                                val notifications = viewModel { NotificationsViewModel(this@MainActivity.get<NotificationsServices>(), sync::unreadChanged) }
                                val notificationsState by notifications.state.collectAsStateWithLifecycle()
                                val versionNotices: @Composable () -> Unit = {
                                    VersionNotice(versionStatus, policy::dismissUpdateAvailable)
                                    UnlockSessionNotices(
                                        unlockState,
                                        unlock::dismissChainProblem,
                                        unlock::dismissRewrapIssue,
                                    )
                                }
                                val passwordsPage: @Composable () -> Unit = {
                                    val passwordsModel = viewModel { PasswordsViewModel(this@MainActivity.get<PasswordsServices>()) }
                                    val passwordsState by passwordsModel.state.collectAsStateWithLifecycle()
                                    PasswordsScreen(passwordsState, passwordsModel)
                                }
                                val vaultPage: @Composable () -> Unit = {
                                    val vaultModel = viewModel {
                                        VaultViewModel(this@MainActivity.get<VaultServices>(), this@MainActivity.get<CoreFolderTabsServices>(named("secrets-tabs")))
                                    }
                                    val vaultState by vaultModel.state.collectAsStateWithLifecycle()
                                    VaultScreen(vaultState, vaultModel)
                                }
                                val notesPage: @Composable () -> Unit = {
                                    val notesModel = viewModel {
                                        NotesViewModel(this@MainActivity.get<NotesServices>(), this@MainActivity.get<CoreFolderTabsServices>(named("notes-tabs")))
                                    }
                                    val notesState by notesModel.state.collectAsStateWithLifecycle()
                                    NotesScreen(notesState, notesModel)
                                }
                                val drivePage: @Composable () -> Unit = {
                                    val driveModel = viewModel { DriveViewModel(this@MainActivity.get<DriveServices>()) }
                                    val driveState by driveModel.state.collectAsStateWithLifecycle()
                                    DriveScreen(driveState, driveModel)
                                }
                                val sharedPage: @Composable () -> Unit = {
                                    val connectionsModel = viewModel { ConnectionsViewModel(this@MainActivity.get<SharingServices>()) }
                                    val connectionsState by connectionsModel.state.collectAsStateWithLifecycle()
                                    ConnectionsScreen(connectionsState, connectionsModel)
                                }
                                Shell(
                                    status = syncStatus,
                                    onLock = guard::lockNow,
                                    notices = versionNotices,
                                    settings = { openNotifications ->
                                        SettingsContent(
                                            status = syncStatus,
                                            onOpenNotifications = openNotifications,
                                            onDiscard = sync::discardRefused,
                                            notices = {},
                                            account = {
                                                AccountSecuritySection(securityState, security)
                                                RemoveThisPhoneSection(removalState, removal)
                                            },
                                        )
                                    },
                                    notifications = { NotificationsScreen(notificationsState, notifications) },
                                    listPages = mapOf(Destination.Passwords to passwordsPage, Destination.Vault to vaultPage, Destination.Notes to notesPage, Destination.Drive to drivePage, Destination.Shared to sharedPage),
                                )
                                AccountSecuritySheet(securityState, security)
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        guard.userActivity()
    }
}
