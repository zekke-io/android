package zekke.android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import zekke.android.clients.ClientPolicyMonitor
import zekke.android.di.coreModule
import zekke.android.items.PlaintextFiles
import zekke.android.platform.platformModule
import zekke.android.session.SessionGuard
import zekke.android.session.registerScreenOffLock
import zekke.android.sync.SyncService
import zekke.android.uploads.UploadService

class ZekkeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ZekkeApplication)
            modules(platformModule, coreModule)
        }
        val guard = get<SessionGuard>()
        ProcessLifecycleOwner.get().lifecycle.addObserver(guard)
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<ClientPolicyMonitor>())
        val sync = get<SyncService>()
        sync.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(sync)
        val plaintext = get<PlaintextFiles>()
        plaintext.sweepEverything()
        guard.onBeforeLock(plaintext::sweepPreviews)
        guard.onLocked(plaintext::sweepPreviews)
        get<UploadService>().start()
        registerScreenOffLock(this, guard)
    }
}
