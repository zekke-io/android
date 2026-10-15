package zekke.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PIN_LENGTH
import zekke.android.ui.components.PinField
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SegmentedControl
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke
import zekke.core.pin.PinRejection

@Composable
fun SecurityScreen(state: SecurityState, viewModel: SecurityViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.idle_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
            Text(stringResource(R.string.idle_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
            SegmentedControl(
                segments = IDLE_CHOICES_MINUTES.map { Segment(it, pluralStringResource(R.plurals.idle_minutes, it, it)) },
                selected = state.idleMinutes,
                onSelect = viewModel::chooseIdle,
                label = stringResource(R.string.idle_title),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.device_pin_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
            Text(stringResource(R.string.device_pin_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
            state.outcome?.let { outcome ->
                Notice(outcomeText(outcome), tone = if (outcome == DevicePinChange.Done) NoticeTone.Success else NoticeTone.Warning, onDismiss = viewModel::dismissOutcome)
            }
            state.problem?.let { Notice(devicePinProblemText(it), tone = NoticeTone.Danger) }
            PinField(
                value = state.pin,
                onValueChange = viewModel::pinChanged,
                label = stringResource(R.string.device_pin_new),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            PinField(
                value = state.confirmation,
                onValueChange = viewModel::confirmationChanged,
                label = stringResource(R.string.device_pin_confirm),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            ZekkeButton(
                stringResource(if (state.busy) R.string.device_pin_changing else R.string.device_pin_submit),
                viewModel::submit,
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
                enabled = !state.busy && state.pin.length == PIN_LENGTH && state.confirmation.length == PIN_LENGTH,
            )
        }
    }
}

@Composable
private fun outcomeText(outcome: DevicePinChange): String = when (outcome) {
    DevicePinChange.Done -> stringResource(R.string.device_pin_done)
    DevicePinChange.Offline -> stringResource(R.string.security_offline)
    is DevicePinChange.RateLimited -> rateLimitedMessage(outcome.retryAfterSeconds)
    DevicePinChange.PhoneLocked -> stringResource(R.string.security_phone_locked)
    DevicePinChange.Failed -> stringResource(R.string.security_failed)
}

@Composable
private fun devicePinProblemText(problem: PinProblem): String = stringResource(
    when (problem) {
        PinProblem.Mismatch -> R.string.security_pin_mismatch
        PinProblem.CurrentIncomplete -> R.string.security_current_pin_incomplete
        is PinProblem.Rejected -> when (problem.reason) {
            PinRejection.WRONG_LENGTH -> R.string.security_pin_wrong_length
            PinRejection.NON_DIGIT -> R.string.security_pin_non_digit
            PinRejection.REPEATING_DIGIT -> R.string.security_pin_repeating
            PinRejection.ASCENDING_SEQUENCE -> R.string.security_pin_ascending
            PinRejection.DESCENDING_SEQUENCE -> R.string.security_pin_descending
        }
    },
)
