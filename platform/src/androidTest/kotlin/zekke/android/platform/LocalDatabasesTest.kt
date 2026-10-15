package zekke.android.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import zekke.core.api.Method
import zekke.core.feed.Change
import zekke.core.feed.FeedScope
import zekke.core.feed.ItemTypes
import zekke.core.feed.Outbox
import zekke.core.feed.Replica

@RunWith(AndroidJUnit4::class)
class LocalDatabasesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databases = LocalDatabases(context)
    private val account = "a".repeat(64)
    private val other = "b".repeat(64)

    @Before
    fun clean() = databases.deleteAll()

    @After
    fun cleanAfter() = databases.deleteAll()

    private fun item(id: String) = JsonObject(mapOf("id" to JsonPrimitive(id), "ciphertext" to JsonPrimitive("c")))

    private fun write(address: String) {
        val driver = databases.replicaDriver()
        Replica(driver, address).apply(FeedScope.SECRETS, listOf(Change(5, ItemTypes.SECRET, "s1", item = item("s1"))), cursor = 5, synced = true)
        driver.close()
    }

    @Test
    fun theReplicaSurvivesClosingAndReopeningTheFile() {
        write(account)
        val driver = databases.replicaDriver()
        val replica = Replica(driver, account)
        assertEquals(5, replica.cursor(FeedScope.SECRETS))
        assertTrue(replica.isSynced(FeedScope.SECRETS))
        assertEquals(listOf("s1"), replica.items(FeedScope.SECRETS, ItemTypes.SECRET).map { it.id })
        driver.close()
    }

    @Test
    fun anotherAccountFindsTheReplicaEmpty() {
        write(account)
        val driver = databases.replicaDriver()
        val replica = Replica(driver, other)
        assertEquals(0, replica.cursor(FeedScope.SECRETS))
        assertTrue(replica.items(FeedScope.SECRETS, ItemTypes.SECRET).isEmpty())
        driver.close()
    }

    @Test
    fun theOutboxKeepsWhatTheServerDoesNotHaveYet() {
        val driver = databases.outboxDriver()
        Outbox(driver, account).enqueue(OUTBOX_ITEM, FeedScope.SECRETS, Method.POST, "/secrets", item("s1"), 1)
        driver.close()
        val reopened = databases.outboxDriver()
        assertEquals(listOf(OUTBOX_ITEM), Outbox(reopened, account).entries().map { it.itemId })
        reopened.close()
    }

    @Test
    fun deletingTheReplicaKeepsTheOutboxAndDeletingAllRemovesBoth() {
        write(account)
        val outbox = databases.outboxDriver()
        Outbox(outbox, account).enqueue(OUTBOX_ITEM, FeedScope.SECRETS, Method.POST, "/secrets", item("s1"), 1)
        outbox.close()
        val dir = context.noBackupFilesDir
        assertTrue(File(dir, LocalDatabases.REPLICA_FILE).exists() && File(dir, LocalDatabases.OUTBOX_FILE).exists())

        databases.deleteReplica()
        assertFalse(File(dir, LocalDatabases.REPLICA_FILE).exists())
        assertTrue(File(dir, LocalDatabases.OUTBOX_FILE).exists())

        databases.deleteAll()
        assertFalse(File(dir, LocalDatabases.OUTBOX_FILE).exists())
    }

    @Test
    fun theFilesLiveInNoBackupStorage() {
        write(account)
        assertTrue(File(context.noBackupFilesDir, LocalDatabases.REPLICA_FILE).exists())
        assertFalse(File(context.filesDir.parentFile, "databases/${LocalDatabases.REPLICA_FILE}").exists())
    }

    private companion object {
        const val OUTBOX_ITEM = "0b8e3c1a-5d7f-4a62-9c1e-3f4a5b6c7d8e"
    }
}
