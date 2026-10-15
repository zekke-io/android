package zekke.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zekke.core.pin.PinValidation
import zekke.core.pin.validatePin

data class SecurityState(
    val idleMinutes: Int,
    val pin: String = "",
    val confirmation: String = "",
    val problem: PinProblem? = null,
    val outcome: DevicePinChange? = null,
    val busy: Boolean = false,
)

class SecurityViewModel(private val services: SecurityServices) : ViewModel() {
    private val mutableState = MutableStateFlow(SecurityState(idleMinutes = services.idleMinutes))
    private var attempt = 0

    val state: StateFlow<SecurityState> = mutableState.asStateFlow()

    fun chooseIdle(minutes: Int) {
        if (minutes !in IDLE_CHOICES_MINUTES) return
        services.setIdleMinutes(minutes)
        mutableState.update { it.copy(idleMinutes = minutes) }
    }

    fun pinChanged(value: String) = mutableState.update { if (it.busy) it else it.copy(pin = value, problem = null, outcome = null) }

    fun confirmationChanged(value: String) = mutableState.update {
        if (it.busy) it else it.copy(confirmation = value, problem = if (value.length < it.pin.length) null else it.problem, outcome = null)
    }

    fun dismissOutcome() = mutableState.update { it.copy(outcome = null) }

    fun submit() {
        val current = mutableState.value
        if (current.busy) return
        val pin = current.pin.toCharArray()
        val validation = validatePin(pin)
        if (validation is PinValidation.Invalid) {
            pin.fill('\u0000')
            mutableState.update { it.copy(problem = PinProblem.Rejected(validation.reason), confirmation = "") }
            return
        }
        if (current.confirmation != current.pin) {
            pin.fill('\u0000')
            mutableState.update { it.copy(problem = PinProblem.Mismatch, confirmation = "") }
            return
        }
        val mine = ++attempt
        mutableState.update { it.copy(busy = true, problem = null, outcome = null) }
        viewModelScope.launch {
            try {
                val result = try {
                    services.changeDevicePin(pin)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    DevicePinChange.Failed
                }
                if (mine != attempt) return@launch
                mutableState.update {
                    if (result == DevicePinChange.Done) it.copy(busy = false, pin = "", confirmation = "", outcome = result) else it.copy(busy = false, outcome = result)
                }
            } finally {
                pin.fill('\u0000')
            }
        }
    }

    override fun onCleared() {
        attempt++
    }
}
