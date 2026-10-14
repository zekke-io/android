package zekke.android.unlock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ThisPhoneEvents {
    private val mutableRemovals = MutableStateFlow(0)

    val removals: StateFlow<Int> = mutableRemovals.asStateFlow()

    fun removed() = mutableRemovals.update { it + 1 }
}
