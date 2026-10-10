package zekke.android.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import zekke.android.onboarding.CoreOnboardingServices
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
import zekke.core.api.ClientIdentity
import zekke.core.api.ClientPlatform
import zekke.core.api.ZekkeApi
import zekke.core.feed.Outbox
import zekke.core.feed.Replica
import zekke.core.files.HttpObjectStore
import zekke.core.files.ObjectStore
import zekke.core.items.ItemContext
import zekke.core.session.SessionKeystore

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
    factory<OnboardingServices> { CoreOnboardingServices(androidContext(), get(), get(), get()) }
    single<ObjectStore> { HttpObjectStore() }
    factory { ItemContext(get(), get()) }
    factory { (userAddress: String) -> Replica(get<LocalDatabases>().replicaDriver(), userAddress) }
    factory { (userAddress: String) -> Outbox(get<LocalDatabases>().outboxDriver(), userAddress) }
}
