package zekke.android.feature.vault

import kotlinx.coroutines.flow.Flow
import zekke.android.ui.components.ItemFailure
import zekke.core.secrets.SecretPayload
import zekke.core.secrets.VaultView

sealed interface VaultLoad {
    data class Loaded(val view: VaultView, val synced: Boolean) : VaultLoad

    data class Failed(val failure: ItemFailure) : VaultLoad
}

sealed interface VaultWrite {
    data class Done(val id: String? = null) : VaultWrite

    data class Failed(val failure: ItemFailure) : VaultWrite
}

interface VaultServices {
    val canDelete: Boolean

    val changes: Flow<Unit>

    suspend fun load(): VaultLoad

    suspend fun create(payload: SecretPayload): VaultWrite

    suspend fun delete(ids: List<String>): VaultWrite

    suspend fun restore(id: String): VaultWrite

    suspend fun purge(ids: List<String>): VaultWrite
}
