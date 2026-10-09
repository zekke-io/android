package zekke.android.platform

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import zekke.core.device.DeviceVault
import zekke.core.device.MemoryDeviceVault

val platformModule = module {
    single<DeviceVault> { MemoryDeviceVault() }
    single { LocalDatabases(androidContext()) }
}
