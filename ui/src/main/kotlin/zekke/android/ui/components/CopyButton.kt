package zekke.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import zekke.android.ui.R
import zekke.android.ui.clipboard.CLIPBOARD_CLEAR_AFTER_MILLIS
import zekke.android.ui.clipboard.SensitiveClipboard

const val COPIED_NOTICE_MILLIS = 2_000L

@Composable
fun CopyButton(label: String, value: () -> CharSequence, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val context = LocalContext.current
    val clipboard = remember(context) { SensitiveClipboard(context.applicationContext) }
    var copied by remember { mutableStateOf(0) }
    LaunchedEffect(copied) {
        if (copied > 0) {
            delay(COPIED_NOTICE_MILLIS)
            copied = 0
        }
    }
    ZekkeButton(
        text = if (copied > 0) stringResource(R.string.copy_done, CLIPBOARD_CLEAR_AFTER_MILLIS / 1000) else label,
        onClick = {
            clipboard.copy(label, value())
            copied += 1
        },
        variant = ButtonVariant.Secondary,
        enabled = enabled,
        modifier = modifier,
    )
}
