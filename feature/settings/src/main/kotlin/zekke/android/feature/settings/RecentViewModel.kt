package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val RECENT_LIMIT = 20

data class RecentState(val loaded: Boolean = false, val entries: List<RecentEntry> = emptyList())

class RecentViewModel(private val services: RecentServices) : ViewModel() {
    private val mutableState = MutableStateFlow(RecentState())
    private var attempt = 0

    val state: StateFlow<RecentState> = mutableState.asStateFlow()

    fun opened() {
        val mine = ++attempt
        viewModelScope.launch {
            val entries = try {
                services.load()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emptyList()
            }
            if (mine != attempt) return@launch
            val newestFirst = entries
                .mapNotNull { entry -> runCatching { Instant.parse(entry.updatedAt) }.getOrNull()?.let { it to entry } }
                .sortedByDescending { it.first }
                .take(RECENT_LIMIT)
                .map { it.second }
            mutableState.update { it.copy(loaded = true, entries = newestFirst) }
        }
    }
}
