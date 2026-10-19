package zekke.android.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class TopBarActionsSlot {
    var content: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
}

val LocalTopBarActions = staticCompositionLocalOf<TopBarActionsSlot?> { null }

@Composable
fun ProvideTopBarActions(content: @Composable RowScope.() -> Unit) {
    val slot = LocalTopBarActions.current ?: return
    val current by rememberUpdatedState(content)
    DisposableEffect(slot) {
        slot.content = { current() }
        onDispose { slot.content = null }
    }
}
