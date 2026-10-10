package zekke.android.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PinField
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SegmentedControl
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke
import zekke.core.pin.PinRejection

@Composable
fun OnboardingFlow(state: OnboardingState, viewModel: OnboardingViewModel, version: String) {
    when (state.step) {
        OnboardingStep.Start -> StartStep(version, viewModel::startCreating)
        OnboardingStep.Done -> if (state.lostToLock) LostToLockStep(viewModel::acknowledgeLoss) else StartStep(version, viewModel::startCreating)
        OnboardingStep.Phrase -> PhraseStep(state.wordCount, viewModel::chooseWordCount, viewModel::confirmPhrase, viewModel::back)
        OnboardingStep.Mode -> ModeStep(state.paranoid, viewModel::chooseMode, viewModel::confirmMode, viewModel::back)
        OnboardingStep.Pin -> PinStep(state, viewModel)
        OnboardingStep.Creating -> CreatingStep()
        OnboardingStep.Recovery -> RecoveryStep(state, viewModel::revealPhrase, viewModel::finish)
    }
}

@Composable
private fun Page(title: String?, body: String?, footer: @Composable ColumnScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    val palette = Zekke.palette
    Column(
        Modifier.fillMaxSize().background(palette.ground).safeDrawingPadding().imePadding(),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = palette.ink, modifier = Modifier.semantics { heading() })
            }
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft)
            }
            content()
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = footer,
        )
    }
}

@Composable
private fun StartStep(version: String, onCreate: () -> Unit) {
    val palette = Zekke.palette
    Page(
        title = null,
        body = null,
        footer = {
            ZekkeButton(stringResource(R.string.start_create), onCreate, Modifier.fillMaxWidth(), ButtonVariant.Accent)
            Text(
                stringResource(R.string.start_version, version),
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        },
    ) {
        Spacer(Modifier.height(96.dp))
        Text(
            stringResource(R.string.start_title),
            style = MaterialTheme.typography.displaySmall,
            color = palette.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.start_tagline), style = MaterialTheme.typography.bodyLarge, color = palette.inkSoft)
    }
}

@Composable
private fun LostToLockStep(onContinue: () -> Unit) {
    Page(
        title = stringResource(R.string.lost_title),
        body = null,
        footer = { ZekkeButton(stringResource(R.string.continue_label), onContinue, Modifier.fillMaxWidth()) },
    ) {
        Notice(stringResource(R.string.lost_body), tone = NoticeTone.Warning)
    }
}

@Composable
private fun BackAndContinue(continueLabel: String, onContinue: () -> Unit, onBack: () -> Unit, enabled: Boolean = true) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ZekkeButton(stringResource(R.string.back), onBack, Modifier.weight(1f), ButtonVariant.Secondary, enabled)
        ZekkeButton(continueLabel, onContinue, Modifier.weight(2f), ButtonVariant.Primary, enabled)
    }
}

