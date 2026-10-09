package zekke.android.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import zekke.core.feed.outboxSchema
import zekke.core.feed.replicaSchema
import java.io.File

class LocalDatabases(private val context: Context) {
    fun replicaDriver(): SqlDriver = AndroidSqliteDriver(replicaSchema, context, path(REPLICA_FILE))

    fun outboxDriver(): SqlDriver = AndroidSqliteDriver(outboxSchema, context, path(OUTBOX_FILE))

    fun deleteAll() {
        listOf(REPLICA_FILE, OUTBOX_FILE).forEach { context.deleteDatabase(path(it)) }
    }

    private fun path(name: String): String = File(context.noBackupFilesDir, name).path

    companion object {
        const val REPLICA_FILE = "replica.db"
        const val OUTBOX_FILE = "outbox.db"
    }
}
