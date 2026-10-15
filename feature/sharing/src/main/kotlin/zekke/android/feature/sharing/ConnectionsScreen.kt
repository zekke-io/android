package zekke.android.feature.sharing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zekke.android.ui.components.Block
import zekke.android.ui.components.ButtonVariant
import zekke.android.ui.components.ConfirmDeleteDialog
import zekke.android.ui.components.EmptyState
import zekke.android.ui.components.FormSheet
import zekke.android.ui.components.ItemRow
import zekke.android.ui.components.Notice
import zekke.android.ui.components.NoticeTone
import zekke.android.ui.components.PrivateField
import zekke.android.ui.components.StatusBadge
import zekke.android.ui.components.Tone
import zekke.android.ui.components.ZekkeButton
import zekke.android.ui.components.itemFailureText
import zekke.android.ui.components.itemFailureTone
import zekke.android.ui.theme.MonoStyle
import zekke.android.ui.theme.Zekke

@Composable
fun alarmText(alarm: TrustAlarm): String = stringResource(
    when (alarm) {
        TrustAlarm.RootChanged -> R.string.sharing_alarm_root_changed
        TrustAlarm.ProofInvalid -> R.string.sharing_alarm_proof_invalid
        TrustAlarm.AccountChanged -> R.string.sharing_alarm_account_changed
        TrustAlarm.Unresolvable -> R.string.sharing_alarm_unresolvable
    },
)

fun alarmTone(alarm: TrustAlarm): NoticeTone = if (alarm.danger) NoticeTone.Danger else NoticeTone.Warning

@Composable
fun ConnectionsScreen(state: ConnectionsState, viewModel: ConnectionsViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        state.failure?.let { Notice(itemFailureText(it), tone = itemFailureTone(it), onDismiss = viewModel::dismissFailure) }
        state.invited?.let { Notice(stringResource(R.string.sharing_invite_sent, it), tone = NoticeTone.Success, onDismiss = viewModel::dismissInvited) }
        InviteBlock(state, viewModel)
        if (state.groups.awaitingMe.isNotEmpty()) {
            Block(title = stringResource(R.string.sharing_pending_inbound)) {
                Column {
                    state.groups.awaitingMe.forEach { item ->
                        ItemRow(
                            title = item.username,
                            trailing = { ZekkeButton(stringResource(R.string.sharing_review), { viewModel.startReview(item) }) },
                        )
                    }
                }
            }
        }
        Block(title = stringResource(R.string.sharing_connected), subtitle = stringResource(R.string.sharing_connected_subtitle)) {
            val shown = state.groups.accepted + state.groups.awaitingThem
            if (shown.isEmpty()) {
                EmptyState(stringResource(R.string.sharing_connections_empty), icon = zekke.android.ui.R.drawable.ic_sharing)
            } else {
                Column {
                    shown.forEach { item -> ConnectionRow(item, state, viewModel) }
                }
            }
        }
    }
    ReviewSheet(state, viewModel)
    ConnectionSheet(state, viewModel)
    state.confirmingDisconnect?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.sharing_disconnect_title, item.label),
            message = stringResource(R.string.sharing_disconnect_message),
            onDismiss = viewModel::cancelDisconnect,
            onConfirm = viewModel::confirmDisconnect,
            confirmLabel = stringResource(R.string.sharing_disconnect_confirm),
            busy = state.busy,
        )
    }
}

@Composable
private fun InviteBlock(state: ConnectionsState, viewModel: ConnectionsViewModel) {
    Block(title = stringResource(R.string.sharing_invite_title), subtitle = stringResource(R.string.sharing_invite_hint)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrivateField(
                value = state.username,
                onValueChange = viewModel::usernameChanged,
                label = stringResource(R.string.sharing_invite_label),
                singleLine = true,
                enabled = !state.inviting,
                imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                onImeAction = viewModel::invite,
                modifier = Modifier.fillMaxWidth(),
            )
            state.inviteProblem?.let { problem ->
                Notice(
                    when (problem) {
                        InviteProblem.Unknown -> stringResource(R.string.sharing_invite_unknown)
                        InviteProblem.Exists -> stringResource(R.string.sharing_invite_exists)
                        is InviteProblem.Refused -> problem.alarm?.let { alarmText(it) } ?: stringResource(R.string.sharing_invite_refused)
                    },
                    tone = NoticeTone.Danger,
                    onDismiss = viewModel::dismissInviteProblem,
                )
            }
            ZekkeButton(
                stringResource(if (state.inviting) R.string.sharing_invite_sending else R.string.sharing_invite_submit),
                viewModel::invite,
                Modifier.fillMaxWidth(),
                enabled = state.canInvite,
            )
            Notice(stringResource(R.string.sharing_reshare_warning), tone = NoticeTone.Warning)
        }
    }
}

