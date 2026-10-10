package zekke.android.session

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionViewModels : ViewModelStoreOwner {
    private val generation = MutableStateFlow(0)

    val epoch: StateFlow<Int> = generation.asStateFlow()

    override var viewModelStore: ViewModelStore = ViewModelStore()
        private set

    fun clear() {
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
        generation.value++
    }
}
