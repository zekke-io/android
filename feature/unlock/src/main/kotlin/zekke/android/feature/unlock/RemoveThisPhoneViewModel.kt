package zekke.android.feature.unlock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemovePhoneState(
    val confirming: Boolean = false,
    val busy: Boolean = false,
    val failure: RemovalOutcome? = null,
)

class RemoveThisPhoneViewModel(private val services: ThisPhoneServices) : ViewModel() {
    private val mutableState = MutableStateFlow(RemovePhoneState())
    private var attemptId = 0

    val state = mutableState.asStateFlow()

    fun requestRemoval() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(confirming = true, failure = null) }
    }

    fun stay() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(confirming = false) }
    }

    fun confirmRemoval() {
        val current = mutableState.value
        if (!current.confirming || current.busy) return
        val currentAttempt = ++attemptId
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            val outcome = try {
                services.removeThisPhone()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RemovalOutcome.Failed
            }
            if (currentAttempt != attemptId) return@launch
            mutableState.update {
                when (outcome) {
                    RemovalOutcome.Removed, RemovalOutcome.AlreadyRemoved -> RemovePhoneState()
                    else -> RemovePhoneState(failure = outcome)
                }
            }
        }
    }

    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    override fun onCleared() {
        attemptId++
    }
}
