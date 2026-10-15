package zekke.android.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.feature.tabs.FolderTabsController
import zekke.android.feature.tabs.FolderTabsServices
import zekke.android.ui.components.ItemFailure
import zekke.core.folders.ManifestScope
import zekke.core.items.newItemId
import zekke.core.secrets.DeletedVaultRow
import zekke.core.secrets.SecretPayload
import zekke.core.secrets.VaultRow

data class AddSecretForm(val name: String = "", val value: String = "") {
    val complete: Boolean get() = name.trim().isNotEmpty() && value.isNotEmpty()
}

data class VaultState(
    val loaded: Boolean = false,
    val synced: Boolean = false,
    val rows: List<VaultRow> = emptyList(),
    val deleted: List<DeletedVaultRow> = emptyList(),
    val revealed: Boolean = false,
    val failure: ItemFailure? = null,
    val busy: Boolean = false,
    val adding: AddSecretForm? = null,
    val detail: String? = null,
    val confirmingDelete: VaultRow? = null,
    val showingDeleted: Boolean = false,
    val confirmingPurge: List<DeletedVaultRow>? = null,
    val canDelete: Boolean = false,
)

class VaultViewModel(
    private val services: VaultServices,
    tabsServices: FolderTabsServices,
    newId: () -> String = ::newItemId,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VaultState(canDelete = services.canDelete))

    val state: StateFlow<VaultState> = mutableState.asStateFlow()

    val tabs = FolderTabsController(tabsServices, ManifestScope.SECRETS.rules, viewModelScope, newId)

    init {
        viewModelScope.launch { services.changes.collect { refresh() } }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val result = try {
                services.load()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                VaultLoad.Failed(ItemFailure.Failed)
            }
            when (result) {
                is VaultLoad.Loaded -> mutableState.update {
                    it.copy(loaded = true, synced = result.synced, rows = result.view.rows, deleted = result.view.deleted, failure = null)
                }
                is VaultLoad.Failed -> mutableState.update { it.copy(failure = result.failure) }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun toggleRevealed() = mutableState.update { it.copy(revealed = !it.revealed) }

    fun startAdd() = mutableState.update { it.copy(adding = AddSecretForm()) }

    fun closeAdd() = mutableState.update { if (it.busy) it else it.copy(adding = null) }

    fun nameChanged(value: String) = mutableState.update { state -> state.adding?.let { state.copy(adding = it.copy(name = value)) } ?: state }

    fun valueChanged(value: String) = mutableState.update { state -> state.adding?.let { state.copy(adding = it.copy(value = value)) } ?: state }

    fun add() {
        val form = mutableState.value.adding ?: return
        if (mutableState.value.busy || !form.complete) return
        val itemIds = mutableState.value.rows.map { it.id }
        val active = tabs.active(itemIds)
        viewModelScope.launch {
            val result = write { services.create(SecretPayload(form.name.trim(), form.value)) }
            if (result is VaultWrite.Done) {
                val id = result.id
                if (id != null && !tabs.file(id, active)) mutableState.update { it.copy(failure = tabs.state.value.failure) }
                mutableState.update { it.copy(adding = null) }
            }
        }
    }

    fun open(row: VaultRow) {
        if (row.readable) mutableState.update { it.copy(detail = row.id) }
    }

    fun closeDetail() = mutableState.update { it.copy(detail = null) }

    fun moveTo(row: VaultRow, tabId: String) {
        tabs.move(listOf(row.id), tabId)
        closeDetail()
    }

    fun askDelete(row: VaultRow) {
        if (!mutableState.value.canDelete) return
        mutableState.update { it.copy(confirmingDelete = row, detail = null) }
    }

    fun cancelDelete() = mutableState.update { if (it.busy) it else it.copy(confirmingDelete = null) }

    fun confirmDelete() {
        val row = mutableState.value.confirmingDelete ?: return
        if (mutableState.value.busy) return
        viewModelScope.launch {
            write { services.delete(listOf(row.id)) }
            mutableState.update { it.copy(confirmingDelete = null) }
        }
    }

    suspend fun deleteTabItems(ids: List<String>): ItemFailure? {
        val result = try {
            services.delete(ids)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            VaultWrite.Failed(ItemFailure.Failed)
        }
        return (result as? VaultWrite.Failed)?.failure
    }

    fun toggleDeleted() = mutableState.update { it.copy(showingDeleted = !it.showingDeleted) }

    fun hideDeleted() = mutableState.update { it.copy(showingDeleted = false) }

    fun restore(row: DeletedVaultRow) {
        if (mutableState.value.busy) return
        viewModelScope.launch { write { services.restore(row.id) } }
    }

    fun askPurge(rows: List<DeletedVaultRow>) {
        if (!mutableState.value.canDelete || rows.isEmpty()) return
        mutableState.update { it.copy(confirmingPurge = rows) }
    }

    fun cancelPurge() = mutableState.update { if (it.busy) it else it.copy(confirmingPurge = null) }

    fun confirmPurge() {
        val rows = mutableState.value.confirmingPurge ?: return
        if (mutableState.value.busy) return
        viewModelScope.launch {
            val result = write { services.purge(rows.map { it.id }) }
            if (result is VaultWrite.Done) tabs.forget(rows.map { it.id })
            mutableState.update { it.copy(confirmingPurge = null) }
        }
    }

    private suspend fun write(call: suspend () -> VaultWrite): VaultWrite {
        mutableState.update { it.copy(busy = true, failure = null) }
        val result = try {
            call()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            VaultWrite.Failed(ItemFailure.Failed)
        }
        when (result) {
            is VaultWrite.Done -> {
                mutableState.update { it.copy(busy = false) }
                refresh()
            }
            is VaultWrite.Failed -> mutableState.update { it.copy(busy = false, failure = result.failure) }
        }
        return result
    }
}
