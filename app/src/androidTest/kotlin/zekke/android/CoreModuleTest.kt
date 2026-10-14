package zekke.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.getKoin
import zekke.android.clients.ClientPolicyMonitor
import zekke.android.feature.unlock.ThisPhoneServices
import zekke.android.platform.LocalDatabases
import zekke.core.account.AccountServices
import zekke.core.api.CLIENT_HEADER
import zekke.core.api.ClientIdentity
import zekke.core.api.ZekkeApi
import zekke.core.device.DeviceVault
import zekke.core.feed.Outbox
import zekke.core.feed.Replica
import zekke.core.files.ObjectStore
import zekke.core.items.ItemContext
import zekke.core.session.SessionKeystore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class CoreModuleTest {
    private val koin get() = getKoin()

    @Test
    fun everyCoreServiceResolvesOnceAndSharesTheSameSession() {
        val api = koin.get<ZekkeApi>()
        val session = koin.get<SessionKeystore>()
        val services = koin.get<AccountServices>()
        assertSame(api, services.api)
        assertSame(session, services.session)
        assertSame(koin.get<DeviceVault>(), services.vault)
        assertSame(api, koin.get<ZekkeApi>())
        assertEquals("android/${BuildConfig.VERSION_NAME}", koin.get<ClientIdentity>().header)
        assertEquals("Zekke-Client", CLIENT_HEADER)
        assertEquals(BuildConfig.API_BASE_URL, api.baseUrl)
        koin.get<ObjectStore>()
        koin.get<ThisPhoneServices>()
        assertSame(koin.get<ClientPolicyMonitor>(), koin.get<ClientPolicyMonitor>())
        val context = koin.get<ItemContext>()
        assertSame(session, context.session)
    }

    @Test
    fun theReplicaAndTheOutboxOpenInNoBackupStorage() {
        val address = "a".repeat(64)
        koin.get<Replica> { parametersOf(address) }
        koin.get<Outbox> { parametersOf(address) }
        val databases = koin.get<LocalDatabases>()
        val dir = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir
        assertTrue(java.io.File(dir, LocalDatabases.REPLICA_FILE).exists())
        assertTrue(java.io.File(dir, LocalDatabases.OUTBOX_FILE).exists())
        databases.deleteAll()
    }
}
