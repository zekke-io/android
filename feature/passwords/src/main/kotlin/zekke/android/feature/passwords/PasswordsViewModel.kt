package zekke.android.feature.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import zekke.android.ui.components.ItemFailure
import zekke.core.credentials.CredentialPayload
import zekke.core.credentials.DeletedPasswordRow
import zekke.core.credentials.PasswordRow
import zekke.core.credentials.SiteMatch
import zekke.core.credentials.siteLabel

data class PasswordForm(
    val editing: PasswordRow? = null,
    val site: String = "",
    val username: String = "",
    val password: String = "",
    val note: String = "",
    val urls: String = "",
    val match: SiteMatch = SiteMatch.DOMAIN,
    val previous: List<PreviousPassword>? = null,
    val previousFailure: ItemFailure? = null,
) {
    val complete: Boolean get() = site.trim().isNotEmpty() && username.trim().isNotEmpty() && password.isNotEmpty()
}

data class PasswordsState(
    val loaded: Boolean = false,
    val synced: Boolean = false,
    val rows: List<PasswordRow> = emptyList(),
    val deleted: List<DeletedPasswordRow> = emptyList(),
    val query: String = "",
    val failure: ItemFailure? = null,
    val busy: Boolean = false,
    val form: PasswordForm? = null,
    val confirmingDelete: PasswordRow? = null,
    val showingDeleted: Boolean = false,
    val confirmingPurge: List<DeletedPasswordRow>? = null,
    val canPurge: Boolean = false,
) {
    val visible: List<PasswordRow> get() = rows.filter { matches(it, query) }
}

private fun matches(row: PasswordRow, query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    val fields = listOf(row.site, siteLabel(row.site), row.username, row.note) + (row.payload?.urls ?: emptyList())
    return fields.any { it.lowercase().contains(needle) }
}

class PasswordsViewModel(private val services: PasswordsServices) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordsState(canPurge = services.canPurge))

    val state: StateFlow<PasswordsState> = mutableState.asStateFlow()

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
                PasswordsLoad.Failed(ItemFailure.Failed)
            }
            when (result) {
                is PasswordsLoad.Loaded -> mutableState.update {
                    it.copy(loaded = true, synced = result.synced, rows = result.view.rows, deleted = result.view.deleted, failure = null)
                }
                is PasswordsLoad.Failed -> mutableState.update { it.copy(failure = result.failure) }
            }
        }
    }

    fun queryChanged(query: String) = mutableState.update { it.copy(query = query) }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    fun startAdd() = mutableState.update { it.copy(form = PasswordForm()) }

    fun startEdit(row: PasswordRow) {
        if (!row.readable) return
        mutableState.update {
            it.copy(
                form = PasswordForm(
                    editing = row,
                    site = row.site,
                    username = row.username,
                    password = row.password,
                    note = row.note,
                    urls = (row.payload?.urls ?: emptyList()).joinToString(", "),
                    match = row.payload?.match ?: SiteMatch.DOMAIN,
                ),
            )
        }
    }

    fun closeForm() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(form = null) }
    }

    fun siteChanged(value: String) = editForm { it.copy(site = value) }

    fun usernameChanged(value: String) = editForm { it.copy(username = value) }

    fun passwordChanged(value: String) = editForm { it.copy(password = value) }

    fun noteChanged(value: String) = editForm { it.copy(note = value) }

    fun urlsChanged(value: String) = editForm { it.copy(urls = value) }

    fun matchChanged(value: SiteMatch) = editForm { it.copy(match = value) }

    private fun editForm(change: (PasswordForm) -> PasswordForm) =
        mutableState.update { state -> state.form?.let { state.copy(form = change(it)) } ?: state }

    fun save() {
        val current = mutableState.value
        val form = current.form ?: return
        if (current.busy || !form.complete) return
        val base = form.editing?.payload
        val payload = CredentialPayload(
            site = form.site.trim(),
            username = form.username.trim(),
            password = form.password,
            note = form.note.trim(),
            urls = form.urls.split(',', '\n'),
            match = form.match,
            extra = base?.extra ?: JsonObject(emptyMap()),
        )
        val done = write { services.save(payload, form.editing?.id) }
        viewModelScope.launch { if (done.await()) mutableState.update { it.copy(form = null) } }
    }

    fun askDelete(row: PasswordRow) = mutableState.update { it.copy(confirmingDelete = row) }

    fun cancelDelete() = mutableState.update { if (it.busy) it else it.copy(confirmingDelete = null) }

    fun confirmDelete() {
        val row = mutableState.value.confirmingDelete ?: return
        if (mutableState.value.busy) return
        val done = write { services.delete(row.id) }
        viewModelScope.launch {
            val succeeded = done.await()
            mutableState.update {
                it.copy(confirmingDelete = null, form = if (succeeded && it.form?.editing?.id == row.id) null else it.form)
            }
        }
    }

    fun toggleDeleted() = mutableState.update { it.copy(showingDeleted = !it.showingDeleted) }

    fun hideDeleted() = mutableState.update { it.copy(showingDeleted = false) }

    fun restore(row: DeletedPasswordRow) {
        if (mutableState.value.busy) return
        write { services.restore(row.deleted) }
    }

    fun askPurge(rows: List<DeletedPasswordRow>) {
        if (!mutableState.value.canPurge || rows.isEmpty()) return
        mutableState.update { it.copy(confirmingPurge = rows) }
    }

    fun cancelPurge() = mutableState.update { if (it.busy) it else it.copy(confirmingPurge = null) }

    fun confirmPurge() {
        val rows = mutableState.value.confirmingPurge ?: return
        if (mutableState.value.busy) return
        val done = write { services.purge(rows.map { it.deleted }) }
        viewModelScope.launch {
            done.await()
            mutableState.update { it.copy(confirmingPurge = null) }
        }
    }

    fun showPrevious() {
        val form = mutableState.value.form ?: return
        val id = form.editing?.id ?: return
        viewModelScope.launch {
            val result = try {
                services.previous(id)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PreviousLoad.Failed(ItemFailure.Failed)
            }
            editForm {
                when (result) {
                    is PreviousLoad.Loaded -> it.copy(previous = result.entries, previousFailure = null)
                    is PreviousLoad.Failed -> it.copy(previousFailure = result.failure)
                }
            }
        }
    }

    private fun write(call: suspend () -> PasswordsWrite) = viewModelScope.async {
        mutableState.update { it.copy(busy = true, failure = null) }
        val result = try {
            call()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PasswordsWrite.Failed(ItemFailure.Failed)
        }
        when (result) {
            PasswordsWrite.Done -> {
                mutableState.update { it.copy(busy = false) }
                refresh()
                true
            }
            is PasswordsWrite.Failed -> {
                mutableState.update { it.copy(busy = false, failure = result.failure) }
                false
            }
        }
    }
}
