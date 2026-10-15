package zekke.android.items

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import zekke.android.feature.passwords.PasswordsLoad
import zekke.android.feature.passwords.PasswordsServices
import zekke.android.feature.passwords.PasswordsWrite
import zekke.android.feature.passwords.PreviousLoad
import zekke.android.feature.passwords.PreviousPassword
import zekke.android.sync.SyncService
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.CredentialPayload
import zekke.core.credentials.DeletedCredential
import zekke.core.credentials.MalformedCredentialPayloadException
import zekke.core.credentials.decodeCredentialPayload
import zekke.core.credentials.deleteCredential
import zekke.core.credentials.encodeCredentialPayload
import zekke.core.credentials.listRevisions
import zekke.core.credentials.openCredential
import zekke.core.credentials.passwordsView
import zekke.core.credentials.purgeDeletedCredential
import zekke.core.credentials.restoreCredential
import zekke.core.credentials.writeCredential
import zekke.core.feed.FeedScope
import zekke.core.items.ItemContext

class CorePasswordsServices(
    private val sync: SyncService,
    private val calls: ItemCalls,
    private val context: ItemContext,
) : PasswordsServices {
    override val canPurge: Boolean get() = context.session.isFullDevice

    override val changes: Flow<Unit> = sync.changes.filter { it == FeedScope.PASSWORDS }.map { }

    override suspend fun load(): PasswordsLoad {
        val replica = sync.replica() ?: return PasswordsLoad.Failed(ItemFailure.Failed)
        return calls.read { passwordsView(context, replica) to replica.isSynced(FeedScope.PASSWORDS) }
            .fold({ (view, synced) -> PasswordsLoad.Loaded(view, synced) }, { PasswordsLoad.Failed(it) })
    }

    override suspend fun save(payload: CredentialPayload, credentialId: String?): PasswordsWrite =
        written { writeCredential(context, encodeCredentialPayload(payload), credentialId) }

    override suspend fun delete(credentialId: String): PasswordsWrite = written { deleteCredential(context, credentialId) }

    override suspend fun restore(deleted: DeletedCredential): PasswordsWrite = written { restoreCredential(context, deleted) }

    override suspend fun purge(deleted: List<DeletedCredential>): PasswordsWrite =
        calls.write { deleted.forEach { purgeDeletedCredential(context, it) } }.fold(
            {
                sync.resetScope(FeedScope.PASSWORDS)
                PasswordsWrite.Done
            },
            { PasswordsWrite.Failed(it) },
        )

    override suspend fun previous(credentialId: String): PreviousLoad = calls.read {
        listRevisions(context, credentialId).filter { !it.deleted }.drop(1).mapNotNull { revision ->
            try {
                val payload = decodeCredentialPayload(openCredential(context, revision))
                PreviousPassword(revision.revisionId, payload.password, revision.createdAt)
            } catch (_: MalformedCredentialPayloadException) {
                null
            }
        }
    }.fold({ PreviousLoad.Loaded(it) }, { PreviousLoad.Failed(it) })

    private suspend fun written(call: suspend () -> Unit): PasswordsWrite =
        calls.write { call() }.fold(
            {
                sync.syncNow()
                PasswordsWrite.Done
            },
            { PasswordsWrite.Failed(it) },
        )
}
