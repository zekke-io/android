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
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PIN_LENGTH
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.components.PinField
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke
import zekke.core.pin.PinRejection

private const val REFUSALS_BEFORE_WAITING_HINT = 2

@Composable
fun AccountSecuritySection(state: AccountSecurityState, viewModel: AccountSecurityViewModel) {
    LaunchedEffect(Unit) { viewModel.loadMode() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        state.finished?.let { finished ->
            Notice(
                stringResource(if (finished.flow == SecurityFlow.TurnOnParanoid) R.string.security_paranoid_done else R.string.security_rotate_done),
                tone = NoticeTone.Success,
                onDismiss = viewModel::dismissFinished,
            )
            if (finished.signedOutDevices > 0) {
                Notice(
                    pluralStringResource(R.plurals.security_signed_out, finished.signedOutDevices, finished.signedOutDevices),
                    tone = NoticeTone.Info,
                    onDismiss = viewModel::dismissFinished,
                )
            }
        }
        if (!state.open && state.failure == AccountSecurityOutcome.ParanoidOnUnfinished) {
            Notice(
                stringResource(R.string.security_paranoid_unfinished),
                tone = NoticeTone.Warning,
                onDismiss = viewModel::dismissFailure,
            )
        }
        when (state.mode) {
            null -> if (state.modeUnavailable) {
                Notice(stringResource(R.string.security_mode_unavailable), tone = NoticeTone.Warning)
                ZekkeButton(stringResource(R.string.security_try_again), viewModel::loadMode, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
            }
            AccountMode.Standard -> {
                SectionText(stringResource(R.string.paranoid_section_title), stringResource(R.string.paranoid_offer_summary))
                ZekkeButton(
                    stringResource(R.string.paranoid_open),
                    { viewModel.open(SecurityFlow.TurnOnParanoid) },
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                )
            }
            AccountMode.Paranoid -> {
                SectionText(stringResource(R.string.paranoid_on_title), stringResource(R.string.paranoid_on_summary))
                Notice(stringResource(R.string.paranoid_on_one_way), tone = NoticeTone.Info)
                ZekkeButton(
                    stringResource(R.string.rotate_open),
                    { viewModel.open(SecurityFlow.ChangeAccountPin) },
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SectionText(title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Zekke.palette.ink)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
    }
}

@Composable
fun AccountSecuritySheet(state: AccountSecurityState, viewModel: AccountSecurityViewModel) {
    val flow = state.flow
    if (!state.open || flow == null) return
    val turningOn = flow == SecurityFlow.TurnOnParanoid
    val working = state.step == SecurityStep.Working
    val pinsTyped = state.pin.length == PIN_LENGTH && state.confirmation.length == PIN_LENGTH &&
        (turningOn || state.currentPin.length == PIN_LENGTH)
    FormSheet(
        title = stringResource(if (turningOn) R.string.paranoid_offer_title else R.string.rotate_title),
        submitLabel = when (state.step) {
            SecurityStep.Warning, SecurityStep.Phrase, SecurityStep.Closed -> stringResource(R.string.paranoid_continue)
            SecurityStep.Pins -> stringResource(if (turningOn) R.string.paranoid_submit else R.string.rotate_submit)
            SecurityStep.Working -> stringResource(if (turningOn) R.string.paranoid_submitting else R.string.rotate_submitting)
        },
        onSubmit = {
            when (state.step) {
                SecurityStep.Warning -> viewModel.continueFromWarning()
                SecurityStep.Phrase -> viewModel.confirmPhrase()
                SecurityStep.Pins -> viewModel.submit()
                SecurityStep.Closed, SecurityStep.Working -> Unit
            }
        },
        onDismiss = viewModel::close,
        canSubmit = when (state.step) {
            SecurityStep.Phrase -> viewModel.phrase.complete
            SecurityStep.Pins -> pinsTyped
            else -> true
        },
        busy = working,
    ) {
        state.failure?.let { failure ->
            Notice(failureText(failure, state.refusals), tone = failureTone(failure), onDismiss = viewModel::dismissFailure)
        }
        when (state.step) {
            SecurityStep.Warning -> {
                Notice(stringResource(R.string.paranoid_one_way), tone = NoticeTone.Warning)
                Text(stringResource(R.string.paranoid_offer_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
                Notice(stringResource(R.string.paranoid_signs_out), tone = NoticeTone.Info)
            }
            SecurityStep.Phrase -> {
                if (!turningOn) Text(stringResource(R.string.rotate_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
                Text(stringResource(R.string.security_phrase_prompt), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
                PhraseEntryFields(viewModel.phrase, scan = PhraseScan(viewModel::systemScreenWillOpen, viewModel::systemScreenReturned))
            }
            SecurityStep.Pins -> PinsStep(state, viewModel, turningOn)
            SecurityStep.Working -> {
                Text(stringResource(R.string.security_working), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
                LoadingIndicator()
            }
            SecurityStep.Closed -> Unit
        }
    }
}

@Composable
private fun PinsStep(state: AccountSecurityState, viewModel: AccountSecurityViewModel, turningOn: Boolean) {
    if (turningOn) {
        Notice(stringResource(R.string.paranoid_one_way), tone = NoticeTone.Warning)
        Text(stringResource(R.string.paranoid_pin_hint), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
    } else {
        Notice(stringResource(R.string.rotate_signs_out), tone = NoticeTone.Info)
        PinField(
            value = state.currentPin,
            onValueChange = viewModel::currentPinChanged,
            label = stringResource(R.string.security_current_pin_label),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PinField(
        value = state.pin,
        onValueChange = viewModel::pinChanged,
        label = stringResource(if (turningOn) R.string.security_account_pin_label else R.string.security_new_pin_label),
        modifier = Modifier.fillMaxWidth(),
    )
    PinField(
        value = state.confirmation,
        onValueChange = viewModel::confirmationChanged,
        label = stringResource(R.string.security_confirm_pin_label),
        modifier = Modifier.fillMaxWidth(),
    )
    state.pinProblem?.let { Notice(pinProblemText(it), tone = NoticeTone.Danger) }
}

@Composable
private fun pinProblemText(problem: PinProblem): String = stringResource(
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

@Composable
private fun failureText(failure: AccountSecurityOutcome, refusals: Int): String = when (failure) {
    AccountSecurityOutcome.PhraseMismatch -> stringResource(R.string.security_phrase_mismatch)
    AccountSecurityOutcome.PinRefused -> stringResource(
        if (refusals >= REFUSALS_BEFORE_WAITING_HINT) R.string.security_pin_refused_wait else R.string.security_pin_refused,
    )
    AccountSecurityOutcome.ParanoidOnUnfinished -> stringResource(R.string.security_paranoid_unfinished)
    AccountSecurityOutcome.Offline -> stringResource(R.string.security_offline)
    is AccountSecurityOutcome.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
    AccountSecurityOutcome.PhoneLocked -> stringResource(R.string.security_phone_locked)
    AccountSecurityOutcome.ChainProblem -> stringResource(R.string.security_chain_problem)
    is AccountSecurityOutcome.Done, AccountSecurityOutcome.Failed -> stringResource(R.string.security_failed)
}

private fun failureTone(failure: AccountSecurityOutcome): NoticeTone = when (failure) {
    AccountSecurityOutcome.Offline, is AccountSecurityOutcome.RateLimited, AccountSecurityOutcome.PhoneLocked,
    AccountSecurityOutcome.ParanoidOnUnfinished -> NoticeTone.Warning
    else -> NoticeTone.Danger
}
