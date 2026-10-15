package zekke.android.feature.vault

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import zekke.android.ui.components.ItemFailure
import zekke.core.secrets.DeletedVaultRow
import zekke.core.secrets.SecretPayload
import zekke.core.secrets.VaultRow
import zekke.core.secrets.VaultView

fun vaultRow(id: String, name: String, value: String = "v-$id", readable: Boolean = true) =
    VaultRow(id, name, value, value.length, "v1", "2026-10-01T10:00:00Z", readable)

fun deletedSecret(id: String, name: String) = DeletedVaultRow(id, name, true, "2026-10-02T10:00:00Z")

class FakeVaultServices(override var canDelete: Boolean = true) : VaultServices {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    var view = VaultView(emptyList(), emptyList())
    var synced = true
    var loadFailure: ItemFailure? = null
    var loads = 0
    val created = mutableListOf<SecretPayload>()
    val deleted = mutableListOf<List<String>>()
    val restored = mutableListOf<String>()
    val purged = mutableListOf<List<String>>()
    var result: VaultWrite = VaultWrite.Done("new-id")
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun load(): VaultLoad {
        loads++
        return loadFailure?.let { VaultLoad.Failed(it) } ?: VaultLoad.Loaded(view, synced)
    }

    private suspend fun outcome(): VaultWrite {
        gate?.await()
        return result
    }

    override suspend fun create(payload: SecretPayload): VaultWrite {
        created += payload
        return outcome()
    }

    override suspend fun delete(ids: List<String>): VaultWrite {
        deleted += ids
        return outcome()
    }

    override suspend fun restore(id: String): VaultWrite {
        restored += id
        return outcome()
    }

    override suspend fun purge(ids: List<String>): VaultWrite {
        purged += ids
        return outcome()
    }
}
