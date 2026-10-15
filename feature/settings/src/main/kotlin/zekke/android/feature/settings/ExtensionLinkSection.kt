package zekke.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke

fun formatCountdown(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
fun ExtensionLinkSection(state: ExtensionLinkState, viewModel: ExtensionLinkViewModel) {
    if (!state.canLink) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.extension_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        Text(stringResource(R.string.extension_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        when (val step = state.step) {
            LinkStep.Idle, LinkStep.Linked, LinkStep.Refused, is LinkStep.Ended -> {
                when (step) {
                    LinkStep.Linked -> Notice(stringResource(R.string.extension_linked), tone = NoticeTone.Success, onDismiss = viewModel::dismiss)
                    LinkStep.Refused -> Notice(stringResource(R.string.extension_mismatch_warning), tone = NoticeTone.Danger, onDismiss = viewModel::dismiss)
                    is LinkStep.Ended -> Notice(
                        stringResource(if (step.reason == LinkEnd.Expired) R.string.extension_expired else R.string.extension_cancelled),
                        tone = NoticeTone.Warning,
                        onDismiss = viewModel::dismiss,
                    )
                    else -> Unit
                }
                Text(stringResource(R.string.extension_paranoid_note), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
                ZekkeButton(stringResource(R.string.extension_start), viewModel::start, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }
            LinkStep.Opening -> LoadingIndicator()
            is LinkStep.Waiting -> {
                Text(stringResource(R.string.extension_code_heading), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
                Text(step.code, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MonoStyle.fontFamily), color = Zekke.palette.ink)
                Text(
                    stringResource(R.string.extension_code_hint, formatCountdown(step.secondsLeft)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Zekke.palette.inkMuted,
                )
                ZekkeButton(stringResource(R.string.extension_cancel), viewModel::cancel, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }
            is LinkStep.Comparing -> {
                Text(stringResource(R.string.extension_compare_heading), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
                Text(step.fingerprint, style = MaterialTheme.typography.headlineLarge.copy(fontFamily = MonoStyle.fontFamily), color = Zekke.palette.brandText)
                Text(stringResource(R.string.extension_compare_hint), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
                if (state.canName) {
                    PrivateField(
                        value = state.name,
                        onValueChange = viewModel::nameChanged,
                        label = stringResource(R.string.extension_name_label),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ZekkeButton(stringResource(R.string.extension_match), viewModel::confirm, Modifier.fillMaxWidth())
                ZekkeButton(stringResource(R.string.extension_no_match), viewModel::refuse, Modifier.fillMaxWidth(), ButtonVariant.Danger)
                Text(
                    stringResource(R.string.extension_time_left, formatCountdown(step.secondsLeft)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Zekke.palette.inkMuted,
                )
            }
            LinkStep.Linking -> Text(stringResource(R.string.extension_linking), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        }
    }
}
