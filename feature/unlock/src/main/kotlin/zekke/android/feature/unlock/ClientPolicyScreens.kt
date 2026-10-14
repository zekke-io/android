package zekke.android.feature.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.theme.Zekke
import java.text.DateFormat
import java.util.Date

@Composable
fun UpgradeRequiredScreen(latest: String?) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize()
            .background(palette.ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.upgrade_title),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.ink,
            modifier = Modifier.semantics { heading() },
        )
        Notice(stringResource(R.string.upgrade_body), tone = NoticeTone.Danger)
        if (latest != null) {
            Text(
                stringResource(R.string.upgrade_latest, latest),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
            )
        }
        Text(
            stringResource(R.string.upgrade_how),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.inkMuted,
        )
    }
}

@Composable
fun VersionNotice(status: ClientVersionStatus, onDismissAvailable: () -> Unit) {
    when (status) {
        is ClientVersionStatus.UpdateAvailable -> Notice(
            stringResource(R.string.version_available, status.latest),
            tone = NoticeTone.Info,
            onDismiss = onDismissAvailable,
        )
        is ClientVersionStatus.Deprecated -> {
            val date = remember(status.endsAtEpochMillis) {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(status.endsAtEpochMillis))
            }
            Notice(stringResource(R.string.version_deprecated, date), tone = NoticeTone.Warning)
        }
        ClientVersionStatus.Current, is ClientVersionStatus.UpgradeRequired -> Unit
    }
}
