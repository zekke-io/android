package zekke.android.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.components.PinField

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
internal fun RecoveryPhraseEntryStep(viewModel: OnboardingViewModel) {
    Page(
        title = stringResource(R.string.enrol_phrase_title),
        body = stringResource(R.string.enrol_phrase_body),
        footer = {
            BackAndContinue(
                stringResource(R.string.continue_label),
                viewModel::confirmEnteredPhrase,
                viewModel::back,
                enabled = viewModel.phrase.complete,
            )
        },
    ) {
        PhraseEntryFields(viewModel.phrase, scan = PhraseScan(viewModel::systemScreenWillOpen, viewModel::systemScreenReturned))
    }
}

@Composable
internal fun EnrolPinStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    val devicesToRemove = state.devicesToRemove
    val requiresChoice = devicesToRemove != null && !state.removeAllDevices
    val removing = state.removeAllDevices || devicesToRemove != null
    Page(
        title = stringResource(R.string.enrol_pin_title),
        body = stringResource(R.string.enrol_pin_body),
        footer = {
            BackAndContinue(
                stringResource(
                    when {
                        state.removeAllDevices -> R.string.enrol_remove_all_and_add
                        devicesToRemove != null -> R.string.enrol_remove_and_add
                        else -> R.string.enrol_add
                    },
                ),
                viewModel::submitEnrolment,
                viewModel::back,
                enabled = !requiresChoice || (!devicesToRemove.isNullOrEmpty() && state.selectedDeviceIds.isNotEmpty()),
                continueVariant = if (removing) ButtonVariant.Danger else ButtonVariant.Primary,
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
        CheckboxRow(
            title = stringResource(R.string.enrol_lost_devices),
            checked = state.removeAllDevices,
            onCheckedChange = viewModel::toggleRemoveAllDevices,
        )
        if (state.removeAllDevices) Notice(stringResource(R.string.enrol_lost_devices_warning), tone = NoticeTone.Warning)
        if (requiresChoice) {
            Notice(stringResource(R.string.enrol_device_limit), tone = NoticeTone.Warning)
            if (devicesToRemove.isNullOrEmpty()) Notice(stringResource(R.string.enrol_devices_unavailable), tone = NoticeTone.Danger)
            Column {
                devicesToRemove.orEmpty().forEach { device ->
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
