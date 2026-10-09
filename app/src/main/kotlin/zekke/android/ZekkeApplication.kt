package zekke.android

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import zekke.android.di.coreModule
import zekke.android.platform.platformModule

class ZekkeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ZekkeApplication)
            modules(platformModule, coreModule)
        }
    }
}
