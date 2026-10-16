package zekke.android.feature.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.Mascot
import zekke.android.ui.components.MascotPose
import zekke.android.ui.components.PinPad
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke

@Composable
fun UnlockFlow(
    state: UnlockState,
    viewModel: UnlockViewModel,
    onAddRecoveryPhrase: () -> Unit,
    versionNotice: @Composable () -> Unit = {},
) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize()
            .background(palette.ground)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Mascot(MascotPose.Closed, Modifier.align(Alignment.CenterHorizontally).size(96.dp))
        Text(
            stringResource(R.string.unlock_title),
            style = MaterialTheme.typography.headlineMedium,
            color = palette.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        versionNotice()
        if (state.registrationLost != null) {
            val message = when (state.registrationLost) {
                RegistrationLost.Forgotten -> stringResource(R.string.unlock_forgotten)
                RegistrationLost.Removed -> stringResource(R.string.unlock_removed)
            }
            Notice(message, tone = NoticeTone.Warning)
            ZekkeButton(
                stringResource(R.string.unlock_add_phrase),
                onAddRecoveryPhrase,
                Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(R.string.unlock_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            PinPad(
                value = viewModel.pinValue(),
                onValueChange = viewModel::pinChanged,
                label = stringResource(R.string.unlock_pin_label),
                enabled = !state.busy,
                onComplete = { viewModel.submitPin() },
            )
            state.feedback?.let { feedback ->
                Notice(
                    feedbackText(feedback),
                    tone = feedbackTone(feedback),
                    onDismiss = viewModel::dismissFeedback,
                )
            }
            if (!state.confirmingForget) {
                ZekkeButton(
                    stringResource(if (state.busy) R.string.unlock_submitting else R.string.unlock_submit),
                    viewModel::submitPin,
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy && viewModel.pinValue().length == 6,
                )
                ZekkeButton(
                    stringResource(R.string.unlock_start_over),
                    viewModel::requestForget,
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                    enabled = !state.busy,
                )
            } else {
                Notice(stringResource(R.string.unlock_start_over_confirm), tone = NoticeTone.Warning)
                ZekkeButton(
                    stringResource(if (state.busy) R.string.unlock_forgetting else R.string.unlock_forget_submit),
                    viewModel::forgetThisPhone,
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Danger,
                    enabled = !state.busy,
                )
                ZekkeButton(
                    stringResource(R.string.unlock_stay),
                    viewModel::keepTrying,
                    Modifier.fillMaxWidth(),
                    ButtonVariant.Secondary,
                    enabled = !state.busy,
                )
            }
        }
    }
}

@Composable
fun UnlockSessionNotices(
    state: UnlockState,
    onDismissChainProblem: () -> Unit,
    onDismissRewrapIssue: () -> Unit,
) {
    if (state.chainProblem) {
        Notice(
            stringResource(R.string.unlock_chain_problem),
            tone = NoticeTone.Danger,
            onDismiss = onDismissChainProblem,
        )
    }
    state.rewrapIssue?.let { issue ->
        Notice(
            rewrapIssueText(issue),
            tone = NoticeTone.Warning,
            onDismiss = onDismissRewrapIssue,
        )
    }
}

@Composable
private fun feedbackText(feedback: UnlockFeedback): String = when (feedback) {
    is UnlockFeedback.WrongPin -> {
        if (feedback.attemptsRemaining <= 1) {
            stringResource(R.string.unlock_last_attempt)
        } else {
            pluralStringResource(
                R.plurals.unlock_attempts_remaining,
                feedback.attemptsRemaining,
                feedback.attemptsRemaining,
            )
        }
    }
    UnlockFeedback.Offline -> stringResource(R.string.unlock_offline)
    is UnlockFeedback.RateLimited -> rateLimitedMessage(feedback.retryAfterSeconds)
    UnlockFeedback.PhoneLocked -> stringResource(R.string.unlock_phone_locked)
    UnlockFeedback.Interrupted -> stringResource(R.string.unlock_interrupted)
    UnlockFeedback.Failed -> stringResource(R.string.unlock_failed)
}

@Composable
private fun feedbackTone(feedback: UnlockFeedback): NoticeTone = when (feedback) {
    is UnlockFeedback.WrongPin, UnlockFeedback.Failed -> NoticeTone.Danger
    UnlockFeedback.Offline,
    is UnlockFeedback.RateLimited,
    UnlockFeedback.PhoneLocked,
    UnlockFeedback.Interrupted -> NoticeTone.Warning
}

@Composable
private fun rewrapIssueText(issue: RewrapIssue): String = when (issue) {
    RewrapIssue.Offline -> stringResource(R.string.unlock_rewrap_offline)
    is RewrapIssue.RateLimited -> rateLimitedMessage(issue.retryAfterSeconds)
    RewrapIssue.Failed -> stringResource(R.string.unlock_rewrap_failed)
}
