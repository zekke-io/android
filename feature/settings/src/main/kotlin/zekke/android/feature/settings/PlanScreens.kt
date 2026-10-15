package zekke.android.feature.settings

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.StorageMeter
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke

fun planDate(at: String?, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String? =
    at?.let { runCatching { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(Instant.parse(it)) }.getOrNull() }

@Composable
fun PlanScreen(state: PlanState, viewModel: PlanViewModel) {
    LaunchedEffect(Unit) { viewModel.opened() }
    val resources: Resources = LocalResources.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        val plan = state.plan
        if (plan == null) {
            if (!state.loaded) LoadingIndicator()
            return@Column
        }
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        Text(planName(plan.code, resources), style = MaterialTheme.typography.headlineSmall, color = Zekke.palette.ink)
        when (plan.state) {
            PlanStates.ACTIVE -> {
                planDate(plan.paidUntil)?.let { Text(stringResource(R.string.plan_paid_until, it), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft) }
                if (plan.renews) Notice(stringResource(R.string.plan_renews), tone = NoticeTone.Info)
            }
            PlanStates.GRACE -> {
                Notice(
                    stringResource(
                        R.string.plan_grace,
                        planDate(plan.graceEndsAt) ?: stringResource(R.string.plan_grace_soon),
                    ),
                    tone = NoticeTone.Warning,
                )
                Text(stringResource(R.string.plan_data_loss), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
            }
            else -> Unit
        }
        state.storage?.let { StorageMeter(it) }
        Text(
            if (plan.retentionDays == 0) {
                stringResource(R.string.plan_trash_none)
            } else {
                pluralStringResource(R.plurals.plan_trash_days, plan.retentionDays, plan.retentionDays)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Zekke.palette.inkSoft,
        )
        Text(stringResource(R.string.plan_read_only), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
    }
}

@Composable
fun AboutScreen(versionName: String, notices: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        notices()
    }
}
