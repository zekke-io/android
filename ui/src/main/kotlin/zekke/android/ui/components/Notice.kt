package zekke.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.ToneColors
import zekke.android.ui.theme.Zekke

enum class NoticeTone { Info, Success, Warning, Danger }

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Info, onDismiss: (() -> Unit)? = null) {
    val palette = Zekke.palette
    val colors = when (tone) {
        NoticeTone.Info -> ToneColors(palette.brandText, palette.brandTint, palette.brandTintLine)
        NoticeTone.Success -> palette.success
        NoticeTone.Warning -> palette.warning
        NoticeTone.Danger -> palette.danger
    }
    val shape = MaterialTheme.shapes.medium
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background, shape)
            .border(1.dp, colors.line, shape)
            .padding(start = 16.dp, end = if (onDismiss == null) 16.dp else 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
            modifier = Modifier.weight(1f).padding(top = if (onDismiss == null) 0.dp else 2.dp),
        )
        if (onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = colors.text),
                modifier = Modifier.size(24.dp),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.notice_dismiss), modifier = Modifier.size(16.dp))
            }
        }
    }
}
