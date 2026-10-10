package zekke.android.feature.onboarding

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.CheckboxRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PinField
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SegmentedControl
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.theme.Zekke

private const val DEVICE_ID_SUFFIX_LENGTH = 8
private const val REFUSALS_BEFORE_WAITING_HINT = 2

fun deviceAddedDate(createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(Instant.parse(createdAt))
} catch (_: DateTimeParseException) {
    createdAt
}

@Composable
private fun enrolmentFailureText(failure: DeviceEnrolmentFailure): String = when (failure) {
    DeviceEnrolmentFailure.NoAccount -> stringResource(R.string.enrol_no_account)
    DeviceEnrolmentFailure.Refused -> stringResource(R.string.enrol_refused)
    DeviceEnrolmentFailure.Offline -> stringResource(R.string.enrol_offline)
    is DeviceEnrolmentFailure.RateLimited -> failure.retryAfterSeconds?.let {
        pluralStringResource(R.plurals.failure_rate_limited_seconds, it.toInt(), it.toInt())
    } ?: stringResource(R.string.failure_rate_limited)
    is DeviceEnrolmentFailure.TooManyDevices -> stringResource(R.string.enrol_device_limit)
    DeviceEnrolmentFailure.Failed -> stringResource(R.string.enrol_failed)
}

@Composable
internal fun RecoveryPhraseEntryStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val requesters = remember(state.phraseWordCount) { List(state.phraseWordCount) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.focusedPhraseWord, state.phraseWordCount) {
        requesters.getOrNull(state.focusedPhraseWord)?.requestFocus()
    }
    Page(
        title = stringResource(R.string.enrol_phrase_title),
        body = stringResource(R.string.enrol_phrase_body),
        footer = {
            BackAndContinue(
                stringResource(R.string.continue_label),
                viewModel::confirmEnteredPhrase,
                viewModel::back,
                enabled = viewModel.phraseComplete,
            )
        },
    ) {
        SegmentedControl(
            segments = listOf(12, 24).map { Segment(it, pluralStringResource(R.plurals.phrase_words, it, it)) },
            selected = state.phraseWordCount,
            onSelect = viewModel::changePhraseWordCount,
            label = stringResource(R.string.phrase_length),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        repeat(state.phraseWordCount) { index ->
            val last = index == state.phraseWordCount - 1
            PrivateField(
                value = viewModel.phraseWord(index),
                onValueChange = { viewModel.phraseWordChanged(index, it) },
                label = stringResource(R.string.enrol_word_index, index + 1),
                singleLine = true,
                keyboardType = KeyboardType.Ascii,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                onImeAction = { if (last) focusManager.clearFocus() else requesters[index + 1].requestFocus() },
                modifier = Modifier.fillMaxWidth()
                    .focusRequester(requesters[index])
                    .onFocusChanged { if (it.isFocused) viewModel.focusPhraseWord(index) },
            )
            if (state.focusedPhraseWord == index && state.phraseSuggestions.isNotEmpty()) {
                Text(stringResource(R.string.enrol_suggestions), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.phraseSuggestions.forEach { suggestion ->
                        ZekkeButton(suggestion, { viewModel.choosePhraseSuggestion(index, suggestion) }, variant = ButtonVariant.Ghost)
                    }
                }
            }
        }
        if (state.phraseProblem != null) Notice(stringResource(R.string.enrol_phrase_checksum), tone = NoticeTone.Danger)
    }
}

@Composable
internal fun EnrolPinStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val devicesToRemove = state.devicesToRemove
    val requiresRemoval = devicesToRemove != null
    Page(
        title = stringResource(R.string.enrol_pin_title),
        body = stringResource(R.string.enrol_pin_body),
        footer = {
            BackAndContinue(
                stringResource(if (requiresRemoval) R.string.enrol_remove_and_add else R.string.enrol_add),
                viewModel::submitEnrolment,
                viewModel::back,
                enabled = !requiresRemoval || (!devicesToRemove.isNullOrEmpty() && state.selectedDeviceIds.isNotEmpty()),
                continueVariant = if (requiresRemoval) ButtonVariant.Danger else ButtonVariant.Primary,
            )
        },
    ) {
        PinField(
            value = state.enrolPin,
            onValueChange = viewModel::enrolPinChanged,
            label = stringResource(R.string.enrol_pin_label),
            modifier = Modifier.fillMaxWidth(),
        )
        PinField(
            value = state.enrolPinConfirmation,
            onValueChange = viewModel::enrolPinConfirmationChanged,
            label = stringResource(R.string.pin_confirm_label),
            modifier = Modifier.fillMaxWidth(),
        )
        state.enrolPinProblem?.let { Notice(pinProblemText(it), tone = NoticeTone.Danger) }
        state.enrolmentFailure?.takeUnless { it is DeviceEnrolmentFailure.TooManyDevices }?.let {
            Notice(enrolmentFailureText(it), tone = NoticeTone.Danger)
        }
        if (state.enrolmentRefusals >= REFUSALS_BEFORE_WAITING_HINT) {
            Notice(stringResource(R.string.enrol_refused_wait), tone = NoticeTone.Info)
        }
        if (devicesToRemove != null) {
            Notice(stringResource(R.string.enrol_device_limit), tone = NoticeTone.Warning)
            if (devicesToRemove.isEmpty()) Notice(stringResource(R.string.enrol_devices_unavailable), tone = NoticeTone.Danger)
            Column {
                devicesToRemove.forEach { device ->
                    val added = remember(device.createdAt) { deviceAddedDate(device.createdAt) }
                    CheckboxRow(
                        title = stringResource(R.string.enrol_device_added, added),
                        checked = device.id in state.selectedDeviceIds,
                        onCheckedChange = { viewModel.toggleDeviceRemoval(device.id) },
                        detail = stringResource(R.string.enrol_device_details, device.id.takeLast(DEVICE_ID_SUFFIX_LENGTH)),
                        detailIsValue = true,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddingThisPhoneStep() {
    Page(title = stringResource(R.string.enrolling_title), body = stringResource(R.string.enrolling_body)) {
        LoadingIndicator()
    }
}
