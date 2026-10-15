package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.android.ui.components.ItemFailure
import zekke.android.ui.components.StorageReading

data class PlanState(
    val loaded: Boolean = false,
    val plan: PlanInfo? = null,
    val storage: StorageReading? = null,
    val failure: ItemFailure? = null,
)

class PlanViewModel(private val services: PlanServices) : ViewModel() {
    private val mutableState = MutableStateFlow(PlanState())
    private var attempt = 0

    val state: StateFlow<PlanState> = mutableState.asStateFlow()

    fun opened() {
        val mine = ++attempt
        viewModelScope.launch {
            val result = try {
                services.load()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                PlanLoad.Failed(ItemFailure.Failed)
            }
            if (mine != attempt) return@launch
            when (result) {
                is PlanLoad.Loaded -> mutableState.update { it.copy(loaded = true, plan = result.plan, storage = result.storage, failure = null) }
                is PlanLoad.Failed -> mutableState.update { it.copy(loaded = true, failure = result.failure) }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }
}
