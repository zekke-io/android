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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import zekke.android.feature.settings.AboutScreen
import zekke.android.feature.settings.DevicesScreen
import zekke.android.feature.settings.PlanScreen
import zekke.android.feature.settings.PlanServices
import zekke.android.feature.settings.PlanViewModel
import zekke.android.feature.settings.SecurityScreen
import zekke.android.feature.settings.SecurityServices
import zekke.android.feature.settings.SecurityViewModel
import zekke.android.feature.settings.DevicesServices
import zekke.android.feature.settings.DevicesViewModel
import zekke.android.feature.settings.ExtensionLinkServices
import zekke.android.feature.settings.LinkStep
import zekke.android.feature.settings.ExtensionLinkSection
import zekke.android.feature.settings.ExtensionLinkViewModel
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
import zekke.android.feature.sharing.ShareSheet
import zekke.android.feature.sharing.ShareViewModel
import zekke.android.feature.sharing.SharedType
import zekke.android.feature.sharing.SharedViewModel
import zekke.android.feature.sharing.SharingScreen
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

private data class ShareTarget(val type: SharedType, val id: String)

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
                                var shareTarget by remember { mutableStateOf<ShareTarget?>(null) }
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
                                val extension = viewModel { ExtensionLinkViewModel(this@MainActivity.get<ExtensionLinkServices>()) }
                                val extensionState by extension.state.collectAsStateWithLifecycle()
                                val syncStatus by sync.status.collectAsStateWithLifecycle()
                                val notifications = viewModel { NotificationsViewModel(this@MainActivity.get<NotificationsServices>(), sync::unreadChanged) }
                                val notificationsState by notifications.state.collectAsStateWithLifecycle()
                                val versionNotices: @Composable () -> Unit = {
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
                                    VaultScreen(vaultState, vaultModel, onShare = { id -> shareTarget = ShareTarget(SharedType.Secret, id) })
                                }
                                val notesPage: @Composable () -> Unit = {
                                    val notesModel = viewModel {
                                        NotesViewModel(this@MainActivity.get<NotesServices>(), this@MainActivity.get<CoreFolderTabsServices>(named("notes-tabs")))
                                    }
                                    val notesState by notesModel.state.collectAsStateWithLifecycle()
                                    NotesScreen(notesState, notesModel, onShare = { id -> shareTarget = ShareTarget(SharedType.Note, id) })
                                }
                                val drivePage: @Composable () -> Unit = {
                                    val driveModel = viewModel { DriveViewModel(this@MainActivity.get<DriveServices>()) }
                                    val driveState by driveModel.state.collectAsStateWithLifecycle()
                                    DriveScreen(driveState, driveModel, onShare = { id -> shareTarget = ShareTarget(SharedType.File, id) })
                                }
                                val sharedPage: @Composable () -> Unit = {
                                    val connectionsModel = viewModel { ConnectionsViewModel(this@MainActivity.get<SharingServices>()) }
                                    val connectionsState by connectionsModel.state.collectAsStateWithLifecycle()
                                    val sharedModel = viewModel { SharedViewModel(this@MainActivity.get<SharingServices>()) }
                                    val sharedState by sharedModel.state.collectAsStateWithLifecycle()
                                    SharingScreen(sharedState, sharedModel, connectionsState, connectionsModel)
                                }
                                Shell(
                                    status = syncStatus,
                                    onLock = guard::lockNow,
                                    notices = versionNotices,
                                    settings = { openNotifications, open ->
                                        SettingsContent(
                                            status = syncStatus,
                                            onOpenNotifications = openNotifications,
                                            onDiscard = sync::discardRefused,
                                            onOpen = open,
                                            notices = {},
                                        )
                                    },
                                    settingsPages = mapOf(
                                        Destination.SettingsDevices to {
                                            val devicesModel = viewModel { DevicesViewModel(this@MainActivity.get<DevicesServices>()) }
                                            val devicesState by devicesModel.state.collectAsStateWithLifecycle()
                                            LaunchedEffect(extensionState.step) {
                                                if (extensionState.step == LinkStep.Linked) devicesModel.opened()
                                            }
                                            DevicesScreen(
                                                devicesState,
                                                devicesModel,
                                                extension = { ExtensionLinkSection(extensionState, extension) },
                                                thisPhone = { RemoveThisPhoneSection(removalState, removal) },
                                            )
                                        },
                                        Destination.SettingsAccount to {
                                            AccountSecuritySection(securityState, security)
                                        },
                                        Destination.SettingsSecurity to {
                                            val securityModel = viewModel { SecurityViewModel(this@MainActivity.get<SecurityServices>()) }
                                            val pinState by securityModel.state.collectAsStateWithLifecycle()
                                            SecurityScreen(pinState, securityModel)
                                        },
                                        Destination.SettingsPlan to {
                                            val planModel = viewModel { PlanViewModel(this@MainActivity.get<PlanServices>()) }
                                            val planState by planModel.state.collectAsStateWithLifecycle()
                                            PlanScreen(planState, planModel)
                                        },
                                        Destination.SettingsAbout to {
                                            AboutScreen(BuildConfig.VERSION_NAME) { VersionNotice(versionStatus, policy::dismissUpdateAvailable) }
                                        },
                                    ),
                                    notifications = { NotificationsScreen(notificationsState, notifications) },
                                    listPages = mapOf(Destination.Passwords to passwordsPage, Destination.Vault to vaultPage, Destination.Notes to notesPage, Destination.Drive to drivePage, Destination.Shared to sharedPage),
                                )
                                AccountSecuritySheet(securityState, security)
                                shareTarget?.let { target ->
                                    val shareModel = viewModel(key = "share-${target.type}-${target.id}") {
                                        ShareViewModel(this@MainActivity.get<SharingServices>(), target.type, target.id)
                                    }
                                    val shareState by shareModel.state.collectAsStateWithLifecycle()
                                    ShareSheet(shareState, shareModel) { shareTarget = null }
                                }
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
