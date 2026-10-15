package zekke.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.CheckboxRow
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PIN_LENGTH
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.components.PinField
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke

@Composable
fun AccountSettingsSection(state: AccountSettingsState, viewModel: AccountSettingsViewModel) {
    LaunchedEffect(Unit) { viewModel.opened() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        UsernameBlock(state, viewModel)
    }
}

@Composable
private fun UsernameBlock(state: AccountSettingsState, viewModel: AccountSettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.username_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        Text(stringResource(R.string.username_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
        state.renamed?.let { Notice(stringResource(R.string.username_renamed, it), tone = NoticeTone.Success, onDismiss = viewModel::dismissRenamed) }
        state.usernameProblem?.let { problem ->
            Notice(usernameProblemText(problem), tone = if (problem is UsernameProblem.Refused && problem.outcome !is UsernameOutcome.Unavailable) NoticeTone.Warning else NoticeTone.Danger, onDismiss = viewModel::dismissUsernameProblem)
        }
        state.profile?.let {
            Text(stringResource(R.string.username_current), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
            Text(it.username, style = MonoStyle, color = Zekke.palette.ink)
        }
        if (state.fullDevice) {
            PrivateField(
                value = state.claim,
                onValueChange = viewModel::claimChanged,
                label = stringResource(R.string.username_label),
                singleLine = true,
                enabled = !state.renaming,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = viewModel::rename,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.username_hint), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
            ZekkeButton(
                stringResource(if (state.renaming) R.string.username_submitting else R.string.username_submit),
                viewModel::rename,
                Modifier.fillMaxWidth(),
                ButtonVariant.Secondary,
                enabled = !state.renaming && state.claim.isNotBlank(),
            )
            Notice(stringResource(R.string.username_permanence), tone = NoticeTone.Warning)
            Notice(stringResource(R.string.username_old_name_stops), tone = NoticeTone.Info)
        } else {
            Notice(stringResource(R.string.username_limited_device), tone = NoticeTone.Info)
        }
    }
}

@Composable
private fun usernameProblemText(problem: UsernameProblem): String = when (problem) {
    UsernameProblem.Malformed -> stringResource(R.string.username_malformed)
    UsernameProblem.Unchanged -> stringResource(R.string.username_unchanged)
    is UsernameProblem.Refused -> when (val outcome = problem.outcome) {
        UsernameOutcome.Unavailable -> stringResource(R.string.username_unavailable)
        UsernameOutcome.Offline -> stringResource(R.string.security_offline)
        is UsernameOutcome.RateLimited -> rateLimitedMessage(outcome.retryAfterSeconds)
        else -> stringResource(R.string.security_failed)
    }
}

@Composable
fun DeleteAccountSection(state: AccountSettingsState, viewModel: AccountSettingsViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.delete_title), style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        if (!state.fullDevice) {
            Notice(stringResource(R.string.delete_limited_device), tone = NoticeTone.Info)
            return@Column
        }
        Text(stringResource(R.string.delete_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
        Notice(stringResource(R.string.delete_warning), tone = NoticeTone.Danger)
        val retention = state.profile?.retentionDays ?: 0
        if (retention > 0) {
            Text(
                pluralStringResource(R.plurals.delete_retention, retention, retention),
                style = MaterialTheme.typography.bodySmall,
                color = Zekke.palette.inkMuted,
            )
        }
        if (state.profile?.renews == true) Notice(stringResource(R.string.delete_renews), tone = NoticeTone.Warning)
        ZekkeButton(stringResource(R.string.delete_open), viewModel::openDelete, Modifier.fillMaxWidth(), ButtonVariant.Danger)
    }
}

@Composable
fun DeleteAccountSheet(state: AccountSettingsState, viewModel: AccountSettingsViewModel) {
    if (!state.deleting) return
    FormSheet(
        title = stringResource(R.string.delete_title),
        submitLabel = stringResource(if (state.working) R.string.delete_deleting else R.string.delete_submit),
        onSubmit = viewModel::delete,
        onDismiss = viewModel::closeDelete,
        canSubmit = viewModel.canDelete(),
        busy = state.working,
    ) {
        state.deleteFailure?.let { failure ->
            Notice(deleteFailureText(failure, state.refusals), tone = deleteFailureTone(failure), onDismiss = viewModel::dismissDeleteFailure)
        }
        Notice(stringResource(R.string.delete_warning), tone = NoticeTone.Danger)
        Text(stringResource(R.string.delete_phrase_hint), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        PhraseEntryFields(viewModel.phrase, scan = PhraseScan(viewModel::systemScreenWillOpen, viewModel::systemScreenReturned))
        if (state.needsPin) {
            PinField(
                value = state.pin,
                onValueChange = viewModel::pinChanged,
                label = stringResource(R.string.delete_account_pin),
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        CheckboxRow(
            title = stringResource(R.string.delete_confirm),
            checked = state.confirmed,
            onCheckedChange = viewModel::confirmedChanged,
            enabled = !state.working,
        )
    }
}

@Composable
private fun deleteFailureText(failure: DeleteOutcome, refusals: Int): String = when (failure) {
    DeleteOutcome.PhraseMismatch -> stringResource(R.string.security_phrase_mismatch)
    DeleteOutcome.PinRefused -> stringResource(if (refusals >= REFUSALS_BEFORE_WAIT_HINT) R.string.security_pin_refused_wait else R.string.security_pin_refused)
    DeleteOutcome.Offline -> stringResource(R.string.security_offline)
    is DeleteOutcome.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
    DeleteOutcome.PhoneLocked -> stringResource(R.string.security_phone_locked)
    DeleteOutcome.ChainProblem -> stringResource(R.string.security_chain_problem)
    DeleteOutcome.Deleted, DeleteOutcome.Failed -> stringResource(R.string.security_failed)
}

private fun deleteFailureTone(failure: DeleteOutcome): NoticeTone = when (failure) {
    DeleteOutcome.Offline, is DeleteOutcome.RateLimited, DeleteOutcome.PhoneLocked -> NoticeTone.Warning
    else -> NoticeTone.Danger
}
