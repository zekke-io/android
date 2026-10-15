package zekke.android.items

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import zekke.android.feature.vault.VaultLoad
import zekke.android.feature.vault.VaultServices
import zekke.android.feature.vault.VaultWrite
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.feed.FeedScope
import zekke.core.items.ItemContext
import zekke.core.secrets.SecretPayload
import zekke.core.secrets.createSecret
import zekke.core.secrets.deleteSecrets
import zekke.core.secrets.encodeSecretPayload
import zekke.core.secrets.purgeSecrets
import zekke.core.secrets.restoreSecret
import zekke.core.secrets.vaultView

class CoreVaultServices(
    private val sync: SyncService,
    private val calls: ItemCalls,
    private val context: ItemContext,
) : VaultServices {
    override val canDelete: Boolean get() = context.session.isFullDevice

    override val changes: Flow<Unit> = sync.changes.filter { it == FeedScope.SECRETS }.map { }

    override suspend fun load(): VaultLoad {
        val replica = sync.replica() ?: return VaultLoad.Failed(ItemFailure.Failed)
        return calls.read { vaultView(context, replica) to replica.isSynced(FeedScope.SECRETS) }
            .fold({ (view, synced) -> VaultLoad.Loaded(view, synced) }, { VaultLoad.Failed(it) })
    }

    override suspend fun create(payload: SecretPayload): VaultWrite =
        written { VaultWrite.Done(createSecret(context, encodeSecretPayload(payload)).secret.id) }

    override suspend fun delete(ids: List<String>): VaultWrite = written { deleteSecrets(context, ids); VaultWrite.Done() }

    override suspend fun restore(id: String): VaultWrite = written { restoreSecret(context, id); VaultWrite.Done() }

    override suspend fun purge(ids: List<String>): VaultWrite = written { purgeSecrets(context, ids); VaultWrite.Done() }

    private suspend fun written(call: suspend () -> VaultWrite): VaultWrite =
        calls.write(call).fold(
            {
                sync.syncNow()
                it
            },
            { VaultWrite.Failed(it) },
        )
}
