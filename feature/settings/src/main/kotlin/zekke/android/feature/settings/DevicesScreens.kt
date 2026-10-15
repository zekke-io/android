package zekke.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PhraseEntryFields
import zekke.android.ui.components.PhraseScan
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.components.rateLimitedMessage
import zekke.android.ui.theme.Zekke
import zekke.core.scopes.Scope

@Composable
private fun scopeLabel(scope: Scope): String = stringResource(
    when (scope) {
        Scope.ADMIN -> R.string.devices_scope_admin
        Scope.PASSWORDS -> R.string.devices_scope_passwords
        Scope.SECRETS -> R.string.devices_scope_secrets
        Scope.NOTES -> R.string.devices_scope_notes
        Scope.DOCUMENTS -> R.string.devices_scope_documents
        Scope.FILES -> R.string.devices_scope_files
        Scope.SHARING -> R.string.devices_scope_sharing
    },
)

@Composable
fun deviceScopesText(device: DeviceItem): String {
    if (device.everything) return stringResource(R.string.devices_everything)
    val labels = device.scopes.map { scopeLabel(it) }
    return labels.joinToString(", ")
}

fun deviceDate(addedAt: String, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(Instant.parse(addedAt)) }
        .getOrDefault("")

@Composable
fun DevicesScreen(
    state: DevicesState,
    viewModel: DevicesViewModel,
    extension: @Composable () -> Unit,
    thisPhone: @Composable () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.opened() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.devices_summary), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkMuted)
        if (!state.chainDismissed) {
            when (state.chainVerified) {
                true -> Notice(stringResource(R.string.devices_chain_verified), tone = NoticeTone.Success, onDismiss = viewModel::dismissChain)
                false -> Notice(stringResource(R.string.devices_chain_broken), tone = NoticeTone.Danger, onDismiss = viewModel::dismissChain)
                null -> Unit
            }
        }
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        state.notice?.let { Notice(stringResource(R.string.devices_removed), tone = NoticeTone.Success, onDismiss = viewModel::dismissNotice) }
        if (!state.loaded) LoadingIndicator()
        Column {
            state.devices.forEach { device ->
                val clickable = state.canName || state.canRemove(device)
                ItemRow(
                    title = device.name ?: stringResource(if (device.thisDevice) R.string.devices_this_phone else R.string.devices_unnamed),
                    meta = stringResource(R.string.devices_meta, deviceScopesText(device), deviceDate(device.addedAt)),
                    onClick = if (clickable) ({ viewModel.open(device) }) else null,
                    trailing = {
                        if (device.thisDevice) StatusBadge(stringResource(R.string.devices_this_phone), Tone.Brand)
                        StatusBadge(stringResource(if (device.full) R.string.devices_full else R.string.devices_limited), Tone.Neutral)
                    },
                )
            }
        }
        extension()
        thisPhone()
    }
    DeviceActionsSheet(state, viewModel)
    RemoveDeviceSheet(state, viewModel)
}

@Composable
private fun DeviceActionsSheet(state: DevicesState, viewModel: DevicesViewModel) {
    val device = state.actingDevice ?: return
    FormSheet(
        title = device.name ?: stringResource(if (device.thisDevice) R.string.devices_this_phone else R.string.devices_unnamed),
        submitLabel = stringResource(R.string.devices_save),
        onSubmit = viewModel::saveName,
        onDismiss = viewModel::closeActions,
        canSubmit = state.canName,
        busy = state.busy,
    ) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        if (state.canName) {
            PrivateField(
                value = state.draft,
                onValueChange = viewModel::draftChanged,
                label = stringResource(R.string.devices_name_label),
                singleLine = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onImeAction = viewModel::saveName,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.devices_name_hint), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        }
        if (state.canRemove(device)) {
            ZekkeButton(stringResource(R.string.devices_remove), { viewModel.askRemove(device) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
        }
    }
}

@Composable
private fun RemoveDeviceSheet(state: DevicesState, viewModel: DevicesViewModel) {
    val device = state.removing ?: return
    FormSheet(
        title = stringResource(R.string.devices_remove_title, device.name ?: stringResource(R.string.devices_unnamed)),
        submitLabel = stringResource(if (state.working) R.string.devices_removing else R.string.devices_remove_submit),
        onSubmit = viewModel::confirmRemove,
        onDismiss = viewModel::cancelRemove,
        canSubmit = viewModel.phrase.complete,
        busy = state.working,
    ) {
        state.removalFailure?.let { failure ->
            Notice(removalText(failure), tone = removalTone(failure), onDismiss = viewModel::dismissRemovalFailure)
        }
        Notice(stringResource(R.string.devices_remove_warning), tone = NoticeTone.Warning)
        Text(stringResource(R.string.devices_remove_phrase), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        PhraseEntryFields(viewModel.phrase, scan = PhraseScan(viewModel::systemScreenWillOpen, viewModel::systemScreenReturned))
    }
}

@Composable
private fun removalText(failure: DeviceRemoval): String = when (failure) {
    DeviceRemoval.PhraseMismatch -> stringResource(R.string.security_phrase_mismatch)
    DeviceRemoval.Offline -> stringResource(R.string.security_offline)
    is DeviceRemoval.RateLimited -> rateLimitedMessage(failure.retryAfterSeconds)
    DeviceRemoval.PhoneLocked -> stringResource(R.string.security_phone_locked)
    DeviceRemoval.ChainProblem -> stringResource(R.string.devices_chain_broken)
    DeviceRemoval.Removed, DeviceRemoval.Failed -> stringResource(R.string.security_failed)
}

private fun removalTone(failure: DeviceRemoval): NoticeTone = when (failure) {
    DeviceRemoval.Offline, is DeviceRemoval.RateLimited, DeviceRemoval.PhoneLocked -> NoticeTone.Warning
    else -> NoticeTone.Danger
}
