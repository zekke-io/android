package zekke.android.feature.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.CopyButton
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.Segment
import zekke.android.ui.components.SecretField
import zekke.android.ui.components.SegmentedControl
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke
import zekke.core.credentials.SiteMatch
import androidx.compose.runtime.remember

@Composable
fun PasswordFormSheet(state: PasswordsState, viewModel: PasswordsViewModel) {
    val form = state.form ?: return
    val editing = form.editing
    val site = remember { FocusRequester() }
    val username = remember { FocusRequester() }
    val password = remember { FocusRequester() }
    val note = remember { FocusRequester() }
    val urls = remember { FocusRequester() }
    FormSheet(
        title = stringResource(if (editing == null) R.string.password_form_add_title else R.string.password_form_edit_title),
        submitLabel = when {
            state.busy -> stringResource(R.string.password_form_saving)
            editing == null -> stringResource(R.string.password_form_add_submit)
            else -> stringResource(R.string.password_form_edit_submit)
        },
        onSubmit = viewModel::save,
        onDismiss = viewModel::closeForm,
        canSubmit = form.complete,
        busy = state.busy,
    ) {
        Text(
            stringResource(if (editing == null) R.string.password_form_add_subtitle else R.string.password_form_edit_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = Zekke.palette.inkSoft,
        )
        state.failure?.let {
            Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure)
        }
        PrivateField(
            value = form.site,
            onValueChange = viewModel::siteChanged,
            label = stringResource(R.string.password_field_site),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = { username.requestFocus() },
            modifier = Modifier.fillMaxWidth().focusRequester(site),
        )
        PrivateField(
            value = form.username,
            onValueChange = viewModel::usernameChanged,
            label = stringResource(R.string.password_field_username),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = { password.requestFocus() },
            modifier = Modifier.fillMaxWidth().focusRequester(username),
        )
        SecretField(
            value = form.password,
            onValueChange = viewModel::passwordChanged,
            label = stringResource(R.string.password_field_password),
            imeAction = ImeAction.Next,
            onImeAction = { note.requestFocus() },
            modifier = Modifier.fillMaxWidth().focusRequester(password),
        )
        PrivateField(
            value = form.note,
            onValueChange = viewModel::noteChanged,
            label = stringResource(R.string.password_field_note),
            singleLine = true,
            imeAction = ImeAction.Next,
            onImeAction = { urls.requestFocus() },
            modifier = Modifier.fillMaxWidth().focusRequester(note),
        )
        PrivateField(
            value = form.urls,
            onValueChange = viewModel::urlsChanged,
            label = stringResource(R.string.password_field_urls),
            singleLine = true,
            imeAction = ImeAction.Done,
            modifier = Modifier.fillMaxWidth().focusRequester(urls),
        )
        Text(
            stringResource(R.string.password_field_urls_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Zekke.palette.inkMuted,
        )
        Text(stringResource(R.string.password_field_match), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.ink)
        SegmentedControl(
            segments = listOf(
                Segment(SiteMatch.DOMAIN, stringResource(R.string.password_match_domain)),
                Segment(SiteMatch.HOST, stringResource(R.string.password_match_host)),
            ),
            selected = form.match,
            onSelect = viewModel::matchChanged,
            label = stringResource(R.string.password_field_match),
        )
        if (editing != null) {
            Previous(form, viewModel)
            ZekkeButton(
                stringResource(R.string.password_delete),
                { viewModel.askDelete(editing) },
                Modifier.fillMaxWidth(),
                ButtonVariant.Danger,
                enabled = !state.busy,
            )
        }
    }
}

@Composable
private fun Previous(form: PasswordForm, viewModel: PasswordsViewModel) {
    val palette = Zekke.palette
    form.previousFailure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it)) }
    val previous = form.previous
    when {
        previous == null -> ZekkeButton(
            stringResource(R.string.password_show_previous),
            viewModel::showPrevious,
            Modifier.fillMaxWidth(),
            ButtonVariant.Ghost,
        )
        previous.isEmpty() -> Text(stringResource(R.string.password_previous_none), style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.password_previous_title), style = MaterialTheme.typography.titleSmall, color = palette.inkSoft)
            previous.forEach { entry ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.passwords_masked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatMoment(entry.changedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    CopyButton(stringResource(R.string.passwords_copy), { entry.password })
                }
            }
        }
    }
}
