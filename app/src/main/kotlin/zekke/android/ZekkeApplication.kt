package zekke.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import zekke.android.di.coreModule
import zekke.android.platform.platformModule
import zekke.android.session.SessionGuard
import zekke.android.session.registerScreenOffLock

class ZekkeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ZekkeApplication)
            modules(platformModule, coreModule)
        }
        val guard = get<SessionGuard>()
        ProcessLifecycleOwner.get().lifecycle.addObserver(guard)
        registerScreenOffLock(this, guard)
    }
}