@Composable
private fun ConnectionRow(item: ConnectionItem, state: ConnectionsState, viewModel: ConnectionsViewModel) {
    val alarm = state.alarms[item.id]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ItemRow(
            title = item.label,
            meta = if (item.nickname != null) item.username else null,
            metaIsValue = item.nickname != null,
            onClick = { viewModel.open(item) },
            divider = alarm == null,
            trailing = {
                if (alarm?.danger == true) StatusBadge(stringResource(R.string.sharing_alarm_badge), Tone.Danger)
                if (item.standing == ConnectionStanding.WaitingForThem) StatusBadge(stringResource(R.string.sharing_pending_outbound), Tone.Neutral)
            },
        )
        if (alarm != null) Notice(alarmText(alarm), tone = alarmTone(alarm), modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun ReviewSheet(state: ConnectionsState, viewModel: ConnectionsViewModel) {
    val review = state.review ?: return
    FormSheet(
        title = stringResource(R.string.sharing_fingerprint_title),
        submitLabel = stringResource(if (review.accepting) R.string.sharing_fingerprint_accepting else R.string.sharing_fingerprint_confirm),
        onSubmit = viewModel::accept,
        onDismiss = viewModel::closeReview,
        canSubmit = review.canAccept,
        busy = review.accepting,
    ) {
        Text(stringResource(R.string.sharing_fingerprint_why), style = MaterialTheme.typography.bodyMedium, color = Zekke.palette.inkSoft)
        review.alarm?.let { Notice(alarmText(it), tone = alarmTone(it)) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sharing_fingerprint_theirs, review.username), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
            Text(review.theirs ?: stringResource(R.string.sharing_fingerprint_loading), style = MonoStyle, color = Zekke.palette.ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sharing_fingerprint_mine), style = MaterialTheme.typography.titleSmall, color = Zekke.palette.inkSoft)
            Text(review.mine ?: stringResource(R.string.sharing_fingerprint_loading), style = MonoStyle, color = Zekke.palette.inkSoft)
        }
        ZekkeButton(
            stringResource(R.string.sharing_fingerprint_decline),
            viewModel::decline,
            Modifier.fillMaxWidth(),
            ButtonVariant.Secondary,
            enabled = !review.accepting,
        )
    }
}

@Composable
private fun ConnectionSheet(state: ConnectionsState, viewModel: ConnectionsViewModel) {
    val renaming = state.renaming ?: return
    val item = state.items.firstOrNull { it.id == renaming.connectionId } ?: return
    FormSheet(
        title = item.username,
        submitLabel = stringResource(if (state.busy) R.string.sharing_nickname_saving else R.string.sharing_nickname_save),
        onSubmit = viewModel::saveNickname,
        onDismiss = viewModel::closeDetail,
        busy = state.busy,
    ) {
        PrivateField(
            value = renaming.draft,
            onValueChange = viewModel::nicknameChanged,
            label = stringResource(R.string.sharing_nickname_label),
            singleLine = true,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = viewModel::saveNickname,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.sharing_nickname_hint), style = MaterialTheme.typography.bodySmall, color = Zekke.palette.inkMuted)
        if (state.canDisconnect && item.standing == ConnectionStanding.Accepted) {
            ZekkeButton(stringResource(R.string.sharing_detail_disconnect), { viewModel.askDisconnect(item) }, Modifier.fillMaxWidth(), ButtonVariant.Danger)
        }
    }
}
