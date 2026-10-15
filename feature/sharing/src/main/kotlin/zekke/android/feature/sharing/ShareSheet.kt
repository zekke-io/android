package zekke.android.feature.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.LoadingIndicator
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.Zekke

@Composable
fun ShareSheet(state: ShareState, viewModel: ShareViewModel, onClose: () -> Unit) {
    LaunchedEffect(viewModel) { viewModel.opened() }
    val noun = when (viewModel.type) {
        SharedType.Secret -> stringResource(R.string.share_noun_secret)
        SharedType.Note -> stringResource(R.string.share_noun_note)
        SharedType.Document -> stringResource(R.string.share_noun_document)
        SharedType.File -> stringResource(R.string.share_noun_file)
    }
    FormSheet(
        title = stringResource(R.string.share_title, noun),
        submitLabel = stringResource(if (state.busy) R.string.share_sending else R.string.share_send),
        onSubmit = viewModel::send,
        onDismiss = onClose,
        canSubmit = state.canSend,
        busy = state.busy,
    ) {
        state.notice?.let { notice ->
            Notice(
                when (notice) {
                    is SendNotice.Sent -> stringResource(R.string.share_sent, notice.username)
                    is SendNotice.SentNotFiled -> stringResource(R.string.share_sent_not_filed, notice.username)
                },
                tone = if (notice is SendNotice.Sent) NoticeTone.Success else NoticeTone.Warning,
                onDismiss = viewModel::dismissNotice,
            )
        }
        state.problem?.let { problem ->
            val text = when (problem) {
                is SendProblem.Failure -> itemFailureText(problem.failure)
                is SendProblem.Refused -> problem.alarm?.let { alarmText(it) } ?: stringResource(R.string.sharing_alarm_unaccepted)
                SendProblem.NotShared -> stringResource(R.string.share_not_shared)
            }
            val tone = (problem as? SendProblem.Failure)?.let { itemFailureTone(it.failure) } ?: NoticeTone.Danger
            Notice(text, tone = tone, onDismiss = viewModel::dismissProblem)
        }
        if (!state.loaded) {
            LoadingIndicator()
        } else if (state.connections.isEmpty() && state.recipients.isEmpty()) {
            EmptyState(stringResource(R.string.sharing_connections_empty), icon = zekke.android.ui.R.drawable.ic_sharing)
        } else {
            if (state.connections.isNotEmpty()) {
                Text(stringResource(R.string.share_send_to), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.ink)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.connections.forEach { connection ->
                        val selected = state.chosen == connection.id
                        ZekkeButton(
                            text = (if (selected) "✓ " else "") + connection.label,
                            onClick = { viewModel.choose(if (selected) null else connection.id) },
                            modifier = Modifier.fillMaxWidth(),
                            variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
                            enabled = !state.busy,
                        )
                    }
                }
            }
            if (state.chosen != null && state.choices.isNotEmpty()) {
                Text(stringResource(R.string.share_put_in), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.ink)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = listOf<FolderChoice?>(null) + state.choices
                    options.forEach { choice ->
                        val selected = state.folder == choice?.id
                        ZekkeButton(
                            text = (if (selected) "✓ " else "") + (choice?.name ?: stringResource(R.string.shared_move_top)),
                            onClick = { viewModel.chooseFolder(choice?.id) },
                            modifier = Modifier.fillMaxWidth().padding(start = ((choice?.depth ?: 0) * 16).dp),
                            variant = if (selected) ButtonVariant.Primary else ButtonVariant.Secondary,
                            enabled = !state.busy,
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.share_already_sent), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
        if (state.recipients.isEmpty()) {
            Text(stringResource(R.string.share_nobody), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        } else {
            Column {
                state.recipients.forEach { recipient ->
                    ItemRow(
                        title = recipient.username,
                        trailing = if (state.canRemove) {
                            { ZekkeButton(stringResource(R.string.share_remove), { viewModel.remove(recipient) }, variant = ButtonVariant.Ghost, enabled = !state.busy) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
