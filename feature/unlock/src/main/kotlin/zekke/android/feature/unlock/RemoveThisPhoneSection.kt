package zekke.android.feature.unlock

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
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke

@Composable
fun RemoveThisPhoneSection(state: RemovePhoneState, viewModel: RemoveThisPhoneViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!state.confirming) {
            ZekkeButton(
                stringResource(R.string.remove_phone_label),
                viewModel::requestRemoval,
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
                enabled = !state.busy,
            )
            Text(
                stringResource(R.string.remove_phone_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Zekke.palette.inkMuted,
            )
        } else {
            Notice(stringResource(R.string.remove_phone_confirm), tone = NoticeTone.Warning)
            ZekkeButton(
                stringResource(if (state.busy) R.string.remove_phone_removing else R.string.remove_phone_label),
                viewModel::confirmRemoval,
                Modifier.fillMaxWidth(),
                ButtonVariant.Danger,
                enabled = !state.busy,
            )
            ZekkeButton(
                stringResource(R.string.remove_phone_stay),
                viewModel::stay,
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
                enabled = !state.busy,
            )
        }
        state.failure?.let { failure ->
            Notice(
                removalFailureText(failure),
                tone = removalFailureTone(failure),
                onDismiss = viewModel::dismissFailure,
            )
        }
    }
}

@Composable
private fun removalFailureText(failure: RemovalOutcome): String = when (failure) {
    RemovalOutcome.Offline -> stringResource(R.string.remove_phone_offline)
    is RemovalOutcome.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
    RemovalOutcome.Refused -> stringResource(R.string.remove_phone_refused)
    RemovalOutcome.ChainProblem -> stringResource(R.string.remove_phone_chain_problem)
    RemovalOutcome.PhoneLocked -> stringResource(R.string.remove_phone_phone_locked)
    RemovalOutcome.Removed, RemovalOutcome.AlreadyRemoved, RemovalOutcome.Failed -> stringResource(R.string.unlock_failed)
}

private fun removalFailureTone(failure: RemovalOutcome): NoticeTone = when (failure) {
    RemovalOutcome.Offline, is RemovalOutcome.RateLimited, RemovalOutcome.PhoneLocked -> NoticeTone.Warning
    RemovalOutcome.Refused, RemovalOutcome.ChainProblem, RemovalOutcome.Failed,
    RemovalOutcome.Removed, RemovalOutcome.AlreadyRemoved -> NoticeTone.Danger
}
