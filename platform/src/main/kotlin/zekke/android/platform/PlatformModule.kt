package zekke.android.platform

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import zekke.core.device.DeviceVault

val platformModule = module {
    single<DeviceVault> { KeystoreDeviceVault(androidContext()) }
    single { LocalDatabases(androidContext()) }
}
