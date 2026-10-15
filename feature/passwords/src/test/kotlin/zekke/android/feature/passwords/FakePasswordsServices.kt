package zekke.android.feature.passwords

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.CredentialPayload
import zekke.core.credentials.CredentialRevision
import zekke.core.credentials.DeletedCredential
import zekke.core.credentials.DeletedPasswordRow
import zekke.core.credentials.PasswordRow
import zekke.core.credentials.PasswordsView

fun passwordRow(
    id: String,
    site: String,
    username: String = "ana",
    password: String = "pw-$id",
    note: String = "",
    readable: Boolean = true,
    payload: CredentialPayload? = CredentialPayload(site, username, password, note.ifEmpty { null }),
) = PasswordRow(id, "rev-$id", 1, site, username, password, note, "2026-10-01T10:00:00Z", readable, payload)

fun deletedRow(id: String, site: String, username: String = "ana"): DeletedPasswordRow {
    val revision = CredentialRevision(id, "rev-$id", 2, "ct", "wd", 1, "v1", "2026-10-01T10:00:00Z", false)
    return DeletedPasswordRow(id, site, username, true, "2026-10-02T10:00:00Z", DeletedCredential(id, "2026-10-02T10:00:00Z", revision))
}

class FakePasswordsServices(override var canPurge: Boolean = true) : PasswordsServices {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    var view = PasswordsView(emptyList(), emptyList())
    var synced = true
    var loadFailure: ItemFailure? = null
    val loads = mutableListOf<Unit>()
    val saved = mutableListOf<Pair<CredentialPayload, String?>>()
    val deletedIds = mutableListOf<String>()
    val restored = mutableListOf<String>()
    val purged = mutableListOf<List<String>>()
    var writeResult: PasswordsWrite = PasswordsWrite.Done
    var gate: CompletableDeferred<Unit>? = null
    var previous: PreviousLoad = PreviousLoad.Loaded(emptyList())
    var throwOnLoad = false

    override suspend fun load(): PasswordsLoad {
        loads += Unit
        if (throwOnLoad) error("boom")
        return loadFailure?.let { PasswordsLoad.Failed(it) } ?: PasswordsLoad.Loaded(view, synced)
    }

    private suspend fun write(): PasswordsWrite {
        gate?.await()
        return writeResult
    }

    override suspend fun save(payload: CredentialPayload, credentialId: String?): PasswordsWrite {
        saved += payload to credentialId
        return write()
    }

    override suspend fun delete(credentialId: String): PasswordsWrite {
        deletedIds += credentialId
        return write()
    }

    override suspend fun restore(deleted: DeletedCredential): PasswordsWrite {
        restored += deleted.credentialId
        return write()
    }

    override suspend fun purge(deleted: List<DeletedCredential>): PasswordsWrite {
        purged += deleted.map { it.credentialId }
        return write()
    }

    override suspend fun previous(credentialId: String): PreviousLoad = previous
}

val EMPTY_EXTRA = JsonObject(emptyMap())
