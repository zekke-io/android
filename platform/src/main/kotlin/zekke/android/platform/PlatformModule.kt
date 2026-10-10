package zekke.android.platform

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import zekke.core.device.DeviceVault

val platformModule = module {
    single { KeystoreDeviceVault(androidContext()) } bind DeviceVault::class
    single { LocalDatabases(androidContext()) }
}
