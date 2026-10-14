package zekke.android.rekey

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyRotationEvents {
    private val mutablePending = MutableStateFlow(false)

    val pending: StateFlow<Boolean> = mutablePending.asStateFlow()

    fun rotated() {
        mutablePending.value = true
    }

    fun handled() {
        mutablePending.value = false
    }
}
