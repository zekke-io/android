package zekke.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zekke.android.ui.R
import zekke.android.ui.theme.Zekke

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val palette = Zekke.palette
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 56.dp),
    ) {
        if (icon != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).background(palette.brandTint, MaterialTheme.shapes.large),
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = palette.brandMark, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 384.dp),
        )
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val palette = Zekke.palette
    val label = stringResource(R.string.loading)
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().padding(vertical = 48.dp)) {
        CircularProgressIndicator(
            color = palette.brandMark,
            trackColor = palette.brandTintLine,
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp).semantics { contentDescription = label },
        )
    }
}
