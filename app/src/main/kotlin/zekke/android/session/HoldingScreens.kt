package zekke.android.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.R
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.Zekke

@Composable
private fun HoldingScreen(title: String, body: String, action: @Composable () -> Unit = {}) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize().background(palette.ground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.ink, modifier = Modifier.semantics { heading() })
        Text(body, style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft)
        action()
    }
}

@Composable
fun HomeHoldingScreen(
    onLock: () -> Unit,
    notices: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) = HoldingScreen(stringResource(R.string.home_title), stringResource(R.string.home_body)) {
    notices()
    ZekkeButton(stringResource(R.string.home_lock), onLock, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
    actions()
}
