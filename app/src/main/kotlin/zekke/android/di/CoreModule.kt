package zekke.android.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import zekke.android.onboarding.CoreOnboardingServices
import zekke.android.feature.settings.AccountSecurityServices
import zekke.android.rekey.KeyRotationEvents
import zekke.android.security.CoreAccountSecurityServices
import zekke.android.clients.ClientPolicyMonitor
import zekke.android.feature.unlock.ThisPhoneServices
import zekke.android.unlock.CoreThisPhoneServices
import zekke.android.unlock.CoreUnlockServices
import zekke.android.unlock.ThisPhoneEvents
import zekke.android.feature.unlock.UnlockServices
import zekke.android.feature.onboarding.OnboardingServices
import zekke.android.session.KeystoreSessionControl
import zekke.android.session.KillThisProcess
import zekke.android.session.SessionControl
import zekke.android.session.SessionGuard
import zekke.android.session.SessionViewModels
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import zekke.android.BuildConfig
import zekke.android.platform.LocalDatabases
import zekke.core.account.AccountServices
import zekke.core.account.changeAccountPin
import zekke.core.account.turnOnParanoid
import zekke.core.account.forgetThisDevice
import zekke.core.account.removeThisDevice
import zekke.core.clients.getClientPolicy
import zekke.core.api.ClientIdentity
import zekke.core.api.ClientPlatform
import zekke.core.api.ZekkeApi
import zekke.android.feature.passwords.PasswordsServices
import zekke.android.feature.vault.VaultServices
import zekke.android.items.CoreFolderTabsServices
import zekke.android.items.CoreVaultServices
import zekke.core.folders.ManifestScope
import zekke.android.feature.settings.NotificationsServices
import zekke.android.items.CorePasswordsServices
import zekke.android.items.ItemCalls
import zekke.android.sync.CoreNotificationsServices
import zekke.android.sync.CoreSyncBackendFactory
import zekke.android.sync.KeystoreSyncSession
import zekke.android.sync.SyncService
import zekke.core.feed.Outbox
import zekke.core.feed.Replica
import zekke.core.files.HttpObjectStore
import zekke.core.files.ObjectStore
import zekke.core.items.ItemContext
import zekke.core.session.SessionKeystore
import zekke.core.users.getMe

val APP_SCOPE = named("app-scope")
val MAIN_SCOPE = named("main-scope")

val coreModule = module {
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { ClientIdentity(ClientPlatform.ANDROID, BuildConfig.VERSION_NAME) }
    single { ZekkeApi(baseUrl = BuildConfig.API_BASE_URL, clientIdentity = get()) }
    single { SessionKeystore(timerScope = get(APP_SCOPE)) }
    single { AccountServices(get(), get(), get()) }
    single<CoroutineScope>(MAIN_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    single<SessionControl> { KeystoreSessionControl(get()) }
    single { SessionViewModels() }
    single { SessionGuard(get(), get(MAIN_SCOPE), KillThisProcess).also { guard -> guard.onLocked(get<SessionViewModels>()::clear) } }
    factory<OnboardingServices> { CoreOnboardingServices(androidContext(), get(), get(), get(), get()) }
    factory<UnlockServices> { val sync = get<SyncService>(); CoreUnlockServices(get(), get(), sync::leaveAccount) }
    single { ThisPhoneEvents() }
    single { KeyRotationEvents() }
    factory<ThisPhoneServices> {
        val account = get<AccountServices>()
        CoreThisPhoneServices(get(), get(), { removeThisDevice(account) }, { forgetThisDevice(account) })
    }
    single { ItemCalls(get(), get()) }
    factory<VaultServices> { CoreVaultServices(get(), get(), get()) }
    factory(named("secrets-tabs")) { CoreFolderTabsServices(ManifestScope.SECRETS, get(), get(), get()) }
    factory<PasswordsServices> { CorePasswordsServices(get(), get(), get()) }
    factory<NotificationsServices> { CoreNotificationsServices(get()) }
    factory<AccountSecurityServices> {
        val account = get<AccountServices>()
        val api = get<ZekkeApi>()
        CoreAccountSecurityServices(
            get(),
            get(),
            { words, pin -> turnOnParanoid(account, words, pin) },
            { words, current, new -> changeAccountPin(account, words, current, new) },
            { getMe(api).paranoid },
        )
    }
    single {
        val api = get<ZekkeApi>()
        val guard = get<SessionGuard>()
        ClientPolicyMonitor(
            BuildConfig.VERSION_NAME,
            { getClientPolicy(api, ClientPlatform.ANDROID) },
            api.upgradeRequired,
            get(MAIN_SCOPE),
            { guard.lockNow() },
        )
    }
    single<ObjectStore> { HttpObjectStore() }
    factory { ItemContext(get(), get()) }
    factory { (userAddress: String) -> Replica(get<LocalDatabases>().replicaDriver(), userAddress) }
    single {
        val databases = get<LocalDatabases>()
        SyncService(
            session = KeystoreSyncSession(get()),
            scope = get(APP_SCOPE),
            factory = CoreSyncBackendFactory(get(), get(), databases, get(APP_SCOPE), get()),
            wipe = { includeOutbox -> if (includeOutbox) databases.deleteAll() else databases.deleteReplica() },
            nowEpochSeconds = { System.currentTimeMillis() / 1000 },
        ).also { sync ->
            val removals = get<ThisPhoneEvents>().removals
            get<CoroutineScope>(APP_SCOPE).launch { removals.drop(1).collect { sync.leaveAccount(true) } }
        }
    }
    factory { (userAddress: String) -> Outbox(get<LocalDatabases>().outboxDriver(), userAddress) }
}
