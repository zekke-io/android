package zekke.android.feature.passwords

import kotlinx.coroutines.flow.Flow
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.CredentialPayload
import zekke.core.credentials.DeletedCredential
import zekke.core.credentials.PasswordsView

sealed interface PasswordsLoad {
    data class Loaded(val view: PasswordsView, val synced: Boolean) : PasswordsLoad

    data class Failed(val failure: ItemFailure) : PasswordsLoad
}

sealed interface PasswordsWrite {
    data object Done : PasswordsWrite

    data class Failed(val failure: ItemFailure) : PasswordsWrite
}

class PreviousPassword(val revisionId: String, val password: String, val changedAt: String)

sealed interface PreviousLoad {
    data class Loaded(val entries: List<PreviousPassword>) : PreviousLoad

    data class Failed(val failure: ItemFailure) : PreviousLoad
}

interface PasswordsServices {
    val canPurge: Boolean

    val changes: Flow<Unit>

    suspend fun load(): PasswordsLoad

    suspend fun save(payload: CredentialPayload, credentialId: String?): PasswordsWrite

    suspend fun delete(credentialId: String): PasswordsWrite

    suspend fun restore(deleted: DeletedCredential): PasswordsWrite

    suspend fun purge(deleted: List<DeletedCredential>): PasswordsWrite

    suspend fun previous(credentialId: String): PreviousLoad
}
