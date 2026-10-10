package zekke.android.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import zekke.core.session.SessionState

@Composable
fun AppRoot(
    state: SessionState,
    epoch: Int,
    viewModels: SessionViewModels,
    locked: @Composable (SessionState) -> Unit,
    unlocked: @Composable () -> Unit,
) {
    if (state == SessionState.UNLOCKED) {
        key(epoch) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModels) { unlocked() }
        }
    } else {
        locked(state)
    }
}