@Composable
private fun PhraseStep(wordCount: Int, onWordCount: (Int) -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    Page(
        title = stringResource(R.string.phrase_title),
        body = stringResource(R.string.phrase_body),
        footer = { BackAndContinue(stringResource(R.string.phrase_create), onContinue, onBack) },
    ) {
        SegmentedControl(
            segments = listOf(12, 24).map { Segment(it, pluralStringResource(R.plurals.phrase_words, it, it)) },
            selected = wordCount,
            onSelect = onWordCount,
            label = stringResource(R.string.phrase_length),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ModeStep(paranoid: Boolean, onMode: (Boolean) -> Unit, onContinue: () -> Unit, onBack: () -> Unit) {
    val palette = Zekke.palette
    Page(
        title = stringResource(R.string.mode_title),
        body = null,
        footer = { BackAndContinue(stringResource(R.string.continue_label), onContinue, onBack) },
    ) {
        SegmentedControl(
            segments = listOf(Segment(false, stringResource(R.string.mode_standard)), Segment(true, stringResource(R.string.mode_paranoid))),
            selected = paranoid,
            onSelect = onMode,
            label = stringResource(R.string.mode_label),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Column(
            Modifier.fillMaxWidth()
                .background(palette.surface, MaterialTheme.shapes.large)
                .border(1.dp, palette.line, MaterialTheme.shapes.large)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(if (paranoid) R.string.mode_paranoid_summary else R.string.mode_standard_summary),
                style = MaterialTheme.typography.titleSmall,
                color = palette.ink,
            )
            Text(
                stringResource(if (paranoid) R.string.mode_paranoid_tradeoff else R.string.mode_standard_tradeoff),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.inkMuted,
            )
        }
        if (paranoid) Notice(stringResource(R.string.mode_one_way), tone = NoticeTone.Warning)
    }
}

@Composable
fun pinProblemText(problem: PinProblem): String = stringResource(
    when (problem) {
        PinProblem.Mismatch -> R.string.pin_mismatch
        is PinProblem.Rejected -> when (problem.reason) {
            PinRejection.WRONG_LENGTH -> R.string.pin_wrong_length
            PinRejection.NON_DIGIT -> R.string.pin_non_digit
            PinRejection.REPEATING_DIGIT -> R.string.pin_repeating
            PinRejection.ASCENDING_SEQUENCE -> R.string.pin_ascending
            PinRejection.DESCENDING_SEQUENCE -> R.string.pin_descending
        }
    },
)

@Composable
fun failureText(failure: CreationFailure): String = when (failure) {
    CreationFailure.Offline -> stringResource(R.string.failure_offline)
    CreationFailure.Failed -> stringResource(R.string.failure_generic)
    is CreationFailure.RateLimited -> failure.retryAfterSeconds?.let { pluralStringResource(R.plurals.failure_rate_limited_seconds, it.toInt(), it.toInt()) }
        ?: stringResource(R.string.failure_rate_limited)
}

@Composable
private fun PinStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    Page(
        title = stringResource(R.string.pin_title),
        body = stringResource(R.string.pin_subtitle),
        footer = { BackAndContinue(stringResource(R.string.pin_create), viewModel::submitPin, viewModel::back) },
    ) {
        if (state.paranoid) Notice(stringResource(R.string.mode_one_way), tone = NoticeTone.Warning)
        PinField(
            value = state.pin,
            onValueChange = viewModel::pinChanged,
            label = stringResource(R.string.pin_label),
            modifier = Modifier.fillMaxWidth(),
        )
        PinField(
            value = state.confirmation,
            onValueChange = viewModel::confirmationChanged,
            label = stringResource(R.string.pin_confirm_label),
            modifier = Modifier.fillMaxWidth(),
            onComplete = { viewModel.submitPin() },
        )
        state.pinProblem?.let { Notice(pinProblemText(it), tone = NoticeTone.Danger) }
        state.failure?.let { Notice(failureText(it), tone = NoticeTone.Danger) }
    }
}

@Composable
private fun CreatingStep() {
    Page(title = stringResource(R.string.creating_title), body = stringResource(R.string.creating_body)) {
        LoadingIndicator()
    }
}

@Composable
private fun RecoveryStep(state: OnboardingState, onReveal: () -> Unit, onContinue: () -> Unit) {
    val palette = Zekke.palette
    val revealed = state.revealedPhrase
    Page(
        title = stringResource(R.string.recovery_title),
        body = state.username?.let { stringResource(R.string.recovery_username, it) },
        footer = {
            ZekkeButton(stringResource(R.string.recovery_continue), onContinue, Modifier.fillMaxWidth(), enabled = revealed != null)
        },
    ) {
        if (state.paranoidFailed) Notice(stringResource(R.string.recovery_paranoid_failed), tone = NoticeTone.Warning)
        Notice(stringResource(R.string.recovery_warning), tone = NoticeTone.Warning)
        if (revealed == null) {
            ZekkeButton(stringResource(R.string.recovery_reveal), onReveal, Modifier.fillMaxWidth(), ButtonVariant.Secondary)
        } else {
            Column(
                Modifier.fillMaxWidth()
                    .background(palette.raised, MaterialTheme.shapes.medium)
                    .border(1.dp, palette.line, MaterialTheme.shapes.medium)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                revealed.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEachIndexed { column, word ->
                            Text(
                                stringResource(R.string.recovery_word, row * 2 + column + 1, word),
                                style = MonoStyle,
                                color = palette.ink,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Text(stringResource(R.string.recovery_not_copyable), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        }
    }
}
